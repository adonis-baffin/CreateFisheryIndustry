package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerWaterSprintMixin {
    @Shadow protected int jumpTriggerTime;

    private boolean wasInFluid = false;
    private boolean jumpHandled = false;
    private int jumpCooldown = 0;
    private boolean jumpKeyPressed = false;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (jumpCooldown > 0) {
            jumpCooldown--;
        }

        Player player = (Player) (Object) this;
        wasInFluid = player.isInWater() || player.isInLava();

        if (jumpTriggerTime > 0) {
            jumpKeyPressed = true;
        } else if (jumpTriggerTime == 0) {
            jumpKeyPressed = false;
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void enhanceFluidJump(Vec3 travelVector, CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (!player.isInWater() && !player.isInLava()) return;

        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        if (!hasDivingBoots || !hasLeggings) return;

        if (player.isPassenger() || player.isFallFlying()) {
            return;
        }

        // 在生存模式中，检查饥饿度
        if (!player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= 6) {
            return;
        }

        boolean isJumping = false;

        if (jumpTriggerTime > 0 && !jumpHandled && jumpCooldown == 0) {
            isJumping = true;
        }

        Vec3 motion = player.getDeltaMovement();
        if (!isJumping && motion.y > 0.05 && wasInFluid && !jumpHandled && jumpCooldown == 0) {
            isJumping = true;
        }

        if (!isJumping && jumpKeyPressed && !jumpHandled && jumpCooldown == 0) {
            isJumping = true;
        }

        if (isJumping) {
            // 调整跳跃增强和疾跑配合效果
            double jumpBoost = player.getAbilities().instabuild ? 0.8 : 1.0;
            // 如果正在疾跑，进一步增强跳跃高度
            if (player.isSprinting()) {
                jumpBoost *= 1.1; // 疾跑时额外增加10%跳跃高度
            }

            player.setDeltaMovement(motion.x, jumpBoost, motion.z);
            // 设置强化跳跃标志
            player.getPersistentData().putBoolean("EnhancedJumpActive", true);
            jumpHandled = true;
            jumpCooldown = 5;

            if (!player.getAbilities().instabuild) {
                player.causeFoodExhaustion(0.05F);
            }
        }

        if (jumpHandled && (player.onGround() || (!player.isInWater() && !player.isInLava()))) {
            jumpHandled = false;
            // 重置强化跳跃标志
            player.getPersistentData().putBoolean("EnhancedJumpActive", false);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void onJumpFromGround(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        if (player.isInWater() || player.isInLava()) {
            boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
            boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                    || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

            if (hasDivingBoots && hasLeggings) {
                jumpHandled = true;
                jumpCooldown = 5;
                // 设置强化跳跃标志
                player.getPersistentData().putBoolean("EnhancedJumpActive", true);
            }
        }
    }
}