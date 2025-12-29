package com.adonis.createfisheryindustry.network;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record HarpoonGunStateSyncPacket(boolean hooked) implements CustomPacketPayload {

    public static final Type<HarpoonGunStateSyncPacket> TYPE =
            new Type<>(CreateFisheryMod.asResource("harpoon_gun_state_sync"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, HarpoonGunStateSyncPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    HarpoonGunStateSyncPacket::hooked,
                    HarpoonGunStateSyncPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null) return;

            updateIfHarpoonGun(player.getMainHandItem());
            updateIfHarpoonGun(player.getOffhandItem());
            player.inventoryMenu.broadcastChanges();
        });
    }

    private void updateIfHarpoonGun(ItemStack stack) {
        if (stack.getItem() instanceof com.adonis.createfisheryindustry.item.PneumaticHarpoonGunItem) {
            stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, data ->
                    data.update(tag -> tag.putBoolean("tagHooked", hooked))
            );
        }
    }
}