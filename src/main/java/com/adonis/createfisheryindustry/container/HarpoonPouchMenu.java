package com.adonis.createfisheryindustry.container;

import com.adonis.createfisheryindustry.item.HarpoonPouchItem;
import com.adonis.createfisheryindustry.registry.CreateFisheryMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class HarpoonPouchMenu extends AbstractContainerMenu {
    private final ItemStack pouchStack;
    private final IItemHandler itemHandler;
    private final int pouchSlot;

    public HarpoonPouchMenu(int windowId, Inventory playerInventory, ItemStack pouchStack) {
        super(CreateFisheryMenuTypes.HARPOON_POUCH.get(), windowId);
        this.pouchStack = pouchStack;
        // 使用 playerInventory.player.level() 获取 Level
        this.itemHandler = HarpoonPouchItem.getItemHandler(pouchStack, playerInventory.player.level());
        this.pouchSlot = findPouchSlot(playerInventory);

        // Pouch inventory slots (3x3 grid)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int index = col + row * 3;
                addSlot(new SlotItemHandler(itemHandler, index, 62 + col * 18, 17 + row * 18));
            }
        }

        // Player inventory slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }

        // Hotbar slots
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    private int findPouchSlot(Inventory playerInventory) {
        for (int i = 0; i < playerInventory.getContainerSize(); i++) {
            if (playerInventory.getItem(i) == pouchStack) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemstack = slotStack.copy();

            if (index < 9) {
                // From pouch to player inventory
                if (!this.moveItemStackTo(slotStack, 9, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(slotStack, 0, 9, false)) {
                // From player inventory to pouch
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return !pouchStack.isEmpty();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickTypeIn, Player player) {
        if (slotId >= 0) {
            // Prevent taking the pouch itself
            if (slotId >= 9 && this.getSlot(slotId).getItem() == this.pouchStack) {
                return;
            }
        }
        super.clicked(slotId, dragType, clickTypeIn, player);
    }
}