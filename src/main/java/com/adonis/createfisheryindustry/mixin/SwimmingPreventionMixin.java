package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 阻止穿戴潜水装备时进入游泳姿态
 */
@Mixin(value = Entity.class, priority = 1200)
public abstract class SwimmingPreventionMixin {
    
    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void preventSwimmingWithDivingGear(CallbackInfo ci) {
        if ((Object) this instanceof Player player) {
            boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
            boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                    || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
            
            if (hasDivingBoots && hasLeggings && (player.isInWater() || player.isInLava())) {
                // 取消默认的游泳更新逻辑，保持站立姿态
                ci.cancel();
            }
        }
    }
}