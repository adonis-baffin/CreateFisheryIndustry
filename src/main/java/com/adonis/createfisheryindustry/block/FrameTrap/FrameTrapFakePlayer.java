package com.adonis.createfisheryindustry.block.FrameTrap;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

public class FrameTrapFakePlayer extends FakePlayer {
    public static final GameProfile FRAME_TRAP_PROFILE = new GameProfile(
            UUID.fromString("12345678-1234-5678-9abc-123456789abc"), 
            "Frame Trap"
    );

    public FrameTrapFakePlayer(ServerLevel level) {
        super(level, FRAME_TRAP_PROFILE);
    }
}