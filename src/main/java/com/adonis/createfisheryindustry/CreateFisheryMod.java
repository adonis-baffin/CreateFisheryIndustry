package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.client.UnderwaterSprintFOVHandler;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.config.CreateFisheryStressConfig;
import com.adonis.createfisheryindustry.event.HarpoonDropHandler;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(CreateFisheryMod.ID)
public class CreateFisheryMod {
    public static final String ID = "createfisheryindustry";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CFIRegistrate REGISTRATE = CFIRegistrate.create(ID)
            .setTooltipModifier(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            );

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, ID);

    public static final CreateFisheryStressConfig STRESS_CONFIG = new CreateFisheryStressConfig(ID);

    private static ModConfigSpec stressConfigSpec;

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public CreateFisheryMod(IEventBus bus, ModContainer modContainer) {
        REGISTRATE.registerEventListeners(bus);

        CreateFisheryBlocks.register();
        CreateFisheryBlockEntities.register(bus);
        CreateFisheryEntityTypes.register(bus);
        CreateFisheryItems.register(bus);
        CreateFisheryTabs.register(bus);
        CreateFisheryComponents.register(bus);

        SERIALIZERS.register(bus);
        TYPES.register(bus);
        CreateFisheryRecipeTypes.register(SERIALIZERS, TYPES);

        modContainer.registerConfig(ModConfig.Type.COMMON, CreateFisheryCommonConfig.CONFIG_SPEC);

        ModConfigSpec.Builder stressBuilder = new ModConfigSpec.Builder();
        STRESS_CONFIG.registerAll(stressBuilder);
        stressConfigSpec = stressBuilder.build();
        modContainer.registerConfig(ModConfig.Type.SERVER, stressConfigSpec, STRESS_CONFIG.getName() + ".toml");

        bus.addListener(this::commonSetup);
        bus.addListener(this::onModConfigEvent);
        bus.register(CreateFisheryBlockEntities.class);

        // 注册客户端事件（仅在客户端运行）
        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(UnderwaterSprintFOVHandler.class);
        }

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.register(HarpoonDropHandler.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BacktankInventorySupplier.register();
        });
    }

    private void onModConfigEvent(ModConfigEvent event) {
        ModConfig config = event.getConfig();

        if (config.getSpec() == CreateFisheryCommonConfig.CONFIG_SPEC) {
            if (event instanceof ModConfigEvent.Loading) {
                CreateFisheryCommonConfig.onLoad();
            } else if (event instanceof ModConfigEvent.Reloading) {
                CreateFisheryCommonConfig.onReload();
            }
        } else if (stressConfigSpec != null && config.getSpec() == stressConfigSpec) {
            if (event instanceof ModConfigEvent.Loading) {
            } else if (event instanceof ModConfigEvent.Reloading) {
            }
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        CreateFisheryCommonConfig.refreshCache();
    }

    private void onServerStopping(ServerStoppingEvent event) {
    }
}