package com.adonis.createfisheryindustry.block.BaitTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.equipment.wrench.WrenchItem;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmItem;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

public class BaitTrapBlock extends Block implements EntityBlock, ProperWaterloggedBlock, IWrenchable {

    public static final BooleanProperty BAITED = BooleanProperty.create("baited");
    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public BaitTrapBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BAITED, false)
                .setValue(FACING, Direction.UP)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BAITED, FACING, WATERLOGGED);
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

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(BAITED) ? 5 : 0;
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
        return net.minecraft.world.phys.shapes.Shapes.block(); // 保持完整方块碰撞箱
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                           Player player, InteractionHand hand, BlockHitResult hitResult) {
        // 扳手处理（保持不变）
        if (stack.getItem() instanceof WrenchItem) {
            UseOnContext wrenchContext = new UseOnContext(level, player, hand, stack, hitResult);
            InteractionResult result = player.isShiftKeyDown()
                    ? onSneakWrenched(state, wrenchContext)
                    : onWrenched(state, wrenchContext);
            if (result != InteractionResult.PASS) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        if (stack.getItem() instanceof ArmItem) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BaitTrapBlockEntity baitTrap)) {
            return ItemInteractionResult.FAIL;
        }

        // 只有潜行 + 手持有效诱饵时才允许放入诱饵
        if (player.isShiftKeyDown()) {
            BaitTrapRecipe recipe = baitTrap.findRecipe(stack);
            if (recipe != null) {
                // 如果当前已有不同类型的诱饵，先吐出旧诱饵
                if (baitTrap.isBaited() && !ItemStack.isSameItemSameComponents(baitTrap.getCurrentBait(), stack)) {
                    ItemStack removedBait = baitTrap.removeBait();
                    if (!removedBait.isEmpty() && !player.isCreative()) {
                        if (!player.getInventory().add(removedBait)) {
                            player.drop(removedBait, false);
                        }
                    }
                }

                // 尝试放入新诱饵
                if (baitTrap.tryAddBait(stack)) {
                    // stack 已经被 tryAddBait shrink，无需额外操作
                    return ItemInteractionResult.sidedSuccess(false);
                }
            }
        }

        // 其他情况（包括手持非诱饵物品右键）不处理提取，直接 PASS
        // 提取普通物品的逻辑移到空手右键（useWithoutItem）
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BaitTrapBlockEntity baitTrap)) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            // 空手 + 潜行：取出全部诱饵
            ItemStack removedBait = baitTrap.removeBait();
            if (!removedBait.isEmpty()) {
                if (!player.getInventory().add(removedBait)) {
                    player.drop(removedBait, false);
                }
                player.displayClientMessage(
                        Component.translatable("create_fishery.bait_trap.bait_removed",
                                removedBait.getCount(),
                                Component.translatable(removedBait.getDescriptionId())),
                        true
                );
                return InteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(
                        Component.translatable("create_fishery.bait_trap.no_bait"),
                        true
                );
                return InteractionResult.CONSUME;
            }
        } else {
            // 空手 + 非潜行：提取普通库存物品（一次最多64个）
            var inventory = baitTrap.getInventory();
            if (inventory == null) {
                return InteractionResult.FAIL;
            }

            ItemStack extracted = ItemStack.EMPTY;
            int totalItems = 0;

            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack slotStack = inventory.getStackInSlot(i);
                if (!slotStack.isEmpty()) {
                    totalItems += slotStack.getCount();
                }
            }

            for (int i = 0; i < inventory.getSlots(); i++) {
                extracted = inventory.extractItem(i, 64, false);
                if (!extracted.isEmpty()) {
                    break;
                }
            }

            if (!extracted.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, extracted);

                player.displayClientMessage(
                        Component.translatable("create_fishery.bait_trap.extracted",
                                extracted.getCount(),
                                Component.translatable(extracted.getDescriptionId())),
                        true
                );
                return InteractionResult.SUCCESS;
            } else {
                if (totalItems > 0) {
                    player.displayClientMessage(
                            Component.translatable("create_fishery.bait_trap.extraction_failed"),
                            true
                    );
                } else {
                    player.displayClientMessage(
                            Component.translatable("create_fishery.bait_trap.empty"),
                            true
                    );
                }
                return InteractionResult.CONSUME;
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BaitTrapBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == CreateFisheryBlockEntities.BAIT_TRAP.get()) {
            return (lvl, pos, st, be) -> BaitTrapBlockEntity.tick(lvl, pos, st, (BaitTrapBlockEntity) be);
        }
        return null;
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

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BaitTrapBlockEntity baitTrap) {
            var inventory = baitTrap.getInventory();
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
            int signal = (int) Math.floor((stack.getCount() / (float) stack.getMaxStackSize()) * 14) + 1;
            return signal;
        }
        return 0;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BaitTrapBlockEntity baitTrap) {
                baitTrap.dropInventory();
                baitTrap.dropBait();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
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
            Block.getDrops(state, serverLevel, pos, level.getBlockEntity(pos), player, context.getItemInHand())
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
}