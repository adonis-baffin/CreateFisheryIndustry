package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.common.EntityCaptureHelper;
import com.adonis.createfisheryindustry.block.common.IItemReceiver;
import com.adonis.createfisheryindustry.block.common.PlayerInventoryReceiver;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class MeshTrapInteractionHandler {

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        Entity target = event.getTarget();

        // 检查玩家手持物品是否为 MeshTrap 的方块物品
        if (!heldItem.is(CreateFisheryBlocks.MESH_TRAP.asItem())) {
            return;
        }

        // 确保是在服务器端处理
        if (player.level().isClientSide() || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 确保目标是 Mob 实体
        if (!(target instanceof Mob mob) || !mob.isAlive()) {
            return;
        }

        // 使用统一的实体筛选逻辑
        if (!EntityCaptureHelper.shouldCaptureEntity(mob)) {
            return;
        }

        // 处理实体捕获（使用统一的捕获逻辑）
        processEntityCapture(serverLevel, mob, player);

        // 触发手臂摆动动画
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.swing(InteractionHand.MAIN_HAND, true);
        }

        // 取消默认交互事件
        event.setCanceled(true);
    }

    /**
     * 处理实体捕获，使用统一的捕获逻辑
     */
    private static void processEntityCapture(ServerLevel level, Mob mob, Player player) {
        // 创建物品接收器（优先放入玩家背包）
        IItemReceiver receiver = new PlayerInventoryReceiver(
                player,
                level,
                mob.getX(),
                mob.getY(),
                mob.getZ()
        );

        // 使用统一的捕获逻辑（处理战利品、经验、音效、粒子效果、实体移除）
        EntityCaptureHelper.captureEntity(mob, level, mob.blockPosition(), receiver);
    }
}