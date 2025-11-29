package com.adonis.createfisheryindustry.item;

import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class HarpoonPouchContents implements TooltipComponent {
    public static final HarpoonPouchContents EMPTY = new HarpoonPouchContents(List.of());
    public static final Codec<HarpoonPouchContents> CODEC = ItemStack.CODEC.listOf().xmap(HarpoonPouchContents::new, contents -> contents.items);
    public static final StreamCodec<RegistryFriendlyByteBuf, HarpoonPouchContents> STREAM_CODEC =
            ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).map(HarpoonPouchContents::new, contents -> contents.items);
    private static final Fraction HARPOON_WEIGHT = Fraction.getFraction(1, 4); // 每根鱼叉权重1/4
    private static final int MAX_SLOTS = 4; // 最多4根鱼叉
    private final List<ItemStack> items;
    private final Fraction weight;

    public HarpoonPouchContents(List<ItemStack> items) {
        this(items, computeContentWeight(items));
    }

    private HarpoonPouchContents(List<ItemStack> items, Fraction weight) {
        this.items = items;
        this.weight = weight;
    }

    private static Fraction computeContentWeight(List<ItemStack> content) {
        Fraction fraction = Fraction.ZERO;
        for (ItemStack stack : content) {
            if (Mutable.isValidItem(stack)) {
                fraction = fraction.add(HARPOON_WEIGHT.multiplyBy(Fraction.getFraction(stack.getCount(), 1)));
            }
        }
        return fraction;
    }

    public static Fraction getWeight(ItemStack stack) {
        if (stack.getItem() == CreateFisheryItems.HARPOON.get()) {
            return HARPOON_WEIGHT;
        }
        return Fraction.ZERO; // 仅鱼叉有权重
    }

    public ItemStack getItemUnsafe(int index) {
        return this.items.get(index);
    }

    public Stream<ItemStack> itemCopyStream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Iterable<ItemStack> items() {
        return this.items;
    }

    public Iterable<ItemStack> itemsCopy() {
        return Lists.transform(this.items, ItemStack::copy);
    }

    public int size() {
        return this.items.size();
    }

    public Fraction weight() {
        return this.weight;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof HarpoonPouchContents contents)) return false;
        return this.weight.equals(contents.weight) && ItemStack.listMatches(this.items, contents.items);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashStackList(this.items);
    }

    @Override
    public String toString() {
        return "HarpoonPouchContents" + this.items;
    }

    public static class Mutable {
        private final List<ItemStack> items;
        private Fraction weight;

        public Mutable(HarpoonPouchContents contents) {
            this.items = new ArrayList<>(contents.items);
            this.weight = contents.weight;
        }

        public Mutable clearItems() {
            this.items.clear();
            this.weight = Fraction.ZERO;
            return this;
        }

        private int findStackIndex(ItemStack stack) {
            if (!stack.isStackable()) return -1;
            for (int i = 0; i < this.items.size(); i++) {
                if (ItemStack.isSameItemSameComponents(this.items.get(i), stack)) {
                    return i;
                }
            }
            return -1;
        }

        private int getMaxAmountToAdd(ItemStack stack) {
            if (!isValidItem(stack)) {
                return 0;
            }
            int remainingSlots = MAX_SLOTS - this.items.size();
            return Math.min(remainingSlots, stack.getCount());
        }

        private static boolean isValidItem(ItemStack stack) {
            return stack.getItem() == CreateFisheryItems.HARPOON.get() ||
                    stack.getItem() == net.minecraft.world.item.Items.TRIDENT;
        }

        public int tryInsert(ItemStack stack) {
            if (stack.isEmpty() || !isValidItem(stack)) {
                return 0;
            }
            int maxAdd = Math.min(stack.getCount(), getMaxAmountToAdd(stack));
            if (maxAdd == 0) return 0;

            this.weight = this.weight.add(HARPOON_WEIGHT.multiplyBy(Fraction.getFraction(maxAdd, 1)));
            int stackIndex = findStackIndex(stack);
            if (stackIndex != -1) {
                ItemStack existing = this.items.remove(stackIndex);
                ItemStack newStack = existing.copyWithCount(existing.getCount() + maxAdd);
                stack.shrink(maxAdd);
                this.items.add(0, newStack);
            } else {
                this.items.add(0, stack.split(maxAdd));
            }
            return maxAdd;
        }

        public int tryTransfer(Slot slot, Player player) {
            ItemStack stack = slot.getItem();
            int maxAdd = getMaxAmountToAdd(stack);
            return tryInsert(slot.safeTake(stack.getCount(), maxAdd, player));
        }

        @Nullable
        public ItemStack removeOne() {
            if (this.items.isEmpty()) return null;
            ItemStack stack = this.items.remove(0).copy();
            this.weight = this.weight.subtract(HARPOON_WEIGHT);
            return stack;
        }

        public Fraction weight() {
            return this.weight;
        }

        public HarpoonPouchContents toImmutable() {
            return new HarpoonPouchContents(List.copyOf(this.items), this.weight);
        }
    }
}