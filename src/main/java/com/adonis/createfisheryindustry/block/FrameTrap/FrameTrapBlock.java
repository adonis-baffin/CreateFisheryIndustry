package com.adonis.createfisheryindustry.block.FrameTrap;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import com.simibubi.create.foundation.block.WrenchableDirectionalBlock;
import java.util.List;
import java.util.function.Predicate;
import net.createmod.catnip.placement.IPlacementHelper;
import net.createmod.catnip.placement.PlacementHelpers;
import net.createmod.catnip.placement.PlacementOffset;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

public class FrameTrapBlock extends WrenchableDirectionalBlock implements ProperWaterloggedBlock, IWrenchable {
    protected static final int PLACEMENT_HELPER_ID = PlacementHelpers.register(new PlacementHelper());

    public FrameTrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.SOUTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof WrenchItem) {
            UseOnContext wrenchContext = new UseOnContext(level, player, hand, stack, hitResult);
            InteractionResult result = player.isShiftKeyDown()
                    ? onSneakWrenched(state, wrenchContext)
                    : onWrenched(state, wrenchContext);

            if (result != InteractionResult.PASS) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        IPlacementHelper placementHelper = PlacementHelpers.get(PLACEMENT_HELPER_ID);
        if (!player.isShiftKeyDown() && player.mayBuild() && placementHelper.matchesItem(stack)) {
            placementHelper
                    .getOffset(player, level, state, pos, hitResult)
                    .placeInWorld(level, (BlockItem) stack.getItem(), player, hand, hitResult);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        VoxelShape frame = Shapes.box(0.0, 0.4375, 0.0, 1.0, 0.5625, 1.0); // 16x2x16 像素
        VoxelShape net = Shapes.box(0.125, 0.4375, 0.125, 0.875, 0.4375, 0.875); // 12x0x12 像素
        VoxelShape baseShape = Shapes.or(frame, net);

        // 根据朝向旋转碰撞箱
        return switch (facing) {
            case UP -> baseShape;
            case DOWN -> preciseRotateX(baseShape, 180);
            case NORTH -> preciseRotateX(baseShape, 90);
            case SOUTH -> preciseRotateX(baseShape, -90);
            case WEST -> preciseRotateZ(baseShape, 90);
            case EAST -> preciseRotateZ(baseShape, -90);
        };
    }

    private VoxelShape preciseRotateX(VoxelShape shape, int degrees) {
        List<AABB> boxes = shape.toAabbs();
        VoxelShape[] result = { Shapes.empty() };

        double cos = Mth.cos((float) Math.toRadians(degrees));
        double sin = Mth.sin((float) Math.toRadians(degrees));

        boxes.forEach(box -> {
            AABB rotated = new AABB(
                    preciseRound(box.minX),
                    preciseRound(cos * (box.minY - 0.5) - sin * (box.minZ - 0.5) + 0.5),
                    preciseRound(sin * (box.minY - 0.5) + cos * (box.minZ - 0.5) + 0.5),
                    preciseRound(box.maxX),
                    preciseRound(cos * (box.maxY - 0.5) - sin * (box.maxZ - 0.5) + 0.5),
                    preciseRound(sin * (box.maxY - 0.5) + cos * (box.maxZ - 0.5) + 0.5));
            result[0] = Shapes.join(result[0], Shapes.create(rotated), BooleanOp.OR);
        });

        return result[0];
    }

    private VoxelShape preciseRotateZ(VoxelShape shape, int degrees) {
        List<AABB> boxes = shape.toAabbs();
        VoxelShape[] result = { Shapes.empty() };

        double cos = Mth.cos((float) Math.toRadians(degrees));
        double sin = Mth.sin((float) Math.toRadians(degrees));

        boxes.forEach(box -> {
            AABB rotated = new AABB(
                    preciseRound(cos * (box.minX - 0.5) - sin * (box.minY - 0.5) + 0.5),
                    preciseRound(sin * (box.minX - 0.5) + cos * (box.minY - 0.5) + 0.5),
                    preciseRound(box.minZ),
                    preciseRound(cos * (box.maxX - 0.5) - sin * (box.maxY - 0.5) + 0.5),
                    preciseRound(sin * (box.maxX - 0.5) + cos * (box.maxY - 0.5) + 0.5),
                    preciseRound(box.maxZ));
            result[0] = Shapes.join(result[0], Shapes.create(rotated), BooleanOp.OR);
        });

        return result[0];
    }

    private double preciseRound(double value) {
        return Math.round(value * 16) / 16.0;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(WATERLOGGED));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState stateForPlacement = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return withWater(stateForPlacement, context);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return state;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (entity.isSuppressingBounce()) {
            super.fallOn(level, state, pos, entity, fallDistance);
        } else {
            entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
        }
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entityIn) {
        if (entityIn.isSuppressingBounce()) {
            super.updateEntityAfterFallOn(level, entityIn);
        } else {
            this.bounceEntity(entityIn);
        }
    }

    protected void bounceEntity(Entity entity) {
        Vec3 vec3d = entity.getDeltaMovement();
        if (vec3d.y < 0.0) {
            double entityWeightOffset = entity instanceof LivingEntity ? 0.6 : 0.8;
            entity.setDeltaMovement(vec3d.x, -vec3d.y * entityWeightOffset, vec3d.z);
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState rotated = getRotatedBlockState(state, context.getClickedFace());
        if (!rotated.canSurvive(level, pos)) {
            return InteractionResult.PASS;
        }
        if (level.setBlock(pos, rotated, 3)) {
            IWrenchable.playRotateSound(level, pos);
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return InteractionResult.SUCCESS;
        }
        if (player != null && !player.isCreative()) {
            Block.getDrops(state, serverLevel, pos, null, player, context.getItemInHand())
                    .forEach(itemStack -> player.getInventory().placeItemBackInInventory(itemStack));
        }
        state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, true);
        level.destroyBlock(pos, false);
        IWrenchable.playRemoveSound(level, pos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState updateAfterWrenched(BlockState newState, UseOnContext context) {
        return Block.updateFromNeighbourShapes(newState, context.getLevel(), context.getClickedPos());
    }

    @Override
    public BlockState getRotatedBlockState(BlockState originalState, Direction targetedFace) {
        if (!originalState.hasProperty(FACING)) {
            return originalState;
        }

        Direction currentFacing = originalState.getValue(FACING);
        if (currentFacing.getAxis().equals(targetedFace.getAxis())) {
            return originalState;
        }
        Direction newFacing = currentFacing.getClockWise(targetedFace.getAxis());
        return originalState.setValue(FACING, newFacing);
    }

    protected static class PlacementHelper implements IPlacementHelper {
        @Override
        public Predicate<ItemStack> getItemPredicate() {
            return stack -> stack.getItem() instanceof BlockItem blockItem &&
                    blockItem.getBlock() instanceof FrameTrapBlock;
        }

        @Override
        public Predicate<BlockState> getStatePredicate() {
            return state -> state.getBlock() instanceof FrameTrapBlock;
        }

        @Override
        public PlacementOffset getOffset(Player player, Level level, BlockState state, BlockPos pos, BlockHitResult hit) {
            List<Direction> directions = IPlacementHelper.orderedByDistanceExceptAxis(
                    pos,
                    hit.getLocation(),
                    state.getValue(FrameTrapBlock.FACING).getAxis(),
                    direction -> level.getBlockState(pos.relative(direction)).canBeReplaced());
            if (directions.isEmpty()) {
                return PlacementOffset.fail();
            } else {
                return PlacementOffset.success(
                        pos.relative(directions.getFirst()),
                        placed -> placed.setValue(FACING, state.getValue(FACING)));
            }
        }
    }
}
