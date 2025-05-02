package com.adonis.createfisheryindustry.config;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.google.common.collect.ImmutableList;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreateFisheryCommonConfig {
    public static final ModConfigSpec CONFIG_SPEC;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    // 缓存解析后的ResourceLocation列表
    private static List<ResourceLocation> cachedWhitelist = new ArrayList<>();
    private static List<ResourceLocation> cachedBlacklist = new ArrayList<>();

    // 记录上次更新的时间戳
    private static long lastWhitelistUpdate = 0;
    private static long lastBlacklistUpdate = 0;

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

    // 注册配置
//    public static void register() {
//        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG_SPEC);
//    }

    // 强制重新解析并缓存白名单
    public static List<ResourceLocation> getWhitelist() {
        long currentTime = System.currentTimeMillis();
        // 如果列表为空或者超过5秒没更新，则重新解析
        if (cachedWhitelist.isEmpty() || currentTime - lastWhitelistUpdate > 5000) {
            cachedWhitelist = WHITELIST.get().stream()
                    .map(ResourceLocation::tryParse)
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            lastWhitelistUpdate = currentTime;

            // 记录日志，便于调试
//            CreateFisheryMod.LOGGER.info("Updated whitelist: {}",
//                    cachedWhitelist.stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));
        }
        return cachedWhitelist;
    }

    // 强制重新解析并缓存黑名单
    public static List<ResourceLocation> getBlacklist() {
        long currentTime = System.currentTimeMillis();
        // 如果列表为空或者超过5秒没更新，则重新解析
        if (cachedBlacklist.isEmpty() || currentTime - lastBlacklistUpdate > 5000) {
            cachedBlacklist = BLACKLIST.get().stream()
                    .map(ResourceLocation::tryParse)
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            lastBlacklistUpdate = currentTime;

            // 记录日志，便于调试
//            CreateFisheryMod.LOGGER.info("Updated blacklist: {}",
//                    cachedBlacklist.stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));
        }
        return cachedBlacklist;
    }

    // 强制刷新缓存
    public static void refreshCache() {
        lastWhitelistUpdate = 0;
        lastBlacklistUpdate = 0;
        getWhitelist();
        getBlacklist();
//        CreateFisheryMod.LOGGER.info("Config cache forcibly refreshed");
    }

    // 配置加载事件处理
    public static void onLoad() {
//        CreateFisheryMod.LOGGER.info("CreateFisheryCommonConfig loaded.");
        refreshCache();
    }

    // 配置重载事件处理
    public static void onReload() {
//        CreateFisheryMod.LOGGER.info("CreateFisheryCommonConfig reloaded.");
        refreshCache();
    }

    // 直接检查实体是否在白名单中（避免每次都读取整个列表）
    public static boolean isEntityWhitelisted(ResourceLocation entityId) {
        List<ResourceLocation> whitelist = getWhitelist();
        boolean result = whitelist.contains(entityId);
        if (entityId.toString().equals("minecraft:breeze")) {
            // 特殊调试breeze实体
//            CreateFisheryMod.LOGGER.debug("Checking if breeze is whitelisted: {} (whitelist size: {})",
//                    result, whitelist.size());
        }
        return result;
    }

    // 直接检查实体是否在黑名单中（避免每次都读取整个列表）
    public static boolean isEntityBlacklisted(ResourceLocation entityId) {
        return getBlacklist().contains(entityId);
    }
}