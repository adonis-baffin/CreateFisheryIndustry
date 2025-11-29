package com.adonis.createfisheryindustry.block.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * BlockEntity 库存的物品接收器实现
 * 尝试插入到 ItemStackHandler，失败则掉落到世界
 */
public class InventoryReceiver implements IItemReceiver {
    private final ItemStackHandler inventory;
    private final ServerLevel level;
    private final Vec3 dropPosition;

    public InventoryReceiver(ItemStackHandler inventory, ServerLevel level, Vec3 dropPosition) {
        this.inventory = inventory;
        this.level = level;
        this.dropPosition = dropPosition;
    }

    @Override
    public boolean canReceive() {
        if (inventory == null || inventory.getSlots() <= 0) {
            return false;
        }

        // 检查是否至少有一个槽位可以接收物品
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stackInSlot = inventory.getStackInSlot(i);
            if (stackInSlot.isEmpty()) {
                return true; // 有空槽位
            }
            if (stackInSlot.getCount() < stackInSlot.getMaxStackSize()) {
                return true; // 有未满的槽位
            }
        }
        return false;
    }

    @Override
    public boolean receiveItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ItemStack remainder = stack.copy();

        // 尝试插入到库存的每个槽位
        for (int i = 0; i < inventory.getSlots(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                return true; // 完全插入成功
            }
        }

        // 如果有剩余，掉落到世界
        if (!remainder.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(level, dropPosition.x, dropPosition.y, dropPosition.z, remainder);
            level.addFreshEntity(itemEntity);
            return stack.getCount() != remainder.getCount(); // 部分成功
        }

        return true;
    }
}