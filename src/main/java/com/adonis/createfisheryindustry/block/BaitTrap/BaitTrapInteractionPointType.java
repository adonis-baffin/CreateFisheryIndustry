package com.adonis.createfisheryindustry.block.BaitTrap;

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
public class BaitTrapInteractionPointType extends ArmInteractionPointType {
public BaitTrapInteractionPointType() {
}
@Override
public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
boolean canCreate = CreateFisheryBlocks.BAIT_TRAP.has(state);
return canCreate;
}
@Override
public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
return new BaitTrapInteractionPoint(this, level, pos, state);
}
}
class BaitTrapInteractionPoint extends ArmInteractionPoint {
public BaitTrapInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
super(type, level, pos, state);
}
@Override
protected Vec3 getInteractionPositionVector() {
Vec3 pos = Vec3.atLowerCornerOf(this.pos).add(0.5, 14 / 16.0, 0.5);
return pos;
}
protected IItemHandler getHandler() {
if (level.getBlockEntity(pos) instanceof BaitTrapBlockEntity baitTrap) {
IItemHandler handler = baitTrap.getInventory();
return handler;
}
return null;
}
public ItemStack insert(ItemStack stack, boolean simulate) {
IItemHandler handler = getHandler();
if (handler == null) {
return stack;
}
ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, simulate);
return remainder;
}
public ItemStack extract(int slot, int amount, boolean simulate) {
IItemHandler handler = getHandler();
if (handler == null) {
return ItemStack.EMPTY;
}
ItemStack extracted = handler.extractItem(slot, amount, simulate);
return extracted;
}
public int getSlotCount() {
IItemHandler handler = getHandler();
int slotCount = handler != null ? handler.getSlots() : 0;
return slotCount;
}
}