package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockMovementChecks.class)
public class BlockMovementChecksMixin {
    private static final Logger LOGGER = LogManager.getLogger("CreateFisheryIndustry");

    @Inject(
            method = "isBrittle",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void injectIsBrittle(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof FrameTrapBlock) {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "isBlockAttachedTowards",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void injectIsBlockAttachedTowards(BlockState state, Level world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof FrameTrapBlock) {
            cir.setReturnValue(true); // 暂时返回 true，允许附着
        }
    }
}