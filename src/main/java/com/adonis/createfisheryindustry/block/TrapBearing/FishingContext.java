package com.adonis.createfisheryindustry.block.TrapBearing;

import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapContext;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class FishingContext extends FrameTrapContext {
    private BlockPos worldPosition;
    private List<BlockPos> framePositions = new ArrayList<>();
    private List<BlockPos> effectivePositions = new ArrayList<>();
    private int effectiveCount = 0;
    private boolean isOpenWater = false;
    private long lastOpenCheck = 0;

    public FishingContext(ServerLevel level, ItemStack fishingRod) {
        super(level, fishingRod);
    }

    public void setWorldPosition(BlockPos pos) {
        this.worldPosition = pos;
    }

    public BlockPos getWorldPosition() {
        return worldPosition;
    }

    public void setFramePositions(List<BlockPos> positions) {
        this.framePositions = new ArrayList<>(positions);
    }

    public void updateEffective(ServerLevel level, Vec3 contraptionPos, float angle) {
        effectivePositions.clear();
        effectiveCount = 0;
        visitedBlocks.clear();
        BlockPos base = new BlockPos(Mth.floor(contraptionPos.x), Mth.floor(contraptionPos.y), Mth.floor(contraptionPos.z));
        for (BlockPos relPos : framePositions) {
            BlockPos rotatedRel = adjustForRotation(relPos, angle);
            BlockPos worldPos = base.offset(rotatedRel);
            if (visitNewPosition(level, worldPos)) {
                effectivePositions.add(worldPos);
                effectiveCount++;
            }
        }
    }

    private BlockPos adjustForRotation(BlockPos relPos, float angle) {
        double rad = Math.toRadians(angle);
        double cos = Mth.cos((float) rad);
        double sin = Mth.sin((float) rad);
        double x = relPos.getX() * cos - relPos.getZ() * sin;
        double z = relPos.getX() * sin + relPos.getZ() * cos;
        return new BlockPos(Mth.floor(x), relPos.getY(), Mth.floor(z));
    }

    public void updateOpenWater(ServerLevel level, Vec3 contraptionPos, float angle) {
        if (level.getGameTime() - lastOpenCheck < 100) return;
        lastOpenCheck = level.getGameTime();
        boolean allOpen = true;
        BlockPos base = new BlockPos(Mth.floor(contraptionPos.x), Mth.floor(contraptionPos.y), Mth.floor(contraptionPos.z));
        for (BlockPos relPos : framePositions) {
            BlockPos rotatedRel = adjustForRotation(relPos, angle);
            BlockPos worldPos = base.offset(rotatedRel);
            FluidState fluid = level.getFluidState(worldPos);
            boolean inWater = fluid.is(FluidTags.WATER);
            boolean inLava = isValidLavaFishingPosition(level, worldPos);
            if (inWater) {
                if (fishingHook.getOpenWaterTypeForArea(worldPos.offset(-2, 0, -2), worldPos.offset(2, 0, 2)) != FishingHook.OpenWaterType.INSIDE_WATER) {
                    allOpen = false;
                    break;
                }
            } else if (inLava && CreateFisheryCommonConfig.isLavaFishingEnabled()) {
                if (!calculateOpenLava(level, worldPos)) {
                    allOpen = false;
                    break;
                }
            } else {
                allOpen = false;
                break;
            }
        }
        isOpenWater = allOpen;
    }

    public BlockPos getRandomEffectivePos(ServerLevel level, Vec3 contraptionPos, float angle) {
        if (effectivePositions.isEmpty()) return getWorldPosition();
        int index = level.random.nextInt(effectivePositions.size());
        return effectivePositions.get(index);
    }

    public int getEffectiveCount() {
        return effectiveCount;
    }

    @Override
    public void reset(ServerLevel level) {
        super.reset(level);
    }

    @Override
    public void reset(ServerLevel level, BlockPos currentPos) {
        super.reset(level, currentPos);
    }

    @Override
    public boolean visitNewPosition(ServerLevel level, BlockPos pos) {
        return super.visitNewPosition(level, pos);
    }

    @Override
    public boolean canCatch(ServerLevel level, BlockPos currentPos) {
        return super.canCatch(level, currentPos);
    }

    @Override
    public LootTable getLootTable(ServerLevel level, BlockPos pos) {
        return super.getLootTable(level, pos);
    }

    public LootParams buildLootContext(Object context, ServerLevel level, BlockPos pos) {
        fishingHook.setPos(Vec3.atCenterOf(pos));

        boolean isLava = isValidLavaFishingPosition(level, pos);
        if (isLava) {
            fishingHook.openWater = calculateOpenLava(level, pos);
        } else {
            fishingHook.openWater = fishingHook.getOpenWaterTypeForArea(
                    pos.offset(-2, 0, -2), pos.offset(2, 0, 2)) == FishingHook.OpenWaterType.INSIDE_WATER;
        }

        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, fishingHook.position())
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, fishingHook)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level, fishingRod, null));
        return builder.create(LootContextParamSets.FISHING);
    }

    protected boolean isValidLavaFishingPosition(ServerLevel level, BlockPos pos) {
        return super.isValidLavaFishingPosition(level, pos);
    }

    public LootTable getLavaFishingLootTable(ServerLevel level, net.minecraft.server.ReloadableServerRegistries.Holder registries) {
        return super.getLavaFishingLootTable(level, registries);
    }

    public boolean calculateOpenLava(ServerLevel level, BlockPos pos) {
        return super.calculateOpenLava(level, pos);
    }

    @Override
    public boolean canCatch() {
        return super.canCatch();
    }

    @Override
    public void invalidate(ServerLevel level) {
        super.invalidate(level);
    }

    public FishingHook getFishingHook() {
        return super.getFishingHook();
    }

    public ItemStack getFishingRod() {
        return super.getFishingRod();
    }

    public boolean isOpenWater() {
        return isOpenWater;
    }
}