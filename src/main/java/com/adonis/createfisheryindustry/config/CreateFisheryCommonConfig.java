package com.adonis.createfisheryindustry.config;

import com.google.common.collect.ImmutableList;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreateFisheryCommonConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    // Frame Trap 配置
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.BooleanValue ENABLE_FISHING;
    public static final ModConfigSpec.BooleanValue ENABLE_LAVA_FISHING;
    public static final ModConfigSpec.BooleanValue USE_NDU_LOOT_TABLES;
    public static final ModConfigSpec.DoubleValue FISHING_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue LAVA_FISHING_COOLDOWN_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue FISHING_SUCCESS_RATE;
    public static final ModConfigSpec.DoubleValue LAVA_FISHING_SUCCESS_RATE;

    // 潜水装备配置
    public static final ModConfigSpec.DoubleValue DIVING_BASE_JUMP_POWER;
    public static final ModConfigSpec.DoubleValue DIVING_SPRINT_JUMP_POWER;
    public static final ModConfigSpec.IntValue DIVING_JUMP_COOLDOWN;
    public static final ModConfigSpec.DoubleValue DIVING_JUMP_HUNGER_COST;
    public static final ModConfigSpec.DoubleValue DIVING_COPPER_SPRINT_SPEED;
    public static final ModConfigSpec.DoubleValue DIVING_NETHERITE_SPRINT_SPEED;
    public static final ModConfigSpec.DoubleValue DIVING_SPRINT_HUNGER_COST;
    public static final ModConfigSpec.IntValue DIVING_SPRINT_DOUBLE_CLICK_WINDOW;
    public static final ModConfigSpec.BooleanValue DIVING_PREVENT_FALL_DAMAGE;
    public static final ModConfigSpec.IntValue DIVING_MIN_HUNGER_LEVEL;
    public static final ModConfigSpec.DoubleValue DIVING_SPRINT_FOV_MULTIPLIER;

    private static List<ResourceLocation> cachedWhitelist = new ArrayList<>();
    private static List<ResourceLocation> cachedBlacklist = new ArrayList<>();
    private static long lastWhitelistUpdate = 0;
    private static long lastBlacklistUpdate = 0;
    private static boolean isConfigLoaded = false;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // Frame Trap 配置部分
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

        // 潜水装备配置部分
        builder.comment("Diving Equipment settings")
                .push("divingEquipment");

        builder.comment("Jump Enhancement settings")
                .push("jump");

        DIVING_BASE_JUMP_POWER = builder
                .comment("Base jump power when wearing diving equipment in fluids",
                        "Default: 1.0 (higher = stronger jump)")
                .defineInRange("baseJumpPower", 1.0, 0.1, 2.0);

        DIVING_SPRINT_JUMP_POWER = builder
                .comment("Jump power when sprinting with diving equipment in fluids",
                        "Default: 1.0 (higher = stronger jump)")
                .defineInRange("sprintJumpPower", 1.0, 0.1, 2.0);

        DIVING_JUMP_COOLDOWN = builder
                .comment("Cooldown between enhanced jumps in ticks (20 ticks = 1 second)",
                        "Default: 10")
                .defineInRange("jumpCooldown", 10, 0, 100);

        DIVING_JUMP_HUNGER_COST = builder
                .comment("Hunger cost per enhanced jump",
                        "Default: 0.05 (1.0 = 1 full hunger point)")
                .defineInRange("jumpHungerCost", 0.05, 0.0, 1.0);

        builder.pop();

        builder.comment("Sprint Enhancement settings")
                .push("sprint");

        DIVING_COPPER_SPRINT_SPEED = builder
                .comment("Sprint speed multiplier for Copper Diving Leggings",
                        "Default: 0.01 (higher = faster movement)")
                .defineInRange("copperSprintSpeed", 0.01, 0.0, 0.5);

        DIVING_NETHERITE_SPRINT_SPEED = builder
                .comment("Sprint speed multiplier for Netherite Diving Leggings",
                        "Default: 0.01 (higher = faster movement)")
                .defineInRange("netheriteSprintSpeed", 0.01, 0.0, 0.5);

        DIVING_SPRINT_HUNGER_COST = builder
                .comment("Hunger cost per second while sprinting underwater",
                        "Default: 0.1 (1.0 = 1 full hunger point per second)")
                .defineInRange("sprintHungerCostPerSecond", 0.1, 0.0, 1.0);

        DIVING_SPRINT_DOUBLE_CLICK_WINDOW = builder
                .comment("Time window for double-clicking W to start sprint (in ticks)",
                        "Default: 7")
                .defineInRange("sprintDoubleClickWindow", 7, 1, 20);

        builder.pop();

        builder.comment("General Diving Equipment settings")
                .push("general");

        DIVING_PREVENT_FALL_DAMAGE = builder
                .comment("Prevent fall damage from enhanced jumps",
                        "Default: true")
                .define("preventFallDamage", true);

        DIVING_MIN_HUNGER_LEVEL = builder
                .comment("Minimum hunger level required for sprint/jump enhancements (0-20)",
                        "Default: 6 (3 hunger bars)")
                .defineInRange("minHungerLevel", 6, 0, 20);

        DIVING_SPRINT_FOV_MULTIPLIER = builder
                .comment("FOV multiplier when underwater sprinting (1.0 = no effect, 1.01 = default sprint effect)",
                        "Default: 1.01")
                .defineInRange("sprintFovMultiplier", 1.01, 1.0, 2.0);

        builder.pop();
        builder.pop();

        CONFIG_SPEC = builder.build();
    }

    // 原有的缓存和基础方法
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

    public static double getDivingBaseJumpPower() {
        if (!isConfigLoaded) {
            return 1.0;
        }
        try {
            return DIVING_BASE_JUMP_POWER.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static double getDivingSprintJumpPower() {
        if (!isConfigLoaded) {
            return 1.0;
        }
        try {
            return DIVING_SPRINT_JUMP_POWER.get();
        } catch (IllegalStateException e) {
            return 1.0;
        }
    }

    public static int getDivingJumpCooldown() {
        if (!isConfigLoaded) {
            return 10;
        }
        try {
            return DIVING_JUMP_COOLDOWN.get();
        } catch (IllegalStateException e) {
            return 10;
        }
    }

    public static double getDivingJumpHungerCost() {
        if (!isConfigLoaded) {
            return 0.05;
        }
        try {
            return DIVING_JUMP_HUNGER_COST.get();
        } catch (IllegalStateException e) {
            return 0.05;
        }
    }

    public static double getDivingCopperSprintSpeed() {
        if (!isConfigLoaded) {
            return 0.01; // 修复：与配置定义保持一致
        }
        try {
            return DIVING_COPPER_SPRINT_SPEED.get();
        } catch (IllegalStateException e) {
            return 0.01; // 修复：与配置定义保持一致
        }
    }

    public static double getDivingNetheriteSprintSpeed() {
        if (!isConfigLoaded) {
            return 0.01;
        }
        try {
            return DIVING_NETHERITE_SPRINT_SPEED.get();
        } catch (IllegalStateException e) {
            return 0.01;
        }
    }

    public static double getDivingSprintFovMultiplier() {
        if (!isConfigLoaded) {
            return 1.01;
        }
        try {
            return DIVING_SPRINT_FOV_MULTIPLIER.get();
        } catch (IllegalStateException e) {
            return 1.01;
        }
    }

    public static double getDivingSprintHungerCost() {
        if (!isConfigLoaded) {
            return 0.1;
        }
        try {
            return DIVING_SPRINT_HUNGER_COST.get();
        } catch (IllegalStateException e) {
            return 0.1;
        }
    }

    public static int getDivingSprintDoubleClickWindow() {
        if (!isConfigLoaded) {
            return 7;
        }
        try {
            return DIVING_SPRINT_DOUBLE_CLICK_WINDOW.get();
        } catch (IllegalStateException e) {
            return 7;
        }
    }

    public static boolean shouldPreventDivingFallDamage() {
        if (!isConfigLoaded) {
            return true;
        }
        try {
            return DIVING_PREVENT_FALL_DAMAGE.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static int getDivingMinHungerLevel() {
        if (!isConfigLoaded) {
            return 6;
        }
        try {
            return DIVING_MIN_HUNGER_LEVEL.get();
        } catch (IllegalStateException e) {
            return 6;
        }
    }
}