package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 这个Mixin用于监控蜜蜂寻找蜂箱的行为
 */
@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGoToHiveGoal")
public abstract class BeeGoToHiveGoalMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-BeeGoToHiveGoalMixin");

    @Inject(method = "canBeeUse", at = @At("HEAD"))
    private void logCanBeeUse(CallbackInfoReturnable<Boolean> cir) {
        try {
            // 获取封闭的Bee实例
            java.lang.reflect.Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            // 每20次检查只记录一次日志，减少日志量
            if (bee.getRandom().nextInt(20) == 0) {
                java.lang.reflect.Field hivePosField = Bee.class.getDeclaredField("hivePos");
                hivePosField.setAccessible(true);
                BlockPos hivePos = (BlockPos) hivePosField.get(bee);

                LOGGER.info("BeeGoToHiveGoal.canBeeUse调用: bee={}, hivePos={}, hasNectar={}, health={}/{}, isNight={}, isRaining={}",
                        bee.getStringUUID().substring(0, 8), hivePos, bee.hasNectar(),
                        bee.getHealth(), bee.getMaxHealth(),
                        bee.level().isNight(), bee.level().isRaining());
            }
        } catch (Exception e) {
            LOGGER.error("监控BeeGoToHiveGoal.canBeeUse时出错: ", e);
        }
    }

    @Inject(method = "canBeeContinueToUse", at = @At("RETURN"))
    private void logCanBeeContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        try {
            java.lang.reflect.Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            // 随机记录，避免过多日志
            if (bee.getRandom().nextInt(20) == 0) {
                LOGGER.info("BeeGoToHiveGoal.canBeeContinueToUse结果: {}", cir.getReturnValue());
            }
        } catch (Exception e) {
            LOGGER.error("监控BeeGoToHiveGoal.canBeeContinueToUse时出错: ", e);
        }
    }
}