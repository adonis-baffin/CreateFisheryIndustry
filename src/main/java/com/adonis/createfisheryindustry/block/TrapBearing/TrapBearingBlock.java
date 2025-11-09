package com.adonis.createfisheryindustry.block.TrapBearing;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TrapBearingBlock extends BearingBlock implements IBE<TrapBearingBlockEntity> {

    public TrapBearingBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!player.mayBuild() || player.isShiftKeyDown()) {
            return ItemInteractionResult.FAIL;
        }

        if (stack.isEmpty()) {
            if (level.isClientSide) {
                return ItemInteractionResult.SUCCESS;
            }

            withBlockEntityDo(level, pos, be -> {
                if (be.isRunning()) {
                    be.disassemble();
                    // 显示拆卸成功消息
                    player.displayClientMessage(
                            Component.translatable("create_fishery.trap_bearing.disassembled")
                                    .withStyle(ChatFormatting.YELLOW),
                            true
                    );
                } else {
                    // 尝试组装
                    String errorMessage = be.tryAssemble();

                    if (errorMessage == null) {
                        // 组装成功
                        be.setAssembleNextTick(true);
                        player.displayClientMessage(
                                Component.translatable("create_fishery.trap_bearing.assembled")
                                        .withStyle(ChatFormatting.GREEN),
                                true
                        );
                    } else {
                        // 组装失败，显示错误信息
                        player.displayClientMessage(
                                Component.literal(errorMessage)
                                        .withStyle(ChatFormatting.RED),
                                true
                        );
                    }
                }
            });
            return ItemInteractionResult.SUCCESS;
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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