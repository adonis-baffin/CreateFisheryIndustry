package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = "createfisheryindustry")
public class WaterJumpEventHandler {
    private static boolean jumpHandled = false;

    /**
     * 捕获玩家跳跃事件
     */
    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 检查是否在水中且装备铜质潜水护腿
        if (!player.isInWater()) return;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
        if (!hasLeggings) return;

        // 施加 Y 速度 0.4 以实现高跳
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, 0.4, motion.z);
        jumpHandled = true;
        // 调试日志
        System.out.println("LivingJumpEvent: Water jump enhanced for player " + player.getName().getString());
    }

    /**
     * 在 EntityTickEvent.Pre 处理潜泳或漂浮状态的跳跃
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 检查是否在水中且装备铜质潜水护腿
        if (!player.isInWater()) {
            jumpHandled = false;
            return;
        }
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
        if (!hasLeggings) {
            jumpHandled = false;
            return;
        }

        // 检测潜泳或漂浮时的跳跃意图（潜泳或朝上）
        boolean isJumping = (player.isSwimming() || player.getLookAngle().y > 0.0) && player.getDeltaMovement().y < 0.3 && !jumpHandled;
        if (isJumping) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, 0.4, motion.z);
            jumpHandled = true;
            System.out.println("EntityTickEvent.Pre: Water jump enhanced for player " + player.getName().getString());
        }

        // 调试日志
        if (player.isInWater() && hasLeggings) {
            System.out.println("Debug Pre: InWater=" + player.isInWater() + ", HasLeggings=" + hasLeggings + ", LookAngleY=" + player.getLookAngle().y + ", YMotion=" + player.getDeltaMovement().y + ", IsSwimming=" + player.isSwimming() + ", JumpHandled=" + jumpHandled);
        }
    }

    /**
     * 在 EntityTickEvent.Post 防止潜水靴子覆盖
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        // 检查是否在水中且装备铜质潜水护腿
        if (!player.isInWater() || !jumpHandled) return;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
        if (!hasLeggings) return;

        // 如果 Y 速度被修改（例如潜水靴子下沉），恢复增强
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0.3) {
            player.setDeltaMovement(motion.x, 0.4, motion.z);
            System.out.println("EntityTickEvent.Post: Restored water jump Y velocity for player " + player.getName().getString());
        }

        // 重置 jumpHandled，当玩家触地或离开水
        if (player.onGround() || !player.isInWater()) {
            jumpHandled = false;
        }
    }
}