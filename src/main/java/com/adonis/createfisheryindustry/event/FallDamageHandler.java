package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class FallDamageHandler {
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        // 检查是否为强化跳跃
        if (entity.getPersistentData().getBoolean("EnhancedJumpActive")) {
            // 取消摔落伤害
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);
            // 重置强化跳跃标志
            entity.getPersistentData().putBoolean("EnhancedJumpActive", false);
        }
    }
}