package com.adonis.createfisheryindustry.block.SmartTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class SmartTrapInteractionPointType extends ArmInteractionPointType {
    public SmartTrapInteractionPointType() {
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        boolean canCreate = CreateFisheryBlocks.SMART_TRAP.has(state);
        return canCreate;
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new SmartTrapInteractionPoint(this, level, pos, state);
    }
}

class SmartTrapInteractionPoint extends ArmInteractionPoint {
    public SmartTrapInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
    }

    @Override
    protected Vec3 getInteractionPositionVector() {
        Vec3 pos = Vec3.atLowerCornerOf(this.pos).add(0.5, 14 / 16.0, 0.5);
        return pos;
    }

    @Override
    protected IItemHandler getHandler() {
        if (level.getBlockEntity(pos) instanceof SmartTrapBlockEntity smartTrap) {
            IItemHandler handler = smartTrap.getInventory();
            return handler;
        }
        return null;
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            return stack;
        }
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, simulate);
        return remainder;
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = handler.extractItem(slot, amount, simulate);
        return extracted;
    }

    @Override
    public int getSlotCount() {
        IItemHandler handler = getHandler();
        int slotCount = handler != null ? handler.getSlots() : 0;
        return slotCount;
    }
}