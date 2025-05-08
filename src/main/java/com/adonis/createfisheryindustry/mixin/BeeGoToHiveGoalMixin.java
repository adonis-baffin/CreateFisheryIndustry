package com.adonis.createfisheryindustry.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeGoToHiveGoal")
public abstract class BeeGoToHiveGoalMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-BeeGoToHiveGoalMixin");

    @Inject(method = "canBeeUse", at = @At("HEAD"))
    private void logCanBeeUse(CallbackInfoReturnable<Boolean> cir) {
        try {
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            if (bee.getRandom().nextInt(20) == 0) {
                Field hivePosField = Bee.class.getDeclaredField("hivePos");
                hivePosField.setAccessible(true);
                BlockPos hivePos = (BlockPos) hivePosField.get(bee);
            }
        } catch (Exception e) {

        }
    }

    @Inject(method = "canBeeContinueToUse", at = @At("RETURN"))
    private void logCanBeeContinueToUse(CallbackInfoReturnable<Boolean> cir) {
        try {
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            if (bee.getRandom().nextInt(20) == 0) {

            }
        } catch (Exception e) {

        }
    }
}