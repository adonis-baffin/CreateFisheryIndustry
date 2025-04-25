

package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.google.common.collect.Sets;
import java.util.LinkedHashSet;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public class CreateFisheryItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, CreateFisheryMod.ID);
    public static LinkedHashSet<Supplier<Item>> CREATIVE_TAB_ITEMS = Sets.newLinkedHashSet();

    public static Supplier<Item> registerWithTab(final String name, final Supplier<Item> supplier) {
        Supplier<Item> item = ITEMS.register(name, supplier);
        CREATIVE_TAB_ITEMS.add(item);
        return item;
    }

    public static Item.Properties basicItem() {
        return new Item.Properties().stacksTo(64);
    }

    public static final Supplier<Item> ZINC_SHEET = registerWithTab("zinc_sheet",
            () -> new Item(basicItem()));
}
