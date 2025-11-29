package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;

import java.util.Optional;

public class CreateFisheryEnchantments {
    
    // 附魔的 ResourceKey
    public static final ResourceKey<Enchantment> EXECUTION = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "execution")
    );

    public static final ResourceKey<Enchantment> BARBED = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "barbed")
    );
    
    /**
     * 获取物品上处决附魔的等级
     */
    public static int getExecutionLevel(ItemStack stack, RegistryAccess registryAccess) {
        return getEnchantmentLevel(stack, registryAccess, EXECUTION);
    }
    
    /**
     * 获取物品上倒刺附魔的等级
     */
    public static int getBarbedLevel(ItemStack stack, RegistryAccess registryAccess) {
        return getEnchantmentLevel(stack, registryAccess, BARBED);
    }
    
    /**
     * 通用方法：获取指定附魔的等级
     */
    private static int getEnchantmentLevel(ItemStack stack, RegistryAccess registryAccess, ResourceKey<Enchantment> enchantmentKey) {
        if (stack.isEmpty()) {
            return 0;
        }
        
        Optional<Holder.Reference<Enchantment>> enchantmentHolder = registryAccess
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(enchantmentKey);
        
        return enchantmentHolder.map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack)).orElse(0);
    }
}