package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.item.ItemHelper; // Keep for potential future use or if other methods are called
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
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
import java.util.List;
import java.util.function.Predicate;

@ParametersAreNonnullByDefault
public class MechanicalPeelerBlock extends DirectionalAxisKineticBlock implements IBE<MechanicalPeelerBlockEntity> {
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");
    private static final int placementHelperId = PlacementHelpers.register(new PlacementHelper());

    public MechanicalPeelerBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(AXIS_ALONG_FIRST_COORDINATE, true)
                .setValue(FLIPPED, false));
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FLIPPED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = super.getStateForPlacement(context);
        Direction facing = stateForPlacement.getValue(FACING);
        if (facing.getAxis() == Axis.Y) {
            return stateForPlacement.setValue(FLIPPED, context.getHorizontalDirection()
                    .getAxisDirection() == AxisDirection.POSITIVE);
        }
        return stateForPlacement;
    }

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

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return AllShapes.CASING_12PX.get(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        IPlacementHelper placementHelper = PlacementHelpers.get(placementHelperId);
        if (!player.isShiftKeyDown() && player.mayBuild()) {
            if (placementHelper.matchesItem(stack) && placementHelper.getOffset(player, level, state, pos, hitResult)
                    .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult)
                    .consumesAction())
                return ItemInteractionResult.SUCCESS;
        }

        if (player.isSpectator() || !stack.isEmpty()) // Allow placing items if hand is not empty
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(FACING) != Direction.UP)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        // If hand is empty, try to retrieve items
        return onBlockEntityUseItemOn(level, pos, be -> {
            boolean itemsRetrieved = false;
            // Retrieve from input inventory
            ItemStack inputStack = be.inputInventory.getStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT);
            if (!inputStack.isEmpty()) {
                if (!level.isClientSide) player.getInventory().placeItemBackInInventory(inputStack);
                be.inputInventory.setStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
                itemsRetrieved = true;
            }

            // Retrieve from output inventory (temp primary and secondaries)
            for (int i = 0; i < be.outputInventory.getSlots(); i++) {
                ItemStack outputStack = be.outputInventory.getStackInSlot(i);
                if (!outputStack.isEmpty()) {
                    if (!level.isClientSide) player.getInventory().placeItemBackInInventory(outputStack);
                    be.outputInventory.setStackInSlot(i, ItemStack.EMPTY);
                    itemsRetrieved = true;
                }
            }

            if (itemsRetrieved) {
                be.notifyUpdate(); // Updates BE state which might include visual changes or comparator output
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // No items to retrieve
        });
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
        super.updateEntityAfterFallOn(worldIn, entityIn);
        if (!(entityIn instanceof ItemEntity itemEntity))
            return;
        if (entityIn.level().isClientSide)
            return;

        BlockPos pos = entityIn.blockPosition(); // Use the entity's current block position
        Level level = entityIn.level(); // Get level from entity
        if (level == null) return; // Should not happen if entity is valid

        // The MechanicalPeelerBlock is at entityIn.blockPosition() if the item landed on it.
        // If it landed *next* to it and should be picked up, pos needs to be the BE's pos.
        // Assuming the item entity is AT the peeler's position (or directly above it and fell on).
        BlockState blockStateAtEntity = level.getBlockState(pos);
        if (blockStateAtEntity.getBlock() != this) { // Check if the entity is on THIS block type
            // If entity is above, check block below
            BlockPos belowEntity = pos.below();
            if (level.getBlockState(belowEntity).getBlock() == this) {
                pos = belowEntity;
            } else {
                return; // Entity is not on or directly above a MechanicalPeelerBlock
            }
        }


        withBlockEntityDo(level, pos, be -> { // 'pos' is now confirmed or adjusted to be the Peeler's position
            if (be.getSpeed() == 0)
                return;
            be.insertItem(itemEntity); // insertItem logic in BE will handle putting it in inputInventory
        });
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.NORMAL;
    }

    public static boolean isHorizontal(BlockState state) {
        return state.getValue(FACING).getAxis().isHorizontal();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return isHorizontal(state) ? state.getValue(FACING).getAxis() : super.getRotationAxis(state);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return isHorizontal(state) ? face == state.getValue(FACING).getOpposite() : super.hasShaftTowards(world, pos, state, face);
    }

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

    private static class PlacementHelper implements IPlacementHelper {
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof MechanicalPeelerBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof MechanicalPeelerBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level world, BlockState state, BlockPos pos, BlockHitResult ray) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(pos, ray.getLocation(),
                    state.getValue(FACING).getAxis(),
                    dir -> world.getBlockState(pos.relative(dir)).canBeReplaced());

            if (directions.isEmpty())
                return PlacementOffset.fail();
            else {
                return PlacementOffset.success(pos.relative(directions.get(0)),
                        s -> s.setValue(FACING, state.getValue(FACING))
                                .setValue(AXIS_ALONG_FIRST_COORDINATE, state.getValue(AXIS_ALONG_FIRST_COORDINATE))
                                .setValue(FLIPPED, state.getValue(FLIPPED)));
            }
        }
    }
}