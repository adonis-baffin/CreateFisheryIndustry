package com.adonis.createfisheryindustry.block.common;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 玩家背包的物品接收器实现
 * 优先插入到玩家背包，失败则掉落到世界
 */
public class PlayerInventoryReceiver implements IItemReceiver {
    private final Player player;
    private final ServerLevel level;
    private final double dropX;
    private final double dropY;
    private final double dropZ;
    
    public PlayerInventoryReceiver(Player player, ServerLevel level, double dropX, double dropY, double dropZ) {
        this.player = player;
        this.level = level;
        this.dropX = dropX;
        this.dropY = dropY;
        this.dropZ = dropZ;
    }
    
    @Override
    public boolean receiveItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        // 尝试添加到玩家背包
        ItemStack remainder = stack.copy();
        boolean added = player.getInventory().add(remainder);
        
        if (added && remainder.isEmpty()) {
            // 完全添加成功
            return true;
        }
        
        // 如果有剩余（背包满了），掉落到世界
        if (!remainder.isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(level, dropX, dropY, dropZ, remainder);
            level.addFreshEntity(itemEntity);
        }
        
        // 只要有部分成功就返回 true
        return stack.getCount() != remainder.getCount() || added;
    }
}