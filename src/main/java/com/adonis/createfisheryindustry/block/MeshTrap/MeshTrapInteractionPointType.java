

package com.adonis.createfisheryindustry.block.MeshTrap;

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

public class MeshTrapInteractionPointType extends ArmInteractionPointType {
    public MeshTrapInteractionPointType() {
        CreateFisheryMod.LOGGER.debug("Creating MeshTrapInteractionPointType instance");
    }

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        boolean canCreate = CreateFisheryBlocks.MESH_TRAP.has(state);
        CreateFisheryMod.LOGGER.debug("Checking if point can be created at {}: BlockState={}, CanCreate={}", pos, state, canCreate);
        return canCreate;
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        CreateFisheryMod.LOGGER.debug("Creating MeshTrapInteractionPoint at {}", pos);
        return new MeshTrapInteractionPoint(this, level, pos, state);
    }
}

class MeshTrapInteractionPoint extends ArmInteractionPoint {
    public MeshTrapInteractionPoint(ArmInteractionPointType type, Level level, BlockPos pos, BlockState state) {
        super(type, level, pos, state);
        CreateFisheryMod.LOGGER.debug("Initialized MeshTrapInteractionPoint at {}", pos);
    }

    @Override
    protected Vec3 getInteractionPositionVector() {
        Vec3 pos = Vec3.atLowerCornerOf(this.pos).add(0.5, 14 / 16.0, 0.5);
        CreateFisheryMod.LOGGER.debug("Interaction position for {}: {}", this.pos, pos);
        return pos;
    }

    @Override
    protected IItemHandler getHandler() {
        if (level.getBlockEntity(pos) instanceof MeshTrapBlockEntity meshTrap) {
            IItemHandler handler = meshTrap.getInventory();
            CreateFisheryMod.LOGGER.debug("Found MeshTrapBlockEntity at {}, Inventory: {}", pos, handler != null ? "Present" : "Null");
            return handler;
        }
        CreateFisheryMod.LOGGER.debug("No MeshTrapBlockEntity found at {}", pos);
        return null;
    }

    @Override
    public ItemStack insert(ItemStack stack, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            CreateFisheryMod.LOGGER.debug("Insert failed at {}: No handler available", pos);
            return stack;
        }
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, stack, simulate);
        CreateFisheryMod.LOGGER.debug("Insert at {}: Stack={}, Simulate={}, Remainder={}", pos, stack, simulate, remainder);
        return remainder;
    }

    @Override
    public ItemStack extract(int slot, int amount, boolean simulate) {
        IItemHandler handler = getHandler();
        if (handler == null) {
            CreateFisheryMod.LOGGER.debug("Extract failed at {}: No handler available", pos);
            return ItemStack.EMPTY;
        }
        ItemStack extracted = handler.extractItem(slot, amount, simulate);
        CreateFisheryMod.LOGGER.debug("Extract at {}: Slot={}, Amount={}, Simulate={}, Extracted={}", pos, slot, amount, simulate, extracted);
        return extracted;
    }

    @Override
    public int getSlotCount() {
        IItemHandler handler = getHandler();
        int slotCount = handler != null ? handler.getSlots() : 0;
        CreateFisheryMod.LOGGER.debug("Slot count at {}: {}", pos, slotCount);
        return slotCount;
    }
}
