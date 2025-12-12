package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.BaitTrap.BaitTrapBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class BaitTrapInteractionHandler {

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void useOnBaitTrapIgnoresSneak(RightClickBlock event) {
        if (event.getUseItem() == TriState.DEFAULT &&
                event.getLevel().getBlockState(event.getPos()).getBlock() instanceof BaitTrapBlock) {
            event.setUseBlock(TriState.TRUE);
        }
    }
}