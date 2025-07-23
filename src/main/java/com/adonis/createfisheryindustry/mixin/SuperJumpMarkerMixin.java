package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 检测Create潜水靴跳跃并增强+标记保护
 */
@Mixin(value = Player.class, priority = 900)
public class SuperJumpMarkerMixin {
    
    @Unique
    private boolean cfi_wasOnGround = true;
    
    @Unique
    private boolean cfi_jumpDetected = false;
    
    @Inject(method = "tick", at = @At("HEAD"))
    private void enhanceAndMarkJump(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        
        // 检查装备
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;
        
        // 必须在流体中并装备完整潜水装备
        if (!hasDivingBoots || !hasLeggings || (!player.isInWater() && !player.isInLava())) {
            cfi_wasOnGround = player.onGround();
            return;
        }
        
        // 检测跳跃：从地面起跳
        if (cfi_wasOnGround && !player.onGround() && player.getDeltaMovement().y > 0) {
            if (!cfi_jumpDetected) {
                cfi_jumpDetected = true;
                
                // 获取配置的跳跃力度
                double configJumpPower = CreateFisheryCommonConfig.getDivingBaseJumpPower();
                if (player.isSprinting()) {
                    configJumpPower = CreateFisheryCommonConfig.getDivingSprintJumpPower();
                }
                
                // Create默认添加0.5，我们需要额外添加的量
                double extraPower = configJumpPower - 0.5;
                
                if (extraPower > 0) {
                    // 增强跳跃
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.add(0, extraPower, 0));
                    
                    CreateFisheryMod.LOGGER.info("Enhanced jump for {} - Extra power: {}, Total Y velocity: {}", 
                        player.getName().getString(), extraPower, motion.y + extraPower);
                }
                
                // 标记超级跳跃用于摔落保护
                player.getPersistentData().putBoolean("CFI_SuperJumpActive", true);
                
                // 消耗饥饿度
                if (!player.getAbilities().instabuild) {
                    float hungerCost = (float) CreateFisheryCommonConfig.getDivingJumpHungerCost();
                    player.causeFoodExhaustion(hungerCost);
                }
            }
        }
        
        // 重置跳跃检测
        if (player.onGround()) {
            cfi_jumpDetected = false;
        }
        
        cfi_wasOnGround = player.onGround();
    }
}