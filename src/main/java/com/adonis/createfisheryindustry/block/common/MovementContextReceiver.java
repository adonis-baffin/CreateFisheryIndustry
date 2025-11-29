package com.adonis.createfisheryindustry.block.common;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * MovementBehaviour 的物品接收器实现
 * 直接使用装置的通用存储系统
 */
public class MovementContextReceiver implements IItemReceiver {
    private final MovementContext context;

    public MovementContextReceiver(MovementContext context) {
        this.context = context;
    }

    @Override
    public boolean receiveItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        // 获取装置的通用物品存储
        var contraptionStorage = context.contraption.getStorage().getAllItems();
        if (contraptionStorage == null) {
            return false;
        }

        // 尝试插入物品
        ItemStack remainder = ItemHandlerHelper.insertItem(contraptionStorage, stack, false);

        return remainder.getCount() < stack.getCount();
    }
}