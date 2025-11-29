package com.adonis.createfisheryindustry.block.TrapBearing;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

public class TrapBearingFakePlayer extends FakePlayer {
    public static final GameProfile TRAP_BEARING_PROFILE = new GameProfile(
            UUID.fromString("87654321-4321-8765-cba9-987654321abc"),
            "Trap Bearing"
    );

    public TrapBearingFakePlayer(ServerLevel level) {
        super(level, TRAP_BEARING_PROFILE);
    }
}