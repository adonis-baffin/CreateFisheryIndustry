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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

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
            captureNearbyEntities(context, currentPos, level);

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
        // 先检查是否能存储物品
        if (!canInsertToContraption(context)) {
            return;  // 没有空间，不收集
        }

        AABB boundingBox = new AABB(pos).inflate(0.2);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);

        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                // 尝试插入到装置存储
                if (tryInsertToContraption(context, stack.copy())) {
                    itemEntity.discard();  // 成功插入，移除物品实体
                }
                // 如果插入失败，物品实体保留在世界中
            }
        }
    }

    /**
     * 捕获附近实体
     */
    protected void captureNearbyEntities(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        // 强制刷新配置缓存，确保使用最新的白名单/黑名单
        CreateFisheryCommonConfig.refreshCache();

        // 创建物品接收器（用于检查和接收）
        IItemReceiver receiver = new MovementContextReceiver(context);

        for (Entity entity : entities) {
            // 使用统一的实体筛选逻辑
            if (EntityCaptureHelper.shouldCaptureEntity(entity)) {
                // EntityCaptureHelper.captureEntity 内部会检查 canReceive()
                // 如果无法接收物品，会返回 false 且不移除实体
                EntityCaptureHelper.captureEntity((Mob) entity, level, pos, receiver);
            }
        }
    }

    /**
     * 检查是否能向装置存储中插入物品
     */
    private boolean canInsertToContraption(MovementContext context) {
        if (context.contraption == null) {
            return false;
        }

        var storage = context.contraption.getStorage();
        if (storage == null) {
            return false;
        }

        var contraptionItems = storage.getAllItems();
        if (contraptionItems == null || contraptionItems.getSlots() <= 0) {
            return false;
        }

        // 检查是否有可用空间
        return hasAvailableSpace(contraptionItems);
    }

    /**
     * 尝试将物品插入到装置存储中
     *
     * @param context 移动上下文
     * @param stack 要插入的物品
     * @return 是否成功插入（全部或部分）
     */
    private boolean tryInsertToContraption(MovementContext context, ItemStack stack) {
        if (context.contraption == null) {
            return false;
        }

        var storage = context.contraption.getStorage();
        if (storage == null) {
            return false;
        }

        var contraptionItems = storage.getAllItems();
        if (contraptionItems == null) {
            return false;
        }

        // 尝试插入
        ItemStack remainder = ItemHandlerHelper.insertItem(contraptionItems, stack, false);

        // 如果有剩余，说明没有完全插入成功
        // 但只要插入了一部分就算成功（物品实体会被移除）
        return remainder.getCount() < stack.getCount();
    }

    /**
     * 检查物品处理器是否有可用空间
     */
    private boolean hasAvailableSpace(IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stackInSlot = handler.getStackInSlot(i);
            if (stackInSlot.isEmpty()) {
                return true; // 有空槽位
            }
            if (stackInSlot.getCount() < stackInSlot.getMaxStackSize()) {
                return true; // 有未满的槽位
            }
        }
        return false;
    }
}