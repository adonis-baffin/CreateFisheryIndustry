package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper; // Keep this for dropContents
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
//import net.minecraft.core.NonNullList; // Not directly used here
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers; // For dropping items
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
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper; // For inserting into other handlers
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.LinkedList; // If still used for totalResults in applyRecipe
import java.util.List;
import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class MechanicalPeelerBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    public ProcessingInventory inputInventory;
    public ItemStackHandler outputInventory;

    private ItemStack playEvent;
    public final IItemHandler itemHandler;

    public static final int INPUT_SLOT = 0;
    private static final int MAX_SECONDARY_OUTPUTS = 4;
    public static final int OUTPUT_INV_PRIMARY_SLOT_TEMP = 0;
    private static final int OUTPUT_INV_SIZE = 1 + MAX_SECONDARY_OUTPUTS;


    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inputInventory = new ProcessingInventory(this::startProcessingRecipe);
        inputInventory.remainingTime = -1;

        outputInventory = new ItemStackHandler(OUTPUT_INV_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sendData();
            }
        };

        playEvent = ItemStack.EMPTY;
        itemHandler = new PeelerItemHandler(inputInventory, outputInventory);
    }

    private void startProcessingRecipe(ItemStack stackInInputSlot) {
        if (!canProcess() || stackInInputSlot.isEmpty() || (level.isClientSide && !isVirtual()))
            return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(stackInInputSlot);
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(recipeInput);

        if (recipeHolder.isEmpty()) {
            inputInventory.remainingTime = inputInventory.recipeDuration = 10;
            inputInventory.appliedRecipe = true;
            sendData();
            return;
        }

        PeelingRecipe recipe = recipeHolder.get().value();
        int time = recipe.getProcessingDuration();
        if (time == 0) time = 100;

        inputInventory.remainingTime = time;
        inputInventory.recipeDuration = inputInventory.remainingTime;
        inputInventory.appliedRecipe = false;
        sendData();
    }


    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        // DirectBeltInputBehaviour will use the BE's main capability,
        // which our PeelerItemHandler will route to inputInventory slot 0.
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnelsWhen(this::canProcess));
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("InputInventory", inputInventory.serializeNBT(registries));
        compound.put("OutputInventory", outputInventory.serializeNBT(registries));
        super.write(compound, registries, clientPacket);

        if (!clientPacket || playEvent.isEmpty())
            return;
        compound.put("PlayEvent", playEvent.saveOptional(registries));
        playEvent = ItemStack.EMPTY;
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inputInventory.deserializeNBT(registries, compound.getCompound("InputInventory"));
        if (compound.contains("OutputInventory")) {
            outputInventory.deserializeNBT(registries, compound.getCompound("OutputInventory"));
        } else {
            this.outputInventory = new ItemStackHandler(OUTPUT_INV_SIZE);
        }

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

    protected void spawnEventParticles(ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null)
            return;
        ParticleOptions particleData = (stack.getItem() instanceof BlockItem blockItem)
                ? new BlockParticleOption(ParticleTypes.BLOCK, blockItem.getBlock().defaultBlockState())
                : new ItemParticleOption(ParticleTypes.ITEM, stack);
        RandomSource r = level.random;
        Vec3 v = VecHelper.getCenterOf(this.worldPosition).add(0, 5 / 16f, 0);
        for (int i = 0; i < 10; i++) {
            Vec3 randomOffset = VecHelper.offsetRandomly(Vec3.ZERO, r, 0.125f);
            Vec3 m = randomOffset.add(0, 0.25f, 0);
            level.addParticle(particleData, v.x, v.y, v.z, m.x, m.y, m.z);
        }
    }

    protected void spawnParticles(ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null) return;
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
        float processingOffsetRatio = (inputInventory.recipeDuration != 0) ? (1f - (float) inputInventory.remainingTime / inputInventory.recipeDuration) : 0.5f;
        float displayOffset = inputInventory.appliedRecipe ? (processingOffsetRatio * 0.5f) : ((1.0f - processingOffsetRatio) * 0.5f);
        Vec3 particlePos = center.add(itemMovementVec.scale(displayOffset));
        Vec3 randomMotionOffset = VecHelper.offsetRandomly(Vec3.ZERO, r, particleSpeed * 0.25f);
        Vec3 particleMotion = itemMovementVec.scale(-particleSpeed * 0.5f).add(randomMotionOffset).add(0, r.nextFloat() * particleSpeed * 0.5f, 0);
        level.addParticle(particleData, particlePos.x, particlePos.y + 0.3, particlePos.z, particleMotion.x, particleMotion.y, particleMotion.z);
    }


    @Override
    public void tick() {
        super.tick();

        if (!canProcess() || getSpeed() == 0) {
            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && inputInventory.remainingTime <= 0) {
                if (inputInventory.remainingTime == -1) {
                    inputInventory.remainingTime = inputInventory.recipeDuration = 10;
                    inputInventory.appliedRecipe = true;
                    sendData();
                } else if (inputInventory.remainingTime == 0) {
                    ejectInputOrPrimaryOutput();
                }
            }
            tryEjectSecondaryOutputs();
            return;
        }

        if (inputInventory.remainingTime == -1) {
            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe) {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
            tryEjectSecondaryOutputs();
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 32f, 1, 128);
        inputInventory.remainingTime -= processingSpeed;

        if (inputInventory.remainingTime > 0) {
            spawnParticles(inputInventory.getStackInSlot(INPUT_SLOT));
        }

        if (inputInventory.remainingTime < 5 && !inputInventory.appliedRecipe) {
            if (level.isClientSide && !isVirtual()) return;
            applyRecipeProducts();
            inputInventory.appliedRecipe = true;
            inputInventory.remainingTime = inputInventory.recipeDuration = 20; // Time for primary output to eject
            playEvent = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty() ?
                    inputInventory.getStackInSlot(INPUT_SLOT) : // if pass-through
                    outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP);
            sendData();
            return;
        }

        if (inputInventory.remainingTime <= 0) {
            inputInventory.remainingTime = 0;
            ejectInputOrPrimaryOutput();
            tryEjectSecondaryOutputs();
        }
    }

    private void ejectInputOrPrimaryOutput() {
        ItemStack stackToEject;
        boolean isPassThrough = false;

        if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
            stackToEject = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP);
        } else if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && inputInventory.appliedRecipe) {
            stackToEject = inputInventory.getStackInSlot(INPUT_SLOT);
            isPassThrough = true;
        } else {
            return;
        }
        if (stackToEject.isEmpty()) return;

        Vec3 itemMovement = getItemMovementVec();
        Direction itemMovementFacing = Direction.getNearest(itemMovement.x, itemMovement.y, itemMovement.z);
        DirectBeltInputBehaviour funnelBehaviour = getBehaviour(DirectBeltInputBehaviour.TYPE);

        if (funnelBehaviour != null) {
            ItemStack funnelRemainder = funnelBehaviour.tryExportingToBeltFunnel(stackToEject, itemMovementFacing.getOpposite(), false);
            if (funnelRemainder != null && funnelRemainder.getCount() != stackToEject.getCount()) {
                updateEjectedStack(isPassThrough, funnelRemainder);
                if (funnelRemainder.isEmpty() && isPassThrough) inputInventory.remainingTime = -1;
                notifyUpdate();
                return;
            }
        }

        BlockPos nextPos = worldPosition.offset(BlockPos.containing(itemMovement));
        DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);
        if (beltBehaviour != null && beltBehaviour.canInsertFromSide(itemMovementFacing)) {
            if (level.isClientSide && !isVirtual()) return;
            ItemStack beltRemainder = beltBehaviour.handleInsertion(stackToEject, itemMovementFacing, false);
            if (!ItemStack.matches(beltRemainder, stackToEject)) {
                updateEjectedStack(isPassThrough, beltRemainder);
                if (beltRemainder.isEmpty() && isPassThrough) inputInventory.remainingTime = -1;
                setChanged();
                sendData();
                return;
            }
        }

        Vec3 outPos = VecHelper.getCenterOf(worldPosition).add(itemMovement.scale(.5f)).add(0, .5, 0);
        Vec3 outMotion = itemMovement.scale(.0625).add(0, .125, 0);
        ItemEntity entityOut = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stackToEject.copy());
        entityOut.setDeltaMovement(outMotion);
        level.addFreshEntity(entityOut);
        updateEjectedStack(isPassThrough, ItemStack.EMPTY);
        if (isPassThrough) inputInventory.remainingTime = -1;
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        sendData();
    }

    private void updateEjectedStack(boolean isPassThrough, ItemStack remainder) {
        if (isPassThrough) {
            inputInventory.setStackInSlot(INPUT_SLOT, remainder);
        } else {
            outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, remainder);
        }
    }


    private void tryEjectSecondaryOutputs() {
        for (int i = 1; i < outputInventory.getSlots(); i++) {
            ItemStack stack = outputInventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            BlockPos below = worldPosition.below();
            IItemHandler belowInventory = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, below, Direction.UP);
            if (belowInventory != null) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(belowInventory, stack, false); // Corrected
                outputInventory.setStackInSlot(i, remainder);
                if (!ItemStack.matches(stack, remainder)) {
                    setChanged();
                    sendData();
                    if (remainder.isEmpty()) continue;
                    else return;
                }
            } else {
                ItemEntity entityIn = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() - 0.5, worldPosition.getZ() + 0.5, stack.copy());
                entityIn.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(entityIn);
                outputInventory.setStackInSlot(i, ItemStack.EMPTY);
                setChanged();
                sendData();
                return;
            }
        }
    }


    private void applyRecipeProducts() {
        ItemStack inputStack = inputInventory.getStackInSlot(INPUT_SLOT);
        if (inputStack.isEmpty()) return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(inputStack);
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(recipeInput);

        if (recipeHolder.isEmpty()) return;

        PeelingRecipe recipe = recipeHolder.get().value();
        inputInventory.extractItem(INPUT_SLOT, 1, false);

        ItemStack primaryOutput = recipe.getPrimaryOutput().copy();
        outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, primaryOutput);

        // Get the secondary ProcessingOutputs first
        List<ProcessingOutput> secondaryProcessingOutputs = recipe.getSecondaryOutputs();
        // Then roll results for this specific list
        List<ItemStack> rolledSecondaryItemStacks = recipe.rollResultsFor(secondaryProcessingOutputs);

        for (ItemStack secondaryStack : rolledSecondaryItemStacks) {
            if (secondaryStack.isEmpty()) continue;
            ItemStack remainder = secondaryStack.copy();
            for (int i = 1; i < outputInventory.getSlots(); i++) {
                remainder = outputInventory.insertItem(i, remainder, false);
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.75, worldPosition.getZ() + 0.5,
                        remainder);
                itemEntity.setDeltaMovement((level.random.nextDouble() - 0.5) * 0.1, 0.2, (level.random.nextDouble() - 0.5) * 0.1);
                level.addFreshEntity(itemEntity);
            }
        }
        setChanged();
    }

    public Vec3 getItemMovementVec() {
        Direction facing = getBlockState().getValue(MechanicalPeelerBlock.FACING);
        if (facing == Direction.UP) {
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            int speedSign = getSpeed() < 0 ? 1 : -1;
            return new Vec3(speedSign * (alongLocalX ? 1 : 0), 0, speedSign * (alongLocalX ? 0 : 1));
        }
        if (facing.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(facing.getNormal());
        }
        return Vec3.ZERO;
    }


    private Optional<RecipeHolder<PeelingRecipe>> getMatchingRecipe(SingleRecipeInput input) {
        if (level == null || input.item().isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(CreateFisheryRecipeTypes.PEELING.getType(), input, level);
    }

    public void insertItem(ItemEntity entity) {
        if (!canProcess() || !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() || !entity.isAlive() || level.isClientSide)
            return;
        ItemStack toInsert = entity.getItem().copy();
        toInsert.setCount(1);
        ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, toInsert, false);
        if (remainder.isEmpty()) {
            ItemStack entityStack = entity.getItem();
            entityStack.shrink(1);
            if (entityStack.isEmpty()) entity.discard();
            else entity.setItem(entityStack);
        }
    }

    protected boolean canProcess() {
        return getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.kinetic_stats").forGoggles(tooltip);
        CreateLang.text("").add(Component.translatable("create.gui.goggles.speed", String.format("%.1f", Math.abs(getSpeed())))).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);

        CreateLang.translate("gui.goggles.peeler_input").forGoggles(tooltip);
        ItemStack inputStack = inputInventory.getStackInSlot(INPUT_SLOT);
        if (!inputStack.isEmpty()) CreateLang.text("").add(Component.translatable(inputStack.getDescriptionId()).withStyle(ChatFormatting.GRAY)).add(CreateLang.text(" x" + inputStack.getCount()).style(ChatFormatting.GREEN)).forGoggles(tooltip, 1);
        else CreateLang.translate("gui.goggles.inventory.empty").forGoggles(tooltip, 1);

        CreateLang.translate("gui.goggles.peeler_outputs").forGoggles(tooltip);
        boolean outputEmpty = true;
        ItemStack primaryTemp = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP);
        if(!primaryTemp.isEmpty()){
            outputEmpty = false;
            CreateLang.text("(Primary) ").add(Component.translatable(primaryTemp.getDescriptionId()).withStyle(ChatFormatting.GRAY)).add(CreateLang.text(" x" + primaryTemp.getCount()).style(ChatFormatting.GREEN)).forGoggles(tooltip, 1);
        }
        for (int i = 1; i < outputInventory.getSlots(); i++) {
            ItemStack stack = outputInventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                outputEmpty = false;
                CreateLang.text("").add(Component.translatable(stack.getDescriptionId()).withStyle(ChatFormatting.GRAY)).add(CreateLang.text(" x" + stack.getCount()).style(ChatFormatting.GREEN)).forGoggles(tooltip, 1);
            }
        }
        if (outputEmpty) CreateLang.translate("gui.goggles.inventory.empty").forGoggles(tooltip, 1);
        return true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inputInventory); // Method exists
        // For ItemStackHandler, use Containers.dropContents
        if (level != null && !level.isClientSide) {
            for(int i = 0; i < outputInventory.getSlots(); ++i) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), outputInventory.getStackInSlot(i));
            }
        }
    }

    // Custom Item Handler
    private static class PeelerItemHandler implements IItemHandler {
        private final ProcessingInventory inputInv;
        private final ItemStackHandler outputInv;

        public PeelerItemHandler(ProcessingInventory inputInventory, ItemStackHandler outputInventory) {
            this.inputInv = inputInventory;
            this.outputInv = outputInventory;
        }

        @Override
        public int getSlots() {
            return 1 + outputInv.getSlots(); // input + (primary_temp + secondaries)
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == INPUT_SLOT) return inputInv.getStackInSlot(INPUT_SLOT);
            int outputSlot = slot - 1;
            if (outputSlot >= 0 && outputSlot < outputInv.getSlots()) return outputInv.getStackInSlot(outputSlot);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == INPUT_SLOT && inputInv.getStackInSlot(INPUT_SLOT).isEmpty() && inputInv.remainingTime == -1) {
                return inputInv.insertItem(INPUT_SLOT, stack, simulate);
            }
            return stack; // No insertion into output slots via this general handler
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot == INPUT_SLOT) { // Extract from input only if it's a pass-through
                if (inputInv.appliedRecipe && inputInv.remainingTime == 0) { // Check if it's ready to be ejected
                    return inputInv.extractItem(INPUT_SLOT, amount, simulate);
                }
            } else { // Extract from output slots
                int outputSlot = slot - 1;
                if (outputSlot >= 0 && outputSlot < outputInv.getSlots()) {
                    return outputInv.extractItem(outputSlot, amount, simulate);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == INPUT_SLOT) return inputInv.getSlotLimit(INPUT_SLOT); // Typically 1
            int outputSlot = slot - 1;
            if (outputSlot >= 0 && outputSlot < outputInv.getSlots()) return outputInv.getSlotLimit(outputSlot); // Typically 64
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == INPUT_SLOT) return inputInv.isItemValid(INPUT_SLOT, stack);
            return false; // Output slots not for validation via this general handler
        }
    }
}