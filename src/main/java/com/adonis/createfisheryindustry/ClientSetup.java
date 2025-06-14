package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshRenderer;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.adonis.createfisheryindustry.client.renderer.MechanicalPeelerRenderer;
import com.adonis.createfisheryindustry.client.renderer.HarpoonRenderer;
import com.adonis.createfisheryindustry.client.renderer.HarpoonISTER;
import com.adonis.createfisheryindustry.client.renderer.TetheredHarpoonRenderer;
import com.adonis.createfisheryindustry.item.ClientHarpoonPouchTooltip;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.HarpoonPouchItem;
import com.adonis.createfisheryindustry.item.HarpoonPouchTooltip;
import com.adonis.createfisheryindustry.procedures.PneumaticHarpoonGunChainsLineProcedure;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = CreateFisheryMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // 首先初始化 PartialModels - 这必须在任何使用它们的代码之前完成
        CreateFisheryPartialModels.init();

        event.enqueueWork(() -> {
            // 设置方块渲染层
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.MESH_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.TRAP_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_MESH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_BEEHIVE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.MECHANICAL_PEELER.get(), RenderType.cutoutMipped());

            // 注册方块实体渲染器
            BlockEntityRenderers.register(CreateFisheryBlockEntities.SMART_MESH.get(), SmartMeshRenderer::new);
            // 添加缺失的 MechanicalPeeler 渲染器注册
            BlockEntityRenderers.register(CreateFisheryBlockEntities.MECHANICAL_PEELER.get(), MechanicalPeelerRenderer::new);

            // 注册实体渲染器
            EntityRenderers.register(CreateFisheryEntityTypes.HARPOON.get(), HarpoonRenderer::new);
            EntityRenderers.register(CreateFisheryEntityTypes.TETHERED_HARPOON.get(), TetheredHarpoonRenderer::new);

            // 注册物品属性
            HarpoonItem.registerItemProperties(CreateFisheryItems.HARPOON);

            ItemProperties.register(
                    CreateFisheryItems.HARPOON_POUCH.get(),
                    ResourceLocation.fromNamespaceAndPath(CreateFisheryMod.ID, "filled"),
                    (stack, level, entity, seed) -> HarpoonPouchItem.getFullnessDisplay(stack)
            );

            // 注册其他程序
            PneumaticHarpoonGunChainsLineProcedure.register();
        });
    }

    @SubscribeEvent
    public static void registerTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(HarpoonPouchTooltip.class, ClientHarpoonPouchTooltip::new);
    }

    @SubscribeEvent
    public static void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(HarpoonISTER.RENDERER);
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return HarpoonISTER.RENDERER;
            }
        }, CreateFisheryItems.HARPOON.get());
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(TetheredHarpoonRenderer.HARPOON_LAYER,
                TetheredHarpoonRenderer.HarpoonModel::createBodyLayer);
    }
}