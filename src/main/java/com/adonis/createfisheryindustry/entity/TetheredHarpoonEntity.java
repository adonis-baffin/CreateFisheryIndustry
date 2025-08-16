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
import net.minecraft.world.item.Items;
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

import java.util.ArrayList;
import java.util.List;

public class TetheredHarpoonEntity extends AbstractArrow {
    private static final Logger LOGGER = LoggerFactory.getLogger(TetheredHarpoonEntity.class);
    private Player owner;
    private BlockPos anchoredPos;
    private boolean anchored = false;
    private boolean retrieving = false;
    private Entity hitEntity = null;
    private int hitTick = 0;
    private int ignoreCollisionTicks = 5;
    private int deathDelayTicks = 0; // 实体死亡后的延迟计数
    private static final int MAX_DEATH_DELAY = 10; // 延迟 10 tick
    private Entity lastHitEntity = null; // 缓存最近命中的实体
    private Player lastHitPlayer = null; // 缓存最近命中的玩家
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
        // 设置实体大小，确保它能被渲染
        this.setBoundingBox(this.makeBoundingBox());
    }

    public TetheredHarpoonEntity(Level level, Player owner, Vec3 position) {
        // 修复1：使用正确的构造函数，不传递ItemStack
        this(CreateFisheryEntityTypes.TETHERED_HARPOON.get(), level);
        this.owner = owner;
        this.setPos(position);
        this.setOwner(owner);
        // 确保实体有正确的边界框
        this.setBoundingBox(this.makeBoundingBox());
    }

    // 添加这个方法确保实体总是被渲染
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        // 增加渲染距离
        double d0 = this.getBoundingBox().getSize() * 10.0D;
        if (Double.isNaN(d0)) {
            d0 = 1.0D;
        }
        d0 *= 64.0D * getViewScale();
        return distance < d0 * d0;
    }

    // 确保实体可见
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
            this.currentState = this.hitEntity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.FLYING;
        }
        if (DATA_ANCHORED.equals(key)) {
            this.anchored = this.getEntityData().get(DATA_ANCHORED);
            this.currentState = this.anchored ? HarpoonState.ANCHORED : HarpoonState.FLYING;
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
        this.hitTick = tick;
        this.deathDelayTicks = 0; // 重置死亡延迟
        this.lastHitEntity = entity; // 缓存命中实体
        this.lastHitPlayer = this.owner; // 缓存命中玩家
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

    public void shoot(Vec3 direction, float speed) {
        this.shoot(direction.x, direction.y, direction.z, speed, 0.0F);
        this.currentState = HarpoonState.FLYING;
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
                // 先设置状态，确保事件触发前状态正确
                this.setHitEntity(living, this.tickCount);
                // 再造成伤害
                living.hurt(this.damageSources().trident(this, owner != null ? owner : this), 7.0F); // 降低伤害
                if (owner instanceof Player player) {
                    living.setLastHurtByPlayer(player); // 记录玩家为击杀者
                }
                this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
            }
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            BlockPos pos = result.getBlockPos();
            this.setAnchored(pos);
            this.playSound(SoundEvents.TRIDENT_HIT_GROUND, 1.0F, 1.0F);
        }
    }

    private void checkCollision() {
        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
        } else {
            Vec3 start = this.position();
            Vec3 end = start.add(this.getDeltaMovement());
            ClipContext clipContext = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
            hitResult = level().clip(clipContext);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                this.onHit(hitResult);
            }
        }
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
        long tetherEndTime = serverLevel.getGameTime() + 200; // 10秒后结束牵引（20 tick/秒 * 10秒）
        for (ItemStack item : lootTable.getRandomItems(params)) {
            ItemEntity drop = new ItemEntity(level(), deadEntity.getX(), deadEntity.getY(), deadEntity.getZ(), item);
            // 添加牵引标记
            CompoundTag tag = drop.getPersistentData();
            tag.putBoolean("HarpoonTethered", true);
            tag.putInt("TetherPlayerId", player.getId());
            tag.putLong("TetherEndTime", tetherEndTime);
            double dx = player.getX() - drop.getX();
            double dy = player.getY() - drop.getY();
            double dz = player.getZ() - drop.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            drop.setDeltaMovement(dx * 0.2, dy * 0.2 + Math.sqrt(distance) * 0.1, dz * 0.2); // 初始速度
            drop.setPickUpDelay(10);
            level().addFreshEntity(drop);
        }

        int xp = ((LivingEntity) deadEntity).getExperienceReward(serverLevel, this);
        if (xp > 0) {
            ExperienceOrb orb = new ExperienceOrb(level(), deadEntity.getX(), deadEntity.getY(), deadEntity.getZ(), xp);
            // 添加牵引标记
            CompoundTag tag = orb.getPersistentData();
            tag.putBoolean("HarpoonTethered", true);
            tag.putInt("TetherPlayerId", player.getId());
            tag.putLong("TetherEndTime", tetherEndTime);
            double dx = player.getX() - orb.getX();
            double dy = player.getY() - orb.getY();
            double dz = player.getZ() - orb.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            orb.setDeltaMovement(dx * 0.2, dy * 0.2 + Math.sqrt(distance) * 0.1, dz * 0.2); // 初始速度
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

            if (currentState == HarpoonState.ANCHORED) {
                if (anchoredPos != null && !level().isEmptyBlock(anchoredPos)) {
                    this.setDeltaMovement(Vec3.ZERO);
                    this.setPos(getAnchoredPosition());
                } else {
                    this.startRetrieving();
                }
                return;
            }

            if (tickCount <= ignoreCollisionTicks) {
                return;
            }

            if (currentState == HarpoonState.FLYING) {
                // 改进的物品检测
                if (tickCount > 2) {
                    // 使用位置而不是碰撞盒来检测
                    Vec3 harpoonPos = this.position();
                    double detectionRadius = 3.0;

                    // 获取附近所有实体，然后筛选ItemEntity
                    List<Entity> allEntities = level().getEntities(this,
                            this.getBoundingBox().inflate(detectionRadius * 2));

                    List<ItemEntity> nearbyItems = new ArrayList<>();
                    int entityCount = 0;

                    for (Entity entity : allEntities) {
                        entityCount++;
                        if (entity instanceof ItemEntity item) {
                            double distance = item.position().distanceTo(harpoonPos);
                            if (distance <= detectionRadius && !item.isRemoved() && item.isAlive()) {
                                nearbyItems.add(item);
                            }
                        }
                    }

                    if (!nearbyItems.isEmpty()) {
                        // 使用 getOwner() 而不是 owner 字段
                        Entity ownerEntity = this.getOwner();
                        if (ownerEntity instanceof Player player) {

                            long tetherEndTime = level().getGameTime() + 200;
                            for (ItemEntity item : nearbyItems) {
                                CompoundTag tag = item.getPersistentData();
                                tag.putBoolean("HarpoonTethered", true);
                                tag.putInt("TetherPlayerId", player.getId());
                                tag.putLong("TetherEndTime", tetherEndTime);

                                // 初始速度
                                Vec3 itemPos = item.position();
                                Vec3 playerPos = player.position();
                                Vec3 direction = playerPos.subtract(itemPos).normalize();
                                item.setDeltaMovement(direction.scale(0.5));
                                item.setPickUpDelay(10);
                            }

                            this.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 1.0F);
                            this.startRetrieving();
                            return;
                        }
                    }
                }

                // 碰撞检测
                this.checkCollision();
                if (hitEntity != null) {
                    this.currentState = HarpoonState.HOOKED_IN_ENTITY;
                    this.setDeltaMovement(Vec3.ZERO);
                    return;
                }
                if (anchoredPos != null) {
                    this.currentState = HarpoonState.ANCHORED;
                    this.setDeltaMovement(Vec3.ZERO);
                    return;
                }
            }

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
            this.lastHitEntity = this.hitEntity; // 恢复缓存
            this.currentState = this.hitEntity != null ? HarpoonState.HOOKED_IN_ENTITY : HarpoonState.FLYING;
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        // 修复：安全的NBT保存，避免空ItemStack导致的传送门崩溃
        try {
            super.addAdditionalSaveData(compound);
        } catch (IllegalStateException e) {
            compound.putByte("pickup", (byte) this.pickup.ordinal());
            compound.putDouble("damage", this.getBaseDamage());
            compound.putBoolean("crit", this.isCritArrow());
        }

        // 保存自定义数据
        compound.putBoolean("Anchored", anchored);
        compound.putBoolean("Retrieving", retrieving);
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