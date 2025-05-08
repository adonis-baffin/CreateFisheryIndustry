package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            Field hivePosField = Bee.class.getDeclaredField("hivePos");
            hivePosField.setAccessible(true);
            BlockPos hivePos = (BlockPos) hivePosField.get(bee);

            if (hivePos == null || bee.hasRestriction() || !hivePos.closerToCenterThan(bee.position(), 2.0)) {
                if (bee.getRandom().nextInt(10) == 0) {
                }
                cir.setReturnValue(false);
                return;
            }

            BlockEntity blockEntity = bee.level().getBlockEntity(hivePos);
            if (blockEntity instanceof SmartBeehiveBlockEntity smartBeehive) {
                boolean hasSpace = smartBeehive.getStored().size() < smartBeehive.getMaxOccupants();
                if (bee.getRandom().nextInt(10) == 0) {
                }
                if (hasSpace) {
                    cir.setReturnValue(true);
                } else {
                    hivePosField.set(bee, null);
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
            cir.setReturnValue(false);
        }
    }

    /**
     * 修改start方法，确保蜜蜂导航到蜂箱入口后再进入
     */
    @Inject(method = "start", at = @At("HEAD"), cancellable = true)
    private void injectStart(CallbackInfo ci) {
        try {
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            Field hivePosField = Bee.class.getDeclaredField("hivePos");
            hivePosField.setAccessible(true);
            BlockPos hivePos = (BlockPos) hivePosField.get(bee);

            if (hivePos != null) {
                BlockEntity blockEntity = bee.level().getBlockEntity(hivePos);
                if (blockEntity instanceof SmartBeehiveBlockEntity smartBeehive) {
                    // 获取蜂箱面向方向
                    Direction direction = bee.level().getBlockState(hivePos).getValue(BlockStateProperties.HORIZONTAL_FACING);
                    BlockPos entrancePos = hivePos.relative(direction);
                    double entranceX = entrancePos.getX() + 0.5;
                    double entranceY = entrancePos.getY();
                    double entranceZ = entrancePos.getZ() + 0.5;

                    // 导航到蜂箱入口
                    boolean isNavigating = bee.getNavigation().moveTo(entranceX, entranceY, entranceZ, 1.2);
                    double distanceToEntrance = bee.position().distanceToSqr(entranceX, entranceY, entranceZ);

                    if (bee.getRandom().nextInt(10) == 0) {
                    }

                    // 检查是否接近入口（放宽到 1.0 格）
                    if (distanceToEntrance < 1.0) {
                        smartBeehive.addOccupant(bee);
                        ci.cancel();
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}