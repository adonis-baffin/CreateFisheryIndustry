/*
 * Copyright (C) 2025  DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package plus.dragons.createintegratedfarming.common.fishing.net;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.FishingHook.OpenWaterType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import plus.dragons.createintegratedfarming.config.CIFConfig;

public class FishingNetContext {
    protected final ItemStack fishingRod;
    protected final FishingHook fishingHook;
    protected final Set<BlockPos> visitedBlocks = new HashSet<>(
            Math.min(16, CIFConfig.server().fishingNetMaxRecordedBlocks.get()));
    public int timeUntilCatch;

    public FishingNetContext(ServerLevel level, ItemStack fishingRod) {
        this.fishingRod = fishingRod;
        this.fishingHook = new FishingHook(EntityType.FISHING_BOBBER, level);
        this.reset(level);
    }

    public void reset(ServerLevel level) {
        this.visitedBlocks.clear();
        int lureSpeed = (int) (EnchantmentHelper.getFishingTimeReduction(level, fishingRod, fishingHook) * 20.0F);
        this.timeUntilCatch = (Mth.nextInt(fishingHook.getRandom(), 100, 600) - lureSpeed) *
                CIFConfig.server().fishingNetCooldownMultiplier.get();
    }

    public boolean visitNewPositon(ServerLevel level, BlockPos pos) {
        boolean inWater = fishingHook.getOpenWaterTypeForBlock(pos) == OpenWaterType.INSIDE_WATER;
        if (!inWater)
            return false;
        if (visitedBlocks.size() < CIFConfig.server().fishingNetMaxRecordedBlocks.get())
            visitedBlocks.add(pos);
        return true;
    }

    public LootParams buildLootContext(MovementContext context, ServerLevel level, BlockPos pos) {
        fishingHook.setPos(context.position);
        if (CIFConfig.server().fishingNetChecksOpenWater.get()) {
            fishingHook.openWater = fishingHook.getOpenWaterTypeForArea(
                    pos.offset(-2, 0, -2), pos.offset(2, 0, 2)) == OpenWaterType.INSIDE_WATER;
        } else fishingHook.openWater = false;
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, context.position)
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, fishingHook)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level, fishingRod, context.contraption.entity))
                .create(LootContextParamSets.FISHING);
    }

    public boolean canCatch() {
        int maxRecorded = CIFConfig.server().fishingNetMaxRecordedBlocks.get();
        if (maxRecorded == 0)
            return true;
        return fishingHook.getRandom().nextInt(maxRecorded) < visitedBlocks.size();
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
