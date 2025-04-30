//package com.adonis.createfisheryindustry.mixin;
//
//import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
//import com.simibubi.create.content.equipment.armor.DivingBootsItem;
//import net.minecraft.world.entity.EquipmentSlot;
//import net.minecraft.world.entity.LivingEntity;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.entity.Pose;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
//@Mixin(Player.class)
//public class PlayerUnderwaterSprintMixin {
//    /**
//     * 修改Player.getSpeed方法以增加水下疾跑速度
//     */
//    @Inject(method = "getSpeed", at = @At("RETURN"), cancellable = true)
//    private void onGetSpeed(CallbackInfoReturnable<Float> cir) {
//        Player player = (Player)(Object) this;
//
//        // 检查是否同时穿着潜水靴和铜质潜水护腿
//        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
//        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
//
//        // 如果符合条件且正在水中疾跑
//        if (hasDivingBoots && hasLeggings && player.isInWater() && player.isSprinting()) {
//            float currentSpeed = cir.getReturnValue();
//            // 增加25%的速度
//            cir.setReturnValue(currentSpeed * 1.25f);
//        }
//    }
//
//    /**
//     * 允许在水中保持疾跑状态
//     */
//    @Inject(method = "updateIsUnderwater", at = @At("HEAD"), cancellable = true)
//    private void onIsUnderWater(CallbackInfoReturnable<Boolean> cir) {
//        Player player = (Player)(Object) this;
//
//        // 检查是否同时穿着潜水靴和铜质潜水护腿
//        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
//        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
//
//        // 如果进行的是疾跑检查且玩家有特殊装备，允许在水中疾跑
//        if (hasDivingBoots && hasLeggings && player.isSprinting()) {
//            // 在调用栈中查找是否是sprinting相关检查
//            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
//            for (StackTraceElement element : stackTrace) {
//                if (element.getMethodName().contains("sprint") || element.getMethodName().contains("Sprint")) {
//                    // 欺骗游戏，表示玩家不在水下，这样可以保持疾跑状态
//                    cir.setReturnValue(false);
//                    return;
//                }
//            }
//        }
//    }
//
//    /**
//     * 禁用水下游泳动作
//     */
////    @Inject(method = "setSwimming", at = @At("HEAD"), cancellable = true)
////    private void onSetSwimming(boolean swimming, CallbackInfo ci) {
////        Player player = (Player)(Object) this;
////
////        // 检查是否同时穿着潜水靴和铜质潜水护腿
////        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
////        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
////
////        // 如果玩家穿着特殊装备且尝试开始游泳，则取消该操作
////        if (hasDivingBoots && hasLeggings && swimming && player.isInWater()) {
////            ci.cancel(); // 取消设置游泳状态
////        }
////    }
//
//    /**
//     * 阻止按下W键时自动切换到游泳姿势
//     */
//    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
//    private void onUpdateSwimming(CallbackInfo ci) {
//        Player player = (Player)(Object) this;
//
//        // 检查是否同时穿着潜水靴和铜质潜水护腿
//        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
//        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem;
//
//        // 如果玩家穿着特殊装备，阻止游泳判定更新
//        if (hasDivingBoots && hasLeggings && player.isInWater()) {
//            ci.cancel(); // 取消更新游泳状态
//
//            // 如果玩家处于游泳姿势，将其改回正常姿势
//            if (player.getPose() == Pose.SWIMMING) {
//                player.setPose(Pose.STANDING);
//            }
//
//            // 确保玩家不处于游泳状态
//            if (player.isSwimming()) {
//                player.setSwimming(false);
//            }
//        }
//    }
//}