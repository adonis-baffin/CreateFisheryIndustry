package com.adonis.createfisheryindustry.block.TrapBearing;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Local fake player used by the trap bearing to build fishing loot context.
 *
 * This file was independently rewritten during the repository's license
 * provenance cleanup.
 */
public class TrapBearingFakePlayer extends FakePlayer {
    private static final UUID PROFILE_ID = UUID.fromString("87654321-4321-8765-cba9-987654321abc");
    private static final String PROFILE_NAME = "Trap Bearing";
    private static final GameProfile PROFILE = new GameProfile(PROFILE_ID, PROFILE_NAME);

    public TrapBearingFakePlayer(ServerLevel level) {
        super(level, PROFILE);
    }
}
