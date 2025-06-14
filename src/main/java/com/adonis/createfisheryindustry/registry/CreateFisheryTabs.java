package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class CreateFisheryTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFisheryMod.ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FISHERY_TAB = REGISTER.register("fishery_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createfisheryindustry.fishery_tab"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(CreateFisheryBlocks.MESH_TRAP::asStack)
                    .displayItems(new ItemsGenerator())
                    .build());

    public static class ItemsGenerator implements CreativeModeTab.DisplayItemsGenerator {
        @Override
        public void accept(CreativeModeTab.@NotNull ItemDisplayParameters parameters, CreativeModeTab.@NotNull Output output) {
            output.accept(CreateFisheryBlocks.FRAME_TRAP);
            output.accept(CreateFisheryBlocks.MESH_TRAP);
            output.accept(CreateFisheryBlocks.TRAP_NOZZLE);
            output.accept(CreateFisheryBlocks.SMART_NOZZLE);
            output.accept(CreateFisheryBlocks.SMART_MESH);
//            output.accept(CreateFisheryBlocks.SMART_BEEHIVE);
            output.accept(CreateFisheryBlocks.MECHANICAL_PEELER);
            output.accept(CreateFisheryItems.WORN_HARPOON.get());
            output.accept(CreateFisheryItems.HARPOON.get());
            output.accept(CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get());
            output.accept(CreateFisheryItems.HARPOON_POUCH.get());
            output.accept(CreateFisheryItems.ZINC_SHEET.get());
            output.accept(CreateFisheryItems.PNEUMATIC_MECHANISM.get());

            output.accept(CreateFisheryItems.COPPER_DIVING_LEGGINGS.get());
            output.accept(CreateFisheryItems.NETHERITE_DIVING_LEGGINGS.get());

            output.accept(CreateFisheryItems.WAXED_CARDBOARD.get());
            output.accept(CreateFisheryItems.WAXED_LEATHER.get());

            output.accept(CreateFisheryItems.FISH_SKIN.get());
            output.accept(CreateFisheryItems.ROASTED_FISH_SKIN.get());
            output.accept(CreateFisheryItems.COD_STEAK.get());
            output.accept(CreateFisheryItems.COOKED_COD_STEAK.get());
            output.accept(CreateFisheryItems.SALMON_STEAK.get());
            output.accept(CreateFisheryItems.COOKED_SALMON_STEAK.get());
        }
    }

    @ApiStatus.Internal
    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}