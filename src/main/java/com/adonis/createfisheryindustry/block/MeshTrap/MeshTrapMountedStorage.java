package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.registry.CreateFisheryMountedStorageTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MeshTrapMountedStorage extends WrapperMountedItemStorage<MeshTrapMountedStorage.Handler> {

    public static final MapCodec<MeshTrapMountedStorage> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("slots").forGetter(MeshTrapMountedStorage::getSlots),
            Codec.list(CompoundTag.CODEC).fieldOf("items").forGetter(MeshTrapMountedStorage::serializeItemsAsCompoundList)
    ).apply(i, MeshTrapMountedStorage::new));

    private boolean dirty;
    @Nullable
    private HolderLookup.Provider registries;

    protected MeshTrapMountedStorage(MountedItemStorageType<?> type, int slots, List<CompoundTag> items, @Nullable HolderLookup.Provider registries) {
        super(type, new Handler(slots));
        this.registries = registries;
        this.wrapped.setRegistries(registries);

        if (items != null && !items.isEmpty()) {
            this.wrapped.deserializeItemsFromCompoundList(items);
        }

        this.wrapped.onChange = () -> this.dirty = true;
    }

    protected MeshTrapMountedStorage(int slots, List<CompoundTag> items) {
        this(CreateFisheryMountedStorageTypes.MESH_TRAP.get(), slots, items, null);
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MeshTrapBlockEntity meshTrap) {
            ItemStackHandler inventory = meshTrap.getInventory();

            for (int i = 0; i < Math.min(inventory.getSlots(), this.wrapped.getSlots()); i++) {
                inventory.setStackInSlot(i, this.wrapped.getStackInSlot(i).copy());
            }

            meshTrap.setChanged();
        }
    }

    /**
     * 处理玩家右键交互
     * 完全匹配静态捕获网的逻辑：从第一个非空槽位提取物品，显示相同的消息
     */
    @Override
    public boolean handleInteraction(ServerPlayer player, Contraption contraption, StructureBlockInfo info) {
        Handler handler = this.getHandler();

        // 统计总物品数量
        int totalItems = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (!slotStack.isEmpty()) {
                totalItems += slotStack.getCount();
            }
        }

        // 尝试从第一个非空槽位提取物品
        ItemStack extracted = ItemStack.EMPTY;
        for (int i = 0; i < handler.getSlots(); i++) {
            extracted = handler.extractItem(i, 64, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }

        if (!extracted.isEmpty()) {
            // 成功提取物品，给予玩家
            ItemHandlerHelper.giveItemToPlayer(player, extracted);

            // 显示提取信息（与静态捕获网完全相同）
            player.displayClientMessage(
                    Component.translatable("create_fishery.mesh_trap.extracted",
                            extracted.getCount(),
                            Component.translatable(extracted.getDescriptionId())),
                    true  // actionBar = true
            );

            return true;
        } else {
            // 没有提取到物品
            if (totalItems > 0) {
                // 有物品但提取失败
                player.displayClientMessage(
                        Component.translatable("create_fishery.mesh_trap.extraction_failed"),
                        true
                );
            } else {
                // 捕获网是空的
                player.displayClientMessage(
                        Component.translatable("create_fishery.mesh_trap.empty"),
                        true
                );
            }

            return true;
        }
    }

    public int getSlots() {
        return this.wrapped.getSlots();
    }

    public List<CompoundTag> serializeItemsAsCompoundList() {
        return this.wrapped.serializeItemsAsCompoundList();
    }

    public Handler getHandler() {
        return this.wrapped;
    }

    public static MeshTrapMountedStorage fromMeshTrap(MeshTrapBlockEntity meshTrap) {
        ItemStackHandler inventory = meshTrap.getInventory();
        HolderLookup.Provider registries = meshTrap.getLevel().registryAccess();

        List<CompoundTag> items = new ArrayList<>();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                stack.save(registries, itemTag);
                itemTag.putByte("Slot", (byte) i);
                items.add(itemTag);
            }
        }

        return new MeshTrapMountedStorage(
                CreateFisheryMountedStorageTypes.MESH_TRAP.get(),
                inventory.getSlots(),
                items,
                registries
        );
    }

    public static final class Handler extends ItemStackHandler {
        private Runnable onChange = () -> {};
        @Nullable
        private HolderLookup.Provider registries;

        public Handler(int slots) {
            super(slots);
        }

        public void setRegistries(@Nullable HolderLookup.Provider registries) {
            this.registries = registries;
        }

        public void deserializeItemsFromCompoundList(List<CompoundTag> items) {
            for (CompoundTag itemTag : items) {
                int slot = itemTag.getByte("Slot") & 255;

                if (slot >= 0 && slot < this.getSlots()) {
                    ItemStack stack = ItemStack.parseOptional(this.registries, itemTag);
                    this.setStackInSlot(slot, stack);
                }
            }
        }

        public List<CompoundTag> serializeItemsAsCompoundList() {
            List<CompoundTag> items = new ArrayList<>();
            for (int i = 0; i < this.getSlots(); i++) {
                ItemStack stack = this.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    stack.save(this.registries, itemTag);
                    itemTag.putByte("Slot", (byte) i);
                    items.add(itemTag);
                }
            }
            return items;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
        }

        @Override
        protected void onContentsChanged(int slot) {
            this.onChange.run();
        }
    }
}