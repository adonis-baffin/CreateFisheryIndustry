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

@Mixin(net.minecraft.world.entity.ai.village.poi.PoiManager.class)
public abstract class PoiManagerMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-PoiManager");
    
    @Inject(method = "findClosestWithType", at = @At("RETURN"))
    private void logFindClosestWithType(java.util.function.Predicate poiTypePredicate, 
                                       java.util.function.Predicate posPredicate,
                                       BlockPos pos, int distance, 
                                       net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy occupancy,
                                       CallbackInfoReturnable<java.util.Optional<BlockPos>> cir) {
        
        // 只记录蜜蜂相关的POI查询（蜂箱的POI类型是"beehome"）
        if (poiTypePredicate.toString().contains("beehome")) {
            LOGGER.info("POI查询: pos={}, distance={}, result={}", 
                      pos, distance, cir.getReturnValue().orElse(null));
        }
    }
}