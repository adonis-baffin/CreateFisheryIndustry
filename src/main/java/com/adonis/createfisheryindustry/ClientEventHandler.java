//package com.adonis.createfisheryindustry;
//
//import com.adonis.createfisheryindustry.client.renderer.HarpoonModel;
//import net.neoforged.api.distmarker.Dist;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.client.event.EntityRenderersEvent;
//
//@EventBusSubscriber(modid = CreateFisheryMod.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
//public class ClientEventHandler {
//    @SubscribeEvent
//    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
//        event.registerLayerDefinition(HarpoonModel.HARPOON_LAYER, HarpoonModel::createBodyLayer);
//    }
//}