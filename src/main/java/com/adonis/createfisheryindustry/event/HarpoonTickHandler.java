//package com.adonis.createfisheryindustry.event;
//
//import com.adonis.createfisheryindustry.item.PneumaticHarpoonGunItem;
//import com.adonis.createfisheryindustry.procedures.PneumaticHarpoonGunItemInHandTickProcedure;
//import net.neoforged.bus.api.SubscribeEvent;
//import net.neoforged.fml.common.EventBusSubscriber;
//import net.neoforged.neoforge.event.tick.ServerTickEvent;
//import net.minecraft.server.level.ServerLevel;
//import net.minecraft.server.level.ServerPlayer;
//import net.minecraft.world.item.ItemStack;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//@EventBusSubscriber(modid = "create_sa", bus = EventBusSubscriber.Bus.FORGE)
//public class HarpoonTickHandler {
//    private static final Logger LOGGER = LoggerFactory.getLogger(HarpoonTickHandler.class);
//
//    @SubscribeEvent
//    public static void onServerTick(TickEvent.ServerTickEvent event) {
//        if (event.phase == TickEvent.Phase.END) {
//            ServerLevel world = event.getServer().overworld();
//            for (ServerPlayer player : world.players()) {
//                ItemStack mainHand = player.getMainHandItem();
//                ItemStack offHand = player.getOffhandItem();
//                if (mainHand.getItem() instanceof PneumaticHarpoonGunItem) {
//                    PneumaticHarpoonGunItemInHandTickProcedure.execute(world, player.getX(), player.getY(), player.getZ(), player, mainHand);
//                    LOGGER.debug("Harpoon tick executed for player {} in main hand", player.getName().getString());
//                } else if (offHand.getItem() instanceof PneumaticHarpoonGunItem) {
//                    PneumaticHarpoonGunItemInHandTickProcedure.execute(world, player.getX(), player.getY(), player.getZ(), player, offHand);
//                    LOGGER.debug("Harpoon tick executed for player {} in off hand", player.getName().getString());
//                }
//            }
//        }
//    }
//}