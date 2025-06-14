package com.adonis.createfisheryindustry.block.SmartNozzle;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmItem;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.RenderShape;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class SmartNozzleBlock extends NozzleBlock implements ProperWaterloggedBlock {

    public SmartNozzleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        return withWater(state, context);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, net.minecraft.world.level.LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        updateWater(level, state, currentPos);
        return super.updateShape(state, facing, facingState, level, currentPos, facingPos);
    }

    @Override
    public net.minecraft.world.level.material.FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CreateFisheryBlockEntities.SMART_NOZZLE.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == CreateFisheryBlockEntities.SMART_NOZZLE.get()
                ? (lvl, pos, blockState, be) -> SmartNozzleBlockEntity.tick(lvl, pos, blockState, (SmartNozzleBlockEntity) be)
                : null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SmartNozzleBlockEntity smartNozzle) {
                smartNozzle.dropInventory();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter worldIn, BlockPos pos, CollisionContext context) {
        return AllShapes.NOZZLE.get(state.getValue(FACING));
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader worldIn, BlockPos pos) {
        Direction towardsFan = state.getValue(FACING).getOpposite();
        BlockEntity be = worldIn.getBlockEntity(pos.relative(towardsFan));
        return be instanceof IAirCurrentSource
                && ((IAirCurrentSource) be).getAirflowOriginSide() == towardsFan.getOpposite();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 处理扳手交互
        if (stack.getItem() instanceof WrenchItem) {
            UseOnContext wrenchContext = new UseOnContext(level, player, hand, stack, hitResult);
            InteractionResult result = player.isShiftKeyDown()
                    ? onSneakWrenched(state, wrenchContext)
                    : onWrenched(state, wrenchContext);
            if (result != InteractionResult.PASS) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 处理机械臂交互
        if (stack.getItem() instanceof ArmItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 客户端直接返回成功
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        // 获取方块实体
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SmartNozzleBlockEntity smartNozzle)) {
            return ItemInteractionResult.FAIL;
        }

        var inventory = smartNozzle.getInventory();
        if (inventory == null) {
            return ItemInteractionResult.FAIL;
        }

        // 检查是否有物品并尝试提取
        ItemStack extracted = ItemStack.EMPTY;
        int totalItems = 0;

        // 先统计总物品数量
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack slotStack = inventory.getStackInSlot(i);
            if (!slotStack.isEmpty()) {
                totalItems += slotStack.getCount();
            }
        }

        // 尝试提取物品（从第一个非空槽位提取）
        for (int i = 0; i < inventory.getSlots(); i++) {
            extracted = inventory.extractItem(i, 64, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }

        if (!extracted.isEmpty()) {
            // 成功提取物品，给予玩家
            ItemHandlerHelper.giveItemToPlayer(player, extracted);

            // 显示提取信息
            player.displayClientMessage(
                    Component.translatable("create_fishery.smart_nozzle.extracted",
                            extracted.getCount(),
                            Component.translatable(extracted.getDescriptionId())),
                    true
            );
            return ItemInteractionResult.sidedSuccess(false);
        } else {
            // 没有提取到物品
            if (totalItems > 0) {
                player.displayClientMessage(
                        Component.translatable("create_fishery.smart_nozzle.extraction_failed"),
                        true
                );
            } else {
                player.displayClientMessage(
                        Component.translatable("create_fishery.smart_nozzle.empty"),
                        true
                );
            }
            return ItemInteractionResult.CONSUME;
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 0;
    }
}