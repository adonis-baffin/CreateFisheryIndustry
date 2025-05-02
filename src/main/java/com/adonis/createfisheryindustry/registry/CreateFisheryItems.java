package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.google.common.collect.Sets;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.LinkedHashSet;
import java.util.function.Supplier;

@SuppressWarnings("unused")
public class CreateFisheryItems {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, CreateFisheryMod.ID);
    public static LinkedHashSet<Supplier<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    public static DeferredHolder<Item, Item> registerWithTab(final String name, final Supplier<Item> supplier) {
        DeferredHolder<Item, Item> item = ITEMS.register(name, supplier);
        CREATIVE_TAB_ITEMS.add(item);
        return item;
    }

    public static Item.Properties basicItem() {
        return new Item.Properties().stacksTo(64);
    }

    public static final DeferredHolder<Item, Item> ZINC_SHEET = registerWithTab("zinc_sheet",
            () -> new Item(basicItem()));

    // 铜潜水护腿
    public static final DeferredHolder<Item, Item> COPPER_DIVING_LEGGINGS = registerWithTab("copper_diving_leggings",
            () -> new CopperDivingLeggingsItem(
                    AllArmorMaterials.COPPER,
                    new Item.Properties().durability(105),
                    ResourceLocation.fromNamespaceAndPath(CreateFisheryMod.ID, "copper_diving_leggings.png")
            ));

    // 下界合金潜水护腿
    public static final DeferredHolder<Item, Item> NETHERITE_DIVING_LEGGINGS = registerWithTab("netherite_diving_leggings",
            () -> new NetheriteDivingLeggingsItem(
                    ArmorMaterials.NETHERITE,
                    new Item.Properties().durability(555).fireResistant(), // 耐久度555，防火
                    ResourceLocation.fromNamespaceAndPath(CreateFisheryMod.ID, "netherite_diving_leggings.png")
            ));

    // 鱼叉
// 鱼叉
    public static final DeferredHolder<Item, Item> HARPOON = registerWithTab("harpoon",
            () -> new HarpoonItem(new Item.Properties().durability(250).stacksTo(1)));


    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}