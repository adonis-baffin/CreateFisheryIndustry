package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.adonis.createfisheryindustry.registry.CreateFisheryTabs;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    public CreateFisheryMod(IEventBus bus, ModContainer modContainer) {
        // 注册 Create 基础内容
        REGISTRATE.registerEventListeners(bus);
        CreateFisheryBlocks.register();
        CreateFisheryBlockEntities.register(bus);
        CreateFisheryEntityTypes.register(bus);
        CreateFisheryItems.register(bus);
        CreateFisheryTabs.register(bus);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, CreateFisheryCommonConfig.CONFIG_SPEC);

        // 注册事件监听器
        bus.addListener(this::registerCapabilities);
        bus.addListener(this::setup);
        bus.addListener(this::enqueueIMC);
        bus.addListener(this::processIMC);
        bus.addListener(CreateFisheryMod::clientInit);

        // 注册服务器事件
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.MESH_TRAP.get(),
                (be, side) -> be.getCapability(Capabilities.ItemHandler.BLOCK, side)
        );
        TrapNozzleBlockEntity.registerCapabilities(event);
        SmartMeshBlockEntity.registerCapabilities(event);
    }

    private void setup(final FMLCommonSetupEvent event) {
        CreateFisheryCommonConfig.onLoad();
    }

    private void enqueueIMC(final InterModEnqueueEvent event) {}

    private void processIMC(final InterModProcessEvent event) {}

    public static void clientInit(final FMLClientSetupEvent event) {}

    private void onServerStarting(ServerStartingEvent event) {
        CreateFisheryCommonConfig.refreshCache();
    }

    private void onServerStopping(ServerStoppingEvent event) {}
}