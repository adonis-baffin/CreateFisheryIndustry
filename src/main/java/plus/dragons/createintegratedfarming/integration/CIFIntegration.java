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

package plus.dragons.createintegratedfarming.integration;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;
import org.jetbrains.annotations.ApiStatus.Internal;

@Internal
public class CIFIntegration {
    public static final CIFIntegration FARMERS_DELIGHT = new CIFIntegration("farmersdelight");
    public static final CIFIntegration MY_NETHERS_DELIGHT = new CIFIntegration("mynethersdelight");
    public static final CIFIntegration MMLIB = new CIFIntegration("mysterious_mountain_lib");
    public static final CIFIntegration CREATE_ENCHANTABLE_MACHINERY = new CIFIntegration("createenchantablemachinery");
    public static final CIFIntegration CREATE_CRAFT_AND_ADDITIONS = new CIFIntegration("createadditions");

    private final String modId;

    public CIFIntegration(String modId) {
        this.modId = modId;
    }

    public String getModId() {
        return modId;
    }

    public boolean isLoaded() {
        return ModList.get().isLoaded(modId);
    }

    public ModLoadedCondition condition() {
        return new ModLoadedCondition(modId);
    }

    public ResourceLocation asResource(String location) {
        return ResourceLocation.fromNamespaceAndPath(modId, location);
    }
}
