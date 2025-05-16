package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MechanicalPeelerBlock extends DirectionalAxisKineticBlock implements IBE<MechanicalPeelerBlockEntity> {
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");

    public MechanicalPeelerBlock(Properties properties) {
        super(properties);
        // Default state should make it behave like Saw when placed (FACING=UP typically)
        // DirectionalKineticBlock's default FACING might be NORTH.
        // SawBlock relies on super.getStateForPlacement to set FACING correctly for "in front" placement.
        // We ensure FLIPPED is handled like SawBlock for vertical placements.
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP) // Explicitly default to UP
                .setValue(AXIS_ALONG_FIRST_COORDINATE, true) // DAKB default, matches Saw logic
                .setValue(FLIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FLIPPED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Start with DAKB's placement logic
        BlockState stateForPlacement = super.getStateForPlacement(context);
        Direction facing = stateForPlacement.getValue(FACING);

        // If DAKB decided it should be vertical, apply SawBlock's FLIPPED logic
        if (facing.getAxis() == Axis.Y) {
            return stateForPlacement.setValue(FLIPPED, context.getHorizontalDirection()
                    .getAxisDirection() == AxisDirection.POSITIVE);
        }

        // If DAKB decided it should be horizontal, SawBlock doesn't modify FLIPPED here.
        // The key is that super.getStateForPlacement() should result in FACING=UP
        // when player places it on the ground in front of them, similar to how Saw works.
        // If it results in FACING towards player, then DAKB's logic for "looking at horizontal face" is kicking in.
        // To force UP like saw for "in front" placement on ground:
        // Player usually looks slightly down. If context.getClickedFace() is UP, DAKB should set FACING to UP.
        return stateForPlacement;
    }

    // Rotation and Mirror logic (copied from SawBlock, should be fine)
    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        BlockState newState = super.getRotatedBlockState(originalState, targetedFace);
        if (newState.getValue(FACING).getAxis() != Axis.Y)
            return newState;
        if (targetedFace.getAxis() != Axis.Y)
            return newState;
        if (!originalState.getValue(AXIS_ALONG_FIRST_COORDINATE))
            newState = newState.cycle(FLIPPED);
        return newState;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        BlockState newState = super.rotate(state, rot);
        if (state.getValue(FACING).getAxis() != Axis.Y)
            return newState;

        if (rot.ordinal() % 2 == 1 && (rot == Rotation.CLOCKWISE_90) != state.getValue(AXIS_ALONG_FIRST_COORDINATE))
            newState = newState.cycle(FLIPPED);
        if (rot == Rotation.CLOCKWISE_180)
            newState = newState.cycle(FLIPPED);

        return newState;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirrorIn) {
        BlockState newState = super.mirror(state, mirrorIn);
        if (state.getValue(FACING).getAxis() != Axis.Y)
            return newState;

        boolean alongX = state.getValue(AXIS_ALONG_FIRST_COORDINATE);
        if (alongX && mirrorIn == Mirror.FRONT_BACK)
            newState = newState.cycle(FLIPPED);
        if (!alongX && mirrorIn == Mirror.LEFT_RIGHT)
            newState = newState.cycle(FLIPPED);

        return newState;
    }
    // End Rotation and Mirror logic

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return AllShapes.CASING_12PX.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isSpectator() || !stack.isEmpty())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(FACING) != Direction.UP)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        return onBlockEntityUseItemOn(level, pos, be -> {
            for (int i = 0; i < be.inventory.getSlots(); i++) {
                ItemStack heldItemStack = be.inventory.getStackInSlot(i);
                if (!level.isClientSide && !heldItemStack.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(heldItemStack);
                }
            }
            be.inventory.clear();
            be.notifyUpdate();
            return ItemInteractionResult.SUCCESS;
        });
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
        super.updateEntityAfterFallOn(worldIn, entityIn);
        if (!(entityIn instanceof ItemEntity itemEntity))
            return;
        if (entityIn.level().isClientSide)
            return;

        BlockPos pos = entityIn.blockPosition();
        Level level = entityIn.level();
        if (level == null) return;

        withBlockEntityDo(level, pos, be -> {
            if (be.getSpeed() == 0)
                return;
            be.insertItem(itemEntity);
        });
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.NORMAL;
    }

    // Copied from SawBlock to ensure consistency
    public static boolean isHorizontal(BlockState state) {
        return state.getValue(FACING)
                .getAxis()
                .isHorizontal();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        // If horizontal, animation axis is FACING axis.
        // If vertical, animation axis is from super (DAKB: AXIS_ALONG_FIRST_COORDINATE ? Z : X)
        return isHorizontal(state) ? state.getValue(FACING)
                .getAxis() : super.getRotationAxis(state);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        // If horizontal, shaft connects opposite to FACING.
        // If vertical, from super (DAKB logic based on FACING and AXIS_ALONG_FIRST_COORDINATE)
        return isHorizontal(state) ? face == state.getValue(FACING)
                .getOpposite() : super.hasShaftTowards(world, pos, state, face);
    }
    // End SawBlock copied methods

    @Override
    public Class<MechanicalPeelerBlockEntity> getBlockEntityClass() {
        return MechanicalPeelerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalPeelerBlockEntity> getBlockEntityType() {
        return CreateFisheryBlockEntities.MECHANICAL_PEELER.get();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}