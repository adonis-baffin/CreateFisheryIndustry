package com.adonis.createfisheryindustry.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

public class BowlSoupItem extends Item {
    public BowlSoupItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, net.minecraft.world.level.Level level, net.minecraft.world.entity.LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);

        // 非创造模式下返回碗
        if (entity instanceof net.minecraft.world.entity.player.Player player && !player.getAbilities().instabuild) {
            if (stack.isEmpty()) {
                return new ItemStack(net.minecraft.world.item.Items.BOWL);
            } else {
                if (!player.getInventory().add(new ItemStack(net.minecraft.world.item.Items.BOWL))) {
                    player.drop(new ItemStack(net.minecraft.world.item.Items.BOWL), false);
                }
            }
        }
        return result;
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return new ItemStack(net.minecraft.world.item.Items.BOWL);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getDrinkingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_DRINK;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getEatingSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_DRINK;
    }
}