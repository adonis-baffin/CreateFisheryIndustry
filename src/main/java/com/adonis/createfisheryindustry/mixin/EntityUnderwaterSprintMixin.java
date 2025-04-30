//package com.adonis.createfisheryindustry.mixin;
//
//import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
//import com.simibubi.create.content.equipment.armor.DivingBootsItem;
//import net.minecraft.client.player.Input;
//import net.minecraft.world.entity.EquipmentSlot;
//import net.minecraft.world.entity.player.Player;
//import net.minecraft.world.phys.Vec3;
//import org.spongepowered.asm.mixin.Mixin;
//import org.spongepowered.asm.mixin.injection.At;
//import org.spongepowered.asm.mixin.injection.Inject;
//import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//
//@Mixin(Player.class)
//public abstract class EntityUnderwaterSprintMixin {
//    // 双击检测所需字段
//    private long lastForwardPressTime = 0;
//    private boolean forwardKeyPressed = false;
//    private boolean canUnderwaterSprint = false;
//
//    /**
//     * 检查玩家是否可以在水下疾跑
//     */
//    private boolean canEntityUnderwaterSprint(Player player) {
//        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET)
//                .getItem() instanceof DivingBootsItem;
//        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS)
//                .getItem() instanceof CopperDivingLeggingsItem;
//        return hasDivingBoots
//                && hasLeggings
//                && player.isInWater()
//                && !player.isPassenger();
//    }
//
//    /**
//     * 每 tick 检测双击 W 键激活水下疾跑
//     */
//    @Inject(method = "tick", at = @At("HEAD"))
//    private void onTick(CallbackInfo ci) {
//        Player player = (Player) (Object) this;
//        if (!player.level().isClientSide()) return;
//
//        Input input;
//        try {
//            input = (Input) player.getClass().getField("input").get(player);
//        } catch (Exception e) {
//            // 获取 input 失败，跳过
//            return;
//        }
//
//        boolean isForwardPressed = input.forwardImpulse > 0;
//
//        // 检测双击 W（300ms 内两次按下）
//        if (isForwardPressed && !forwardKeyPressed) {
//            long now = System.currentTimeMillis();
//            if (now - lastForwardPressTime < 300
//                    && canEntityUnderwaterSprint(player)) {
//                canUnderwaterSprint = true;
//                player.setSprinting(true);
//            }
//            lastForwardPressTime = now;
//        }
//        forwardKeyPressed = isForwardPressed;
//
//        // 条件不满足时取消水下疾跑
//        if (!canEntityUnderwaterSprint(player)) {
//            canUnderwaterSprint = false;
//        }
//
//        // 按住前进键时保持冲刺状态
//        if (canUnderwaterSprint && isForwardPressed) {
//            player.setSprinting(true);
//        }
//    }
//
//    /**
//     * 在 travel 末尾应用速度加成
//     */
//    @Inject(method = "travel", at = @At("TAIL"))
//    private void onTravel(Vec3 travelVector, CallbackInfo ci) {
//        Player player = (Player) (Object) this;
//        if (canUnderwaterSprint && player.isInWater()) {
//            Vec3 motion = player.getDeltaMovement();
//            double boost = 0.025;            // 水下疾跑速度提升
//            float yRot = player.getYRot();
//            float forward = forwardKeyPressed ? 1.0f : 0.0f;
//
//            double dx = motion.x + (-Math.sin(Math.toRadians(yRot)) * forward * boost);
//            double dz = motion.z + (Math.cos(Math.toRadians(yRot)) * forward * boost);
//
//            player.setDeltaMovement(dx, motion.y, dz);
//        }
//    }
//}