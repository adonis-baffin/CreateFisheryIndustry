package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(DivingBootsItem.class)
public class DivingBootsItemMixin {
    /**
     * 修改跳跃时的 Y 速度，当同时穿戴铜质或下界合金潜水护腿时增强
     */
    @Inject(
            method = "accelerateDescentUnderwater(Lnet/neoforged/neoforge/event/tick/EntityTickEvent$Pre;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;",
                    ordinal = 0
            ),
            locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static void enhanceJumpVelocity(EntityTickEvent.Pre event, CallbackInfo ci, LivingEntity entity, Vec3 motion) {
        // 检查是否穿戴铜质或下界合金潜水护腿
        boolean hasLeggings = entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || entity.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        if (!hasLeggings) return;

        // 确认是在适当条件下（在水中或岩浆中，非游泳状态）
        if (!entity.isInWater() && !entity.isInLava() || entity.isSwimming() || entity.isPassenger() || entity.isFallFlying()) {
            return;
        }

        // 检测跳跃 - 使用deltaMovement和onGround
        if (motion.y > 0.0 && (entity.onGround() || entity.verticalCollision)) {
            // 如果满足条件，直接设置更高的Y速度
            entity.setDeltaMovement(motion.x, 0.8, motion.z);
            // 设置强化跳跃标志
            entity.getPersistentData().putBoolean("EnhancedJumpActive", true);
            System.out.println("Enhanced jump in " + (entity.isInLava() ? "lava" : "water") + ": Y velocity set to 0.8 for " + entity.getName().getString());
        }
    }
}