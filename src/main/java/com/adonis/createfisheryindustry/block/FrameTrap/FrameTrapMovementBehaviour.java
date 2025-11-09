package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FrameTrapMovementBehaviour implements MovementBehaviour {
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    @Override
    public void tick(MovementContext context) {
        if (context.world instanceof ServerLevel level) {
            // context.position 是 Vec3，需要转换为 BlockPos
            Vec3 positionVec = context.position;
            if (positionVec == null) {
                return;
            }

            BlockPos currentPos = BlockPos.containing(positionVec);

            // 实体捕获（不受流体影响）
            killNearbyEntities(context, currentPos, level);

            // 物品收集（不受流体影响）
            collectNearbyItems(context, currentPos, level);
        }
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        // 不再需要位置访问逻辑
    }

    /**
     * 收集附近物品
     */
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

    /**
     * 捕获附近实体
     */
    protected void killNearbyEntities(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        // 强制刷新配置缓存，确保使用最新的白名单/黑名单
        CreateFisheryCommonConfig.refreshCache();

        for (Entity entity : entities) {
            // 仅处理 Mob 类型实体
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
                processEntityCapture(context, level, mob);
                continue;
            }

            // 规则3：既不在白名单也不在黑名单的实体，根据尺寸判断是否捕获
            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (width <= MAX_COLLISION_BOX_SIZE &&
                    height <= MAX_COLLISION_BOX_SIZE &&
                    depth <= MAX_COLLISION_BOX_SIZE) {
                processEntityCapture(context, level, mob);
            }
        }
    }

    /**
     * 处理实体捕获
     */
    private void processEntityCapture(MovementContext context, ServerLevel level, Mob mob) {
        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) {
            return;
        }

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
        boolean inLava = level.getFluidState(BlockPos.containing(mob.getX(), mob.getY(), mob.getZ()))
                .is(FluidTags.LAVA);

        addExperienceNugget(context, level, mob.getX(), mob.getY() + 0.5, mob.getZ());
        spawnEntityCaptureParticles(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), inLava);
    }

    /**
     * 添加经验颗粒
     */
    private void addExperienceNugget(MovementContext context, ServerLevel level, double x, double y, double z) {
        Item expNuggetItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));

        if (expNuggetItem != null &&
                expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            dropItem(context, expNugget);
        } else {
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, x, y, z, 1));
        }
    }

    /**
     * 实体捕获时的粒子效果
     */
    private void spawnEntityCaptureParticles(ServerLevel level, double x, double y, double z, boolean inLava) {
        // 统一音效
        level.playSound(null, BlockPos.containing(x, y, z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);

        if (inLava) {
            // 熔岩环境中的实体捕获
            level.sendParticles(ParticleTypes.LAVA,
                    x, y, z, 8, 0.4, 0.4, 0.4, 0.1);
            level.sendParticles(ParticleTypes.SMOKE,
                    x, y, z, 5, 0.3, 0.3, 0.3, 0.05);
        } else {
            // 水环境中的实体捕获
            level.sendParticles(ParticleTypes.CLOUD,
                    x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
        }
    }
}