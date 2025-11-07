package com.adonis.createfisheryindustry.block.FishingBearing;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FishingBearingBlock extends BearingBlock implements IBE<FishingBearingBlockEntity> {

    public FishingBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return ItemInteractionResult.FAIL;
        }

        if (stack.isEmpty()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            withBlockEntityDo(level, pos, be -> {
                if (be.isRunning()) {
                    be.disassemble();
                } else {
                    be.setAssembleNextTick(true);
                }
            });
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public boolean isValidCapLocation(BlockState state, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public Class<FishingBearingBlockEntity> getBlockEntityClass() {
        return FishingBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends FishingBearingBlockEntity> getBlockEntityType() {
        return CreateFisheryBlockEntities.FISHING_BEARING.get();
    }
}