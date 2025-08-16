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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TetheredHarpoonEntity extends AbstractArrow {
    private static final Logger LOGGER = LoggerFactory.getLogger(TetheredHarpoonEntity.class);
    private Player owner;
    private BlockPos anchoredPos;
    private boolean anchored = false;
    private boolean retrieving = false;
    private Entity hitEntity = null;
    private int hitTick = 0; // 添加命中时的tick
    private int deathDelayTicks = 0;
    private static final int MAX_DEATH_DELAY = 10;
    private Entity lastHitEntity = null;
    private Player lastHitPlayer = null;
    public HarpoonState currentState = HarpoonState.ANCHORED; // 默认为锚定状态，因为现在是瞬间命中

    private static final EntityDataAccessor<Boolean> DATA_ANCHORED = SynchedEntityData.defineId(TetheredHarpoonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_RETRIEVING = SynchedEntityData.defineId(TetheredHarpoonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY = SynchedEntityData.defineId(TetheredHarpoonEntity.class, EntityDataSerializers.INT);

    public enum HarpoonState {
        FLYING, HOOKED_IN_ENTITY, ANCHORED
    }

    public TetheredHarpoonEntity(EntityType<? extends TetheredHarpoonEntity> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
        this.setBoundingBox(this.makeBoundingBox());
    }

    public TetheredHarpoonEntity(Level level, Player owner, Vec3 position) {
        this(CreateFisheryEntityTypes.TETHERED_HARPOON.get(), level);
        this.owner = owner;
        this.setPos(position);
        this.setOwner(owner);
        this.setBoundingBox(this.makeBoundingBox());
        // 设置为无运动状态，因为是瞬间命中
        this.setDeltaMovement(Vec3.ZERO);
        // 设置不受重力影响
        this.setNoGravity(true);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d0 = this.getBoundingBox().getSize() * 10.0D;
        if (Double.isNaN(d0)) {
            d0 = 1.0D;
        }
        d0 *= 64.0D * getViewScale();
        return distance < d0 * d0;
    }

    @Override
    public boolean isInvisible() {
        return false;
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
        if (DATA_HOOKED_ENTITY.equals(key)) {
            int id = this.getEntityData().get(DATA_HOOKED_ENTITY);
            this.hitEntity = id > 0 ? this.level().getEntity(id - 1) : null;
            this.currentState = this.hitEntity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.ANCHORED;
        }
        if (DATA_ANCHORED.equals(key)) {
            this.anchored = this.getEntityData().get(DATA_ANCHORED);
            this.currentState = this.anchored ? HarpoonState.ANCHORED : HarpoonState.HOOKED_IN_ENTITY;
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
            }
        }
    }

    public void setHitEntity(Entity entity, int tick) {
        this.hitEntity = entity;
        this.hitTick = tick; // 记录命中时的tick
        this.deathDelayTicks = 0;
        this.lastHitEntity = entity;
        this.lastHitPlayer = this.owner;
        this.getEntityData().set(DATA_HOOKED_ENTITY, entity != null ? entity.getId() + 1 : 0);
        this.currentState = entity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.ANCHORED;
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
            }
        }
    }

    private ItemStack getHarpoonGunFromPlayer(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            return mainHand;
        } else if (offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            return offHand;
        }
        return null;
    }

    public Entity getHitEntity() {
        return this.hitEntity;
    }

    public int getHitTick() {
        return this.hitTick;
    }

    public Entity getLastHitEntity() {
        return this.lastHitEntity;
    }

    public Player getLastHitPlayer() {
        return this.lastHitPlayer;
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
                    tag.remove("AccumulatedAirConsumption");
                });
            }
        }
        this.discard();
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return entity != owner && entity.isAlive() && super.canHitEntity(entity);
    }

    public void pullLootToPlayer(Entity deadEntity) {
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
        long tetherEndTime = serverLevel.getGameTime() + 200;

        for (ItemStack item : lootTable.getRandomItems(params)) {
            ItemEntity drop = new ItemEntity(level(), deadEntity.getX(), deadEntity.getY(), deadEntity.getZ(), item);
            CompoundTag tag = drop.getPersistentData();
            tag.putBoolean("HarpoonTethered", true);
            tag.putInt("TetherPlayerId", player.getId());
            tag.putLong("TetherEndTime", tetherEndTime);
            double dx = player.getX() - drop.getX();
            double dy = player.getY() - drop.getY();
            double dz = player.getZ() - drop.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            drop.setDeltaMovement(dx * 0.2, dy * 0.2 + Math.sqrt(distance) * 0.1, dz * 0.2);
            drop.setPickUpDelay(10);
            level().addFreshEntity(drop);
        }

        int xp = ((LivingEntity) deadEntity).getExperienceReward(serverLevel, this);
        if (xp > 0) {
            ExperienceOrb orb = new ExperienceOrb(level(), deadEntity.getX(), deadEntity.getY(), deadEntity.getZ(), xp);
            CompoundTag tag = orb.getPersistentData();
            tag.putBoolean("HarpoonTethered", true);
            tag.putInt("TetherPlayerId", player.getId());
            tag.putLong("TetherEndTime", tetherEndTime);
            double dx = player.getX() - orb.getX();
            double dy = player.getY() - orb.getY();
            double dz = player.getZ() - orb.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            orb.setDeltaMovement(dx * 0.2, dy * 0.2 + Math.sqrt(distance) * 0.1, dz * 0.2);
            level().addFreshEntity(orb);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level().isClientSide) {
            if (retrieving) {
                this.discard();
                return;
            }

            // 处理命中实体的情况
            if (currentState == HarpoonState.HOOKED_IN_ENTITY) {
                if (hitEntity != null && hitEntity.isAlive() && hitEntity.level().dimension() == this.level().dimension()) {
                    Vec3 targetPos = hitEntity.position().add(0, hitEntity.getBbHeight() * 0.5, 0);
                    this.setPos(targetPos);
                    this.setDeltaMovement(Vec3.ZERO);
                    this.deathDelayTicks = 0;
                } else {
                    if (deathDelayTicks < MAX_DEATH_DELAY && hitEntity != null) {
                        this.deathDelayTicks++;
                    } else {
                        this.startRetrieving();
                    }
                }
                return;
            }

            // 处理锚定在方块的情况
            if (currentState == HarpoonState.ANCHORED) {
                if (anchoredPos != null && !level().isEmptyBlock(anchoredPos)) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(getAnchoredPosition());
                } else {
                    this.startRetrieving();
                }
                return;
            }

            // 超时移除
            if (tickCount > 400) {
                startRetrieving();
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
        hitTick = compound.getInt("HitTick"); // 读取hitTick
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
            this.lastHitEntity = this.hitEntity;
            this.currentState = this.hitEntity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.ANCHORED;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        try {
            super.addAdditionalSaveData(compound);
        } catch (IllegalStateException e) {
            compound.putByte("pickup", (byte) this.pickup.ordinal());
            compound.putDouble("damage", this.getBaseDamage());
            compound.putBoolean("crit", this.isCritArrow());
        }

        compound.putBoolean("Anchored", anchored);
        compound.putBoolean("Retrieving", retrieving);
        compound.putInt("HitTick", hitTick); // 保存hitTick
        if (anchoredPos != null) {
            compound.putInt("AnchoredX", anchoredPos.getX());
            compound.putInt("AnchoredY", anchoredPos.getY());
            compound.putInt("AnchoredZ", anchoredPos.getZ());
        }
        compound.putInt("HookedEntity", hitEntity != null ? hitEntity.getId() : 0);
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