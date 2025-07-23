package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 简化的潜水系统主Mixin
 * 只处理基础功能，不包含超级跳跃
 */
@Mixin(value = Player.class, priority = 1100)
public abstract class DivingSystemMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void cfi_maintainCreateCompatibility(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        
        // 检查是否装备了完整潜水套装
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasDivingLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        
        if (hasDivingBoots && hasDivingLeggings && (player.isInWater() || player.isInLava())) {
            // 当装备完整套装时，保持与Create潜水靴的基础兼容性
            // 不需要特殊处理，让Create的DivingBootsItem继续工作
            // 水下疾跑功能由UnderwaterSprintMixin单独处理
        }
    }
}