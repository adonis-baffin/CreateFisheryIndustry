package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 增强的摔落伤害处理器
 * 专门处理超级跳跃的摔落保护
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class EnhancedFallDamageHandler {
    
    /**
     * 处理摔落伤害事件 - 高优先级确保优先处理
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) {
            return;
        }

        // 检查配置
        if (!CreateFisheryCommonConfig.shouldPreventDivingFallDamage()) {
            return;
        }

        // 检查是否为超级跳跃的摔落保护
        if (player.getPersistentData().getBoolean("CFI_SuperJump")) {
            
            // 获取跳跃信息进行验证
            float jumpStartY = player.getPersistentData().getFloat("CFI_JumpStartY");
            long jumpTime = player.getPersistentData().getLong("CFI_JumpTime");
            long currentTime = player.level().getGameTime();
            
            // 验证跳跃的有效性
            boolean validTimeFrame = (currentTime - jumpTime) <= 200; // 10秒内
            boolean significantFall = event.getDistance() > 3.0F; // 摔落距离超过3格
            boolean reasonableHeight = player.getY() < jumpStartY + 15.0F; // 落地点合理
            
            // 输出详细的调试信息
            CreateFisheryMod.LOGGER.debug("Fall damage check for {}: distance={}, validTime={}, significantFall={}, reasonableHeight={}", 
                player.getName().getString(), event.getDistance(), validTimeFrame, significantFall, reasonableHeight);
            
            if (validTimeFrame && (significantFall || reasonableHeight)) {
                // 完全取消摔落伤害
                event.setDamageMultiplier(0.0F);
                event.setCanceled(true);
                
                CreateFisheryMod.LOGGER.info("Prevented super jump fall damage for player: {} (distance: {}, time since jump: {})", 
                    player.getName().getString(), event.getDistance(), currentTime - jumpTime);
            }
            
            // 摔落后清除保护标记
            player.getPersistentData().remove("CFI_SuperJump");
            player.getPersistentData().remove("CFI_JumpStartY");
            player.getPersistentData().remove("CFI_JumpTime");
        }
    }
    
    /**
     * 定期清理过期的保护标记
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        
        // 每60 ticks检查一次（3秒）
        if (player.tickCount % 60 == 0) {
            if (player.getPersistentData().getBoolean("CFI_SuperJump")) {
                long jumpTime = player.getPersistentData().getLong("CFI_JumpTime");
                long currentTime = player.level().getGameTime();
                
                // 清理超过10秒的过期标记
                if (currentTime - jumpTime > 200) {
                    player.getPersistentData().remove("CFI_SuperJump");
                    player.getPersistentData().remove("CFI_JumpStartY");
                    player.getPersistentData().remove("CFI_JumpTime");
                    CreateFisheryMod.LOGGER.debug("Cleaned up expired super jump protection for player: {}", player.getName().getString());
                }
            }
        }
    }
}