package com.adonis.createfisheryindustry.mixin;

import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(targets = "net.minecraft.world.entity.animal.Bee$BeeLocateHiveGoal")
public abstract class BeeLocateHiveGoalMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("FisheryIndustry-BeeLocateHiveGoalMixin");

    /**
     * 扩展findNearbyHivesWithSpace，支持智能蜂箱的POI
     */
    @Inject(method = "findNearbyHivesWithSpace", at = @At("RETURN"), cancellable = true)
    private void injectFindNearbyHivesWithSpace(CallbackInfoReturnable<List<BlockPos>> cir) {
        try {
            Field beeField = this.getClass().getDeclaredField("this$0");
            beeField.setAccessible(true);
            Bee bee = (Bee) beeField.get(this);

            Field hivePosField = Bee.class.getDeclaredField("hivePos");
            hivePosField.setAccessible(true);
            BlockPos hivePos = (BlockPos) hivePosField.get(bee);

            if (hivePos == null && bee.level() instanceof ServerLevel serverLevel) {
                PoiManager poiManager = serverLevel.getPoiManager();
                Stream<PoiRecord> stream = poiManager.getInRange(
                        (holder) -> holder.is(PoiTypeTags.BEE_HOME) || holder.is(CreateFisheryBlockEntities.SMART_BEEHIVE_POI.getKey()),
                        bee.blockPosition(),
                        20,
                        PoiManager.Occupancy.ANY
                );
                List<BlockPos> hivePositions = stream
                        .map(PoiRecord::getPos)
                        .filter((pos) -> {
                            BlockEntity be = bee.level().getBlockEntity(pos);
                            if (be instanceof SmartBeehiveBlockEntity smartBeehive) {
                                return smartBeehive.getStored().size() < smartBeehive.getMaxOccupants();
                            } else if (be instanceof BeehiveBlockEntity beehive) {
                                return !beehive.isFull();
                            }
                            return false;
                        })
                        .sorted(Comparator.comparingDouble((pos) -> pos.distSqr(bee.blockPosition())))
                        .collect(Collectors.toList());

                if (!hivePositions.isEmpty()) {
                }

                cir.setReturnValue(hivePositions);
            }
        } catch (Exception e) {
        }
    }
}