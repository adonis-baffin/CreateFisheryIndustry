package com.adonis.createfisheryindustry.registry;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class BacktankInventorySupplier {

    public static void register() {
        BacktankUtil.addBacktankSupplier(entity -> {
            List<ItemStack> stacks = new ArrayList<>();

            if (entity instanceof Player player) {
                // 只检查主背包和快捷栏（0-35），不检查盔甲槽（36-39）
                // 因为 Create 默认供应器已经通过 entity.getArmorSlots() 检查了盔甲槽
                int containerSize = player.getInventory().getContainerSize();

                for (int i = 0; i < containerSize; i++) {
                    ItemStack itemStack = player.getInventory().getItem(i);

                    // 跳过盔甲槽（索引 36-39）
                    // 盔甲槽由 Create 的默认供应器处理
                    if (i >= 36) {
                        continue;
                    }

                    if (AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.matches(itemStack)) {
                        stacks.add(itemStack);
                    }
                }
            }

            return stacks;
        });
    }
}