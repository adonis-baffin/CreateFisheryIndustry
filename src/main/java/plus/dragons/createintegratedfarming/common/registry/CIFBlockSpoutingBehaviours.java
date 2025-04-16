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

package plus.dragons.createintegratedfarming.common.registry;

import com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour;
import com.simibubi.create.content.fluids.spout.SpoutBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import plus.dragons.createintegratedfarming.common.ranching.coop.ChickenCoopBlockEntity;
import plus.dragons.createintegratedfarming.integration.CIFIntegration;
import vectorwing.farmersdelight.common.registry.ModBlocks;

public class CIFBlockSpoutingBehaviours {
    private static final Holder<Block> LETIOS_COMPOST = DeferredHolder.create(Registries.BLOCK,
            CIFIntegration.MY_NETHERS_DELIGHT.asResource("letios_compost"));

    public static void register() {
        BlockSpoutingBehaviour.BY_BLOCK.register(
                CIFBlocks.CHICKEN_COOP.get(),
                CIFBlockSpoutingBehaviours::fillChickenCoop);
        BlockSpoutingBehaviour.BY_BLOCK.register(
                ModBlocks.ORGANIC_COMPOST.get(),
                CIFBlockSpoutingBehaviours::fillOrganicCompost);
        if (LETIOS_COMPOST.isBound()) {
            BlockSpoutingBehaviour.BY_BLOCK.register(
                    LETIOS_COMPOST.value(),
                    CIFBlockSpoutingBehaviours::fillLetiosCompost);
        }
    }

    private static int fillChickenCoop(Level level, BlockPos pos, SpoutBlockEntity spout, FluidStack fluid, boolean simulate) {
        if (level.getBlockEntity(pos) instanceof ChickenCoopBlockEntity coop) {
            return coop.feedFluid(fluid, simulate);
        }
        return 0;
    }

    private static int fillOrganicCompost(Level level, BlockPos pos, SpoutBlockEntity spout, FluidStack fluid, boolean simulate) {
        if (!fluid.is(FluidTags.WATER))
            return 0;
        if (!simulate && level instanceof ServerLevel) {
            BlockState state = level.getBlockState(pos);
            state.randomTick((ServerLevel) level, pos, level.random);
        }
        return 250;
    }

    private static int fillLetiosCompost(Level level, BlockPos pos, SpoutBlockEntity spout, FluidStack fluid, boolean simulate) {
        if (!fluid.is(FluidTags.LAVA) || !level.dimensionType().ultraWarm())
            return 0;
        if (!simulate && level instanceof ServerLevel) {
            BlockState state = level.getBlockState(pos);
            state.randomTick((ServerLevel) level, pos, level.random);
        }
        return 250;
    }
}
