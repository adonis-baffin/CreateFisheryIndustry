package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 为超级跳跃添加冷却时间
 */
@Mixin(value = LivingEntity.class, priority = 1000)
public class JumpCooldownMixin {

    @Unique
    private int cfi_jumpCooldown = 0;

    @Inject(method = "tick", at = @At("HEAD"))
    private void updateJumpCooldown(CallbackInfo ci) {
        if (cfi_jumpCooldown > 0) {
            cfi_jumpCooldown--;
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void checkJumpCooldown(CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;

        // 只对玩家应用冷却
        if (!(entity instanceof Player player)) {
            return;
        }

        // 检查是否在水中且装备了完整潜水装备
        if ((player.isInWater() || player.isInLava())) {
            boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
            boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                    || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

            if (hasDivingBoots && hasLeggings) {
                // 检查冷却
                if (cfi_jumpCooldown > 0) {
                    // 取消跳跃
                    ci.cancel();

                    if (!player.level().isClientSide && player.tickCount % 20 == 0) {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§c[CFI] 跳跃冷却中... (" + (cfi_jumpCooldown / 20) + "s)"),
                                true
                        );
                    }
                } else {
                    // 设置冷却
                    cfi_jumpCooldown = CreateFisheryCommonConfig.getDivingJumpCooldown();
                }
            }
        }
    }
}