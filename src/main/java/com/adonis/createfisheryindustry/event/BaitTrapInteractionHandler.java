package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.BaitTrap.BaitTrapBlock;
import com.adonis.createfisheryindustry.block.BaitTrap.BaitTrapBlockEntity;
import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Event handler to intercept food consumption when interacting with Bait Trap.
 * This prevents players from eating food when they're trying to add bait to the trap.
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class BaitTrapInteractionHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        InteractionHand hand = event.getHand();
        ItemStack heldItem = player.getItemInHand(hand);
        BlockPos pos = event.getPos();

        // 只处理潜行 + 手持物品
        if (!player.isShiftKeyDown() || heldItem.isEmpty()) {
            return;
        }

        BlockState state = level.getBlockState(pos);

        // 只处理诱捕网
        if (!(state.getBlock() instanceof BaitTrapBlock)) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BaitTrapBlockEntity baitTrap)) {
            return;
        }

        // 检查是否有匹配的配方
        if (baitTrap.findRecipe(heldItem) != null) {
            // 取消默认交互（吃东西、方块交互等）
            event.setCanceled(true);

            // 在服务端执行添加诱饵逻辑
            if (!level.isClientSide()) {
                if (baitTrap.tryAddBait(heldItem)) {
                    if (!player.isCreative()) {
                        heldItem.shrink(1);
                    }
                    level.setBlock(pos, state.setValue(BaitTrapBlock.BAITED, true), 3);
                }
            }
        }
    }
}