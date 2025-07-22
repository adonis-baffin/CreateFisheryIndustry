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
 * 保护水下疾跑状态不被取消
 */
@Mixin(value = LivingEntity.class, priority = 1200)
public abstract class SprintProtectionMixin {

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void protectUnderwaterSprint(boolean sprinting, CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            // 如果试图取消疾跑，检查是否应该保持疾跑
            if (!sprinting && (player.isInWater() || player.isInLava())) {
                boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
                boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                        || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

                // 如果玩家有水下疾跑标记且装备正确，阻止取消疾跑
                if (hasDivingBoots && hasLeggings && player.getPersistentData().getBoolean("CFI_UnderwaterSprint")) {
                    ci.cancel();
                }
            }
        }
    }
}