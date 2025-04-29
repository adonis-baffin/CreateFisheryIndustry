package com.adonis.createfisheryindustry.block.FrameTrap;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FrameTrapContext {
    protected final ItemStack fishingRod;
    protected final FishingHook fishingHook;
    protected final Set<BlockPos> visitedBlocks = new HashSet<>();

    protected final int minCatchTime = 800;
    protected final int maxCatchTime = 1400;
    protected final float catchSuccessRate = 0.4f;

    public int timeUntilCatch;

    public FrameTrapContext(ServerLevel level, ItemStack fishingRod) {
        this.fishingRod = fishingRod;
        this.fishingHook = new FishingHook(EntityType.FISHING_BOBBER, level);
        this.reset(level);
    }

    public void reset(ServerLevel level) {
        this.visitedBlocks.clear();
        this.timeUntilCatch = Mth.nextInt(fishingHook.getRandom(), minCatchTime, maxCatchTime);
    }

    public boolean visitNewPosition(ServerLevel level, BlockPos pos) {
        boolean inWater = fishingHook.getOpenWaterTypeForBlock(pos) == FishingHook.OpenWaterType.INSIDE_WATER;
        if (!inWater) return false;
        visitedBlocks.add(pos);
        return true;
    }

    public LootParams buildLootContext(com.simibubi.create.content.contraptions.behaviour.MovementContext context, ServerLevel level, BlockPos pos) {
        fishingHook.setPos(context.position);
        fishingHook.openWater = fishingHook.getOpenWaterTypeForArea(
                pos.offset(-2, 0, -2), pos.offset(2, 0, 2)) == FishingHook.OpenWaterType.INSIDE_WATER;
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, context.position)
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, fishingHook)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level, fishingRod, context.contraption.entity))
                .create(LootContextParamSets.FISHING);
    }

    public boolean canCatch() {
        return fishingHook.getRandom().nextFloat() < catchSuccessRate;
    }

    public void invalidate(ServerLevel level) {
        reset(level);
        fishingHook.discard();
    }

    public FishingHook getFishingHook() {
        return fishingHook;
    }

    public ItemStack getFishingRod() {
        return fishingRod;
    }
}