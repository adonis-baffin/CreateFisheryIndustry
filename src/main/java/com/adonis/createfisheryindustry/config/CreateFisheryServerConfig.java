package com.adonis.createfisheryindustry.config;

import com.google.common.collect.ImmutableList;
import net.createmod.catnip.config.ConfigBase;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreateFisheryServerConfig extends ConfigBase {
    public final CreateFisheryKineticsConfig kinetics = nested(0, CreateFisheryKineticsConfig::new, "Parameters and abilities of kinetic mechanisms");
    public final ModConfigSpec.ConfigValue<List<? extends String>> frameTrapWhitelist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> frameTrapBlacklist;

    private List<ResourceLocation> cachedWhitelist = new ArrayList<>();
    private List<ResourceLocation> cachedBlacklist = new ArrayList<>();
    private long lastWhitelistUpdate = 0;
    private long lastBlacklistUpdate = 0;

    public CreateFisheryServerConfig(ModConfigSpec.Builder builder) {
        builder.push("frameTrap");

        frameTrapWhitelist = builder
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

        frameTrapBlacklist = builder
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
    }

    @Override
    public String getName() {
        return "server";
    }

    public List<ResourceLocation> getWhitelist() {
        long currentTime = System.currentTimeMillis();
        if (cachedWhitelist.isEmpty() || currentTime - lastWhitelistUpdate > 5000) {
            cachedWhitelist = frameTrapWhitelist.get().stream()
                    .map(ResourceLocation::tryParse)
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            lastWhitelistUpdate = currentTime;
        }
        return cachedWhitelist;
    }

    public List<ResourceLocation> getBlacklist() {
        long currentTime = System.currentTimeMillis();
        if (cachedBlacklist.isEmpty() || currentTime - lastBlacklistUpdate > 5000) {
            cachedBlacklist = frameTrapBlacklist.get().stream()
                    .map(ResourceLocation::tryParse)
                    .filter(r -> r != null)
                    .collect(Collectors.toList());
            lastBlacklistUpdate = currentTime;
        }
        return cachedBlacklist;
    }

    public void refreshCache() {
        lastWhitelistUpdate = 0;
        lastBlacklistUpdate = 0;
        getWhitelist();
        getBlacklist();
    }

    public boolean isEntityWhitelisted(ResourceLocation entityId) {
        return getWhitelist().contains(entityId);
    }

    public boolean isEntityBlacklisted(ResourceLocation entityId) {
        return getBlacklist().contains(entityId);
    }
}