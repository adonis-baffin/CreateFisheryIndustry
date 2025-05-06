package com.adonis.createfisheryindustry.block.SmartBeehive;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;

public class SmartBeehiveBlock extends Block implements EntityBlock, ProperWaterloggedBlock, IWrenchable {
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;
    public static final int MAX_HONEY_LEVELS = 5;

    public SmartBeehiveBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HONEY_LEVEL, 0)
                .setValue(WATERLOGGED, false));
    }

    // 透明方块相关方法
    public boolean isSolidRender(BlockState state, BlockGetter reader, BlockPos pos) {
        return false;
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
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof SmartBeehiveBlockEntity beehive)) {
            return ItemInteractionResult.FAIL;
        }

        // 处理蜜蜂物品添加
        if (stack.is(Items.BEE_SPAWN_EGG)) {
            if (!beehive.isFull()) {
                beehive.addOccupantFromItem(player, stack);
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else {
                player.displayClientMessage(Component.translatable("createfisheryindustry.smart_beehive.full"), true);
                return ItemInteractionResult.CONSUME;
            }
        }

        // 处理剪刀采集蜜脾
        if (state.getValue(HONEY_LEVEL) >= MAX_HONEY_LEVELS && stack.canPerformAction(ItemAbilities.SHEARS_HARVEST)) {
            level.playSound(player, pos, SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
            popResource(level, pos, new ItemStack(Items.HONEYCOMB, 3));
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            beehive.resetHoneyLevel();
            level.gameEvent(player, GameEvent.SHEAR, pos);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 处理玻璃瓶采集蜂蜜
        if (state.getValue(HONEY_LEVEL) >= MAX_HONEY_LEVELS && stack.is(Items.GLASS_BOTTLE)) {
            stack.shrink(1);
            level.playSound(player, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            ItemStack honeyBottle = new ItemStack(Items.HONEY_BOTTLE);
            if (stack.isEmpty()) {
                player.setItemInHand(hand, honeyBottle);
            } else if (!player.getInventory().add(honeyBottle)) {
                player.drop(honeyBottle, false);
            }
            beehive.resetHoneyLevel();
            level.gameEvent(player, GameEvent.FLUID_PICKUP, pos);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        // 手动提取蜜脾
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        var inventory = beehive.getInventory();
        if (inventory == null) {
            return ItemInteractionResult.FAIL;
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
            player.displayClientMessage(Component.translatable("createfisheryindustry.smart_beehive.empty"), true);
            return ItemInteractionResult.CONSUME;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HONEY_LEVEL, WATERLOGGED, BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()), context);
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CreateFisheryBlockEntities.SMART_BEEHIVE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == CreateFisheryBlockEntities.SMART_BEEHIVE.get() ? (lvl, pos, blockState, be) -> SmartBeehiveBlockEntity.tick(lvl, pos, blockState, (SmartBeehiveBlockEntity) be) : null;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SmartBeehiveBlockEntity beehive) {
            var inventory = beehive.getInventory();
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
        return state.getValue(HONEY_LEVEL);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.getPoiManager().add(pos, net.minecraft.core.Holder.direct(CreateFisheryBlockEntities.SMART_BEEHIVE_POI.get()));
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.getPoiManager().remove(pos);
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SmartBeehiveBlockEntity beehive) {
                beehive.dropInventory();
                beehive.releaseAllOccupants(state, SmartBeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
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
        return originalState;
    }
}