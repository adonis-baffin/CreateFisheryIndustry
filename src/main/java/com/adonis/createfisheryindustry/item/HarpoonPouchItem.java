package com.adonis.createfisheryindustry.item;

import com.adonis.createfisheryindustry.container.HarpoonPouchMenu;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.List;

public class HarpoonPouchItem extends Item {
    private static final String TAG_ITEMS = "Items";
    private static final int INVENTORY_SIZE = 9;

    public HarpoonPouchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, playerInventory, playerEntity) ->
                            new HarpoonPouchMenu(windowId, playerInventory, stack),
                    Component.translatable("container.createfisheryindustry.harpoon_pouch")
            ));
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable TooltipContext tooltipContext, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, tooltipContext, tooltip, flag);

        // 需要 Level 来创建 PouchItemHandler
        IItemHandler itemHandler = getItemHandler(stack, tooltipContext.level());
        int itemCount = 0;

        for (int i = 0; i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                itemCount++;
            }
        }

        tooltip.add(Component.translatable("item.createfisheryindustry.harpoon_pouch.tooltip", itemCount, INVENTORY_SIZE));
    }

    public static IItemHandler getItemHandler(ItemStack stack, @Nullable Level level) {
        // 如果 level 为 null，返回空的 ItemHandler 防止崩溃
        if (level == null) {
            return new ItemStackHandler(INVENTORY_SIZE);
        }
        return stack.getCapability(Capabilities.ItemHandler.ITEM);
    }

    public static class PouchItemHandler extends ItemStackHandler implements INBTSerializable<CompoundTag> {
        private final ItemStack container;
        private final Level level;

        public PouchItemHandler(ItemStack container, Level level) {
            super(INVENTORY_SIZE);
            this.container = container;
            this.level = level;
        }

        @Override
        protected void onContentsChanged(int slot) {
            HolderLookup.Provider provider = level.registryAccess();
            CompoundTag tag = serializeNBT(provider);
            container.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }


        public CompoundTag serializeNBT() {
            return serializeNBT(level.registryAccess());
        }


        public void deserializeNBT(CompoundTag nbt) {
            deserializeNBT(level.registryAccess(), nbt);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() != CreateFisheryItems.HARPOON_POUCH.get();
        }
    }
}