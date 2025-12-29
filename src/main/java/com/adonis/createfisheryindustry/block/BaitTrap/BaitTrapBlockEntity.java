package com.adonis.createfisheryindustry.block.BaitTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlockEntity;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.adonis.createfisheryindustry.registry.CreateFisheryTags;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class BaitTrapBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public static final int INVENTORY_SLOTS = 9;
    protected static final double COLLECTION_RANGE = 1.5;
    protected static final int PROCESSING_TIME = 10;

    // Bait duration: 20 minutes = 20 * 60 * 20 = 24000 ticks
    public static final int BAIT_DURATION_TICKS = 24000;

    private static final int AUTO_EXPORT_COOLDOWN = 20;
    private static final int BELT_EXPORT_COOLDOWN = 5;
    private static final int SYNC_RATE = 8;

    protected final ItemStackHandler inventory;
    protected ItemStack currentBait = ItemStack.EMPTY;
    protected int baitTimeRemaining = 0;
    protected int lootPhaseTicks = 0;        // 当前阶段已过去的时间
    protected int extraDelayTicks = 0;       // 当前周期随机出的额外延迟（0 ~ max）
    protected boolean inExtraPhase = false;   // 是否处于额外延迟阶段

    // Cache the current recipe
    @Nullable
    protected BaitTrapRecipe cachedRecipe = null;

    protected int processingTicks = 0;
    protected int autoExportTicks = 0;
    protected int beltExportTicks = 0;
    protected int syncCooldown = 0;
    protected boolean queuedSync = false;

    private final Random random = new Random();

    public BaitTrapBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.BAIT_TRAP.get(), pos, state);
        this.inventory = createInventory();
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
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public boolean isBaited() {
        return !currentBait.isEmpty() && currentBait.getCount() > 0 && baitTimeRemaining > 0;
    }

    public ItemStack getCurrentBait() {
        return currentBait.copy();
    }

    public int getBaitTimeRemaining() {
        return baitTimeRemaining;
    }

    @Nullable
    public BaitTrapRecipe getCachedRecipe() {
        return cachedRecipe;
    }

    /**
     * Find a matching recipe for the given bait item using the RecipeManager.
     * Made public so it can be accessed by the event handler.
     */
    @Nullable
    public BaitTrapRecipe findRecipe(ItemStack bait) {
        if (level == null || bait.isEmpty()) {
            return null;
        }

        SingleRecipeInput input = new SingleRecipeInput(bait);
        Optional<RecipeHolder<BaitTrapRecipe>> recipeHolder = level.getRecipeManager()
                .getRecipeFor(CreateFisheryRecipeTypes.BAIT_TRAP.getType(), input, level);

        if (recipeHolder.isPresent()) {
            BaitTrapRecipe recipe = recipeHolder.get().value();
        }

        return recipeHolder.map(RecipeHolder::value).orElse(null);
    }

    private void resetLootTimer() {
        if (cachedRecipe == null) {
            lootPhaseTicks = 0;
            extraDelayTicks = 0;
            inExtraPhase = false;
            return;
        }

        lootPhaseTicks = 0;
        inExtraPhase = false;

        // 随机生成本次的额外延迟
        int maxExtra = cachedRecipe.getMaxExtraDelayTicks();
        extraDelayTicks = level.getRandom().nextInt(maxExtra + 1); // 包含 maxExtra
    }

    public boolean tryAddBait(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return false;
        }

        BaitTrapRecipe recipe = findRecipe(stack);

        if (recipe == null) {
            return false;
        }

        if (!currentBait.isEmpty() && !ItemStack.isSameItemSameComponents(currentBait, stack)) {
            return false;
        }

        int maxStackSize = stack.getMaxStackSize();
        int spaceLeft = currentBait.isEmpty() ? maxStackSize : (maxStackSize - currentBait.getCount());
        int added = Math.min(spaceLeft, stack.getCount());

        if (added <= 0) {
            return false;
        }

        if (currentBait.isEmpty()) {
            // 首次放入诱饵
            currentBait = stack.copyWithCount(added);
            cachedRecipe = recipe;
            baitTimeRemaining = BAIT_DURATION_TICKS;

            // 新机制：首次放入时立即开始计时周期
            resetLootTimer();
        } else {
            // 仅堆叠，不重置计时（继续当前周期）
            currentBait.grow(added);
        }

        stack.shrink(added);

        updateBlockState();
        setChanged();
        sendData();

        return true;
    }

    public ItemStack removeBait() {
        if (currentBait.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack removed = currentBait.copy();
        currentBait = ItemStack.EMPTY;
        cachedRecipe = null;
        baitTimeRemaining = 0;

        // 新机制：移除诱饵时重置所有计时状态
        resetLootTimer();

        updateBlockState();
        setChanged();
        sendData();

        return removed;
    }

    private void updateBlockState() {
        if (level == null) return;

        BlockState currentState = getBlockState();
        boolean shouldBeBaited = isBaited();

        if (currentState.getValue(BaitTrapBlock.BAITED) != shouldBeBaited) {
            level.setBlock(worldPosition, currentState.setValue(BaitTrapBlock.BAITED, shouldBeBaited), 3);
        }
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

    private boolean hasInventorySpace() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack slotStack = inventory.getStackInSlot(i);
            if (slotStack.isEmpty() || slotStack.getCount() < slotStack.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, BaitTrapBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        boolean isWaterlogged = state.getValue(BlockStateProperties.WATERLOGGED);
        boolean inOverworld = level.dimension() == Level.OVERWORLD;
        boolean environmentValid = true;

        if (be.isBaited()) {
            // 确保配方已缓存
            if (be.cachedRecipe == null) {
                be.cachedRecipe = be.findRecipe(be.currentBait);
            }

            if (be.cachedRecipe != null) {
                boolean recipeRequiresWater = be.cachedRecipe.requiresWater();
                environmentValid = isWaterlogged == recipeRequiresWater;

                // 只有环境正确 + 主世界 才消耗诱饵时间并推进战利品计时
                if (environmentValid && inOverworld) {
                    be.baitTimeRemaining--;

                    // 诱饵时间耗尽：消耗一个诱饵
                    if (be.baitTimeRemaining <= 0) {
                        be.currentBait.shrink(1);
                        if (be.currentBait.getCount() > 0) {
                            // 还有剩余诱饵，补满20分钟并继续当前战利品周期（不重置）
                            be.baitTimeRemaining = BAIT_DURATION_TICKS;
                        } else {
                            // 诱饵完全用完
                            be.currentBait = ItemStack.EMPTY;
                            be.cachedRecipe = null;
                            be.baitTimeRemaining = 0;
                            be.resetLootTimer(); // 重置战利品计时，防止残留状态
                        }
                        be.updateBlockState();
                    }

                    // 诱饵还有剩余时间：推进战利品生成计时（新机制）
                    if (be.hasInventorySpace()) {
                        be.lootPhaseTicks++;

                        int currentTarget = be.inExtraPhase
                                ? be.extraDelayTicks
                                : be.cachedRecipe.getFixedWaitTicks();

                        // 当 currentTarget == 0 时直接通过（防止5级额外延迟为0时卡住）
                        if (currentTarget <= 0 || be.lootPhaseTicks >= currentTarget) {
                            if (be.inExtraPhase) {
                                // 额外延迟阶段结束 → 生成战利品
                                be.generateLoot(serverLevel);
                                // generateLoot 内部已调用 resetLootTimer()，开始全新周期
                            } else {
                                // 固定2分钟阶段结束 → 进入额外延迟阶段
                                be.inExtraPhase = true;
                                be.lootPhaseTicks = 0;
                            }
                        }
                    }
                }
                // else: 环境无效或不在主世界 → 诱饵时间不减少，战利品计时不推进（已在上面不执行相关代码）
            }
        } else {
            // 没有诱饵时确保计时器清零（防御性编程）
            be.resetLootTimer();
        }

        // 以下为原有的自动化功能（不受诱饵状态影响）
        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            be.collectNearbyItems(serverLevel);
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

        be.tickInternal();
        be.setChanged();
        be.sendData();
    }

    private void generateLoot(ServerLevel level) {
        if (cachedRecipe == null) {
            // 尝试重新查找配方（防止意外丢失）
            cachedRecipe = findRecipe(currentBait);
            if (cachedRecipe == null) return;
        }

        // 使用配方进行随机滚结果
        RandomSource randomSource = level.getRandom();
        List<ItemStack> results = cachedRecipe.rollResults(randomSource);

        for (ItemStack result : results) {
            if (!result.isEmpty()) {
                insertItem(result.copy());
            }
        }

        // 重要：生成战利品后立即重置计时器，开始新一轮（固定2分钟 + 新随机额外延迟）
        resetLootTimer();
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

    protected void tryExportToBelts(ServerLevel level) {
        if (level == null || level.isClientSide()) {
            return;
        }

        boolean anyItemMoved = false;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction);

            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(CreateFisheryTags.Blocks.TRAP_EXPORT_BLACKLIST)) {
                continue;
            }

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

                // Try to insert one item at a time to the belt
                ItemStack toInsert = stackInSlot.copy();
                toInsert.setCount(1);

                ItemStack remaining = beltInput.handleInsertion(toInsert, Direction.DOWN, false);

                if (remaining.isEmpty()) {
                    // Successfully inserted, remove from our inventory
                    inventory.extractItem(sourceSlot, 1, false);
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

            // Skip blocks in the export blacklist tag
            BlockState neighborState = level.getBlockState(neighborPos);
            if (neighborState.is(CreateFisheryTags.Blocks.TRAP_EXPORT_BLACKLIST)) {
                continue;
            }

            IItemHandler targetInventory = level.getCapability(Capabilities.ItemHandler.BLOCK, neighborPos, direction.getOpposite());

            if (targetInventory != null) {
                for (int sourceSlot = 0; sourceSlot < inventory.getSlots(); sourceSlot++) {
                    ItemStack stackInSlot = inventory.getStackInSlot(sourceSlot);

                    if (stackInSlot.isEmpty()) {
                        continue;
                    }
                    ItemStack extractedItem = inventory.extractItem(sourceSlot, stackInSlot.getCount(), true);
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

    public void dropBait() {
        if (level instanceof ServerLevel serverLevel && !currentBait.isEmpty()) {
            Vec3 pos = Vec3.atCenterOf(worldPosition);
            ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, currentBait.copy());
            serverLevel.addFreshEntity(itemEntity);
            currentBait = ItemStack.EMPTY;
            baitTimeRemaining = 0;
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
        CreateLang.translate("gui.goggles.bait_trap_contents").forGoggles(tooltip);

        boolean isEmpty = true;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
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

        tooltip.add(Component.empty());

        CreateLang.translate("gui.goggles.bait_trap_status").forGoggles(tooltip);

        if (isBaited()) {
            CreateLang.text("")
                    .add(Component.translatable("gui.createfisheryindustry.bait_trap.bait").withStyle(ChatFormatting.GRAY))
                    .add(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .add(Component.translatable(currentBait.getDescriptionId()).withStyle(ChatFormatting.AQUA))
                    .add(Component.literal(" x" + currentBait.getCount()).withStyle(ChatFormatting.AQUA))  // 新增：显示 count
                    .forGoggles(tooltip, 1);

            if (cachedRecipe == null && level != null) {
                cachedRecipe = findRecipe(currentBait);
            }

            if (cachedRecipe != null) {
                CreateLang.text("")
                        .add(Component.translatable("gui.createfisheryindustry.bait_trap.level").withStyle(ChatFormatting.GRAY))
                        .add(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .add(CreateLang.text(String.valueOf(cachedRecipe.getBaitLevel())).style(ChatFormatting.GREEN))
                        .add(Component.translatable("gui.createfisheryindustry.bait_trap.level_suffix").withStyle(ChatFormatting.GREEN))
                        .forGoggles(tooltip, 1);
            }
            tooltip.add(Component.empty());
            if (isBaited() && cachedRecipe != null) {
                boolean isWaterlogged = getBlockState().getValue(BlockStateProperties.WATERLOGGED);
                boolean requiresWater = cachedRecipe.requiresWater();

                if (isWaterlogged == requiresWater) {
                    CreateLang.translate("gui.createfisheryindustry.bait_trap.active")
                            .style(ChatFormatting.GREEN)
                            .forGoggles(tooltip, 1);
                } else if (requiresWater) {
                    CreateLang.translate("gui.createfisheryindustry.bait_trap.invalid_land")
                            .style(ChatFormatting.RED)
                            .forGoggles(tooltip, 1);
                } else {
                    CreateLang.translate("gui.createfisheryindustry.bait_trap.invalid_water")
                            .style(ChatFormatting.RED)
                            .forGoggles(tooltip, 1);
                }
            }
        } else {
            CreateLang.text("")
                    .add(Component.translatable("gui.createfisheryindustry.bait_trap.no_bait").withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put("Inventory", inventory.serializeNBT(provider));
        tag.putInt("ProcessingTicks", processingTicks);

        if (!currentBait.isEmpty()) {
            tag.put("CurrentBait", currentBait.save(provider));
        }
        tag.putInt("BaitTimeRemaining", baitTimeRemaining);
        tag.putInt("LootPhaseTicks", lootPhaseTicks);
        tag.putInt("ExtraDelayTicks", extraDelayTicks);
        tag.putBoolean("InExtraPhase", inExtraPhase);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(provider, tag.getCompound("Inventory"));
        }
        processingTicks = tag.getInt("ProcessingTicks");

        if (tag.contains("CurrentBait")) {
            currentBait = ItemStack.parseOptional(provider, tag.getCompound("CurrentBait"));
            // Recipe will be re-cached on first tick when level is available
            cachedRecipe = null;
        } else {
            currentBait = ItemStack.EMPTY;
            cachedRecipe = null;
        }
        baitTimeRemaining = tag.getInt("BaitTimeRemaining");
        lootPhaseTicks = tag.getInt("LootPhaseTicks");
        extraDelayTicks = tag.getInt("ExtraDelayTicks");
        inExtraPhase = tag.getBoolean("InExtraPhase");

        if (!tag.contains("LootPhaseTicks")) {
            resetLootTimer();
        }
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
        if (pkt.getTag() != null) {
            handleUpdateTag(pkt.getTag(), provider);
        }
    }

    @Nullable
    public <T> T getCapability(BlockCapability<T, @Nullable Direction> capability, @Nullable Direction side) {
        if (capability == Capabilities.ItemHandler.BLOCK) {
            return (T) inventory;
        }
        return null;
    }
}