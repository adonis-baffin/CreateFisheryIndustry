package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshRenderer;
import com.adonis.createfisheryindustry.client.renderer.HarpoonRenderer;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryEntityTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;

@EventBusSubscriber(modid = CreateFisheryMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register block render types
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.FRAME_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.MESH_TRAP.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.TRAP_NOZZLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(CreateFisheryBlocks.SMART_MESH.get(), RenderType.cutout());

            // Register SmartMeshRenderer
            BlockEntityRenderers.register(CreateFisheryBlockEntities.SMART_MESH.get(), SmartMeshRenderer::new);

            // Register HarpoonRenderer
            EntityRenderers.register(CreateFisheryEntityTypes.HARPOON.get(), HarpoonRenderer::new);

            // Register HarpoonItem properties
            HarpoonItem.registerItemProperties(CreateFisheryItems.HARPOON);
        });
    }
}