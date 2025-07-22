package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 禁用Create mod的原生DivingBoots功能
 * 当玩家装备了完整的潜水套装时
 */
@Mixin(value = DivingBootsItem.class, priority = 1300)
public class CreateBootsDisableMixin {

    /**
     * 拦截Create的潜水靴增强事件
     * 如果玩家装备了完整的潜水装备，则取消Create的原生增强
     */
    @Inject(method = "accelerateDescentUnderwater", at = @At("HEAD"), cancellable = true)
    private static void disableCreateJumpWhenFullEquipped(EntityTickEvent.Pre event, CallbackInfo ci) {
        if (!(event.getEntity() instanceof LivingEntity entity)) {
            return;
        }

        // 检查是否为玩家
        if (!(entity instanceof Player player)) {
            return;
        }

        // 检查是否装备了完整的潜水套装
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        // 如果装备了完整的潜水套装，取消Create的原生跳跃增强
        if (hasDivingBoots && hasLeggings && (player.isInWater() || player.isInLava())) {
            // 移除Create的HeavyBoots标记，防止其影响
            player.getPersistentData().remove("HeavyBoots");
            // 取消Create的跳跃增强逻辑
            ci.cancel();
        }
    }
}