package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.HarpoonPouchContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class CreateFisheryComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateFisheryMod.ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<HarpoonPouchContents>> HARPOON_POUCH_CONTENTS =
            COMPONENTS.register("harpoon_pouch_contents", () ->
                    DataComponentType.<HarpoonPouchContents>builder()
                            .persistent(HarpoonPouchContents.CODEC)
                            .networkSynchronized(HarpoonPouchContents.STREAM_CODEC)
                            .build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}