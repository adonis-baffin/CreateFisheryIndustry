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

package plus.dragons.createintegratedfarming.api.saw;

import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Block tags for blocks that can be handled by {@link SawBlockEntity Mechanical Saw}.
 * <p>
 * Since this controlls Create's behaviour and might be a part of Create's implementation in the future,
 * tags here uses Create's namespace.
 */
public interface SawableBlockTags {
    /**
     * For plants that grows vertically, like Bamboos, Sugar Cane, Cactus and Kelp.
     */
    TagKey<Block> VERTICAL_PLANTS = TagKey.create(Registries.BLOCK, Create.asResource("vertical_plants"));
    /**
     * For plants that grows vertically but breaks instantly when the root is cut, like Powdery Cannon.
     */
    TagKey<Block> FRAGILE_VERTICAL_PLANTS = TagKey.create(Registries.BLOCK, Create.asResource("vertical_plants/fragile"));
}
