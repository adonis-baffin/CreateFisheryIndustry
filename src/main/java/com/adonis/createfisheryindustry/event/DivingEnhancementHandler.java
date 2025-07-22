//package com.adonis.createfisheryindustry.event;
//
//import com.adonis.createfisheryindustry.CreateFisheryMod;
//import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
//import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
//import com.simibubi.create.content.equipment.armor.DivingBootsItem;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.entity.EquipmentSlot;
//import net.minecraft.world.phys.Vec3;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.event.tick.PlayerTickEvent;
//
//@EventBusSubscriber(modid = CreateFisheryMod.ID)
//public class DivingEnhancementHandler {
//
//    @SubscribeEvent
//    public static void onPlayerTick(PlayerTickEvent.Pre event) {
//        Player player = event.getEntity();
//
//        // 检查是否装备完整的潜水装备
//        if (!hasDivingGear(player)) {
//            return;
//        }
//
//        // 处理摔落保护倒计时
//        int protectionTime = player.getPersistentData().getInt("EnhancedJumpProtectionTime");
//        if (protectionTime > 0) {
//            player.getPersistentData().putInt("EnhancedJumpProtectionTime", protectionTime - 1);
//            // 只要还有保护时间，就保持EnhancedJumpActive为true
//            player.getPersistentData().putBoolean("EnhancedJumpActive", true);
//        } else {
//            // 保护时间结束，清除标志
//            player.getPersistentData().putBoolean("EnhancedJumpActive", false);
//        }
//
//        // 修改：只需要玩家在水中，不要求头部也在水下
//        if (!player.isInWater() && !player.isInLava()) {
//            // 重置跳跃标志
//            player.getPersistentData().putInt("DivingJumpCooldown", 0);
//            player.getPersistentData().putInt("DivingSprintCounter", 0);
//            return;
//        }
//
//        // 检查基本条件
//        if (player.isPassenger() || player.isFallFlying()) {
//            return;
//        }
//
//        // 处理冷却时间
//        int cooldown = player.getPersistentData().getInt("DivingJumpCooldown");
//        if (cooldown > 0) {
//            player.getPersistentData().putInt("DivingJumpCooldown", cooldown - 1);
//        }
//
//        // 处理跳跃增强
//        handleEnhancedJumping(player);
//
//        // 处理疾跑增强 - 只要在水中且疾跑就触发
//        handleSprintEnhancement(player);
//    }
//
//    private static boolean hasDivingGear(Player player) {
//        var feetItem = player.getItemBySlot(EquipmentSlot.FEET).getItem();
//        var legsItem = player.getItemBySlot(EquipmentSlot.LEGS).getItem();
//
//        boolean hasDivingBoots = feetItem instanceof DivingBootsItem;
//        boolean hasDivingLeggings = legsItem instanceof CopperDivingLeggingsItem ||
//                legsItem instanceof NetheriteDivingLeggingsItem;
//
//        // 调试：打印装备信息
//        // System.out.println("Feet: " + feetItem.getClass().getSimpleName() + " (DivingBoots: " + hasDivingBoots + ")");
//        // System.out.println("Legs: " + legsItem.getClass().getSimpleName() + " (DivingLeggings: " + hasDivingLeggings + ")");
//
//        return hasDivingBoots && hasDivingLeggings;
//    }
//
//    private static void handleEnhancedJumping(Player player) {
//        // 简化跳跃检测 - 使用多种方法检测跳跃
//        boolean isJumping = false;
//
//        // 方法1：检查Y轴动量 - 降低阈值以便在浅水中也能触发
//        Vec3 motion = player.getDeltaMovement();
//        if (motion.y > 0.02 && !player.onGround()) { // 从0.05降低到0.02
//            isJumping = true;
//        }
//
//        // 方法2：尝试反射访问jumpTriggerTime
//        try {
//            var field = Player.class.getDeclaredField("jumpTriggerTime");
//            field.setAccessible(true);
//            int jumpTriggerTime = field.getInt(player);
//            if (jumpTriggerTime > 0) {
//                isJumping = true;
//            }
//        } catch (Exception e) {
//            // 反射失败时继续使用方法1
//        }
//
//        boolean wasJumping = player.getPersistentData().getBoolean("WasJumpingLastTick");
//        boolean justStartedJumping = isJumping && !wasJumping;
//        player.getPersistentData().putBoolean("WasJumpingLastTick", isJumping);
//
//        int cooldown = player.getPersistentData().getInt("DivingJumpCooldown");
//        boolean enhancedJumpUsed = player.getPersistentData().getBoolean("EnhancedJumpUsed");
//
//        // 更宽松的跳跃检测条件 - 在浅水中也能触发
//        if (isJumping && cooldown <= 0 && (!enhancedJumpUsed || motion.y <= 0.3)) {
//            performEnhancedJump(player);
//        }
//
//        // 修改：只要离开水就重置EnhancedJumpUsed，但不影响摔落保护
//        if (!player.isInWater() && !player.isInLava()) {
//            player.getPersistentData().putBoolean("EnhancedJumpUsed", false);
//            // 不在这里清除EnhancedJumpActive，让保护时间系统处理
//        }
//    }
//
//    private static void performEnhancedJump(Player player) {
//        Vec3 motion = player.getDeltaMovement();
//
//        // 根据水深调整跳跃力度
//        double jumpPower = 1.2; // 基础跳跃力度
//
//        // 检查是否在深水中（头部在水下）
//        if (player.isUnderWater()) {
//            jumpPower = 1.8; // 深水中更强的跳跃
//        } else {
//            jumpPower = 1.0; // 浅水中适中的跳跃
//        }
//
//        // 疾跑时额外增强
//        if (player.isSprinting()) {
//            jumpPower *= 1.3;
//        }
//
//        // 根据护腿类型调整
//        if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem) {
//            jumpPower *= 1.2; // 下界合金护腿额外20%增强
//        }
//
//        // 应用跳跃增强
//        player.setDeltaMovement(motion.x, jumpPower, motion.z);
//        player.getPersistentData().putBoolean("EnhancedJumpActive", true);
//        player.getPersistentData().putBoolean("EnhancedJumpUsed", true);
//        player.getPersistentData().putInt("DivingJumpCooldown", 15); // 增加冷却时间防止连续触发
//
//        // 修复：设置长时间的无伤害标志，确保着陆时还有效
//        player.getPersistentData().putInt("EnhancedJumpProtectionTime", 60); // 3秒保护时间
//
//        // 消耗饥饿度
//        if (!player.getAbilities().instabuild) {
//            player.causeFoodExhaustion(0.1F);
//        }
//    }
//
//    private static void handleSprintEnhancement(Player player) {
//        // 调试：打印疾跑状态
//        boolean isSprinting = player.isSprinting();
//        if (isSprinting) {
//            System.out.println("Player is sprinting in water! Motion before: " + player.getDeltaMovement());
//        }
//
//        if (!isSprinting) {
//            player.getPersistentData().putInt("DivingSprintCounter", 0);
//            return;
//        }
//
//        // 每tick都应用疾跑增强
//        int sprintCounter = player.getPersistentData().getInt("DivingSprintCounter");
//        sprintCounter++;
//        player.getPersistentData().putInt("DivingSprintCounter", sprintCounter);
//
//        Vec3 motion = player.getDeltaMovement();
//
//        // 修改：更强的疾跑效果，更明显
//        double sprintBoostStrength = 0.2; // 增加疾跑推进力
//
//        // 根据护腿类型调整
//        if (player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem) {
//            sprintBoostStrength = 0.3; // 下界合金护腿更强的疾跑
//        }
//
//        // 获取玩家面向方向
//        Vec3 lookAngle = player.getLookAngle();
//
//        // 计算疾跑推进力 - 朝玩家面向的方向
//        Vec3 sprintForce = new Vec3(
//                lookAngle.x * sprintBoostStrength,
//                0, // 不影响Y轴
//                lookAngle.z * sprintBoostStrength
//        );
//
//        // 应用疾跑推进力
//        Vec3 newMotion = motion.add(sprintForce);
//
//        // 调试：打印疾跑效果
//        System.out.println("Sprint force applied: " + sprintForce + ", Motion after: " + newMotion);
//
//        // 限制最大速度，防止过快
//        double maxHorizontalSpeed = 1.5; // 允许较快的疾跑速度
//        double currentHorizontalSpeed = Math.sqrt(newMotion.x * newMotion.x + newMotion.z * newMotion.z);
//
//        if (currentHorizontalSpeed > maxHorizontalSpeed) {
//            double scale = maxHorizontalSpeed / currentHorizontalSpeed;
//            newMotion = new Vec3(newMotion.x * scale, newMotion.y, newMotion.z * scale);
//            System.out.println("Speed limited, final motion: " + newMotion);
//        }
//
//        player.setDeltaMovement(newMotion);
//
//        // 适当的饥饿度消耗
//        if (!player.getAbilities().instabuild && sprintCounter % 30 == 0) {
//            player.causeFoodExhaustion(0.1F);
//        }
//    }
//}