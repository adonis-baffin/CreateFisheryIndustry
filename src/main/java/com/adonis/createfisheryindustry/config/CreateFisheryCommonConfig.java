package com.adonis.createfisheryindustry.config;

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
    public static final ModConfigSpec.BooleanValue ENABLE_FISHING;
    public static final ModConfigSpec.BooleanValue ENABLE_LAVA_FISHING;
    public static final ModConfigSpec.BooleanValue USE_NDU_LOOT_TABLES;
    public static final ModConfigSpec.DoubleValue FISHING_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LAVA_FISHING_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FISHING_SUCCESS_RATE;
    public static final ModConfigSpec.DoubleValue LAVA_FISHING_SUCCESS_RATE;

    private static List<ResourceLocation> cachedWhitelist = new ArrayList<>();
    private static List<ResourceLocation> cachedBlacklist = new ArrayList<>();
    private static long lastWhitelistUpdate = 0;
    private static long lastBlacklistUpdate = 0;
    private static boolean isConfigLoaded = false;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("Frame Trap entity capture settings")
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

        builder.comment("Frame Trap fishing settings")
                .push("fishing");

        ENABLE_FISHING = builder
                .comment("Enable fishing functionality for Frame Trap")
                .define("enableFishing", true);

        ENABLE_LAVA_FISHING = builder
                .comment("Enable lava fishing functionality for Frame Trap (requires lava environment)")
                .define("enableLavaFishing", true);

        USE_NDU_LOOT_TABLES = builder
                .comment("Use Nether Depths Upgrade loot tables for lava fishing when available",
                        "If false or NDU is not installed, will fall back to default fishing loot table")
                .define("useNDULootTables", true);

        FISHING_COOLDOWN_MULTIPLIER = builder
                .comment("Multiplier for normal (water) fishing cooldown time",
                        "Lower values = faster fishing, higher values = slower fishing",
                        "Default: 1.0 (normal speed), 0.5 = twice as fast, 2.0 = twice as slow")
                .defineInRange("fishingCooldownMultiplier", 1.0, 0.1, 10.0);

        LAVA_FISHING_COOLDOWN_MULTIPLIER = builder
                .comment("Multiplier for lava fishing cooldown time",
                        "Lower values = faster fishing, higher values = slower fishing",
                        "Default: 1.5 (slower than water fishing), 0.5 = twice as fast, 2.0 = twice as slow")
                .defineInRange("lavaFishingCooldownMultiplier", 1.5, 0.1, 10.0);

        FISHING_SUCCESS_RATE = builder
                .comment("Base success rate for normal (water) fishing attempts (0.0 to 1.0)",
                        "Default: 0.4 (40% chance), 1.0 = always succeed, 0.0 = never succeed")
                .defineInRange("fishingSuccessRate", 0.4, 0.0, 1.0);

        LAVA_FISHING_SUCCESS_RATE = builder
                .comment("Base success rate for lava fishing attempts (0.0 to 1.0)",
                        "Default: 0.3 (30% chance, harder than water fishing)")
                .defineInRange("lavaFishingSuccessRate", 0.3, 0.0, 1.0);

        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    // 缓存和基础方法
    public static List<ResourceLocation> getWhitelist() {
        if (!isConfigLoaded) {
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
                // 配置尚未加载，使用缓存值
            }
        }
        return cachedWhitelist;
    }

    public static List<ResourceLocation> getBlacklist() {
        if (!isConfigLoaded) {
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
                // 配置尚未加载，使用缓存值
            }
        }
        return cachedBlacklist;
    }

    public static void refreshCache() {
        if (!isConfigLoaded) {
            return;
        }
        lastWhitelistUpdate = 0;
        lastBlacklistUpdate = 0;
        getWhitelist();
        getBlacklist();
    }

    public static void onLoad() {
        isConfigLoaded = true;
        refreshCache();
    }

    public static void onReload() {
        isConfigLoaded = true;
        refreshCache();
    }

    // 实体列表相关方法
    public static boolean isEntityWhitelisted(ResourceLocation entityId) {
        return getWhitelist().contains(entityId);
    }

    public static boolean isEntityBlacklisted(ResourceLocation entityId) {
        return getBlacklist().contains(entityId);
    }

    // 钓鱼功能开关
    public static boolean isFishingEnabled() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return ENABLE_FISHING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean isLavaFishingEnabled() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return ENABLE_LAVA_FISHING.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean useNDULootTables() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return USE_NDU_LOOT_TABLES.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    // 钓鱼速率配置
    public static double getFishingCooldownMultiplier() {
        if (!isConfigLoaded) {
            return 1.0;
        }
        try {
            return FISHING_COOLDOWN_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static double getLavaFishingCooldownMultiplier() {
        if (!isConfigLoaded) {
            return 1.5;
        }
        try {
            return LAVA_FISHING_COOLDOWN_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 1.5;
        }
    }

    // 钓鱼成功率配置
    public static double getFishingSuccessRate() {
        if (!isConfigLoaded) {
            return 0.4;
        }
        try {
            return FISHING_SUCCESS_RATE.get();
        } catch (IllegalStateException e) {
            return 0.4;
        }
    }

    public static double getLavaFishingSuccessRate() {
        if (!isConfigLoaded) {
            return 0.3;
        }
        try {
            return LAVA_FISHING_SUCCESS_RATE.get();
        } catch (IllegalStateException e) {
            return 0.3;
        }
    }
}