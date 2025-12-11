package com.adonis.createfisheryindustry.block.SmartMesh;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlockEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartMeshBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    protected static final int PROCESSING_TIME = 10;
    private static final int AUTO_EXPORT_COOLDOWN = 20; // 每20tick尝试一次输出
    private static final int BELT_EXPORT_COOLDOWN = 5; // 每5tick尝试一次向传送带输出
    protected static final double COLLECTION_RANGE = 1.5; // 收集范围

    private int processingTicks = 0;
    private int autoExportTicks = 0;
    private int beltExportTicks = 0;

    protected ItemStackHandler inventory;
    private final IItemHandler insertionHandler;
    private final IItemHandler extractionHandler;

    protected FilteringBehaviour filtering;

    public SmartMeshBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.SMART_MESH.get(), pos, state);
        this.inventory = createInventory();
        this.insertionHandler = new InsertionOnlyItemHandler(inventory);
        this.extractionHandler = new ExtractionOnlyItemHandler(inventory);
    }

    protected ItemStackHandler createInventory() {
        return new ItemStackHandler(9) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(filtering = new FilteringBehaviour(this, new SmartMeshFilterSlotPositioning()));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SmartMeshBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            be.collectNearbyItems(serverLevel);

            be.setChanged();
            be.sendData();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        be.autoExportTicks++;
        if (be.autoExportTicks >= AUTO_EXPORT_COOLDOWN) {
            be.autoExportTicks = 0;
            be.tryExportItems(serverLevel);
        }

        be.beltExportTicks++;
        if (be.beltExportTicks >= BELT_EXPORT_COOLDOWN) {
            be.beltExportTicks = 0;
            be.tryExportToBelts(serverLevel);
        }
    }

    protected void collectNearbyItems(ServerLevel level) {
        AABB boundingBox = new AABB(getBlockPos()).inflate(COLLECTION_RANGE);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);

        for (ItemEntity itemEntity : items) {
            if (itemEntity == null || !itemEntity.isAlive()) {
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty() && canAcceptItem(stack)) {
                ItemStack copy = stack.copy();
                if (insertItem(copy)) {
                    itemEntity.discard();
                } else if (copy.getCount() < stack.getCount()) {
                    itemEntity.setItem(copy);
                }
            }
        }
    }

    protected boolean insertItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack remainder = stack.copy();
        for (int i = 0; i < inventory.getSlots(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return stack.getCount() != remainder.getCount();
    }

    /**
     * Try to export items to adjacent belts using DirectBeltInputBehaviour
     */
    protected void tryExportToBelts(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean anyItemMoved = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction);

            // Try to find DirectBeltInputBehaviour on the neighbor
            DirectBeltInputBehaviour beltInput = BlockEntityBehaviour.get(level, neighborPos, DirectBeltInputBehaviour.TYPE);

            if (beltInput == null) {
                continue;
            }

            // Skip if belt cannot accept items from this direction
            if (!beltInput.canInsertFromSide(direction.getOpposite())) {
                continue;
            }

            // Try to export items from our inventory to the belt
            for (int sourceSlot = 0; sourceSlot < inventory.getSlots(); sourceSlot++) {
                ItemStack stackInSlot = inventory.getStackInSlot(sourceSlot);

                if (stackInSlot.isEmpty()) {
                    continue;
                }

                // Calculate extraction amount based on filter settings
                int extractionAmount = getExtractionAmount();
                int toExtract = Math.min(1, Math.min(stackInSlot.getCount(), extractionAmount));

                // Try to insert one item at a time to the belt
                ItemStack toInsert = stackInSlot.copy();
                toInsert.setCount(toExtract);

                ItemStack remaining = beltInput.handleInsertion(toInsert, Direction.DOWN, false);

                int inserted = toExtract - remaining.getCount();
                if (inserted > 0) {
                    // Successfully inserted, remove from our inventory
                    inventory.extractItem(sourceSlot, inserted, false);
                    anyItemMoved = true;
                    break;
                }
            }

            if (anyItemMoved) {
                break;
            }
        }

        if (anyItemMoved) {
            setChanged();
            sendData();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    protected void tryExportItems(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean anyItemMoved = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction);

            // Skip belts - they are handled by tryExportToBelts
            DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, neighborPos, DirectBeltInputBehaviour.TYPE);
            if (beltBehaviour != null) {
                continue;
            }

            // Skip other mesh blocks
            if (level.getBlockEntity(neighborPos) instanceof MeshTrapBlockEntity ||
                    level.getBlockEntity(neighborPos) instanceof SmartMeshBlockEntity) {
                continue;
            }

            IItemHandler targetInventory = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());

            if (targetInventory != null) {
                for (int sourceSlot = 0; sourceSlot < inventory.getSlots(); sourceSlot++) {
                    ItemStack stackInSlot = inventory.getStackInSlot(sourceSlot);

                    if (stackInSlot.isEmpty()) {
                        continue;
                    }

                    int extractionAmount = getExtractionAmount();
                    ExtractionCountMode extractionMode = getExtractionMode();

                    ItemStack extractedItem = inventory.extractItem(sourceSlot,
                            Math.min(stackInSlot.getCount(), extractionAmount), true);
                    if (extractedItem.isEmpty()) {
                        continue;
                    }

                    ItemStack remainingItem = insertItemIntoTarget(extractedItem, targetInventory);

                    int actualExtractAmount = extractedItem.getCount() - remainingItem.getCount();
                    if (actualExtractAmount > 0) {
                        inventory.extractItem(sourceSlot, actualExtractAmount, false);
                        anyItemMoved = true;
                        break;
                    }
                }

                if (anyItemMoved) {
                    break;
                }
            }
        }

        if (anyItemMoved) {
            setChanged();
            sendData();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private ItemStack insertItemIntoTarget(ItemStack stack, IItemHandler targetInventory) {
        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < targetInventory.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = targetInventory.insertItem(slot, remaining, false);
        }

        return remaining;
    }

    protected boolean canAcceptItem(ItemStack stack) {
        boolean canAccept = filtering.test(stack);
        return canAccept;
    }

    protected int getExtractionAmount() {
        return filtering.isCountVisible() && !filtering.anyAmount() ? filtering.getAmount() : 64;
    }

    protected ExtractionCountMode getExtractionMode() {
        return filtering.isCountVisible() && !filtering.anyAmount() && !filtering.upTo ?
                ExtractionCountMode.EXACTLY : ExtractionCountMode.UPTO;
    }

    public void dropInventory() {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 pos = Vec3.atCenterOf(worldPosition);
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                    serverLevel.addFreshEntity(itemEntity);
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.smart_mesh_contents").forGoggles(tooltip);

        ItemStackHandler inv = getInventory();
        boolean isEmpty = true;


        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                isEmpty = false;
                CreateLang.text("")
                        .add(Component.translatable(stack.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                        .add(CreateLang.text(" x" + stack.getCount()).style(ChatFormatting.GREEN))
                        .forGoggles(tooltip, 1);
            }
        }

        if (isEmpty) {
            CreateLang.translate("gui.goggles.inventory.empty").forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("ProcessingTicks", processingTicks);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        processingTicks = compound.getInt("ProcessingTicks");
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.SMART_MESH.get(),
                (be, side) -> be.inventory
        );
    }

    private static class InsertionOnlyItemHandler implements IItemHandler {
        private final IItemHandler wrapped;

        public InsertionOnlyItemHandler(IItemHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() {
            return wrapped.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return wrapped.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return wrapped.isItemValid(slot, stack) && canAcceptItem(stack);
        }

        private boolean canAcceptItem(ItemStack stack) {
            if (wrapped instanceof ItemStackHandler) {
                for (int i = 0; i < wrapped.getSlots(); i++) {
                    ItemStack slotStack = wrapped.getStackInSlot(i);
                    if (slotStack.isEmpty() || (ItemStack.isSameItem(slotStack, stack) && slotStack.getCount() < slotStack.getMaxStackSize())) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }

    private static class ExtractionOnlyItemHandler implements IItemHandler {
        private final IItemHandler wrapped;

        public ExtractionOnlyItemHandler(IItemHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() {
            return wrapped.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return wrapped.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return wrapped.isItemValid(slot, stack);
        }
    }
}