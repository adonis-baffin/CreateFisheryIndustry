package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public class FrameTrapMovementBehaviour implements MovementBehaviour {
    // 可调参数：最大碰撞箱尺寸（宽、高、深）
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (context.world instanceof ServerLevel level) {
            // 实体捕获和物品收集功能
            killNearbyEntities(context, pos, level);
            collectNearbyItems(context, pos, level);
        }
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

            // 规则1：黑名单中的实体直接跳过，绝对不捕获
            if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
                continue;
            }

            // 规则2：白名单中的实体直接捕获，不考虑大小限制
            if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
                processEntityDrops(context, level, mob);
                continue;
            }

            // 规则3：既不在白名单也不在黑名单的实体，根据尺寸判断是否捕获
            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE) {
                processEntityDrops(context, level, mob);
            }
        }
    }

    private void processEntityDrops(MovementContext context, ServerLevel level, Mob mob) {
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

        // 确定环境类型用于粒子效果
        boolean inLava = level.getFluidState(new BlockPos((int) mob.getX(), (int) mob.getY(), (int) mob.getZ())).is(FluidTags.LAVA);
        addExperienceNugget(context, level, mob.getX(), mob.getY() + 0.5, mob.getZ());
        spawnEntityCaptureParticles(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), inLava);
    }

    private void addExperienceNugget(MovementContext context, ServerLevel level, double x, double y, double z) {
        Item expNuggetItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));
        if (expNuggetItem != null && expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            dropItem(context, expNugget);
        } else {
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, x, y, z, 1));
        }
    }

    /**
     * 钓鱼成功时的粒子效果
     */
    public static void spawnFishingParticles(ServerLevel level, double x, double y, double z, boolean inLava) {
        if (inLava) {
            // 熔岩钓鱼粒子效果
            level.sendParticles(ParticleTypes.LAVA, x, y, z, 10, 0.3, 0.3, 0.3, 0.1);
            level.sendParticles(ParticleTypes.FLAME, x, y, z, 5, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                    SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 1.2F);
        } else {
            // 水中钓鱼粒子效果
            level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
            level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                    SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * 实体捕获时的粒子效果
     */
    private void spawnEntityCaptureParticles(ServerLevel level, double x, double y, double z, boolean inLava) {
        if (inLava) {
            // 熔岩环境中的实体捕获
            level.sendParticles(ParticleTypes.LAVA, x, y, z, 8, 0.4, 0.4, 0.4, 0.1);
            level.sendParticles(ParticleTypes.SMOKE, x, y, z, 5, 0.3, 0.3, 0.3, 0.05);
        } else {
            // 水环境中的实体捕获
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
        }
        level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public void dropItem(MovementContext context, ItemStack stack) {
        Vec3 pos = context.position.add(0.5, 0.5, 0.5);
        ItemEntity entity = new ItemEntity(context.world, pos.x, pos.y, pos.z, stack);
        context.world.addFreshEntity(entity);
    }

    protected boolean isEntityWhitelisted(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return CreateFisheryCommonConfig.isEntityWhitelisted(id);
    }

    protected boolean isEntityBlacklisted(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return CreateFisheryCommonConfig.isEntityBlacklisted(id);
    }
}