package com.adonis.createfisheryindustry.block.TrapNozzle;

import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
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

public class TrapNozzleInteractionPointType extends ArmInteractionPointType {

    public TrapNozzleInteractionPointType() {
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return CreateFisheryBlocks.TRAP_NOZZLE.has(state);
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new TrapNozzleInteractionPoint(this, level, pos, state);
    }
}

class TrapNozzleInteractionPoint extends ArmInteractionPoint {
    public TrapNozzleInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
    }

    @Override
    protected Vec3 getInteractionPositionVector() {
        return Vec3.atLowerCornerOf(this.pos).add(0.5, 14 / 16.0, 0.5);
    }


    protected IItemHandler getHandler() {
        if (level.getBlockEntity(pos) instanceof TrapNozzleBlockEntity trapNozzle) {
            return trapNozzle.getInventory();
        }
        return null;
    }


    public ItemStack insert(ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            return stack;
        }
        return ItemHandlerHelper.insertItem(handler, stack, simulate);
    }


    public ItemStack extract(int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            return ItemStack.EMPTY;
        }
        return handler.extractItem(slot, amount, simulate);
    }


    public int getSlotCount() {
        IItemHandler handler = getHandler();
        return handler != null ? handler.getSlots() : 0;
    }
}