package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.config.CreateFisheryConfig;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.registry.*;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(CreateFisheryMod.ID)
public class CreateFisheryMod {
    public static final String ID = "createfisheryindustry";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final CreateRegistrate REGISTRATE = CreateRegistrate.create(ID)
            .defaultCreativeTab((ResourceKey<CreativeModeTab>) null)
            .setTooltipModifierFactory(item ->
                    new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE)
                            .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            );

    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, ID);

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

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, CreateFisheryCommonConfig.CONFIG_SPEC);
        new CreateFisheryConfig(modContainer);

        bus.addListener(this::registerCapabilities);
        bus.addListener(this::commonSetup);
        bus.addListener(this::onConfigLoad);
        bus.addListener(this::onConfigReload);

        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.MESH_TRAP.get(),
                (be, side) -> be.getCapability(Capabilities.ItemHandler.BLOCK, side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.SMART_BEEHIVE.get(),
                (be, side) -> {
                    if (be instanceof SmartBeehiveBlockEntity beehive) {
                        return side == null || side == Direction.UP
                                ? beehive.insertionHandler
                                : beehive.extractionHandler;
                    }
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                CreateFisheryBlockEntities.SMART_BEEHIVE.get(),
                (be, side) -> {
                    if (be instanceof SmartBeehiveBlockEntity beehive && (side == null || side == Direction.DOWN)) {
                        return beehive.getFluidTank();
                    }
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.MECHANICAL_PEELER.get(),
                (be, side) -> {
                    if (be instanceof MechanicalPeelerBlockEntity peeler) {
                        return peeler.getItemHandler(side);
                    }
                    return null;
                }
        );

        TrapNozzleBlockEntity.registerCapabilities(event);
        SmartMeshBlockEntity.registerCapabilities(event);
        CreateFisheryItems.registerCapabilities(event);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // 无需配置加载逻辑
    }

    private void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == CreateFisheryCommonConfig.CONFIG_SPEC) {
            CreateFisheryCommonConfig.onLoad();
        }
    }

    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == CreateFisheryCommonConfig.CONFIG_SPEC) {
            CreateFisheryCommonConfig.onReload();
        }
    }

    private void onServerStarting(ServerStartingEvent event) {
        CreateFisheryCommonConfig.refreshCache();
    }

    private void onServerStopping(ServerStoppingEvent event) {
    }
}