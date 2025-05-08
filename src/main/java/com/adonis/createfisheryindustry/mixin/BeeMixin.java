package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Bee.class)
public abstract class BeeMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-BeeMixin");

    @Shadow
    private BlockPos hivePos;

    // 修改 doesHiveHaveSpace，支持智能蜂箱
    @Inject(method = "doesHiveHaveSpace(Lnet/minecraft/core/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void injectDoesHiveHaveSpace(BlockPos hivePos, CallbackInfoReturnable<Boolean> cir) {
        Bee bee = (Bee) (Object) this;
        BlockEntity blockEntity = bee.level().getBlockEntity(hivePos);
        if (blockEntity instanceof SmartBeehiveBlockEntity smartBeehive) {
            boolean hasSpace = smartBeehive.getStored().size() < smartBeehive.getMaxOccupants();
            if (bee.getRandom().nextInt(20) == 0) {
            }
            cir.setReturnValue(hasSpace);
        } else if (blockEntity instanceof BeehiveBlockEntity beehive) {
            cir.setReturnValue(!beehive.isFull());
        } else {
            if (bee.getRandom().nextInt(20) == 0) {
            }
            cir.setReturnValue(false);
        }
    }

    // 修改 isHiveValid，支持智能蜂箱
    @Inject(method = "isHiveValid()Z", at = @At("HEAD"), cancellable = true)
    private void injectIsHiveValid(CallbackInfoReturnable<Boolean> cir) {
        Bee bee = (Bee) (Object) this;
        if (this.hivePos == null) {
            cir.setReturnValue(false);
            return;
        }

        // 距离限制为 32 格（与原版一致）
        double distanceSqr = this.hivePos.distSqr(bee.blockPosition());
        if (distanceSqr > 32 * 32) {
            cir.setReturnValue(false);
            return;
        }

        BlockEntity blockEntity = bee.level().getBlockEntity(this.hivePos);
        boolean isValid = blockEntity instanceof BeehiveBlockEntity || blockEntity instanceof SmartBeehiveBlockEntity;

        if (bee.getRandom().nextInt(40) == 0) {
        }

        cir.setReturnValue(isValid);
    }

    // 修改 wantsToEnterHive，增加进入智能蜂箱的概率
    @Inject(method = "wantsToEnterHive()Z", at = @At("HEAD"), cancellable = true)
    private void injectWantsToEnterHive(CallbackInfoReturnable<Boolean> cir) {
        Bee bee = (Bee) (Object) this;
        if (this.hivePos != null) {
            BlockEntity blockEntity = bee.level().getBlockEntity(this.hivePos);
            if (blockEntity instanceof SmartBeehiveBlockEntity) {
                // 增加进入概率，模仿原版逻辑
                boolean wantsToEnter = bee.hasNectar() ||
                        bee.level().isNight() ||
                        bee.level().isRaining() ||
                        bee.hasSavedFlowerPos() ||
                        bee.getHealth() < bee.getMaxHealth() ||
                        bee.getRandom().nextFloat() < 0.3f;

                if (bee.getRandom().nextInt(40) == 0) {
                }

                cir.setReturnValue(wantsToEnter);
            }
        }
    }
}