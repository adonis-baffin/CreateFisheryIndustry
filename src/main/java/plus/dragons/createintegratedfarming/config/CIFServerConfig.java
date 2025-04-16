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

package plus.dragons.createintegratedfarming.config;

import net.createmod.catnip.config.ConfigBase;

public class CIFServerConfig extends ConfigBase {
    public final ConfigGroup farming = group(1, "farming", Comments.farming);
    public final ConfigBool mushroomColoniesDropSelf = b(false,
            "mushroomColoniesDropSelf",
            Comments.mushroomColoniesDropSelf);

    public final ConfigGroup fishing = group(1, "fishing", Comments.fishing);
    public final ConfigBool fishingNetChecksOpenWater = b(true,
            "fishingNetChecksOpenWater",
            Comments.fishingNetChecksOpenWater);
    public final ConfigInt fishingNetCooldownMultiplier = i(8, 1, 256,
            "fishingNetCooldownMultiplier",
            Comments.fishingNetCooldownMultiplier);
    public final ConfigInt fishingNetMaxRecordedBlocks = i(8, 1, 64,
            "fishingNetMaxRecordedBlocks",
            Comments.fishingNetMaxRecordedBlocks);

    public final ConfigGroup ranching = group(1, "ranching", Comments.ranching);
    public final ConfigBool leashedEntitySitsAutomatically = b(false,
            "leashedEntitySitsAutomatically",
            Comments.leashedEntitySitsAutomatically);

    @Override
    public String getName() {
        return "server";
    }

    static class Comments {
        static final String farming = "Settings for Farming utilities";
        static final String mushroomColoniesDropSelf = "When harvested by Harvester, if mushroom colonies drops itself instead of corresponding mushroom.";

        static final String fishing = "Settings for Fishing utilities";
        static final String[] fishingNetChecksOpenWater = {
                "If Fishing Net should check for open water.",
                "When disabled, the open water check will be skipped and return false."
        };
        static final String[] fishingNetCooldownMultiplier = {
                "Fishing Net's cooldown will be multiplied by this value.",
                "The base cooldown is the same as Fishing Rod's lure speed (100 ~ 600 ticks)."
        };
        static final String[] fishingNetMaxRecordedBlocks = {
                "The maximum amount of the visited valid blocks for fishing recorded by the Fishing Net.",
                "Fishing Net's chance of successful catch depends on [amount] / [maximum amount] of visited valid blocks.",
                "Increasing this value will reduce the efficiency of Fishing Net that travels in a fixed short route.",
                "Example: Fishing Net placed near the rotating axis."
        };

        static final String ranching = "Settings for Ranching utilities";
        static final String[] leashedEntitySitsAutomatically = {
                "If leashed entity automatically sits on unoccupied seat.",
                "When enabled, falls back to vanilla Create behaviour.",
                "When disabled, seated leashable entity can be dismounted by lead."
        };
    }
}
