package com.adonis.createfisheryindustry.block.common;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TrapBlockEntity extends BlockEntity {
    public static final int INVENTORY_SLOTS = 9;
    protected static final double COLLECTION_RANGE = 1.5;
    protected static final double FISH_PROCESSING_RANGE = 1.5;
    protected final ItemStackHandler inventory;
    protected int processingTicks = 0;
    protected int syncCooldown = 0;
    protected boolean queuedSync = false;
    private static final int SYNC_RATE = 8;

    // 添加这个字段用于缓存能力
    private final net.neoforged.neoforge.common.util.Lazy<IItemHandler> itemHandlerLazy;

    public TrapBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = createInventory();
        this.itemHandlerLazy = net.neoforged.neoforge.common.util.Lazy.of(() -> this.inventory);
    }

    protected ItemStackHandler createInventory() {
        return new ItemStackHandler(INVENTORY_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    sendData();
                }
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return true;
            }

            @Override
            public CompoundTag serializeNBT(HolderLookup.Provider provider) {
                CompoundTag nbt = super.serializeNBT(provider);
                return nbt;
            }

            @Override
            public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
                super.deserializeNBT(provider, nbt);
            }
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public boolean insertItem(ItemStack stack) {
        if (stack.isEmpty() || level == null || level.isClientSide()) {
            return false;
        }

        ItemStack remainder = stack.copy();
        for (int i = 0; i < inventory.getSlots(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                sendData();

                return true;
            }
        }

        boolean partialSuccess = stack.getCount() != remainder.getCount();
        if (partialSuccess) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            sendData();
        }
        return partialSuccess;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        CompoundTag inventoryTag = inventory.serializeNBT(provider);
        tag.put("Inventory", inventoryTag);
        tag.putInt("ProcessingTicks", processingTicks);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("Inventory")) {
            CompoundTag inventoryTag = tag.getCompound("Inventory");
            inventory.deserializeNBT(provider, inventoryTag);
        }
        processingTicks = tag.getInt("ProcessingTicks");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        saveAdditional(tag, provider);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider provider) {
        super.onDataPacket(net, pkt, provider);
        handleUpdateTag(pkt.getTag(), provider);
    }

    @Nullable
    public <T> T getCapability(BlockCapability<T, @Nullable Direction> capability, @Nullable Direction side) {
        if (capability == Capabilities.ItemHandler.BLOCK) {
            return (T) inventory;
        }
        return null;
    }

    protected void collectNearbyItems(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        AABB boundingBox = new AABB(getBlockPos()).inflate(COLLECTION_RANGE);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);

        for (ItemEntity itemEntity : items) {
            if (itemEntity == null || !itemEntity.isAlive()) continue;
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                if (insertItem(copy)) {
                    itemEntity.discard();
                } else if (copy.getCount() < stack.getCount()) {
                    itemEntity.setItem(copy);
                }
            }
        }
    }

    protected void tryProcessFish(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        AABB boundingBox = new AABB(getBlockPos()).inflate(FISH_PROCESSING_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;

            boolean isAquatic = entity instanceof WaterAnimal ||
                    (entity.getType() == EntityType.COD ||
                            entity.getType() == EntityType.SALMON ||
                            entity.getType() == EntityType.TROPICAL_FISH ||
                            entity.getType() == EntityType.PUFFERFISH);

            if (isAquatic && entity instanceof Mob mob && mob.getMaxHealth() < 10.0F) {
                try {
                    mob.hurt(level.damageSources().generic(), mob.getMaxHealth() * 2);
                    entity.discard();
                } catch (Exception e) {
                }
            }
        }
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

    public void sendData() {
        if (syncCooldown > 0) {
            queuedSync = true;
            return;
        }
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            setChanged();
        }
        queuedSync = false;
        syncCooldown = SYNC_RATE;
    }

    public void tick() {
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync) {
                sendData();
            }
        }
    }
}
