package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class MechanicalPeelerBlockEntity extends KineticBlockEntity {

    public ProcessingInventory inventory;
    private ItemStack playEvent;

    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new ProcessingInventory(this::start).withSlotLimit(true);
        inventory.remainingTime = -1;
        playEvent = ItemStack.EMPTY;
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
            SoundType soundType = SoundType.METAL;
            Item item = playEvent.getItem();
            if (item instanceof BlockItem blockItem) {
                // soundType = blockItem.getBlock().getSoundType(blockItem.getBlock().defaultBlockState(), level, worldPosition, null);
            }
            spawnEventParticles(playEvent);
            playEvent = ItemStack.EMPTY;

            if (soundType == SoundType.WOOD) {
                AllSoundEvents.SAW_ACTIVATE_WOOD.playAt(level, worldPosition, 1.0f, 1.25f, true);
            } else {
                AllSoundEvents.SAW_ACTIVATE_STONE.playAt(level, worldPosition, 1.0f, 1.25f, true);
            }
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
            if (!inventory.isEmpty() && !inventory.appliedRecipe) {
                start(inventory.getStackInSlot(0));
            }
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 24f, 1f, 128f);
        inventory.remainingTime -= processingSpeed;

        if (inventory.remainingTime > 0) {
            spawnParticles(inventory.getStackInSlot(0));
        }

        if (inventory.remainingTime < 5 && !inventory.appliedRecipe) {
            if (level.isClientSide && !isVirtual())
                return;
            playEvent = inventory.getStackInSlot(0);
            applyRecipe();
            inventory.appliedRecipe = true;
            inventory.recipeDuration = 20;
            inventory.remainingTime = 20;
            sendData();
            return;
        }

        Vec3 itemMovement = getItemMovementVec();
        Direction itemMovementFacing = Direction.getNearest(itemMovement.x, itemMovement.y, itemMovement.z);

        if (inventory.remainingTime > 0)
            return;
        inventory.remainingTime = 0;

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;
            ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
                    .tryExportingToBeltFunnel(stack, itemMovementFacing.getOpposite(), false);
            if (tryExportingToBeltFunnel != null) {
                if (tryExportingToBeltFunnel.getCount() != stack.getCount()) {
                    inventory.setStackInSlot(slot, tryExportingToBeltFunnel);
                    notifyUpdate();
                    return;
                }
                if (!tryExportingToBeltFunnel.isEmpty())
                    return;
            }
        }

        BlockPos nextPos = worldPosition.offset(BlockPos.containing(itemMovement));
        DirectBeltInputBehaviour behaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);
        if (behaviour != null) {
            boolean changed = false;
            if (!behaviour.canInsertFromSide(itemMovementFacing))
                return;
            if (level.isClientSide && !isVirtual())
                return;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty())
                    continue;
                ItemStack remainder = behaviour.handleInsertion(stack, itemMovementFacing, false);
                if (ItemStack.matches(remainder, stack))
                    continue;
                inventory.setStackInSlot(slot, remainder);
                changed = true;
            }
            if (changed) {
                setChanged();
                sendData();
            }
            return;
        }

        Vec3 outPos = VecHelper.getCenterOf(worldPosition)
                .add(itemMovement.scale(.5f).add(0, .5, 0));
        Vec3 outMotion = itemMovement.scale(.0625f).add(0, .125f, 0);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty())
                continue;
            ItemEntity entityIn = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stack);
            entityIn.setDeltaMovement(outMotion);
            level.addFreshEntity(entityIn);
        }
        inventory.clear();
        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        inventory.remainingTime = -1;
        sendData();
    }

    @Override
    public void invalidate() {
        super.invalidate();
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
        if (getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP) {
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            int direction = getSpeed() < 0 ? 1 : -1;
            return new Vec3(direction * (alongLocalX ? 1 : 0), 0, direction * (alongLocalX ? 0 : 1));
        }
        return Vec3.ZERO;
    }

    private void applyRecipe() {
        ItemStack inputStack = inventory.getStackInSlot(0);
        if (inputStack.isEmpty()) return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(inputStack);
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(recipeInput);

        if (recipeHolder.isEmpty()) {
            inventory.clear();
            inventory.remainingTime = -1;
            sendData();
            return;
        }

        PeelingRecipe recipe = recipeHolder.get().value();
        inventory.clear();

        List<ItemStack> currentRollResults = recipe.rollResults();

        List<ItemStack> totalResults = new LinkedList<>();
        for (ItemStack stack : currentRollResults) {
            if (!stack.isEmpty()) {
                ItemHelper.addToList(stack.copy(), totalResults);
            }
        }

        for (int i = 0; i < totalResults.size() && (i + 1) < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i + 1, totalResults.get(i));
        }
    }

    private Optional<RecipeHolder<PeelingRecipe>> getMatchingRecipe(SingleRecipeInput input) {
        if (level == null) return Optional.empty();
        RecipeType<PeelingRecipe> peelingRecipeType = CreateFisheryRecipeTypes.PEELING.getType();
        return level.getRecipeManager().getRecipeFor(peelingRecipeType, input, level);
    }

    public void insertItem(ItemEntity entity) {
        if (!canProcess() || !inventory.isEmpty() || !entity.isAlive() || level.isClientSide)
            return;

        inventory.clear();
        ItemStack remainder = inventory.insertItem(0, entity.getItem().copy(), false);
        if (remainder.isEmpty())
            entity.discard();
        else
            entity.setItem(remainder);
    }

    public void start(ItemStack insertedStack) {
        if (!canProcess() || inventory.isEmpty() || (level.isClientSide && !isVirtual()))
            return;

        SingleRecipeInput recipeInput = new SingleRecipeInput(insertedStack);
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(recipeInput);

        if (recipeHolder.isEmpty()) {
            inventory.remainingTime = inventory.recipeDuration = 10;
            inventory.appliedRecipe = true;
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
}