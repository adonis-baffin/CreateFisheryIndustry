package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 简单的疾跑保护 - 只拦截 setSprinting 调用
 */
@Mixin(value = LivingEntity.class, priority = 1200)
public abstract class LivingEntitySprintProtectionMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void protectUnderwaterSprint(boolean sprinting, CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            // 如果试图取消疾跑，检查是否应该保持疾跑
            if (!sprinting && shouldMaintainUnderwaterSprint(player)) {
                ci.cancel();
            }
        }
    }

    private static boolean shouldMaintainUnderwaterSprint(Player player) {
        // 检查装备
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasDivingLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        // 检查是否在流体中
        boolean inFluid = player.isInWater() || player.isInLava();

        // 检查水下疾跑标记
        boolean hasUnderwaterSprintFlag = player.getPersistentData().getBoolean("CFI_UnderwaterSprintActive");

        return hasDivingBoots && hasDivingLeggings && inFluid && hasUnderwaterSprintFlag;
    }
}