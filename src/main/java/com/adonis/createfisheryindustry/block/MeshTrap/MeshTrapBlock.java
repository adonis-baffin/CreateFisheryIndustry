package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmItem;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class MeshTrapBlock extends Block implements EntityBlock, ProperWaterloggedBlock, IWrenchable {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public MeshTrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false));
    }

    // 透明方块相关方法

    public boolean isSolidRender(BlockState state, BlockGetter reader, BlockPos pos) {
        return false; // 防止方块被视为实心，允许透明渲染
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // 使用模型渲染，确保透明纹理可见
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true; // 允许光线通过
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter world, BlockPos pos) {
        return 0; // 不阻挡光线
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 如果需要更精确的碰撞箱，可以自定义，例如：
        // return AllShapes.CASING_12PX.get(state.getValue(FACING));
        return net.minecraft.world.phys.shapes.Shapes.block(); // 保持完整方块碰撞箱
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 检查玩家是否持有扳手
        if (stack.getItem() instanceof WrenchItem) {
            UseOnContext wrenchContext = new UseOnContext(level, player, hand, stack, hitResult);
            InteractionResult result = player.isShiftKeyDown()
                    ? onSneakWrenched(state, wrenchContext)
                    : onWrenched(state, wrenchContext);
            if (result != InteractionResult.PASS) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        // 检查玩家是否持有动力臂，允许选择交互点
        if (stack.getItem() instanceof ArmItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        // 正常交互逻辑（提取物品）
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MeshTrapBlockEntity meshTrap)) {
            return ItemInteractionResult.FAIL;
        }

        var inventory = meshTrap.getInventory();
        if (inventory == null) {
            return ItemInteractionResult.FAIL;
        }

        boolean hasItems = false;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack slotStack = inventory.getStackInSlot(i);
            if (!slotStack.isEmpty()) {
                hasItems = true;
            }
        }

        ItemStack extracted = ItemStack.EMPTY;
        for (int i = 0; i < inventory.getSlots(); i++) {
            extracted = inventory.extractItem(i, 64, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }

        if (!extracted.isEmpty()) {
            ItemHandlerHelper.giveItemToPlayer(player, extracted);
            return ItemInteractionResult.sidedSuccess(false);
        } else {
            player.displayClientMessage(Component.literal(hasItems ? "Try it again！" : "Trap is empty"), true);
            return ItemInteractionResult.CONSUME;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(
                defaultBlockState().setValue(FACING, context.getClickedFace()),
                context);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CreateFisheryBlockEntities.MESH_TRAP.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == CreateFisheryBlockEntities.MESH_TRAP.get() ? (lvl, pos, blockState, be) -> MeshTrapBlockEntity.tick(lvl, pos, blockState, (MeshTrapBlockEntity) be) : null;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MeshTrapBlockEntity meshTrap) {
            var inventory = meshTrap.getInventory();
            ItemStack stack = ItemStack.EMPTY;
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack s = inventory.getStackInSlot(i);
                if (!s.isEmpty()) {
                    stack = s;
                    break;
                }
            }
            if (stack.isEmpty()) {
                return 0;
            }
            return (int) Math.floor((stack.getCount() / (float) stack.getMaxStackSize()) * 14) + 1;
        }
        return 0;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MeshTrapBlockEntity meshTrap) {
                meshTrap.dropInventory();
            }
            super.onRemove(state, level, pos, newState, isMoving);
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

        // 更新方块状态
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

        // 触发方块破坏事件
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        NeoForge.EVENT_BUS.post(event);
        if (event.isCanceled()) {
            return InteractionResult.SUCCESS;
        }

        // 非创造模式下掉落物品
        if (player != null && !player.isCreative()) {
            Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), player, context.getItemInHand())
                    .forEach(itemStack -> player.getInventory().placeItemBackInInventory(itemStack));
        }

        // 生成破坏粒子并移除方块
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
            return originalState; // 如果点击相同轴上的面，不进行旋转
        }

        Direction newFacing = currentFacing.getClockWise(targetedFace.getAxis());
        return originalState.setValue(FACING, newFacing); // 返回更新后的状态
    }
}