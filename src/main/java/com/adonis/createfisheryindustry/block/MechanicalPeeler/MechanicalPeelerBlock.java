package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.foundation.block.IBE;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Predicate;

import static net.createmod.catnip.placement.IPlacementHelper.orderedByDistanceExceptAxis;

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
        // 与动力锯完全一致，仅设置 FLIPPED
        BlockState stateForPlacement = super.getStateForPlacement(context);
        Direction facing = stateForPlacement.getValue(FACING);
        return stateForPlacement.setValue(FLIPPED, facing.getAxis() == Axis.Y && context.getHorizontalDirection().getAxisDirection() == AxisDirection.NEGATIVE);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        withBlockEntityDo(level, pos, be -> be.notifyUpdate());
        level.updateNeighborsAt(pos, this);
        // 强制更新邻近传动杆
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof ShaftBlock) {
                level.updateNeighbourForOutputSignal(neighborPos, neighborState.getBlock());
            }
        }
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

        if (player.isSpectator() || !stack.isEmpty())
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(FACING) != Direction.UP)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        return onBlockEntityUseItemOn(level, pos, be -> {
            boolean itemsRetrieved = false;
            ItemStack inputStack = be.inputInventory.getStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT);
            if (!inputStack.isEmpty()) {
                if (!level.isClientSide) player.getInventory().placeItemBackInInventory(inputStack);
                be.inputInventory.setStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT, ItemStack.EMPTY);
                itemsRetrieved = true;
            }

            for (int i = 0; i < be.outputInventory.getSlots(); i++) {
                ItemStack outputStack = be.outputInventory.getStackInSlot(i);
                if (!outputStack.isEmpty()) {
                    if (!level.isClientSide) player.getInventory().placeItemBackInInventory(outputStack);
                    be.outputInventory.setStackInSlot(i, ItemStack.EMPTY);
                    itemsRetrieved = true;
                }
            }

            if (itemsRetrieved) {
                be.notifyUpdate();
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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

        BlockState blockStateAtEntity = level.getBlockState(pos);
        if (blockStateAtEntity.getBlock() != this) {
            BlockPos belowEntity = pos.below();
            if (level.getBlockState(belowEntity).getBlock() == this) {
                pos = belowEntity;
            } else {
                return;
            }
        }

        withBlockEntityDo(level, pos, be -> {
            if (be.getSpeed() == 0)
                return;
            be.insertItem(itemEntity);
        });
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        final double interactionDepth = 0.6;
        final double interactionWidth = 0.8;
        final double interactionHeight = 0.8;
        final double offsetFromSurface = 0.1;

        Vec3 min = Vec3.ZERO;
        Vec3 max = Vec3.ZERO;

        switch (facing) {
            case UP:
                min = new Vec3((1 - interactionWidth) / 2, 1 + offsetFromSurface, (1 - interactionHeight) / 2);
                max = new Vec3((1 + interactionWidth) / 2, 1 + offsetFromSurface + interactionDepth, (1 + interactionHeight) / 2);
                break;
            case DOWN:
                min = new Vec3((1 - interactionWidth) / 2, -offsetFromSurface - interactionDepth, (1 - interactionHeight) / 2);
                max = new Vec3((1 + interactionWidth) / 2, -offsetFromSurface, (1 + interactionHeight) / 2);
                break;
            case NORTH:
                min = new Vec3((1 - interactionWidth) / 2, (1 - interactionHeight) / 2, -offsetFromSurface - interactionDepth);
                max = new Vec3((1 + interactionWidth) / 2, (1 + interactionHeight) / 2, -offsetFromSurface);
                break;
            case SOUTH:
                min = new Vec3((1 - interactionWidth) / 2, (1 - interactionHeight) / 2, 1 + offsetFromSurface);
                max = new Vec3((1 + interactionWidth) / 2, (1 + interactionHeight) / 2, 1 + offsetFromSurface + interactionDepth);
                break;
            case WEST:
                min = new Vec3(-offsetFromSurface - interactionDepth, (1 - interactionHeight) / 2, (1 - interactionWidth) / 2);
                max = new Vec3(-offsetFromSurface, (1 + interactionHeight) / 2, (1 + interactionWidth) / 2);
                break;
            case EAST:
                min = new Vec3(1 + offsetFromSurface, (1 - interactionHeight) / 2, (1 - interactionWidth) / 2);
                max = new Vec3(1 + offsetFromSurface + interactionDepth, (1 + interactionHeight) / 2, (1 + interactionWidth) / 2);
                break;
        }

        AABB interactionZone = new AABB(min.x, min.y, min.z, max.x, max.y, max.z).move(pos);

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, interactionZone,
                (e) -> e.isAlive() && (e instanceof Sheep || e instanceof Armadillo || e instanceof Turtle));

        if (!entities.isEmpty()) {
            withBlockEntityDo(level, pos, be -> {
                if (be.getSpeed() != 0) {
                    for (Entity e : entities) {
                        be.processEntity(e);
                    }
                }
            });
        }
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
            List<Direction> directions = orderedByDistanceExceptAxis(pos, ray.getLocation(),
                    state.getValue(FACING).getAxis(),
                    (Direction dir) -> world.getBlockState(pos.relative(dir)).canBeReplaced());

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

    public MovementBehaviour getMovementBehaviour() {
        return new MechanicalPeelerMovementBehaviour();
    }
}