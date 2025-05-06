package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeEnterHiveGoal")
public abstract class BeeEnterHiveGoalMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-BeeEnterHiveGoalMixin");

    /**
     * 修改canBeeUse方法，使蜜蜂能够识别并尝试进入智能蜂箱
     */
    @Inject(method = "canBeeUse", at = @At("HEAD"), cancellable = true)
    private void injectCanBeeUse(CallbackInfoReturnable<Boolean> cir) {
        try {
            // 获取 enclosing Bee 实例（Bee.this）
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            // 获取 hivePos
            Field hivePosField = Bee.class.getDeclaredField("hivePos");
            hivePosField.setAccessible(true);
            BlockPos hivePos = (BlockPos) hivePosField.get(bee);

            if (hivePos == null || bee.hasRestriction() || !hivePos.closerToCenterThan(bee.position(), 2.0)) {
                cir.setReturnValue(false);
                return;
            }

            BlockEntity blockEntity = bee.level().getBlockEntity(hivePos);

            if (blockEntity instanceof SmartBeehiveBlockEntity smartBeehive) {
                // 扩展进入智能蜂箱的条件
                boolean shouldEnter = smartBeehive.getStored().size() < smartBeehive.getMaxOccupants() &&
                        (bee.hasNectar() || bee.level().isNight() || bee.level().isRaining() ||
                                bee.getHealth() < bee.getMaxHealth());

                if (bee.getRandom().nextInt(10) == 0) {
                    LOGGER.info("智能蜂箱进入条件检查: bee={}, 位置={}, 蜜蜂数量={}/{}, hasNectar={}, 应该进入={}",
                            bee.getStringUUID().substring(0, 8),
                            hivePos,
                            smartBeehive.getStored().size(),
                            smartBeehive.getMaxOccupants(),
                            bee.hasNectar(),
                            shouldEnter);
                }

                if (shouldEnter) {
                    cir.setReturnValue(true);
                } else if (smartBeehive.isFull()) {
                    hivePosField.set(bee, null);
                    cir.setReturnValue(false);
                } else {
                    cir.setReturnValue(false);
                }
            } else if (blockEntity instanceof BeehiveBlockEntity beehive) {
                if (!beehive.isFull()) {
                    cir.setReturnValue(true);
                } else {
                    hivePosField.set(bee, null);
                    cir.setReturnValue(false);
                }
            } else {
                cir.setReturnValue(false);
            }

        } catch (Exception e) {
            LOGGER.error("处理BeeEnterHiveGoal.canBeeUse时出错: ", e);
            cir.setReturnValue(false);
        }
    }

    /**
     * 修改start方法，添加蜜蜂进入智能蜂箱的逻辑
     */
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void injectStart(CallbackInfo ci) {
        try {
            // 获取 enclosing Bee 实例（Bee.this）
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            // 获取 hivePos
            Field hivePosField = Bee.class.getDeclaredField("hivePos");
            hivePosField.setAccessible(true);
            BlockPos hivePos = (BlockPos) hivePosField.get(bee);

            if (hivePos != null) {
                BlockEntity blockEntity = bee.level().getBlockEntity(hivePos);
                if (blockEntity instanceof SmartBeehiveBlockEntity smartBeehive) {
                    // 处理蜜蜂进入智能蜂箱
                    LOGGER.info("蜜蜂正在进入智能蜂箱: bee={}, 位置={}, hasNectar={}",
                            bee.getStringUUID().substring(0, 8), hivePos, bee.hasNectar());
                    smartBeehive.addOccupant(bee);
                    ci.cancel(); // 取消原版的start处理
                }
            }
        } catch (Exception e) {
            LOGGER.error("处理BeeEnterHiveGoal.start时出错: ", e);
        }
    }
}