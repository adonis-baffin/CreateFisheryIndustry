package com.adonis.createfisheryindustry.block.common;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.world.item.ItemStack;

/**
 * MovementBehaviour 的物品接收器实现
 * 使用 MovementContext 的存储系统来存储物品
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

        var itemHandler = context.contraption.getStorage().getAllItems();
        if (itemHandler == null) {
            return false;
        }

        // 尝试插入到物品处理器
        ItemStack remainder = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(
                itemHandler, stack, false
        );

        return remainder.getCount() < stack.getCount();
    }
}