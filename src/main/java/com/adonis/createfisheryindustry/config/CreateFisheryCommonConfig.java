package com.adonis.createfisheryindustry.config;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.google.common.collect.ImmutableList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreateFisheryCommonConfig {
    public static final ModConfigSpec CONFIG_SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    private static List<ResourceLocation> cachedWhitelist = new ArrayList<>();
    private static List<ResourceLocation> cachedBlacklist = new ArrayList<>();
    private static long lastWhitelistUpdate = 0;
    private static long lastBlacklistUpdate = 0;
    private static boolean isConfigLoaded = false;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Frame Trap settings")
                .push("frameTrap");

        WHITELIST = builder
                .comment("List of entity registry names to always allow catching (whitelist). Example: 'minecraft:squid'")
                .defineList("whitelist",
                        ImmutableList.of(
                                "minecraft:phantom",
                                "minecraft:squid",
                                "minecraft:spider",
                                "minecraft:cave_spider",
                                "minecraft:silverfish"
                        ),
                        obj -> obj instanceof String);

        BLACKLIST = builder
                .comment("List of entity registry names to never catch (blacklist). Example: 'minecraft:dolphin'")
                .defineList("blacklist",
                        ImmutableList.of(
                                "minecraft:dolphin",
                                "minecraft:axolotl",
                                "minecraft:cat",
                                "minecraft:allay"
                        ),
                        obj -> obj instanceof String);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    public static List<ResourceLocation> getWhitelist() {
        if (!isConfigLoaded) {
            CreateFisheryMod.LOGGER.warn("Attempted to access whitelist before config is loaded. Returning cached or empty list.");
            return cachedWhitelist;
        }

        long currentTime = System.currentTimeMillis();
        if (cachedWhitelist.isEmpty() || currentTime - lastWhitelistUpdate > 5000) {
            try {
                cachedWhitelist = WHITELIST.get().stream()
                        .map(ResourceLocation::tryParse)
                        .filter(r -> r != null)
                        .collect(Collectors.toList());
                lastWhitelistUpdate = currentTime;
            } catch (IllegalStateException e) {
                CreateFisheryMod.LOGGER.error("Failed to load whitelist: {}", e.getMessage());
            }
        }
        return cachedWhitelist;
    }

    public static List<ResourceLocation> getBlacklist() {
        if (!isConfigLoaded) {
            CreateFisheryMod.LOGGER.warn("Attempted to access blacklist before config is loaded. Returning cached or empty list.");
            return cachedBlacklist;
        }

        long currentTime = System.currentTimeMillis();
        if (cachedBlacklist.isEmpty() || currentTime - lastBlacklistUpdate > 5000) {
            try {
                cachedBlacklist = BLACKLIST.get().stream()
                        .map(ResourceLocation::tryParse)
                        .filter(r -> r != null)
                        .collect(Collectors.toList());
                lastBlacklistUpdate = currentTime;
            } catch (IllegalStateException e) {
                CreateFisheryMod.LOGGER.error("Failed to load blacklist: {}", e.getMessage());
            }
        }
        return cachedBlacklist;
    }

    public static void refreshCache() {
        if (!isConfigLoaded) {
            CreateFisheryMod.LOGGER.warn("Cannot refresh cache: Config not loaded yet.");
            return;
        }
        lastWhitelistUpdate = 0;
        lastBlacklistUpdate = 0;
        getWhitelist();
        getBlacklist();
        CreateFisheryMod.LOGGER.info("Config cache refreshed.");
    }

    public static void onLoad() {
        isConfigLoaded = true;
        refreshCache();
        CreateFisheryMod.LOGGER.info("CreateFisheryCommonConfig loaded.");
    }

    public static void onReload() {
        isConfigLoaded = true;
        refreshCache();
        CreateFisheryMod.LOGGER.info("CreateFisheryCommonConfig reloaded.");
    }

    public static boolean isEntityWhitelisted(ResourceLocation entityId) {
        return getWhitelist().contains(entityId);
    }

    public static boolean isEntityBlacklisted(ResourceLocation entityId) {
        return getBlacklist().contains(entityId);
    }
}