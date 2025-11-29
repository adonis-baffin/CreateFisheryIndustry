package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.HarpoonPouchItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.PneumaticHarpoonGunItem;
import com.google.common.collect.Sets;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.foundation.item.ItemDescription;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.LinkedHashSet;
import java.util.function.Supplier;

import static com.adonis.createfisheryindustry.CreateFisheryMod.REGISTRATE;

@SuppressWarnings("unused")
public class CreateFisheryItems {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, CreateFisheryMod.ID);
    public static LinkedHashSet<Supplier<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    public static Item.Properties basicItem() {
        return new Item.Properties().stacksTo(64);
    }

    // Food properties for new items
    public static final FoodProperties FISH_SKIN_FOOD = new FoodProperties.Builder()
            .nutrition(1).saturationModifier(0.4f).build();
    public static final FoodProperties ROASTED_FISH_SKIN_FOOD = new FoodProperties.Builder()
            .nutrition(4).saturationModifier(0.4f).build();
    public static final FoodProperties COD_STEAK_FOOD = new FoodProperties.Builder()
            .nutrition(2).saturationModifier(0.4f).build();
    public static final FoodProperties SALMON_STEAK_FOOD = new FoodProperties.Builder()
            .nutrition(2).saturationModifier(0.4f).build();
    public static final FoodProperties COOKED_COD_STEAK_FOOD = new FoodProperties.Builder()
            .nutrition(4).saturationModifier(0.6f).build();
    public static final FoodProperties COOKED_SALMON_STEAK_FOOD = new FoodProperties.Builder()
            .nutrition(6).saturationModifier(0.96f).build();

    // Existing items
    public static final ItemEntry<Item> ZINC_SHEET = REGISTRATE.item("zinc_sheet",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("zinc_sheet", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<Item> INCOMPLETE_PNEUMATIC_MECHANISM = REGISTRATE.item("incomplete_pneumatic_mechanism",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("incomplete_pneumatic_mechanism", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<Item> PNEUMATIC_MECHANISM = REGISTRATE.item("pneumatic_mechanism",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("pneumatic_mechanism", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<Item> CHAINS = REGISTRATE.item("chains",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("chains", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<Item> WAXED_CARDBOARD = REGISTRATE.item("waxed_cardboard",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("waxed_cardboard", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<Item> WAXED_LEATHER = REGISTRATE.item("waxed_leather",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("waxed_leather", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<Item> WORN_HARPOON = REGISTRATE.item("worn_harpoon",
                    p -> new Item(basicItem()))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.worn_harpoon");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("worn_harpoon", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<HarpoonItem> HARPOON = REGISTRATE.item("harpoon",
                    p -> new HarpoonItem(new Item.Properties().durability(250).stacksTo(1)))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("harpoon", net.minecraft.core.registries.Registries.ITEM)))
            .register();

    public static final ItemEntry<HarpoonPouchItem> HARPOON_POUCH = REGISTRATE.item("harpoon_pouch",
                    p -> new HarpoonPouchItem(new Item.Properties().stacksTo(1)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.harpoon_pouch");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("harpoon_pouch", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<CopperDivingLeggingsItem> COPPER_DIVING_LEGGINGS = REGISTRATE.item("copper_diving_leggings",
                    p -> new CopperDivingLeggingsItem(
                            AllArmorMaterials.COPPER,
                            p.durability(105),
                            ResourceLocation.fromNamespaceAndPath(CreateFisheryMod.ID, "copper_diving_leggings.png")
                    ))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.copper_diving_leggings");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("copper_diving_leggings", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<NetheriteDivingLeggingsItem> NETHERITE_DIVING_LEGGINGS = REGISTRATE.item("netherite_diving_leggings",
                    p -> new NetheriteDivingLeggingsItem(
                            ArmorMaterials.NETHERITE,
                            p.durability(555).fireResistant(),
                            ResourceLocation.fromNamespaceAndPath(CreateFisheryMod.ID, "netherite_diving_leggings.png")
                    ))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.netherite_diving_leggings");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("netherite_diving_leggings", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<PneumaticHarpoonGunItem> PNEUMATIC_HARPOON_GUN = REGISTRATE.item("pneumatic_harpoon_gun",
                    p -> new PneumaticHarpoonGunItem(new Item.Properties().stacksTo(1)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.pneumatic_harpoon_gun");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("pneumatic_harpoon_gun", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    // New food items
    public static final ItemEntry<Item> FISH_SKIN = REGISTRATE.item("fish_skin",
                    p -> new Item(basicItem().food(FISH_SKIN_FOOD)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.fish_skin");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("fish_skin", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<Item> ROASTED_FISH_SKIN = REGISTRATE.item("roasted_fish_skin",
                    p -> new Item(basicItem().food(ROASTED_FISH_SKIN_FOOD)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.roasted_fish_skin");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("roasted_fish_skin", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<Item> COD_STEAK = REGISTRATE.item("cod_steak",
                    p -> new Item(basicItem().food(COD_STEAK_FOOD)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.cod_steak");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("cod_steak", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<Item> SALMON_STEAK = REGISTRATE.item("salmon_steak",
                    p -> new Item(basicItem().food(SALMON_STEAK_FOOD)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.salmon_steak");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("salmon_steak", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<Item> COOKED_COD_STEAK = REGISTRATE.item("cooked_cod_steak",
                    p -> new Item(basicItem().food(COOKED_COD_STEAK_FOOD)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.cooked_cod_steak");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("cooked_cod_steak", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static final ItemEntry<Item> COOKED_SALMON_STEAK = REGISTRATE.item("cooked_salmon_steak",
                    p -> new Item(basicItem().food(COOKED_SALMON_STEAK_FOOD)))
            .onRegister(item -> {
                ItemDescription.useKey(item, "item.createfisheryindustry.cooked_salmon_steak");
                CREATIVE_TAB_ITEMS.add(REGISTRATE.get("cooked_salmon_steak", net.minecraft.core.registries.Registries.ITEM));
            })
            .register();

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.MESH_TRAP.get(),
                (be, side) -> be.getCapability(Capabilities.ItemHandler.BLOCK, side)
        );
        TrapNozzleBlockEntity.registerCapabilities(event);
        SmartMeshBlockEntity.registerCapabilities(event);
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}