package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshRenderer;
import com.adonis.createfisheryindustry.client.renderer.HarpoonRenderer;
import com.adonis.createfisheryindustry.client.renderer.HarpoonISTER;
import com.adonis.createfisheryindustry.item.ClientHarpoonPouchTooltip;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.HarpoonPouchItem;
import com.adonis.createfisheryindustry.item.HarpoonPouchTooltip;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.mojang.logging.LogUtils;
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
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.slf4j.Logger;

@EventBusSubscriber(modid = CreateFisheryMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 注册方块渲染类型
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.MESH_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.TRAP_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_MESH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_BEEHIVE.get(), RenderType.cutout());

            // 注册方块实体渲染器
            BlockEntityRenderers.register(CreateFisheryBlockEntities.SMART_MESH.get(), SmartMeshRenderer::new);

            // 注册实体渲染器
            EntityRenderers.register(CreateFisheryEntityTypes.HARPOON.get(), HarpoonRenderer::new);

            // 注册 HarpoonItem 属性
            HarpoonItem.registerItemProperties(CreateFisheryItems.HARPOON);

            // 注册 HarpoonPouchItem filled 谓词
            ItemProperties.register(
                    CreateFisheryItems.HARPOON_POUCH.get(),
                    ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "filled"),
                    (stack, level, entity, seed) -> {
                        float fullness = HarpoonPouchItem.getFullnessDisplay(stack);
                        return fullness;
                    }
            );
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
        // 注册 HarpoonItem 自定义渲染器
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return HarpoonISTER.RENDERER;
            }
        }, CreateFisheryItems.HARPOON.get());
    }
}