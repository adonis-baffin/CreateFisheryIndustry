package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

/**
 * 处理超级跳跃的摔落保护
 * 只保护标记为超级跳跃的摔落
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class SuperJumpFallProtection {

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        // 检查配置
        if (!CreateFisheryCommonConfig.shouldPreventDivingFallDamage()) {
            return;
        }

        LivingEntity entity = event.getEntity();

        // 检查超级跳跃标记
        if (entity.getPersistentData().getBoolean("CFI_SuperJumpActive")) {
            // 取消摔落伤害
            event.setDamageMultiplier(0.0F);
            event.setCanceled(true);

            // 清除标记
            entity.getPersistentData().remove("CFI_SuperJumpActive");
        }
    }
}