package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.common.EntityCaptureHelper;
import com.adonis.createfisheryindustry.block.common.IItemReceiver;
import com.adonis.createfisheryindustry.block.common.InventoryReceiver;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MeshTrapBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int INVENTORY_SLOTS = 9;
    protected static final double COLLECTION_RANGE = 1.5;
    protected static final double ENTITY_KILL_RANGE = 1.0;
    protected static final int PROCESSING_TIME = 10;

    // 添加自动输出相关常量
    private static final int AUTO_EXPORT_COOLDOWN = 20; // 每20tick尝试一次输出
    private int autoExportTicks = 0;

    // 添加传送带提取相关常量
    private static final int BELT_EXTRACTION_COOLDOWN = 5; // 每5tick尝试一次从传送带提取
    private int beltExtractionTicks = 0;
    private final Map<Direction, TransportedItemStackHandlerBehaviour> beltHandlers = new HashMap<>();

    protected final ItemStackHandler inventory;
    protected int processingTicks = 0;
    protected int syncCooldown = 0;
    protected boolean queuedSync = false;
    private static final int SYNC_RATE = 8;

    private final IItemHandler insertionHandler;
    private final IItemHandler extractionHandler;

    public MeshTrapBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.MESH_TRAP.get(), pos, state);
        this.inventory = createInventory();
        this.insertionHandler = new InsertionOnlyItemHandler(inventory);
        this.extractionHandler = new ExtractionOnlyItemHandler(inventory);
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

    public static void tick(Level level, BlockPos pos, BlockState state, MeshTrapBlockEntity be) {
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
            be.tryProcessEntities(serverLevel);

            be.setChanged();
            be.sendData();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        // 添加自动输出逻辑
        be.autoExportTicks++;
        if (be.autoExportTicks >= AUTO_EXPORT_COOLDOWN) {
            be.autoExportTicks = 0;
            be.tryExportItems(serverLevel);
        }

        // 添加传送带提取逻辑
        be.beltExtractionTicks++;
        if (be.beltExtractionTicks >= BELT_EXTRACTION_COOLDOWN) {
            be.beltExtractionTicks = 0;
            be.extractItemsFromBelts(serverLevel);
        }

        be.tickInternal();
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

    // 添加新方法：从周围的传送带提取物品
    protected void extractItemsFromBelts(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // 检查并更新相邻方向的传送带处理器
        updateBeltHandlers(level);

        // 从所有侧面的传送带提取物品
        for (Direction direction : Direction.values()) {
            // 只从水平方向的传送带提取（侧面）
            if (direction == Direction.UP || direction == Direction.DOWN) {
                continue;
            }

            TransportedItemStackHandlerBehaviour handler = beltHandlers.get(direction);
            if (handler != null) {
                extractFromBelt(handler);
            }
        }
    }

    // 更新周围的传送带处理器缓存
    private void updateBeltHandlers(ServerLevel level) {
        beltHandlers.clear();

        for (Direction direction : Direction.values()) {
            // 排除上下方向，只检查侧面
            if (direction == Direction.UP || direction == Direction.DOWN) {
                continue;
            }

            BlockPos neighborPos = getBlockPos().relative(direction);
            if (level.getBlockEntity(neighborPos) instanceof SmartBlockEntity smartBE) {
                TransportedItemStackHandlerBehaviour behaviour = BlockEntityBehaviour.get(level, neighborPos,
                        TransportedItemStackHandlerBehaviour.TYPE);
                if (behaviour != null) {
                    beltHandlers.put(direction, behaviour);
                }
            }
        }
    }

    // 从单个传送带提取物品
    private void extractFromBelt(TransportedItemStackHandlerBehaviour beltHandler) {
        // 检查我们的库存是否有空间
        boolean hasSpace = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (inventory.getStackInSlot(slot).isEmpty()) {
                hasSpace = true;
                break;
            }
        }

        if (!hasSpace) {
            return; // 如果没有空间就不提取
        }

        // 尝试从传送带上提取物品
        beltHandler.handleCenteredProcessingOnAllItems(.5f, transportedItem -> {
            ItemStack stack = transportedItem.stack;
            if (stack.isEmpty()) {
                return TransportedResult.doNothing();
            }

            // 尝试将物品放入我们的库存
            ItemStack remaining = stack.copy();
            boolean anyInserted = false;

            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack slotStack = inventory.getStackInSlot(slot);

                // 检查是否可以合并或是空槽位
                if (slotStack.isEmpty() || (ItemStack.isSameItem(slotStack, remaining) &&
                        slotStack.getCount() < slotStack.getMaxStackSize())) {

                    ItemStack toInsert = remaining.copy();
                    remaining = inventory.insertItem(slot, toInsert, false);

                    if (remaining.getCount() < toInsert.getCount()) {
                        anyInserted = true;
                        if (remaining.isEmpty()) {
                            break; // 全部插入成功，跳出循环
                        }
                    }
                }
            }

            if (anyInserted) {
                setChanged();
                sendData();
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
                }

                // 如果全部被提取走了，就移除传送带上的物品
                if (remaining.isEmpty()) {
                    return TransportedResult.removeItem();
                } else {
                    // 否则更新传送带上物品的数量
                    TransportedItemStack newTransportedItem = new TransportedItemStack(remaining);
                    newTransportedItem.prevBeltPosition = transportedItem.prevBeltPosition;
                    newTransportedItem.beltPosition = transportedItem.beltPosition;
                    newTransportedItem.insertedFrom = transportedItem.insertedFrom;
                    newTransportedItem.insertedAt = transportedItem.insertedAt;
                    return TransportedResult.convertTo(newTransportedItem);
                }
            }

            return TransportedResult.doNothing();
        });
    }

    // 添加新方法：尝试将物品输出到相邻容器
    protected void tryExportItems(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean anyItemMoved = false;

        // 检查每个方向的相邻方块
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction);

            // 检查是否是传送带，如果是则跳过
            TransportedItemStackHandlerBehaviour beltBehaviour = BlockEntityBehaviour.get(level, neighborPos,
                    TransportedItemStackHandlerBehaviour.TYPE);
            if (beltBehaviour != null) {
                continue; // 跳过传送带
            }

            // 检查目标方块实体类型，如果是 MeshTrap 或 SmartMesh 则跳过
            if (level.getBlockEntity(neighborPos) instanceof MeshTrapBlockEntity ||
                    level.getBlockEntity(neighborPos) instanceof SmartMeshBlockEntity) {
                continue;
            }

            // 使用NeoForge能力系统获取相邻方块的物品处理器
            IItemHandler targetInventory = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());

            if (targetInventory != null) {
                // 从我们的库存中提取物品并插入到目标容器
                for (int sourceSlot = 0; sourceSlot < inventory.getSlots(); sourceSlot++) {
                    ItemStack stackInSlot = inventory.getStackInSlot(sourceSlot);

                    if (stackInSlot.isEmpty()) {
                        continue;
                    }

                    // 尝试每次移动一整组物品
                    ItemStack extractedItem = inventory.extractItem(sourceSlot, stackInSlot.getCount(), true);
                    if (extractedItem.isEmpty()) {
                        continue;
                    }

                    // 找到目标容器中可以接收物品的位置
                    ItemStack remainingItem = insertItemIntoTarget(extractedItem, targetInventory);

                    // 计算实际可以移动的数量
                    int actualExtractAmount = extractedItem.getCount() - remainingItem.getCount();
                    if (actualExtractAmount > 0) {
                        // 实际从源提取
                        inventory.extractItem(sourceSlot, actualExtractAmount, false);
                        anyItemMoved = true;
                        // 只输出一种物品后中断，避免一次性输出过多
                        break;
                    }
                }

                // 如果已经移动了物品，暂时不检查其他方向
                if (anyItemMoved) {
                    break;
                }
            }
        }

        // 如果有物品被移动，更新方块实体状态
        if (anyItemMoved) {
            setChanged();
            sendData();
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    // 辅助方法：尝试将物品插入目标容器
    private ItemStack insertItemIntoTarget(ItemStack stack, IItemHandler targetInventory) {
        ItemStack remaining = stack.copy();

        // 遍历目标容器的所有槽位
        for (int slot = 0; slot < targetInventory.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = targetInventory.insertItem(slot, remaining, false);
        }

        return remaining;
    }

    protected void tryProcessEntities(ServerLevel level) {
        CreateFisheryCommonConfig.refreshCache();

        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_KILL_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (!entity.isAlive()) {
                continue;
            }

            // 使用统一的实体筛选逻辑
            if (EntityCaptureHelper.shouldCaptureEntity(entity)) {
                processEntityDrops(level, (Mob) entity);
            }
        }
    }

    private void processEntityDrops(ServerLevel level, Mob mob) {
        // 创建物品接收器
        IItemReceiver receiver = new InventoryReceiver(this.inventory, level, mob.position());

        // 使用统一的捕获逻辑
        EntityCaptureHelper.captureEntity(mob, level, getBlockPos(), receiver);

        // 更新方块实体状态
        setChanged();
        sendData();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
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

    public void tickInternal() {
        if (syncCooldown > 0) {
            syncCooldown--;
            if (syncCooldown == 0 && queuedSync) {
                sendData();
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.mesh_trap_contents").forGoggles(tooltip);

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
            return (T) inventory; // 所有方向都返回完整库存，允许输入和输出
        }
        return null;
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
            return wrapped.isItemValid(slot, stack);
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