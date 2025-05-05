package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.container.HarpoonPouchMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class CreateFisheryMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = 
            DeferredRegister.create(Registries.MENU, CreateFisheryMod.ID);

    public static final Supplier<MenuType<HarpoonPouchMenu>> HARPOON_POUCH = 
            MENU_TYPES.register("harpoon_pouch",
                () -> IMenuTypeExtension.create((windowId, inv, data) -> {
                    return new HarpoonPouchMenu(windowId, inv, inv.player.getItemInHand(data.readEnum(net.minecraft.world.InteractionHand.class)));
                }));

    public static void register(IEventBus bus) {
        MENU_TYPES.register(bus);
    }
}