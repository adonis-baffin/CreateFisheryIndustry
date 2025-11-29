package com.adonis.createfisheryindustry.config;

import net.minecraft.Util;
import net.minecraft.util.Unit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class CreateFisheryConfig {
    public static final String ID = "createfisheryindustry";
    private static final CreateFisheryServerConfig SERVER_CONFIG = new CreateFisheryServerConfig(new ModConfigSpec.Builder());
    private static ModConfigSpec SERVER_SPEC;

    public CreateFisheryConfig(ModContainer modContainer) {
        SERVER_SPEC = Util.make(new ModConfigSpec.Builder().configure(builder -> {
            SERVER_CONFIG.registerAll(builder);
            return Unit.INSTANCE;
        }).getValue(), spec -> modContainer.registerConfig(ModConfig.Type.SERVER, spec));
    }

    public static CreateFisheryServerConfig server() {
        return SERVER_CONFIG;
    }

    public static CreateFisheryStressConfig stress() {
        return SERVER_CONFIG.kinetics.stressValues;
    }

    @SubscribeEvent
    public void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            SERVER_CONFIG.onLoad();
        }
    }

    @SubscribeEvent
    public void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SERVER_SPEC) {
            SERVER_CONFIG.onReload();
        }
    }
}