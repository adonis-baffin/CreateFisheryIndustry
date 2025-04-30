package com.adonis.createfisheryindustry.item;

import com.simibubi.create.content.equipment.armor.BaseArmorItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;

public class NetheriteDivingLeggingsItem extends BaseArmorItem {
    public static final EquipmentSlot SLOT = EquipmentSlot.LEGS;
    public static final ArmorItem.Type TYPE = ArmorItem.Type.LEGGINGS;

    public NetheriteDivingLeggingsItem(Holder<ArmorMaterial> material, Item.Properties properties, ResourceLocation textureLoc) {
        super(material, TYPE, properties, textureLoc);
    }
}