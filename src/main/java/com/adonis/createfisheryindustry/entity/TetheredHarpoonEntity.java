package com.adonis.createfisheryindustry.entity;

import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TetheredHarpoonEntity extends AbstractArrow {
    private static final Logger LOGGER = LoggerFactory.getLogger(TetheredHarpoonEntity.class);
    private Player owner;
    private BlockPos anchoredPos;
    private boolean anchored = false;
    private boolean retrieving = false;
    private Entity hitEntity = null;
    private int hitTick = 0;
    private int ignoreCollisionTicks = 5;
    public HarpoonState currentState = HarpoonState.FLYING;

    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(TetheredHarpoonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RETRIEVING = SynchedEntityData.defineId(TetheredHarpoonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY = SynchedEntityData.defineId(TetheredHarpoonEntity.class, EntityDataSerializers.INT);

    public enum HarpoonState {
        FLYING, HOOKED_IN_ENTITY, ANCHORED
    }

    public TetheredHarpoonEntity(EntityType<? extends TetheredHarpoonEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        LOGGER.debug("Harpoon entity initialized without owner");
    }

    public TetheredHarpoonEntity(Level level, Player owner, Vec3 position) {
        super(CreateFisheryEntityTypes.TETHERED_HARPOON.get(), owner, level, ItemStack.EMPTY, null);
        this.owner = owner;
        this.setPos(position);
        this.setOwner(owner);
        LOGGER.info("Harpoon entity created for player {} at pos {}", owner != null ? owner.getName().getString() : "null", position);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ANCHORED, false);
        builder.define(DATA_RETRIEVING, false);
        builder.define(DATA_HOOKED_ENTITY, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        try {
            if (DATA_HOOKED_ENTITY.equals(key)) {
                int id = this.getEntityData().get(DATA_HOOKED_ENTITY);
                this.hitEntity = id > 0 ? this.level().getEntity(id - 1) : null;
                this.currentState = this.hitEntity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.FLYING;
                LOGGER.info("Harpoon synced hooked entity for player {}: entityId={}, hitEntity={}",
                        owner != null ? owner.getName().getString() : "null", id, hitEntity != null ? hitEntity.getType().toString() : "null");
            }
            if (DATA_ANCHORED.equals(key)) {
                this.anchored = this.getEntityData().get(DATA_ANCHORED);
                this.currentState = this.anchored ? HarpoonState.ANCHORED : HarpoonState.FLYING;
                LOGGER.info("Harpoon synced anchored state for player {}: anchored={}",
                        owner != null ? owner.getName().getString() : "null", anchored);
            }
        } catch (Exception e) {
            LOGGER.error("Harpoon sync error for player {}: {}", owner != null ? owner.getName().getString() : "null", e.getMessage(), e);
        }
    }

    public boolean isAnchored() {
        return this.getEntityData().get(DATA_ANCHORED);
    }

    public boolean isRetrieving() {
        return this.getEntityData().get(DATA_RETRIEVING);
    }

    public Vec3 getAnchoredPosition() {
        return anchoredPos != null ? new Vec3(anchoredPos.getX() + 0.5, anchoredPos.getY() + 0.5, anchoredPos.getZ() + 0.5) : this.position();
    }

    public void setAnchored(BlockPos pos) {
        this.anchoredPos = pos;
        this.anchored = true;
        this.getEntityData().set(DATA_ANCHORED, true);
        this.currentState = HarpoonState.ANCHORED;
        if (!this.level().isClientSide && this.owner != null) {
            ItemStack harpoonGun = getHarpoonGunFromPlayer(owner);
            if (harpoonGun != null) {
                CustomData.update(DataComponents.CUSTOM_DATA, harpoonGun, tag -> {
                    tag.putBoolean("tagHooked", true);
                    tag.putDouble("xPostion", pos.getX() + 0.5);
                    tag.putDouble("yPostion", pos.getY() + 0.5);
                    tag.putDouble("zPostion", pos.getZ() + 0.5);
                    tag.remove("tagHookedEntityId");
                });
                LOGGER.info("Harpoon anchored for player {} at pos {}, tagHooked set to true", owner.getName().getString(), pos);
            } else {
                LOGGER.warn("Harpoon failed to update gun data for player {}: no harpoon gun found", owner.getName().getString());
            }
        }
    }

    public void setHitEntity(Entity entity, int tick) {
        this.hitEntity = entity;
        this.hitTick = tick;
        this.getEntityData().set(DATA_HOOKED_ENTITY, entity != null ? entity.getId() + 1 : 0);
        this.currentState = entity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.FLYING;
        if (!this.level().isClientSide && this.owner != null) {
            ItemStack harpoonGun = getHarpoonGunFromPlayer(owner);
            if (harpoonGun != null) {
                CustomData.update(DataComponents.CUSTOM_DATA, harpoonGun, tag -> {
                    tag.putBoolean("tagHooked", true);
                    tag.putInt("tagHookedEntityId", entity != null ? entity.getId() : 0);
                    tag.remove("xPostion");
                    tag.remove("yPostion");
                    tag.remove("zPostion");
                });
                LOGGER.info("Harpoon hooked entity {} for player {}, tagHooked set to true, entityId={}",
                        entity != null ? entity.getType().toString() : "null", owner.getName().getString(), entity != null ? entity.getId() : 0);
            } else {
                LOGGER.warn("Harpoon failed to update gun data for player {}: no harpoon gun found", owner.getName().getString());
            }
        }
    }

    private ItemStack getHarpoonGunFromPlayer(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            LOGGER.debug("Harpoon gun found in main hand for player {}", player.getName().getString());
            return mainHand;
        } else if (offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            LOGGER.debug("Harpoon gun found in off hand for player {}", player.getName().getString());
            return offHand;
        }
        LOGGER.warn("Harpoon gun not found for player {}", player.getName().getString());
        return null;
    }

    public Entity getHitEntity() {
        return this.hitEntity;
    }

    public int getHitTick() {
        return this.hitTick;
    }

    public void startRetrieving() {
        this.retrieving = true;
        this.anchored = false;
        this.hitEntity = null;
        this.anchoredPos = null;
        this.getEntityData().set(DATA_ANCHORED, false);
        this.getEntityData().set(DATA_RETRIEVING, true);
        this.getEntityData().set(DATA_HOOKED_ENTITY, 0);
        this.currentState = HarpoonState.FLYING;
        if (!this.level().isClientSide && this.owner != null) {
            ItemStack harpoonGun = getHarpoonGunFromPlayer(owner);
            if (harpoonGun != null) {
                CustomData.update(DataComponents.CUSTOM_DATA, harpoonGun, tag -> {
                    tag.putBoolean("tagHooked", false);
                    tag.remove("tagHookedEntityId");
                    tag.remove("xPostion");
                    tag.remove("yPostion");
                    tag.remove("zPostion");
                });
                LOGGER.info("Harpoon retrieving for player {}, tagHooked set to false", owner.getName().getString());
            }
        }
        this.discard();
        LOGGER.info("Harpoon discarded for player {}", owner != null ? owner.getName().getString() : "null");
    }

    public void shoot(Vec3 direction, float speed) {
        this.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        this.currentState = HarpoonState.FLYING;
        LOGGER.info("Harpoon shot for player {}: speed={}, direction={}", owner != null ? owner.getName().getString() : "null", speed, direction);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != owner && entity.isAlive() && super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!this.level().isClientSide) {
            Entity target = result.getEntity();
            if (target instanceof LivingEntity living) {
                // 造成 14 点伤害
                living.hurt(this.damageSources().trident(this, owner != null ? owner : this), 14.0F);
                this.setHitEntity(living, this.tickCount);
                this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
                LOGGER.info("Harpoon hit entity {} for player {} at pos {}, dealt 14 damage",
                        target.getType().toString(), owner != null ? owner.getName().getString() : "null", result.getLocation());
            }
        }
        // 继续飞行，不停止
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            BlockPos pos = result.getBlockPos();
            this.setAnchored(pos);
            this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 1.0F);
            LOGGER.info("Harpoon hit block for player {} at pos {}", owner != null ? owner.getName().getString() : "null", pos);
        }
    }

    private void checkCollision() {
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            LOGGER.debug("Harpoon collision detected for player {}: type={}, pos={}", owner != null ? owner.getName().getString() : "null", hitResult.getType(), hitResult.getLocation());
            this.onHit(hitResult);
        } else {
            Vec3 start = this.position();
            Vec3 end = start.add(this.getDeltaMovement());
            ClipContext clipContext = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
            hitResult = level().clip(clipContext);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                LOGGER.debug("Harpoon block collision detected for player {} at pos {}", owner != null ? owner.getName().getString() : "null", hitResult.getLocation());
                this.onHit(hitResult);
            } else {
                LOGGER.debug("Harpoon no collision at tick {} for player {}: pos={}, motion={}", tickCount, owner != null ? owner.getName().getString() : "null", position(), getDeltaMovement());
            }
        }
    }

    private void pullLootToPlayer(Entity deadEntity) {
        if (!(getOwner() instanceof Player player) || !(level() instanceof ServerLevel serverLevel) || !(deadEntity instanceof Mob mob)) {
            return;
        }

        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) {
            return;
        }

        LootParams.Builder paramsBuilder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSources().playerAttack(player))
                .withParameter(LootContextParams.ATTACKING_ENTITY, this);

        if (player instanceof ServerPlayer serverPlayer) {
            paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
        }

        LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(lootTableKey);
        for (ItemStack item : lootTable.getRandomItems(params)) {
            ItemEntity drop = new ItemEntity(level(), deadEntity.getX(), deadEntity.getY(), deadEntity.getZ(), item);
            Vec3 motion = player.position().add(0, 1, 0).subtract(drop.position()).normalize().scale(0.25);
            drop.setDeltaMovement(motion);
            level().addFreshEntity(drop);
        }

        int xp = ((LivingEntity) deadEntity).getExperienceReward(serverLevel, this);
        if (xp > 0) {
            level().addFreshEntity(new ExperienceOrb(level(), player.getX(), player.getY(), player.getZ(), xp));
        }
        LOGGER.info("Harpoon pulled loot to player {} from entity {}", player.getName().getString(), deadEntity.getType().toString());
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            LOGGER.debug("Harpoon tick for player {}: state={}, pos={}, motion={}",
                    owner != null ? owner.getName().getString() : "null", currentState, position(), getDeltaMovement());

            if (retrieving) {
                this.discard();
                return;
            }

            if (currentState == HarpoonState.HOOKED_IN_ENTITY) {
                if (hitEntity != null && hitEntity.isAlive() && hitEntity.level().dimension() == this.level().dimension()) {
                    Vec3 targetPos = hitEntity.position().add(0, hitEntity.getBbHeight() * 0.5, 0);
                    this.setPos(targetPos);
                    this.setDeltaMovement(Vec3.ZERO);
                    LOGGER.debug("Harpoon following entity {} for player {} at pos {}",
                            hitEntity.getType().toString(), owner != null ? owner.getName().getString() : "null", targetPos);
                    if (tickCount - hitTick <= 4 && !hitEntity.isAlive()) {
                        pullLootToPlayer(hitEntity);
                        this.startRetrieving();
                        LOGGER.info("Harpoon entity died within 4 ticks for player {}, retrieving with loot",
                                owner != null ? owner.getName().getString() : "null");
                    }
                } else {
                    if (hitEntity != null && !hitEntity.isAlive()) {
                        pullLootToPlayer(hitEntity);
                    }
                    this.startRetrieving();
                    LOGGER.info("Harpoon entity dead or removed for player {}, retrieving",
                            owner != null ? owner.getName().getString() : "null");
                }
                return;
            }

            if (currentState == HarpoonState.ANCHORED) {
                if (anchoredPos != null && !level().isEmptyBlock(anchoredPos)) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(getAnchoredPosition());
                    LOGGER.debug("Harpoon anchored for player {} at pos {}",
                            owner != null ? owner.getName().getString() : "null", anchoredPos);
                } else {
                    this.startRetrieving();
                    LOGGER.info("Harpoon anchor block gone for player {}, retrieving",
                            owner != null ? owner.getName().getString() : "null");
                }
                return;
            }

            if (tickCount <= ignoreCollisionTicks) {
                LOGGER.debug("Harpoon ignoring collisions for player {} at tick {}",
                        owner != null ? owner.getName().getString() : "null", tickCount);
                return;
            }

            if (currentState == HarpoonState.FLYING) {
                this.checkCollision();
                if (hitEntity != null) {
                    this.currentState = HarpoonState.HOOKED_IN_ENTITY;
                    this.setDeltaMovement(Vec3.ZERO);
                    LOGGER.info("Harpoon state changed to HOOKED_IN_ENTITY for player {}",
                            owner != null ? owner.getName().getString() : "null");
                    return;
                }
                if (anchoredPos != null) {
                    this.currentState = HarpoonState.ANCHORED;
                    this.setDeltaMovement(Vec3.ZERO);
                    LOGGER.info("Harpoon state changed to ANCHORED for player {}",
                            owner != null ? owner.getName().getString() : "null");
                    return;
                }
            }

            if (tickCount > 400) {
                startRetrieving();
                LOGGER.info("Harpoon timed out for player {}, retrieving",
                        owner != null ? owner.getName().getString() : "null");
            }
        }
    }

    @Override
    public void playerTouch(Player entity) {
        if (this.ownedBy(entity) || this.getOwner() == null) {
            super.playerTouch(entity);
        }
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    protected SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        anchored = compound.getBoolean("Anchored");
        retrieving = compound.getBoolean("Retrieving");
        if (compound.contains("AnchoredX")) {
            anchoredPos = new BlockPos(
                    compound.getInt("AnchoredX"),
                    compound.getInt("AnchoredY"),
                    compound.getInt("AnchoredZ")
            );
        }
        int hookedId = compound.getInt("HookedEntity");
        if (hookedId > 0 && this.level() instanceof ServerLevel serverLevel) {
            this.hitEntity = serverLevel.getEntity(hookedId);
            this.currentState = this.hitEntity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.FLYING;
        }
        LOGGER.debug("Harpoon loaded save data for player {}: anchored={}, retrieving={}, hookedId={}",
                owner != null ? owner.getName().getString() : "null", anchored, retrieving, hookedId);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Anchored", anchored);
        compound.putBoolean("Retrieving", retrieving);
        if (anchoredPos != null) {
            compound.putInt("AnchoredX", anchoredPos.getX());
            compound.putInt("AnchoredY", anchoredPos.getY());
            compound.putInt("AnchoredZ", anchoredPos.getZ());
        }
        compound.putInt("HookedEntity", hitEntity != null ? hitEntity.getId() : 0);
        LOGGER.debug("Harpoon saved data for player {}: anchored={}, retrieving={}, hookedId={}",
                owner != null ? owner.getName().getString() : "null", anchored, retrieving, hitEntity != null ? hitEntity.getId() : 0);
    }

    @Override
    protected float getWaterInertia() {
        return 0.99F;
    }

    @Override
    public boolean shouldRender(double x, double y, double z) {
        return true;
    }
}