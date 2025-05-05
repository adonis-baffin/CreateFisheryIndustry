package com.adonis.createfisheryindustry.item;

import com.adonis.createfisheryindustry.registry.CreateFisheryComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.math.Fraction;

import java.util.List;
import java.util.Optional;

public class HarpoonPouchItem extends Item {
    private static final int BAR_COLOR = Mth.color(0.4F, 0.4F, 1.0F);
    private static final int TOOLTIP_MAX_WEIGHT = 4; // 最大 4 根鱼叉

    public HarpoonPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public void verifyComponentsAfterLoad(ItemStack stack) {
        if (!stack.has(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get())) {
            stack.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        }
    }

    public static float getFullnessDisplay(ItemStack stack) {
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        return contents.weight().floatValue();
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY) {
            return false;
        }
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        HarpoonPouchContents.Mutable mutableContents = new HarpoonPouchContents.Mutable(contents);
        ItemStack slotItem = slot.getItem();
        if (slotItem.isEmpty()) {
            ItemStack removed = mutableContents.removeOne();
            if (removed != null) {
                this.playRemoveOneSound(player);
                ItemStack remaining = slot.safeInsert(removed);
                mutableContents.tryInsert(remaining);
            }
        } else if (slotItem.getItem() instanceof HarpoonItem) {
            int inserted = mutableContents.tryTransfer(slot, player);
            if (inserted > 0) {
                this.playInsertSound(player);
            }
        }
        stack.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), mutableContents.toImmutable());
        return true;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (stack.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        HarpoonPouchContents.Mutable mutableContents = new HarpoonPouchContents.Mutable(contents);
        if (other.isEmpty()) {
            ItemStack removed = mutableContents.removeOne();
            if (removed != null) {
                this.playRemoveOneSound(player);
                access.set(removed);
            }
        } else if (other.getItem() instanceof HarpoonItem) {
            int inserted = mutableContents.tryInsert(other);
            if (inserted > 0) {
                this.playInsertSound(player);
            }
        }
        stack.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), mutableContents.toImmutable());
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (dropContents(stack, player)) {
            this.playDropContentsSound(player);
            player.awardStat(Stats.ITEM_USED.get(this));
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }
        return InteractionResultHolder.fail(stack);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        return contents.weight().compareTo(Fraction.ZERO) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        return Math.min(1 + Mth.mulAndTruncate(contents.weight(), 12), 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    private static boolean dropContents(ItemStack stack, Player player) {
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        if (!contents.isEmpty()) {
            stack.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
            if (player instanceof ServerPlayer) {
                contents.itemsCopy().forEach(item -> player.drop(item, true));
            }
            return true;
        }
        return false;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        if (stack.has(DataComponents.HIDE_TOOLTIP) || stack.has(DataComponents.HIDE_ADDITIONAL_TOOLTIP)) {
            return Optional.empty();
        }
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        return Optional.of(new HarpoonPouchTooltip(contents));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        HarpoonPouchContents contents = stack.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        int weight = Mth.mulAndTruncate(contents.weight(), TOOLTIP_MAX_WEIGHT);
        tooltip.add(Component.translatable("item.createfisheryindustry.harpoon_pouch.fullness", weight, TOOLTIP_MAX_WEIGHT));
    }

    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        HarpoonPouchContents contents = itemEntity.getItem().getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
        if (!contents.isEmpty()) {
            itemEntity.getItem().set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
            ItemUtils.onContainerDestroyed(itemEntity, contents.itemsCopy());
        }
    }

    private void playRemoveOneSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playInsertSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }

    private void playDropContentsSound(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.8F + entity.level().getRandom().nextFloat() * 0.4F);
    }
}