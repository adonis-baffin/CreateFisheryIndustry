package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
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

        // 注册 Create 模组内容
        REGISTRATE.registerEventListeners(bus);
        CreateFisheryBlocks.register();
        CreateFisheryBlockEntities.register(bus);
        CreateFisheryEntityTypes.register(bus);
        CreateFisheryItems.register(bus);
        CreateFisheryTabs.register(bus);
        CreateFisheryComponents.register(bus);

        // 注册配置
        modContainer.registerConfig(ModConfig.Type.COMMON, CreateFisheryCommonConfig.CONFIG_SPEC);

        // 注册事件监听器
        bus.addListener(this::registerCapabilities);
        bus.addListener(this::commonSetup);

        // 注册服务器事件
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    // 注册能力（如 ItemHandler）
    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 注册 Mesh Trap 的 ItemHandler 能力
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.MESH_TRAP.get(),
                (be, side) -> be.getCapability(Capabilities.ItemHandler.BLOCK, side)
        );

        // 注册 Smart Beehive 的 ItemHandler 能力
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

        // 注册 Smart Beehive 的 FluidHandler 能力
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

        // 其他能力注册
        TrapNozzleBlockEntity.registerCapabilities(event);
        SmartMeshBlockEntity.registerCapabilities(event);
        CreateFisheryItems.registerCapabilities(event);
    }

    // 通用初始化
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CreateFisheryCommonConfig::onLoad);
    }

    // 服务器启动
    private void onServerStarting(ServerStartingEvent event) {
        CreateFisheryCommonConfig.refreshCache();
    }

    // 服务器停止
    private void onServerStopping(ServerStoppingEvent event) {
    }
}