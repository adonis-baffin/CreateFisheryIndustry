package com.adonis.createfisheryindustry.mixin;

import org.spongepowered.asm.mixin.Mixin;

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


@Mixin(Bee.class)
public abstract class BeePathfindingMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-BeePathfinding");
    
    @Inject(method = "pathfindRandomlyTowards(Lnet/minecraft/core/BlockPos;)V", at = @At("HEAD"))
    private void logPathfinding(BlockPos pos, CallbackInfo ci) {
        Bee bee = (Bee) (Object) this;
        // 随机记录，避免日志过多
        if (bee.getRandom().nextInt(10) == 0) {
            LOGGER.info("蜜蜂寻路: bee={}, target={}", bee.getStringUUID().substring(0, 8), pos);
        }
    }
    
    // 监控蜜蜂寻找新蜂箱的行为
    @Inject(method = "findNearestHive(I)Lnet/minecraft/core/BlockPos;", at = @At("RETURN"))
    private void logFindNearestHive(int radius, CallbackInfoReturnable<BlockPos> cir) {
        Bee bee = (Bee) (Object) this;
        BlockPos result = cir.getReturnValue();
        LOGGER.info("寻找最近蜂箱: bee={}, radius={}, 结果={}", 
                  bee.getStringUUID().substring(0, 8), radius, result);
        
        if (result != null) {
            // 记录找到的蜂箱类型
            try {
                Object blockEntity = bee.level().getBlockEntity(result);
                LOGGER.info("找到的蜂箱类型: {}", blockEntity != null ? blockEntity.getClass().getSimpleName() : "null");
            } catch (Exception e) {
                LOGGER.error("检查蜂箱类型时出错", e);
            }
        }
    }
}