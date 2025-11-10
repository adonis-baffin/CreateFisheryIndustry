package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.block.common.EntityCaptureHelper;
import com.adonis.createfisheryindustry.block.common.IItemReceiver;
import com.adonis.createfisheryindustry.block.common.MovementContextReceiver;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FrameTrapMovementBehaviour implements MovementBehaviour {

    @Override
    public void tick(MovementContext context) {
        if (context.world instanceof ServerLevel level) {
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
            // 使用统一的实体筛选逻辑
            if (EntityCaptureHelper.shouldCaptureEntity(entity)) {
                processEntityCapture(context, level, (Mob) entity, pos);
            }
        }
    }

    /**
     * 处理实体捕获
     */
    private void processEntityCapture(MovementContext context, ServerLevel level, Mob mob, BlockPos capturePos) {
        // 创建物品接收器
        IItemReceiver receiver = new MovementContextReceiver(context);

        // 使用统一的捕获逻辑
        EntityCaptureHelper.captureEntity(mob, level, capturePos, receiver);
    }
}