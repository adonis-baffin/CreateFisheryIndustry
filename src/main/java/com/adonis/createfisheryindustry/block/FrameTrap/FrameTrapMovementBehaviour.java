package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

import java.util.List;

public class FrameTrapMovementBehaviour implements MovementBehaviour {
    // 可调参数：最大碰撞箱尺寸（宽、高、深）
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    @Override
    public void tick(MovementContext context) {
        if (context.world instanceof ServerLevel level) {
            FrameTrapContext fishing = getFishingNetContext(context, level);
            if (fishing.timeUntilCatch > 0) {
                fishing.timeUntilCatch--;
            }
        }
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (context.world instanceof ServerLevel level) {
            FrameTrapContext fishing = getFishingNetContext(context, level);
            boolean inWater = fishing.visitNewPosition(level, pos);
            killNearbyEntities(context, pos, level);
            collectNearbyItems(context, pos, level);
            if (!inWater || fishing.timeUntilCatch > 0) return;

            if (fishing.canCatch()) {
                LootParams params = fishing.buildLootContext(context, level, pos);
                LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
                List<ItemStack> loots = lootTable.getRandomItems(params);

                List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
                ServerPlayer selectedPlayer = null;
                if (!players.isEmpty()) {
                    Player nearestPlayer = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, -1, false);
                    if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                        selectedPlayer = serverPlayer;
                    }
                }

                FishingHook fishingHook = selectedPlayer != null ? new FishingHook(selectedPlayer, level, 0, 0) : null;
                ItemFishedEvent event = NeoForge.EVENT_BUS.post(new ItemFishedEvent(loots, 0, fishingHook));
                if (!event.isCanceled()) {
                    loots.forEach(stack -> dropItem(context, stack));
                    addExperienceNugget(context, level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    spawnParticles(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, inWater);
                }
                fishing.reset(level);
            }
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof FrameTrapContext fishing && context.world instanceof ServerLevel level) {
            fishing.invalidate(level);
        }
    }

    protected FrameTrapContext getFishingNetContext(MovementContext context, ServerLevel level) {
        if (context.temporaryData == null || !(context.temporaryData instanceof FrameTrapContext)) {
            context.temporaryData = new FrameTrapContext(level, new ItemStack(Items.FISHING_ROD));
        }
        return (FrameTrapContext) context.temporaryData;
    }

    protected void collectNearbyItems(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.2);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                dropItem(context, stack.copy());
                itemEntity.discard();
            }
        }
    }

    protected void killNearbyEntities(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        // 强制刷新配置缓存，确保使用最新的白名单/黑名单
        CreateFisheryCommonConfig.refreshCache();

        for (Entity entity : entities) {
            // 仅处理Mob类型实体
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            EntityType<?> entityType = entity.getType();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

            // 调试日志
            if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Found breeze entity: {}", entityId);
            }

            // 规则1：黑名单中的实体直接跳过，绝对不捕获
            if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
                if (entityId.toString().equals("minecraft:breeze")) {
                    CreateFisheryMod.LOGGER.debug("Breeze is in blacklist, skipping");
                }
                continue;
            }

            // 规则2：白名单中的实体直接捕获，不考虑大小限制
            if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
                if (entityId.toString().equals("minecraft:breeze")) {
                    CreateFisheryMod.LOGGER.debug("Breeze is in whitelist, capturing");
                }
                processEntityDrops(context, level, mob);
                continue;
            }

            // 如果是breeze实体但执行到这里，说明既不在白名单也不在黑名单
            if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Breeze is neither in whitelist nor blacklist, checking size");
            }

            // 规则3：既不在白名单也不在黑名单的实体，根据尺寸判断是否捕获
            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Breeze size: {}x{}x{}, limit: {}",
                        width, height, depth, MAX_COLLISION_BOX_SIZE);
            }

            if (width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE) {
                if (entityId.toString().equals("minecraft:breeze")) {
                    CreateFisheryMod.LOGGER.debug("Breeze size check passed, capturing");
                }
                processEntityDrops(context, level, mob);
            } else if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Breeze size check failed, not capturing");
            }
        }
    }

    private void processEntityDrops(MovementContext context, ServerLevel level, Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        CreateFisheryMod.LOGGER.debug("Processing drops for entity: {}", entityId);

        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) return;

        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            Player nearestPlayer = level.getNearestPlayer(mob, -1);
            if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
            }
        }

        LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);

        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
        List<ItemStack> loots = lootTable.getRandomItems(params);
        for (ItemStack stack : loots) {
            dropItem(context, stack);
        }

        mob.setRemoved(Entity.RemovalReason.KILLED);
        addExperienceNugget(context, level, mob.getX(), mob.getY() + 0.5, mob.getZ());
        spawnParticles(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), level.getFluidState(new BlockPos((int) mob.getX(), (int) mob.getY(), (int) mob.getZ())).is(net.minecraft.world.level.material.Fluids.WATER));

        // 记录实体被成功捕获
        CreateFisheryMod.LOGGER.debug("Successfully captured entity: {}", entityId);
    }

    private void addExperienceNugget(MovementContext context, ServerLevel level, double x, double y, double z) {
        Item expNuggetItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));
        if (expNuggetItem != null && expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            dropItem(context, expNugget);
            CreateFisheryMod.LOGGER.debug("Added experience nugget at ({}, {}, {})", x, y, z);
        } else {
            CreateFisheryMod.LOGGER.warn("Experience nugget item not found, falling back to ExperienceOrb at ({}, {}, {})", x, y, z);
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, x, y, z, 1));
        }
    }

    private void spawnParticles(ServerLevel level, double x, double y, double z, boolean inWater) {
        // 根据水下状态选择粒子类型
        var particleType = inWater ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD;
        level.sendParticles(particleType, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        CreateFisheryMod.LOGGER.debug("Spawned {} particles at ({}, {}, {}), inWater: {}",
                particleType == ParticleTypes.BUBBLE ? "BUBBLE" : "CLOUD", x, y, z, inWater);
    }

    protected boolean isEntityWhitelisted(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        boolean result = CreateFisheryCommonConfig.isEntityWhitelisted(id);
        if (id.toString().equals("minecraft:breeze")) {
            CreateFisheryMod.LOGGER.debug("Checking if breeze is whitelisted: {}", result);
        }
        return result;
    }

    protected boolean isEntityBlacklisted(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return CreateFisheryCommonConfig.isEntityBlacklisted(id);
    }
}