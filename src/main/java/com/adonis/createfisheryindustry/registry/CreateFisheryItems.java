package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import com.adonis.createfisheryindustry.item.HarpoonPouchItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.google.common.collect.Sets;
import com.simibubi.create.content.equipment.armor.AllArmorMaterials;
import com.simibubi.create.foundation.item.ItemDescription;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
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

    public static final ItemEntry<Item> ZINC_SHEET = REGISTRATE.item("zinc_sheet",
                    p -> new Item(basicItem()))
            .onRegister(item -> CREATIVE_TAB_ITEMS.add(REGISTRATE.get("zinc_sheet", net.minecraft.core.registries.Registries.ITEM)))
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

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.ItemHandler.ITEM, (stack, context) -> {
            if (stack.getItem() instanceof HarpoonPouchItem) {
                // 获取默认的 ServerLevel（主世界）
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                Level level = server != null ? server.getLevel(Level.OVERWORLD) : null;
                if (level == null) {
                    // 如果无法获取 Level，返回空的 ItemHandler 防止崩溃
                    return new ItemStackHandler(9);
                }
                return new HarpoonPouchItem.PouchItemHandler(stack, level);
            }
            return null;
        }, HARPOON_POUCH.get());
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}