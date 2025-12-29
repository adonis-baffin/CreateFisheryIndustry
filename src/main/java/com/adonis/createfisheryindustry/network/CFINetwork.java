package com.adonis.createfisheryindustry.network;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateFisheryMod.ID, bus = EventBusSubscriber.Bus.MOD)
public class CFINetwork {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(CreateFisheryMod.ID)
                .versioned("1")
                .optional();

        registrar.playToClient(
                HarpoonGunStateSyncPacket.TYPE,
                HarpoonGunStateSyncPacket.STREAM_CODEC,
                (payload, context) -> payload.handle(context)
        );
    }

    public static void sendToClient(HarpoonGunStateSyncPacket packet, net.minecraft.server.level.ServerPlayer player) {
        player.connection.send(packet);
    }
}