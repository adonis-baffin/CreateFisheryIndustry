package com.adonis.createfisheryindustry.block.FishingBearing;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

public class FishingBearingFakePlayer extends FakePlayer {
    public static final GameProfile FISHING_BEARING_PROFILE = new GameProfile(
            UUID.fromString("87654321-4321-8765-cba9-987654321abc"),
            "Fishing Bearing"
    );

    public FishingBearingFakePlayer(ServerLevel level) {
        super(level, FISHING_BEARING_PROFILE);
    }
}