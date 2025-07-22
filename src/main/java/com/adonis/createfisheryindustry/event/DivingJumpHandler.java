//package com.adonis.createfisheryindustry.event;
//
//import com.adonis.createfisheryindustry.CreateFisheryMod;
//import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
//import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
//import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
//import com.simibubi.create.content.equipment.armor.DivingBootsItem;
//import net.minecraft.world.entity.EquipmentSlot;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.phys.Vec3;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.event.tick.EntityTickEvent;
//
//@EventBusSubscriber(modid = CreateFisheryMod.ID)
//public class DivingJumpHandler {
//
//    @SubscribeEvent
//    public static void onEntityTick(EntityTickEvent.Post event) {
//        if (!(event.getEntity() instanceof Player player)) {
//            return;
//        }
//
//        // 检查是否装备了潜水装备
//        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
//        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
//                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
//
//        if (!hasDivingBoots || !hasLeggings) {
//            return;
//        }
//
//        // 检查玩家是否在水中/岩浆中
//        if (player.isInWater() || player.isInLava()) {
//            // 获取当前的运动向量
//            Vec3 motion = player.getDeltaMovement();
//
//            // 检测是否刚刚开始跳跃（垂直速度突然增加）
//            boolean justJumped = false;
//            double previousY = player.getPersistentData().getDouble("PreviousMotionY");
//
//            // 如果垂直速度从接近0变为正值，说明正在跳跃
//            if (motion.y > 0.4 && previousY <= 0.1 && player.onGround()) {
//                justJumped = true;
//            }
//
//            // 保存当前的垂直速度供下一tick使用
//            player.getPersistentData().putDouble("PreviousMotionY", motion.y);
//
//            if (justJumped) {
//                // 检查饥饿度
//                int minHunger = CreateFisheryCommonConfig.getDivingMinHungerLevel();
//                if (player.getAbilities().instabuild || player.getFoodData().getFoodLevel() > minHunger) {
//                    // 检查是否在疾跑
//                    boolean isSprinting = player.getPersistentData().getBoolean("UnderwaterSprintActive");
//
//                    // 从配置获取目标跳跃力度
//                    double targetJumpPower = CreateFisheryCommonConfig.getDivingBaseJumpPower();
//                    if (isSprinting) {
//                        targetJumpPower = CreateFisheryCommonConfig.getDivingSprintJumpPower();
//                    }
//
//                    // 计算需要增加的额外跳跃力度
//                    double currentJump = motion.y;
//                    double extraJump = targetJumpPower - currentJump;
//
//                    if (extraJump > 0) {
//                        // 应用额外的跳跃力度
//                        player.setDeltaMovement(motion.x, motion.y + extraJump, motion.z);
//
//                        // 标记为增强跳跃
//                        player.getPersistentData().putBoolean("DivingEnhancedJump", true);
//
//                        // 消耗饥饿度
//                        if (!player.getAbilities().instabuild) {
//                            float hungerCost = (float) CreateFisheryCommonConfig.getDivingJumpHungerCost();
//                            player.causeFoodExhaustion(hungerCost);
//                        }
//                    }
//                }
//            }
//        } else {
//            // 不在水中时清除记录
//            player.getPersistentData().putDouble("PreviousMotionY", 0.0);
//        }
//    }
//}