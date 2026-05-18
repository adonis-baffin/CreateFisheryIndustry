package com.adonis.createfisheryindustry.block.TrapBearing;

import java.util.ArrayList;
import java.util.List;

import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Clean-room rewrite of the trap-bearing fishing controller.
 *
 * The feature contract is preserved:
 * - counts attached frame traps in valid fluids
 * - derives fishing level from trap count
 * - periodically generates fishing loot into internal storage
 * - supports water and optional lava fishing
 * - exposes state through goggle tooltip and block-entity sync
 */
public class TrapBearingBlockEntity extends MechanicalBearingBlockEntity {
    public static final int INVENTORY_SIZE = 27;

    private static final int TRAPS_PER_LEVEL = 8;
    private static final int MAX_LEVEL = 8;
    private static final int MAX_EFFECTIVE_TRAPS = TRAPS_PER_LEVEL * MAX_LEVEL;
    private static final int OPEN_WATER_LEVEL_THRESHOLD = 4;
    private static final int SCAN_INTERVAL = 20;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    };

    private int totalFrameTraps;
    private int waterFrameCount;
    private int lavaFrameCount;
    private int validFrameCount;
    private int currentFishingLevel;
    private int fishingCooldown;
    private int totalCaught;
    private int successfulAttempts;
    private int scanTimer;

    private boolean isOpenWater;
    private boolean inventoryPaused;

    private FluidMode fluidMode = FluidMode.NONE;
    private final List<BlockPos> activeFramePositions = new ArrayList<>();

    private TrapBearingFakePlayer fakePlayer;
    private FishingHook virtualHook;

    public TrapBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    public IItemHandler getInventory() {
        return inventory;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !running) {
            return;
        }

        ensureVirtualFishingTools();

        if (++scanTimer >= SCAN_INTERVAL) {
            scanTimer = 0;
            refreshTrapState();
        }

        tickFishingCycle();
    }

    @Override
    public void assemble() {
        super.assemble();
        if (level instanceof ServerLevel) {
            ensureVirtualFishingTools();
            refreshTrapState();
            if (running) {
                level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    @Override
    public void disassemble() {
        super.disassemble();
        clearRuntimeState();
    }

    public void setAssembleNextTick(boolean value) {
        assembleNextTick = value;
    }

    @Override
    public void remove() {
        discardVirtualFishingTools();
        super.remove();
    }

    public void dropInventoryContents() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private void ensureVirtualFishingTools() {
        if (!(level instanceof ServerLevel serverLevel) || virtualHook != null) {
            return;
        }

        fakePlayer = new TrapBearingFakePlayer(serverLevel);
        virtualHook = new FishingHook(fakePlayer, serverLevel, 0, 0);
    }

    private void discardVirtualFishingTools() {
        if (virtualHook != null) {
            virtualHook.discard();
            virtualHook = null;
        }
        fakePlayer = null;
    }

    private void clearRuntimeState() {
        totalFrameTraps = 0;
        waterFrameCount = 0;
        lavaFrameCount = 0;
        validFrameCount = 0;
        currentFishingLevel = 0;
        fishingCooldown = 0;
        scanTimer = 0;
        isOpenWater = false;
        inventoryPaused = false;
        fluidMode = FluidMode.NONE;
        activeFramePositions.clear();
        discardVirtualFishingTools();
    }

    private void refreshTrapState() {
        totalFrameTraps = 0;
        waterFrameCount = 0;
        lavaFrameCount = 0;
        validFrameCount = 0;
        activeFramePositions.clear();
        fluidMode = FluidMode.NONE;

        if (movedContraption == null) {
            applyLevel(0);
            return;
        }

        Contraption contraption = movedContraption.getContraption();
        if (contraption == null) {
            applyLevel(0);
            return;
        }

        for (var entry : contraption.getBlocks().entrySet()) {
            if (!(entry.getValue().state().getBlock() instanceof FrameTrapBlock)) {
                continue;
            }

            totalFrameTraps++;

            Vec3 globalCenter = movedContraption.toGlobalVector(Vec3.atCenterOf(entry.getKey()), 0);
            BlockPos worldPos = BlockPos.containing(globalCenter);
            FluidState fluidState = level.getFluidState(worldPos);

            if (fluidState.is(FluidTags.WATER)) {
                waterFrameCount++;
                activeFramePositions.add(worldPos);
            } else if (fluidState.is(FluidTags.LAVA) && CreateFisheryCommonConfig.isLavaFishingEnabled()) {
                lavaFrameCount++;
                activeFramePositions.add(worldPos);
            }
        }

        chooseFluidMode();
        applyLevel(levelFromTrapCount(validFrameCount));
    }

    private void chooseFluidMode() {
        if (lavaFrameCount > 0 && CreateFisheryCommonConfig.isLavaFishingEnabled() && resolveLavaLootTable() != LootTable.EMPTY) {
            fluidMode = FluidMode.LAVA;
            validFrameCount = Math.min(lavaFrameCount, MAX_EFFECTIVE_TRAPS);
            return;
        }

        if (waterFrameCount > 0) {
            fluidMode = FluidMode.WATER;
            validFrameCount = Math.min(waterFrameCount, MAX_EFFECTIVE_TRAPS);
            return;
        }

        fluidMode = FluidMode.NONE;
        validFrameCount = 0;
    }

    private void applyLevel(int newLevel) {
        currentFishingLevel = newLevel;
        isOpenWater = currentFishingLevel >= OPEN_WATER_LEVEL_THRESHOLD;

        if (currentFishingLevel <= 0) {
            fishingCooldown = 0;
            inventoryPaused = false;
            return;
        }

        if (fishingCooldown <= 0) {
            fishingCooldown = nextFishingCooldown(currentFishingLevel);
        }
    }

    private int levelFromTrapCount(int trapCount) {
        if (trapCount < TRAPS_PER_LEVEL) {
            return 0;
        }
        return Math.min(MAX_LEVEL, trapCount / TRAPS_PER_LEVEL);
    }

    private int nextFishingCooldown(int level) {
        int minSeconds = Math.max(1, 9 - level);
        int maxSeconds = level >= OPEN_WATER_LEVEL_THRESHOLD ? 9 : 12;
        int minTicks = minSeconds * 20;
        int maxTicks = maxSeconds * 20;
        if (maxTicks <= minTicks) {
            return minTicks;
        }
        return minTicks + this.level.getRandom().nextInt(maxTicks - minTicks + 1);
    }

    private void tickFishingCycle() {
        if (!CreateFisheryCommonConfig.isFishingEnabled() || currentFishingLevel <= 0 || fluidMode == FluidMode.NONE) {
            return;
        }

        if (isInventoryFull()) {
            inventoryPaused = true;
            return;
        }

        inventoryPaused = false;

        if (fishingCooldown > 0) {
            fishingCooldown--;
            maybeShowWorkingParticles();
            return;
        }

        performFishingAttempt();
        fishingCooldown = nextFishingCooldown(currentFishingLevel);
    }

    private boolean isInventoryFull() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    private void performFishingAttempt() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        successfulAttempts++;

        LootTable lootTable = getActiveLootTable(serverLevel);
        if (lootTable == LootTable.EMPTY) {
            return;
        }

        LootParams params = buildFishingLootContext(serverLevel);
        List<ItemStack> loot = lootTable.getRandomItems(params);

        maybeAddExclusiveLoot(serverLevel, loot);

        if (loot.isEmpty()) {
            return;
        }

        for (ItemStack stack : loot) {
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remainder = ItemHandlerHelper.insertItem(inventory, stack.copy(), false);
            if (!remainder.isEmpty()) {
                spawnItemAtBestLocation(remainder);
            }
            totalCaught += stack.getCount() - remainder.getCount();
        }

        spawnExperienceReward();
        playCatchEffects(serverLevel);
        setChanged();
        sendData();
    }

    private LootParams buildFishingLootContext(ServerLevel serverLevel) {
        ensureVirtualFishingTools();

        Vec3 origin = activeFramePositions.isEmpty() ? Vec3.atCenterOf(worldPosition)
                : Vec3.atCenterOf(activeFramePositions.get(serverLevel.getRandom().nextInt(activeFramePositions.size())));

        virtualHook.setPos(origin);
        virtualHook.openWater = isOpenWater;
        fakePlayer.setPos(origin);

        return new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                .withParameter(LootContextParams.THIS_ENTITY, virtualHook)
                .withParameter(LootContextParams.ATTACKING_ENTITY, fakePlayer)
                .withLuck(isOpenWater ? 2.0F : 0.0F)
                .create(LootContextParamSets.FISHING);
    }

    private LootTable getActiveLootTable(ServerLevel serverLevel) {
        if (fluidMode == FluidMode.LAVA) {
            LootTable lavaTable = resolveLavaLootTable();
            if (lavaTable != LootTable.EMPTY) {
                return lavaTable;
            }
        }

        return serverLevel.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
    }

    private LootTable resolveLavaLootTable() {
        if (!(level instanceof ServerLevel serverLevel) || !CreateFisheryCommonConfig.isLavaFishingEnabled()) {
            return LootTable.EMPTY;
        }

        if (!CreateFisheryCommonConfig.useNDULootTables()) {
            return LootTable.EMPTY;
        }

        try {
            var registries = serverLevel.getServer().reloadableRegistries();
            ResourceKey<LootTable> lavaKey = ResourceKey.create(
                    net.minecraft.core.registries.Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("netherdepthsupgrade", "gameplay/lava_fishing"));
            ResourceKey<LootTable> netherKey = ResourceKey.create(
                    net.minecraft.core.registries.Registries.LOOT_TABLE,
                    ResourceLocation.fromNamespaceAndPath("netherdepthsupgrade", "gameplay/nether_fishing"));

            if (serverLevel.dimension() == Level.NETHER) {
                LootTable netherTable = registries.getLootTable(netherKey);
                if (netherTable != LootTable.EMPTY) {
                    return netherTable;
                }
            }

            return registries.getLootTable(lavaKey);
        } catch (Exception ignored) {
            return LootTable.EMPTY;
        }
    }

    private void maybeAddExclusiveLoot(ServerLevel serverLevel, List<ItemStack> loot) {
        if (fluidMode != FluidMode.WATER) {
            return;
        }

        if (serverLevel.getRandom().nextInt(12) == 0) {
            loot.add(new ItemStack(CreateFisheryItems.WORN_HARPOON.get()));
        }

        if (!isOceanBiome(serverLevel) || serverLevel.getRandom().nextInt(6) != 0) {
            return;
        }

        Item mussel = serverLevel.getRandom().nextBoolean()
                ? CreateFisheryItems.RAW_BLUE_MUSSEL.get()
                : CreateFisheryItems.RAW_MEDITERRANEAN_MUSSEL.get();
        loot.add(new ItemStack(mussel));
    }

    private boolean isOceanBiome(ServerLevel serverLevel) {
        BlockPos samplePos = activeFramePositions.isEmpty() ? worldPosition : activeFramePositions.get(0);
        return serverLevel.getBiome(samplePos).is(BiomeTags.IS_OCEAN);
    }

    private void spawnExperienceReward() {
        Item expNugget = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));
        if (expNugget != null && expNugget != Items.AIR) {
            int count = isOpenWater ? 1 + level.getRandom().nextInt(2) : 1;
            ItemStack remainder = ItemHandlerHelper.insertItem(inventory, new ItemStack(expNugget, count), false);
            if (!remainder.isEmpty()) {
                spawnItemAtBestLocation(remainder);
            }
            return;
        }

        BlockPos pos = randomDropPos();
        int amount = isOpenWater ? 2 + level.getRandom().nextInt(3) : 1;
        level.addFreshEntity(new ExperienceOrb(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, amount));
    }

    private void spawnItemAtBestLocation(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        BlockPos pos = randomDropPos();
        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(0, 0.1, 0);
        level.addFreshEntity(itemEntity);
    }

    private BlockPos randomDropPos() {
        if (activeFramePositions.isEmpty()) {
            return worldPosition;
        }
        return activeFramePositions.get(level.getRandom().nextInt(activeFramePositions.size()));
    }

    private void playCatchEffects(ServerLevel serverLevel) {
        BlockPos pos = randomDropPos();
        if (fluidMode == FluidMode.LAVA) {
            serverLevel.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0.1);
            serverLevel.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 5, 0.2, 0.2, 0.2, 0.05);
            level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 1.2F);
            return;
        }

        serverLevel.sendParticles(ParticleTypes.BUBBLE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void maybeShowWorkingParticles() {
        if (!(level instanceof ServerLevel serverLevel) || activeFramePositions.isEmpty()) {
            return;
        }

        for (BlockPos pos : activeFramePositions) {
            if (serverLevel.getRandom().nextFloat() >= 0.05f) {
                continue;
            }

            if (fluidMode == FluidMode.LAVA) {
                serverLevel.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.15, 0.15, 0.15, 0.01);
            } else {
                serverLevel.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.15, 0.15, 0.15, 0.01);
            }
        }
    }

    private MutableComponent levelText() {
        if (currentFishingLevel <= 0) {
            return CreateLang.translateDirect("trap_bearing.idle");
        }
        if (currentFishingLevel >= MAX_LEVEL) {
            return CreateLang.translateDirect("trap_bearing.max_lvl");
        }
        return CreateLang.translateDirect("trap_bearing.lvl", String.valueOf(currentFishingLevel));
    }

    private MutableComponent workingStateText() {
        if (currentFishingLevel <= 0) {
            return totalFrameTraps > 0
                    ? CreateLang.translateDirect("trap_bearing.state.insufficient")
                    : CreateLang.translateDirect("trap_bearing.state.idle");
        }
        if (fluidMode == FluidMode.LAVA) {
            return CreateLang.translateDirect("trap_bearing.state.lava_fishing");
        }
        if (isOpenWater) {
            return CreateLang.translateDirect("trap_bearing.state.water_fishing_treasure");
        }
        return CreateLang.translateDirect("trap_bearing.state.water_fishing");
    }

    private MutableComponent levelBars() {
        return Component.empty()
                .append(Component.literal("|".repeat(currentFishingLevel)).withStyle(ChatFormatting.DARK_GREEN))
                .append(Component.literal("|".repeat(Math.max(0, MAX_LEVEL - currentFishingLevel))).withStyle(ChatFormatting.DARK_GRAY));
    }

    private int usedSlots() {
        int used = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                used++;
            }
        }
        return used;
    }

    private int totalItems() {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            count += inventory.getStackInSlot(slot).getCount();
        }
        return count;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("tooltip.trap_bearing.header").forGoggles(tooltip);

        if (!running) {
            CreateLang.translate("tooltip.trap_bearing.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip);
        } else {
            CreateLang.translate("tooltip.trap_bearing.status", levelText())
                    .style(ChatFormatting.YELLOW)
                    .forGoggles(tooltip);

            CreateLang.builder()
                    .add(CreateLang.translateDirect("tooltip.trap_bearing.frame_traps").withStyle(ChatFormatting.GRAY))
                    .add(levelBars())
                    .forGoggles(tooltip);

            CreateLang.translate("tooltip.trap_bearing.working_state", workingStateText())
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
        }

        int totalItems = totalItems();
        if (totalItems > 0) {
            CreateLang.translate("tooltip.trap_bearing.storage", totalItems, usedSlots(), INVENTORY_SIZE)
                    .style(inventoryPaused ? ChatFormatting.GOLD : ChatFormatting.GREEN)
                    .forGoggles(tooltip);
        }

        float stressAtBase = calculateStressApplied();
        if (StressImpact.isEnabled() && !Mth.equal(stressAtBase, 0)) {
            tooltip.add(CommonComponents.EMPTY);
            addStressImpactStats(tooltip, stressAtBase);
        }

        return true;
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("TotalFrameTraps", totalFrameTraps);
        compound.putInt("WaterFrameCount", waterFrameCount);
        compound.putInt("LavaFrameCount", lavaFrameCount);
        compound.putInt("ValidFrameCount", validFrameCount);
        compound.putInt("CurrentFishingLevel", currentFishingLevel);
        compound.putInt("FishingCooldown", fishingCooldown);
        compound.putInt("FluidMode", fluidMode.ordinal());
        compound.putInt("TotalCaught", totalCaught);
        compound.putInt("SuccessfulAttempts", successfulAttempts);
        compound.putBoolean("OpenWater", isOpenWater);
        compound.putBoolean("InventoryPaused", inventoryPaused);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        if (wasMoved) {
            super.read(compound, registries, clientPacket);
            return;
        }

        super.read(compound, registries, clientPacket);

        if (compound.contains("Inventory")) {
            inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        }

        totalFrameTraps = compound.getInt("TotalFrameTraps");
        waterFrameCount = compound.getInt("WaterFrameCount");
        lavaFrameCount = compound.getInt("LavaFrameCount");
        validFrameCount = compound.getInt("ValidFrameCount");
        currentFishingLevel = compound.getInt("CurrentFishingLevel");
        fishingCooldown = compound.getInt("FishingCooldown");
        totalCaught = compound.getInt("TotalCaught");
        successfulAttempts = compound.getInt("SuccessfulAttempts");
        isOpenWater = compound.getBoolean("OpenWater");
        inventoryPaused = compound.getBoolean("InventoryPaused");

        int fluidOrdinal = compound.getInt("FluidMode");
        fluidMode = fluidOrdinal >= 0 && fluidOrdinal < FluidMode.values().length
                ? FluidMode.values()[fluidOrdinal]
                : FluidMode.NONE;
    }

    private enum FluidMode {
        NONE,
        WATER,
        LAVA
    }
}
