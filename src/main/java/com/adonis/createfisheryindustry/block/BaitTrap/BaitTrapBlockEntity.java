package com.adonis.createfisheryindustry.block.BaitTrap;

import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlockEntity;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
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

    // Bait duration: 12 minutes = 12 * 60 * 20 = 14400 ticks
    public static final int BAIT_DURATION_TICKS = 14400;

    private static final int AUTO_EXPORT_COOLDOWN = 20;
    private static final int BELT_EXPORT_COOLDOWN = 5;
    private static final int PARTICLE_COOLDOWN = 40;
    private static final int SYNC_RATE = 8;

    protected final ItemStackHandler inventory;
    protected ItemStack currentBait = ItemStack.EMPTY;
    protected int baitTimeRemaining = 0;
    protected int lootGenerationTicks = 0;
    protected int currentLootInterval = 0;

    // Cache the current recipe
    @Nullable
    protected BaitTrapRecipe cachedRecipe = null;

    protected int processingTicks = 0;
    protected int autoExportTicks = 0;
    protected int beltExportTicks = 0;
    protected int particleTicks = 0;
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
        return !currentBait.isEmpty() && baitTimeRemaining > 0;
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
     */
    @Nullable
    protected BaitTrapRecipe findRecipe(ItemStack bait) {
        if (level == null || bait.isEmpty()) return null;

        SingleRecipeInput input = new SingleRecipeInput(bait);
        Optional<RecipeHolder<BaitTrapRecipe>> recipeHolder = level.getRecipeManager()
                .getRecipeFor(CreateFisheryRecipeTypes.BAIT_TRAP.getType(), input, level);

        return recipeHolder.map(RecipeHolder::value).orElse(null);
    }

    public boolean tryAddBait(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return false;
        }

        BaitTrapRecipe recipe = findRecipe(stack);
        if (recipe == null) {
            return false;
        }

        // If already baited, don't allow adding new bait
        if (isBaited()) {
            return false;
        }

        currentBait = stack.copyWithCount(1);
        cachedRecipe = recipe;
        baitTimeRemaining = BAIT_DURATION_TICKS;
        lootGenerationTicks = 0;
        currentLootInterval = calculateLootInterval(recipe);

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
        lootGenerationTicks = 0;
        currentLootInterval = 0;

        updateBlockState();
        setChanged();
        sendData();

        return removed;
    }

    private int calculateLootInterval(BaitTrapRecipe recipe) {
        int minTicks = recipe.getMinLootTimeTicks();
        int maxTicks = recipe.getMaxLootTimeTicks();

        return minTicks + random.nextInt(maxTicks - minTicks + 1);
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
        if (level.isClientSide()) {
            be.clientTick();
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Process bait and loot generation
        if (be.isBaited()) {
            be.baitTimeRemaining--;

            if (be.baitTimeRemaining <= 0) {
                be.currentBait = ItemStack.EMPTY;
                be.cachedRecipe = null;
                be.baitTimeRemaining = 0;
                be.lootGenerationTicks = 0;
                be.currentLootInterval = 0;
                be.updateBlockState();
                be.setChanged();
                be.sendData();
            } else {
                // Try to generate loot
                be.lootGenerationTicks++;
                if (be.lootGenerationTicks >= be.currentLootInterval && be.hasInventorySpace()) {
                    be.generateLoot(serverLevel);
                    be.lootGenerationTicks = 0;

                    // Recalculate interval for next loot
                    if (be.cachedRecipe != null) {
                        be.currentLootInterval = be.calculateLootInterval(be.cachedRecipe);
                    }
                }
            }
        }

        // Collect nearby items (like MeshTrap)
        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            be.collectNearbyItems(serverLevel);
            be.setChanged();
            be.sendData();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        // Auto export to containers
        be.autoExportTicks++;
        if (be.autoExportTicks >= AUTO_EXPORT_COOLDOWN) {
            be.autoExportTicks = 0;
            be.tryExportItems(serverLevel);
        }

        // Export to belts
        be.beltExportTicks++;
        if (be.beltExportTicks >= BELT_EXPORT_COOLDOWN) {
            be.beltExportTicks = 0;
            be.tryExportToBelts(serverLevel);
        }

        be.tickInternal();
    }

    private void clientTick() {
        if (!isBaited()) return;

        particleTicks++;
        if (particleTicks >= PARTICLE_COOLDOWN) {
            particleTicks = 0;
            spawnBaitParticles();
        }
    }

    private void spawnBaitParticles() {
        if (level == null || !level.isClientSide()) return;

        double x = worldPosition.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double y = worldPosition.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.3;
        double z = worldPosition.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;

        level.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y, z, 0, 0.02, 0);
    }

    private void generateLoot(ServerLevel level) {
        if (cachedRecipe == null) {
            // Try to find recipe again (in case it wasn't cached)
            cachedRecipe = findRecipe(currentBait);
            if (cachedRecipe == null) return;
        }

        // Roll for results using the recipe's roll system
        RandomSource randomSource = level.getRandom();
        List<ItemStack> results = cachedRecipe.rollResults(randomSource);

        for (ItemStack result : results) {
            if (!result.isEmpty()) {
                insertItem(result.copy());
            }
        }
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

            DirectBeltInputBehaviour beltInput = BlockEntityBehaviour.get(level, neighborPos, DirectBeltInputBehaviour.TYPE);

            if (beltInput == null) {
                continue;
            }

            for (int sourceSlot = 0; sourceSlot < inventory.getSlots(); sourceSlot++) {
                ItemStack stackInSlot = inventory.getStackInSlot(sourceSlot);

                if (stackInSlot.isEmpty()) {
                    continue;
                }

                ItemStack toInsert = stackInSlot.copy();
                toInsert.setCount(1);

                ItemStack remaining = beltInput.handleInsertion(toInsert, Direction.DOWN, false);

                if (remaining.isEmpty()) {
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

            // Skip belts - handled by tryExportToBelts
            DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, neighborPos, DirectBeltInputBehaviour.TYPE);
            if (beltBehaviour != null) {
                continue;
            }

            // Skip other trap blocks
            BlockEntity neighborBE = level.getBlockEntity(neighborPos);
            if (neighborBE instanceof MeshTrapBlockEntity ||
                    neighborBE instanceof SmartMeshBlockEntity ||
                    neighborBE instanceof BaitTrapBlockEntity) {
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

        // Show bait status
        if (isBaited()) {
            int secondsRemaining = baitTimeRemaining / 20;
            int minutes = secondsRemaining / 60;
            int seconds = secondsRemaining % 60;

            CreateLang.text("")
                    .add(Component.translatable("gui.createfisheryindustry.bait_trap.bait").withStyle(ChatFormatting.GOLD))
                    .add(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .add(Component.translatable(currentBait.getDescriptionId()).withStyle(ChatFormatting.YELLOW))
                    .forGoggles(tooltip, 1);

            CreateLang.text("")
                    .add(Component.translatable("gui.createfisheryindustry.bait_trap.time_remaining").withStyle(ChatFormatting.GRAY))
                    .add(Component.literal(String.format(" %d:%02d", minutes, seconds)).withStyle(ChatFormatting.GREEN))
                    .forGoggles(tooltip, 1);

            // Show bait level if recipe is cached
            if (cachedRecipe != null) {
                CreateLang.text("")
                        .add(Component.translatable("gui.createfisheryindustry.bait_trap.level", cachedRecipe.getBaitLevel()).withStyle(ChatFormatting.AQUA))
                        .forGoggles(tooltip, 1);
            }
        } else {
            CreateLang.text("")
                    .add(Component.translatable("gui.createfisheryindustry.bait_trap.no_bait").withStyle(ChatFormatting.DARK_GRAY))
                    .forGoggles(tooltip, 1);
        }

        // Show inventory contents
        CreateLang.translate("gui.goggles.bait_trap_inventory").forGoggles(tooltip);

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
        tag.putInt("LootGenerationTicks", lootGenerationTicks);
        tag.putInt("CurrentLootInterval", currentLootInterval);
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
        lootGenerationTicks = tag.getInt("LootGenerationTicks");
        currentLootInterval = tag.getInt("CurrentLootInterval");
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