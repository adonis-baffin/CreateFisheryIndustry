package com.adonis.createfisheryindustry.block.TrapBearing;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Independently rewritten block wrapper for the trap bearing feature.
 */
public class TrapBearingBlock extends BearingBlock implements IBE<TrapBearingBlockEntity>, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public TrapBearingBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placedState = super.getStateForPlacement(context);
        if (placedState == null) {
            return null;
        }

        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return placedState.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return ItemInteractionResult.FAIL;
        }

        if (!stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

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

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TrapBearingBlockEntity trapBearing) {
                trapBearing.dropInventoryContents();
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    public boolean isValidCapLocation(BlockState state, BlockPos pos, Direction direction) {
        return true;
    }

    @Override
    public Class<TrapBearingBlockEntity> getBlockEntityClass() {
        return TrapBearingBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TrapBearingBlockEntity> getBlockEntityType() {
        return CreateFisheryBlockEntities.TRAP_BEARING.get();
    }
}
