package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MechanicalPeelerBlock extends DirectionalAxisKineticBlock implements IBE<MechanicalPeelerBlockEntity> {
    public static final BooleanProperty FLIPPED = BooleanProperty.create("flipped");
    protected static final VoxelShape SHAPE = box(0, 0, 0, 16, 13, 16);

    public MechanicalPeelerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FLIPPED));
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        Direction facing = state.getValue(FACING);
        return state.setValue(FLIPPED, facing.getAxis() == Direction.Axis.Y && context.getHorizontalDirection().getAxisDirection() == Direction.AxisDirection.POSITIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && !stack.isEmpty()) {
            MechanicalPeelerBlockEntity be = getBlockEntity(level, pos);
            if (be != null) {
                be.insertItem(stack);
                if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                    if (!player.isCreative()) {
                        stack.shrink(1);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return state.getValue(FACING).getAxis().isHorizontal() ? face == state.getValue(FACING).getOpposite() : face == Direction.DOWN;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis().isHorizontal() ? state.getValue(FACING).getAxis() : Direction.Axis.Y;
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

    public static boolean isHorizontal(BlockState state) {
        return state.getValue(FACING).getAxis().isHorizontal();
    }
}