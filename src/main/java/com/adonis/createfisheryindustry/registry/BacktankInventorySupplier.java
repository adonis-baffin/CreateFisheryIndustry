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
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    ItemStack itemStack = player.getInventory().getItem(i);
                    
                    if (AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.matches(itemStack)) {
                        stacks.add(itemStack);
                    }
                }
            }
            
            return stacks;
        });
    }
}