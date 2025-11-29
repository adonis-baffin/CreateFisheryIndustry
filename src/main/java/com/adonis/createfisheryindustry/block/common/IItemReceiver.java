package com.adonis.createfisheryindustry.block.common;

import net.minecraft.world.item.ItemStack;

/**
 * 物品接收器接口
 * 用于定义不同场景下如何存储战利品和经验粒
 */
public interface IItemReceiver {
    /**
     * 接收物品
     * 
     * @param stack 要接收的物品
     * @return 是否成功接收（全部或部分）
     */
    boolean receiveItem(ItemStack stack);
}