package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
// import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities; // Not directly needed here for capability
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.math.VecHelper; // Assuming this is a valid import for your setup
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
// import net.neoforged.neoforge.capabilities.Capabilities; // Not needed for getCapability here
import net.neoforged.neoforge.items.IItemHandler; // Keep for itemHandler field and inner class

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class MechanicalPeelerBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    public ProcessingInventory inventory;
    private ItemStack playEvent;
    // 使 itemHandler 可被外部（如 RegisterCapabilitiesEvent）访问
    public final IItemHandler itemHandler; // Made public final

    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new ProcessingInventory(this::start).withSlotLimit(true); // Create uses 1 slot typically for processing
        inventory.remainingTime = -1;
        playEvent = ItemStack.EMPTY;
        itemHandler = new ProcessingInventoryItemHandler(inventory);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnelsWhen(this::canProcess));
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("Inventory", inventory.serializeNBT(registries));
        super.write(compound, registries, clientPacket);

        if (!clientPacket || playEvent.isEmpty())
            return;
        compound.put("PlayEvent", playEvent.saveOptional(registries));
        playEvent = ItemStack.EMPTY;
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        if (compound.contains("PlayEvent"))
            playEvent = ItemStack.parseOptional(registries, compound.getCompound("PlayEvent"));
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(.125f);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (getSpeed() == 0)
            return;

        if (!playEvent.isEmpty()) {
            boolean isWood = false;
            Item item = playEvent.getItem();
            if (item instanceof BlockItem) {
                Block block = ((BlockItem) item).getBlock();
                isWood = block.getSoundType(block.defaultBlockState(), level, worldPosition, null) == SoundType.WOOD;
            }
            spawnEventParticles(playEvent);
            playEvent = ItemStack.EMPTY;
            if (!isWood)
                AllSoundEvents.SAW_ACTIVATE_STONE.playAt(level, worldPosition, 3, 1, true);
            else
                AllSoundEvents.SAW_ACTIVATE_WOOD.playAt(level, worldPosition, 3, 1, true);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!canProcess())
            return;
        if (getSpeed() == 0)
            return;
        if (inventory.remainingTime == -1) {
            if (!inventory.isEmpty() && !inventory.appliedRecipe)
                start(inventory.getStackInSlot(0));
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 32, 1, 128);
        inventory.remainingTime -= processingSpeed;

        if (inventory.remainingTime > 0)
            spawnParticles(inventory.getStackInSlot(0));

        if (inventory.remainingTime < 5 && !inventory.appliedRecipe) {
            if (level.isClientSide && !isVirtual())
                return;
            playEvent = inventory.getStackInSlot(0);
            applyRecipe();
            inventory.appliedRecipe = true;
            inventory.recipeDuration = 20; // Duration for output to sit before auto-eject
            inventory.remainingTime = 20;
            sendData();
            return;
        }

        Vec3 itemMovement = getItemMovementVec();
        Direction itemMovementFacing = Direction.getNearest(itemMovement.x, itemMovement.y, itemMovement.z);
        if (inventory.remainingTime > 0)
            return;
        inventory.remainingTime = 0;

        // Output handling logic (belt funnel, adjacent belts, world entity)
        for (int slot = 0; slot < inventory.getSlots(); slot++) { // Iterate all slots for output
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;

            // Try DirectBeltInputBehaviour for funnel
            ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
                    .tryExportingToBeltFunnel(stack, itemMovementFacing.getOpposite(), false);
            if (tryExportingToBeltFunnel != null) {
                if (tryExportingToBeltFunnel.getCount() != stack.getCount()) {
                    inventory.setStackInSlot(slot, tryExportingToBeltFunnel);
                    notifyUpdate();
                    // Don't return yet, allow other output slots to try
                    if (tryExportingToBeltFunnel.isEmpty()) continue; // Fully exported this slot
                    else return; // Partially exported, wait for next tick for this slot
                }
                if (!tryExportingToBeltFunnel.isEmpty()) // Couldn't export via funnel
                    return; // Block further output attempts for this tick if funnel failed for this stack
            }
        }

        // Try adjacent belt
        BlockPos nextPos = worldPosition.offset(BlockPos.containing(itemMovement));
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);
        if (behaviour != null) {
            boolean changed = false;
            if (!behaviour.canInsertFromSide(itemMovementFacing))
                return; // Can't insert to this belt from this side

            if (level.isClientSide && !isVirtual())
                return;

            for (int slot = 0; slot < inventory.getSlots(); slot++) { // Iterate all slots for output
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty())
                    continue;

                ItemStack remainder = behaviour.handleInsertion(stack, itemMovementFacing, false);
                if (!ItemStack.matches(remainder, stack)) { // Use ItemStack.matches for robust comparison
                    inventory.setStackInSlot(slot, remainder);
                    changed = true;
                    if (remainder.isEmpty()) continue; // Fully inserted this slot
                    else break; // Partially inserted, stop for this tick
                }
            }
            if (changed) {
                setChanged();
                sendData();
            }
            if (inventory.isEmpty()) { // If all items successfully moved to belt
                inventory.remainingTime = -1; // Reset for next input
            }
            return; // Whether changed or not, if belt behaviour exists, we've handled output for this tick
        }


        // Eject to world as last resort
        Vec3 outPos = VecHelper.getCenterOf(worldPosition)
                .add(itemMovement.scale(.5f)
                        .add(0, .5, 0));
        Vec3 outMotion = itemMovement.scale(.0625)
                .add(0, .125, 0);

        boolean ejectedItems = false;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;
            ItemEntity entityIn = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stack);
            entityIn.setDeltaMovement(outMotion);
            level.addFreshEntity(entityIn);
            inventory.setStackInSlot(slot, ItemStack.EMPTY); // Clear the slot after ejecting
            ejectedItems = true;
        }

        if (ejectedItems) {
            inventory.clear(); // Ensure all slots are cleared if any item was ejected
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            inventory.remainingTime = -1;
            sendData();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        // invalidateCapabilities(); // NeoForge handles this differently, usually not needed to call manually
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
    }

    protected void spawnEventParticles(ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null)
            return;

        ParticleOptions particleData;
        if (stack.getItem() instanceof BlockItem blockItem)
            particleData = new BlockParticleOption(ParticleTypes.BLOCK, blockItem.getBlock().defaultBlockState());
        else
            particleData = new ItemParticleOption(ParticleTypes.ITEM, stack);

        RandomSource r = level.random;
        Vec3 v = VecHelper.getCenterOf(this.worldPosition).add(0, 5 / 16f, 0);
        for (int i = 0; i < 10; i++) {
            Vec3 randomOffset = VecHelper.offsetRandomly(Vec3.ZERO, r, 0.125f);
            Vec3 m = randomOffset.add(0, 0.25f, 0);
            level.addParticle(particleData, v.x, v.y, v.z, m.x, m.y, m.z);
        }
    }

    protected void spawnParticles(ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null)
            return;

        ParticleOptions particleData;
        float particleSpeed = 0.125f;
        if (stack.getItem() instanceof BlockItem blockItem) {
            particleData = new BlockParticleOption(ParticleTypes.BLOCK, blockItem.getBlock().defaultBlockState());
            particleSpeed = 0.2f;
        } else {
            particleData = new ItemParticleOption(ParticleTypes.ITEM, stack);
        }

        RandomSource r = level.random;
        Vec3 itemMovementVec = getItemMovementVec();
        Vec3 center = VecHelper.getCenterOf(this.worldPosition);

        float processingOffsetRatio = 0.5f;
        if (inventory.recipeDuration != 0) {
            processingOffsetRatio = 1f - (float) inventory.remainingTime / inventory.recipeDuration;
        }

        float displayOffset = (processingOffsetRatio - 0.5f);
        if (inventory.appliedRecipe) {
            displayOffset = (processingOffsetRatio * 0.5f);
        } else {
            displayOffset = (1.0f - processingOffsetRatio) * 0.5f;
        }

        Vec3 particlePos = center.add(itemMovementVec.scale(displayOffset));
        Vec3 randomMotionOffset = VecHelper.offsetRandomly(Vec3.ZERO, r, particleSpeed * 0.25f);
        Vec3 particleMotion = itemMovementVec.scale(-particleSpeed * 0.5f)
                .add(randomMotionOffset);
        particleMotion = particleMotion.add(0, r.nextFloat() * particleSpeed * 0.5f, 0);

        level.addParticle(particleData, particlePos.x, particlePos.y + 0.3, particlePos.z,
                particleMotion.x, particleMotion.y, particleMotion.z);
    }

    public Vec3 getItemMovementVec() {
        Direction facing = getBlockState().getValue(MechanicalPeelerBlock.FACING);
        if (facing == Direction.UP) { // Ensure it's facing up
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            int direction = getSpeed() < 0 ? 1 : -1; // Determine based on rotation direction
            // This defines local X/Z based on the AXIS_ALONG_FIRST_COORDINATE property
            return new Vec3(direction * (alongLocalX ? 1 : 0), 0, direction * (alongLocalX ? 0 : 1));
        }
        // If not facing UP, or some other configuration, default to a sensible output or ZERO.
        // For a machine that processes on top and outputs sideways, this needs to match.
        // Example: if output is always to local +X when FACING is UP
        // return new Vec3(1, 0, 0); // This needs to be relative to the block's orientation
        // The original logic for itemMovementVec seems fine if the block's FACING and AXIS props are set up for it.
        // Let's assume it implies output direction when facing UP.
        return Vec3.ZERO; // Fallback if not facing UP
    }

    private void applyRecipe() {
        ItemStack inputStack = inventory.getStackInSlot(0); // Input is always slot 0
        if (inputStack.isEmpty()) return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(inputStack);
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(recipeInput);

        // Clear the input slot regardless of recipe match for now
        // Proper handling would be to consume input only if recipe matches.
        // inventory.setStackInSlot(0, ItemStack.EMPTY); // Moved after recipe processing

        if (recipeHolder.isEmpty()) {
            // Handle no recipe: maybe eject input or mark as no-recipe
            inventory.clear(); // Clears all, including input
            inventory.remainingTime = -1; // Reset
            sendData();
            return;
        }

        PeelingRecipe recipe = recipeHolder.get().value();
        inventory.extractItem(0, 1, false); // Consume one item from input slot 0

        List<ItemStack> currentRollResults = recipe.rollResults();
        List<ItemStack> totalResults = new LinkedList<>();
        for (ItemStack stack : currentRollResults) {
            if (!stack.isEmpty()) {
                ItemHelper.addToList(stack.copy(), totalResults);
            }
        }

        // Output results to available slots (typically starting from slot 1 if slot 0 is input)
        // ProcessingInventory by default has 1 slot. You might need to adjust this.
        // If ProcessingInventory is used for both input and output, then after processing,
        // the results replace the input.
        // For this machine, it seems like input is slot 0, outputs are in other slots.
        // We need to make sure ProcessingInventory has enough slots.
        // Let's assume ProcessingInventory is reconfigured to have more slots, or results go to new slots.
        // For now, let's assume inventory.getSlots() is > 1 for outputs.
        // Typically for Create machines like Millstone/Crusher, output replaces input in the single slot.
        // If you want multiple output slots, ProcessingInventory needs to be initialized with more.
        // Or, the results are placed sequentially if the main inventory is multi-slotted.

        inventory.clear(); // Clear current inventory (which held the input) before adding results.
        // This is typical for Create single-slot processors.
        // If you have dedicated output slots, this logic changes.

        // For now, we'll assume results are placed into the *same* inventory,
        // overwriting the input. This matches Millstone/Press.
        // If MechanicalPeelerBlock's inventory is meant to be multi-slot (0=input, 1+=output),
        // then `ProcessingInventory` needs to be initialized with more slots.
        // Let's stick to Create's common pattern: results appear in the same slots.
        // The `inventory` in `KineticBlockEntity` is often a single `ProcessingInventory`.

        for (int i = 0; i < totalResults.size() && i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, totalResults.get(i)); // Put results back into the inventory
        }
    }


    private Optional<RecipeHolder<PeelingRecipe>> getMatchingRecipe(SingleRecipeInput input) {
        if (level == null) return Optional.empty();
        RecipeType<PeelingRecipe> peelingRecipeType = CreateFisheryRecipeTypes.PEELING.getType();
        return level.getRecipeManager().getRecipeFor(peelingRecipeType, input, level);
    }

    public void insertItem(ItemEntity entity) {
        if (!canProcess() || !inventory.isEmpty() || !entity.isAlive() || level.isClientSide) // inventory.isEmpty() checks if slot 0 is free
            return;

        // inventory.clear(); // Not needed if checking isEmpty above
        ItemStack toInsert = entity.getItem().copy();
        toInsert.setCount(1); // Process one at a time

        ItemStack remainder = inventory.insertItem(0, toInsert, false); // Insert into slot 0

        if (remainder.isEmpty()) { // Successfully inserted one item
            ItemStack entityStack = entity.getItem();
            entityStack.shrink(1);
            if (entityStack.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(entityStack);
            }
        }
        // If remainder is not empty, it means slot 0 was not empty or couldn't accept.
        // This case should be caught by !inventory.isEmpty() earlier.
    }

    public void start(ItemStack insertedStack) { // insertedStack is stack in slot 0
        if (!canProcess() || inventory.isEmpty() || (level.isClientSide && !isVirtual()))
            return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(insertedStack);
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(recipeInput);

        if (recipeHolder.isEmpty()) {
            inventory.remainingTime = inventory.recipeDuration = 10; // Short time for "no recipe" or failed
            inventory.appliedRecipe = true; // Mark as "processed" (even if failed) to allow eject
            sendData();
            return;
        }

        PeelingRecipe recipe = recipeHolder.get().value();
        int time = recipe.getProcessingDuration();
        if (time == 0) time = 100;

        inventory.remainingTime = time;
        inventory.recipeDuration = inventory.remainingTime;
        inventory.appliedRecipe = false;
        sendData();
    }

    protected boolean canProcess() {
        return getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        // setChanged(); // Not strictly necessary on setLevel, but can help ensure sync
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);
        CreateLang.text("")
                .add(Component.translatable("create.gui.goggles.speed", String.format("%.1f", Math.abs(getSpeed())))) // Use Math.abs for speed
                .style(ChatFormatting.AQUA)
                .forGoggles(tooltip, 1);

        CreateLang.translate("gui.goggles.saw_contents").forGoggles(tooltip); // Using a more generic or relevant key like saw_contents
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

    private static class ProcessingInventoryItemHandler implements IItemHandler {
        private final ProcessingInventory inventory;

        public ProcessingInventoryItemHandler(ProcessingInventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Allow insertion only into slot 0 if it's empty and no recipe is active
            // This is a common pattern for Create single-slot processors
            if (slot == 0 && inventory.getStackInSlot(0).isEmpty() && inventory.remainingTime == -1) {
                // Create's ProcessingInventory.insertItem handles slot limits, but we add stricter logic.
                // For simplicity, we'll use its default behavior.
                // If you need more complex logic (e.g. only if canProcess is true), add it here.
                return inventory.insertItem(slot, stack, simulate);
            }
            return stack; // Cannot insert into other slots or if busy/full
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Allow extraction from any slot if a recipe has been applied (i.e., output is ready)
            // or if it's an input slot and recipe failed (though usually input is consumed)
            if (inventory.appliedRecipe || inventory.remainingTime == 0) {
                // Create's ProcessingInventory.extractItem handles this.
                return inventory.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY; // Cannot extract if processing or not ready
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot); // Usually 1 for ProcessingInventory
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Only valid for slot 0, and if a recipe exists for the item
            if (slot == 0) {
                // Optional: check if there's a matching peeling recipe for 'stack'
                // For simplicity, rely on ProcessingInventory's default or allow any.
                // return getMatchingRecipe(new SingleRecipeInput(stack)).isPresent();
                return inventory.isItemValid(slot, stack);
            }
            return false;
        }
    }
}