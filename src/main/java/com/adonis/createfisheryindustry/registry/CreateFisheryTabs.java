package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class CreateFisheryTabs {
    private static final DeferredRegister<CreativeModeTab> REGISTER = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateFisheryMod.ID);

    // 机械动力：渔业
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FISHERY_INDUSTRY_TAB = REGISTER.register("fishery_industry_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createfisheryindustry.fishery_industry_tab"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(CreateFisheryBlocks.MESH_TRAP::asStack)
                    .displayItems(new IndustryItemsGenerator())
                    .build());

    // 机械动力：渔业装备
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FISHERY_EQUIPMENT_TAB = REGISTER.register("fishery_equipment_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createfisheryindustry.fishery_equipment_tab"))
                    .withTabsBefore(FISHERY_INDUSTRY_TAB.getKey())
                    .icon(() -> CreateFisheryItems.HARPOON.get().getDefaultInstance())
                    .displayItems(new EquipmentItemsGenerator())
                    .build());

    // 机械动力：渔业 - 物品生成器
    public static class IndustryItemsGenerator implements CreativeModeTab.DisplayItemsGenerator {
        @Override
        public void accept(CreativeModeTab.@NotNull ItemDisplayParameters parameters, CreativeModeTab.@NotNull Output output) {
            output.accept(CreateFisheryItems.ZINC_SHEET.get());
            output.accept(CreateFisheryBlocks.FRAME_TRAP);
            output.accept(CreateFisheryBlocks.TRAP_BEARING);
            output.accept(CreateFisheryBlocks.MESH_TRAP);
            output.accept(CreateFisheryBlocks.TRAP_NOZZLE);
            output.accept(CreateFisheryBlocks.SMART_NOZZLE);
            output.accept(CreateFisheryBlocks.SMART_MESH);
            output.accept(CreateFisheryBlocks.MECHANICAL_PEELER);

            output.accept(CreateFisheryItems.FISH_SKIN.get());
            output.accept(CreateFisheryItems.ROASTED_FISH_SKIN.get());
            output.accept(CreateFisheryItems.COD_STEAK.get());
            output.accept(CreateFisheryItems.COOKED_COD_STEAK.get());
            output.accept(CreateFisheryItems.SALMON_STEAK.get());
            output.accept(CreateFisheryItems.COOKED_SALMON_STEAK.get());

            output.accept(CreateFisheryItems.RAW_BLUE_MUSSEL.get());
            output.accept(CreateFisheryItems.RAW_MEDITERRANEAN_MUSSEL.get());
            output.accept(CreateFisheryItems.COOKED_BLUE_MUSSEL.get());
            output.accept(CreateFisheryItems.COOKED_MEDITERRANEAN_MUSSEL.get());
            output.accept(CreateFisheryItems.RAW_LOBSTER.get());
            output.accept(CreateFisheryItems.COOKED_LOBSTER.get());
        }
    }

    // 机械动力：渔业装备 - 物品生成器
    public static class EquipmentItemsGenerator implements CreativeModeTab.DisplayItemsGenerator {
        @Override
        public void accept(CreativeModeTab.@NotNull ItemDisplayParameters parameters, CreativeModeTab.@NotNull Output output) {
            output.accept(CreateFisheryItems.WORN_HARPOON.get());
            output.accept(CreateFisheryItems.HARPOON.get());
            output.accept(CreateFisheryItems.WAXED_LEATHER.get());
            output.accept(CreateFisheryItems.PNEUMATIC_MECHANISM.get());
            output.accept(CreateFisheryItems.COPPER_DIVING_LEGGINGS.get());
            output.accept(CreateFisheryItems.NETHERITE_DIVING_LEGGINGS.get());
            output.accept(CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get());
            output.accept(CreateFisheryItems.HARPOON_POUCH.get());

            // 添加自定义附魔书
            addEnchantedBooks(parameters, output);
        }

        private void addEnchantedBooks(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
            // 获取附魔注册表
            var enchantmentRegistry = parameters.holders().lookupOrThrow(Registries.ENCHANTMENT);

            // 添加处决附魔书（所有等级）
            Optional<Holder.Reference<Enchantment>> executionHolder = enchantmentRegistry.get(CreateFisheryEnchantments.EXECUTION);
            executionHolder.ifPresent(holder -> {
                for (int level = 1; level <= holder.value().getMaxLevel(); level++) {
                    ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, level));
                    output.accept(book);
                }
            });

            // 添加倒刺附魔书（所有等级）
            Optional<Holder.Reference<Enchantment>> barbedHolder = enchantmentRegistry.get(CreateFisheryEnchantments.BARBED);
            barbedHolder.ifPresent(holder -> {
                for (int level = 1; level <= holder.value().getMaxLevel(); level++) {
                    ItemStack book = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, level));
                    output.accept(book);
                }
            });
        }
    }

    @ApiStatus.Internal
    public static void register(IEventBus modEventBus) {
        REGISTER.register(modEventBus);
    }
}