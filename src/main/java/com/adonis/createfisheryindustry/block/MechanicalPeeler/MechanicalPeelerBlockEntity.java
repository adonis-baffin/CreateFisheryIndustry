package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.CreateFisheryMod; // 用于 LOGGER
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.math.VecHelper;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity; // Shearable 需要 LivingEntity
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor; // 仍可能用于判断羊的颜色，但不再直接用于获取羊毛物品
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.IShearable; // 导入 IShearable 接口
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class MechanicalPeelerBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    public ProcessingInventory inputInventory;
    public ItemStackHandler outputInventory;

    private ItemStack playEvent;
    public final IItemHandler itemHandler;

    public static final int INPUT_SLOT = 0;
    private static final int MAX_SECONDARY_OUTPUTS_STORAGE = 4;
    public static final int OUTPUT_INV_PRIMARY_SLOT_TEMP = 0;
    private static final int OUTPUT_INV_SIZE = 1 + MAX_SECONDARY_OUTPUTS_STORAGE;

    private final Map<UUID, Long> entityCooldowns = new HashMap<>();
    private static final int ARMADILLO_TURTLE_COOLDOWN_TICKS = 20 * 60 * 1; // 1分钟冷却

    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inputInventory = new ProcessingInventory(this::startProcessingRecipe)
                .withSlotLimit(true);
        inputInventory.remainingTime = -1;

        outputInventory = new ItemStackHandler(OUTPUT_INV_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sendData();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot == OUTPUT_INV_PRIMARY_SLOT_TEMP) {
                    return stack;
                }
                return super.insertItem(slot, stack, simulate);
            }
        };

        playEvent = ItemStack.EMPTY;
        itemHandler = new PeelerItemHandler(this, inputInventory, outputInventory);
    }

    private void startProcessingRecipe(ItemStack stackInInputSlot) {
        if (!canProcess() || stackInInputSlot.isEmpty() || (level != null && level.isClientSide && !isVirtual()))
            return;

        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(new SingleRecipeInput(stackInInputSlot));

        if (recipeHolder.isPresent()) {
            PeelingRecipe recipe = recipeHolder.get().value();
            List<ItemStack> totalPotentialSecondaryProducts = new ArrayList<>();
            for (int i = 0; i < stackInInputSlot.getCount(); i++) {
                totalPotentialSecondaryProducts.addAll(recipe.rollResultsFor(recipe.getSecondaryOutputs()));
            }

            if (!canStoreAllSecondaries(totalPotentialSecondaryProducts)) {
                inputInventory.remainingTime = inputInventory.recipeDuration = 10;
                inputInventory.appliedRecipe = true;
                sendData();
                return;
            }
            int timePerItem = recipe.getProcessingDuration();
            if (timePerItem == 0) timePerItem = 100;
            inputInventory.remainingTime = timePerItem * stackInInputSlot.getCount();
            inputInventory.recipeDuration = inputInventory.remainingTime;
            inputInventory.appliedRecipe = false;
        } else {
            inputInventory.remainingTime = inputInventory.recipeDuration = 10;
            inputInventory.appliedRecipe = true;
        }
        sendData();
    }

    private boolean canStoreAllSecondaries(List<ItemStack> secondaryProducts) {
        if (secondaryProducts.isEmpty()) {
            return true;
        }
        ItemStackHandler tempSecondaryInv = new ItemStackHandler(MAX_SECONDARY_OUTPUTS_STORAGE);
        for (int i = 0; i < MAX_SECONDARY_OUTPUTS_STORAGE; i++) {
            tempSecondaryInv.setStackInSlot(i, outputInventory.getStackInSlot(i + 1).copy());
        }

        for (ItemStack product : secondaryProducts) {
            ItemStack remainder = product.copy();
            for (int i = 0; i < tempSecondaryInv.getSlots(); i++) {
                remainder = tempSecondaryInv.insertItem(i, remainder.copy(), false);
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnelsWhen(this::canProcess));
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("InputInventory", inputInventory.serializeNBT(registries));
        compound.put("OutputInventory", outputInventory.serializeNBT(registries));
        super.write(compound, registries, clientPacket);

        if (!clientPacket) {
            CompoundTag cooldownsTag = new CompoundTag();
            entityCooldowns.forEach((uuid, time) -> cooldownsTag.putLong(uuid.toString(), time));
            compound.put("EntityCooldowns", cooldownsTag);
        }

        if (!clientPacket || !playEvent.isEmpty()) {
            compound.put("PlayEvent", playEvent.saveOptional(registries));
            if (clientPacket) playEvent = ItemStack.EMPTY;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inputInventory.deserializeNBT(registries, compound.getCompound("InputInventory"));
        outputInventory.deserializeNBT(registries, compound.getCompound("OutputInventory"));

        if (!clientPacket) {
            entityCooldowns.clear();
            if (compound.contains("EntityCooldowns", CompoundTag.TAG_COMPOUND)) {
                CompoundTag cooldownsTag = compound.getCompound("EntityCooldowns");
                for (String key : cooldownsTag.getAllKeys()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        long time = cooldownsTag.getLong(key);
                        entityCooldowns.put(uuid, time);
                    } catch (IllegalArgumentException e) {
                        CreateFisheryMod.LOGGER.warn("Failed to parse UUID from cooldowns NBT: " + key, e);
                    }
                }
            }
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
        if (getSpeed() == 0 || !canProcess())
            return;

        if (!playEvent.isEmpty()) {
            boolean isWood = false;
            Item item = playEvent.getItem();
            if (item instanceof BlockItem) {
                Block block = ((BlockItem) item).getBlock();
                if (level != null)
                    isWood = block.getSoundType(block.defaultBlockState(), level, worldPosition, null) == SoundType.WOOD;
            }
            spawnEventParticles(playEvent);
            playEvent = ItemStack.EMPTY;
            if (level != null) {
                if (!isWood)
                    AllSoundEvents.SAW_ACTIVATE_STONE.playAt(level, worldPosition, 3, 1, true);
                else
                    AllSoundEvents.SAW_ACTIVATE_WOOD.playAt(level, worldPosition, 3, 1, true);
            }
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
        if (stack == null || stack.isEmpty() || level == null || !canProcess()) return;
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

        float offsetRatio = 0;
        if (inputInventory.recipeDuration != 0) {
            offsetRatio = (float) (inputInventory.remainingTime) / inputInventory.recipeDuration;
        }
        offsetRatio /= 2;
        if (inputInventory.appliedRecipe)
            offsetRatio -= .5f;

        Vec3 particlePos = center.add(itemMovementVec.x() * -offsetRatio, 0.45, itemMovementVec.z() * -offsetRatio);
        Vec3 particleMotion = new Vec3(-itemMovementVec.x() * particleSpeed, r.nextFloat() * particleSpeed, -itemMovementVec.z() * particleSpeed);

        level.addParticle(particleData, particlePos.x(), particlePos.y(), particlePos.z(), particleMotion.x, particleMotion.y, particleMotion.z);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide) {
            if (level != null && level.isClientSide && !playEvent.isEmpty()) tickAudio();
            return;
        }

        if (!canProcess() || getSpeed() == 0) {
            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() &&
                    inputInventory.appliedRecipe && inputInventory.remainingTime <= 0) {
                ejectInputOrPrimaryOutput();
            } else if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty() && inputInventory.remainingTime <= 0) {
                ejectInputOrPrimaryOutput();
            }
            return;
        }

        if (inputInventory.remainingTime == -1) {
            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe) {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
            return;
        }

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 32f, 1, 128);
        inputInventory.remainingTime -= processingSpeed;

        if (inputInventory.remainingTime < 5 && !inputInventory.appliedRecipe) {
            playEvent = inputInventory.getStackInSlot(INPUT_SLOT).copy();
            sendData();
            applyRecipeProducts();
            inputInventory.appliedRecipe = true;
            inputInventory.remainingTime = 20;
            inputInventory.recipeDuration = 20;
            return;
        }

        if (inputInventory.remainingTime <= 0) {
            inputInventory.remainingTime = 0;
            ejectInputOrPrimaryOutput();
        }
    }

    private void ejectInputOrPrimaryOutput() {
        ItemStack stackToEject = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP);
        boolean isPassThrough = stackToEject.isEmpty() && inputInventory.appliedRecipe;

        if (isPassThrough) {
            stackToEject = inputInventory.getStackInSlot(INPUT_SLOT);
        }

        if (stackToEject.isEmpty()) {
            if (inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() &&
                    outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
            }
            return;
        }

        Vec3 itemMovement = getItemMovementVec();
        Direction itemMovementFacing = Direction.getNearest(itemMovement.x, itemMovement.y, itemMovement.z);

        DirectBeltInputBehaviour funnelBehaviour = getBehaviour(DirectBeltInputBehaviour.TYPE);
        if (funnelBehaviour != null) {
            ItemStack funnelRemainder = funnelBehaviour.tryExportingToBeltFunnel(stackToEject, itemMovementFacing.getOpposite(), false);
            if (funnelRemainder != null && !ItemStack.matches(funnelRemainder, stackToEject)) {
                updateEjectedStack(isPassThrough, funnelRemainder);
                if (funnelRemainder.isEmpty()) {
                    resetStateAfterEjection(isPassThrough);
                }
                notifyUpdate();
                return;
            }
        }

        BlockPos nextPos = worldPosition.offset(BlockPos.containing(itemMovement));
        DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);

        if (beltBehaviour != null && beltBehaviour.canInsertFromSide(itemMovementFacing)) {
            ItemStack beltRemainder = beltBehaviour.handleInsertion(stackToEject.copy(), itemMovementFacing, false);
            if (!ItemStack.matches(beltRemainder, stackToEject)) {
                updateEjectedStack(isPassThrough, beltRemainder);
                if (beltRemainder.isEmpty()) {
                    resetStateAfterEjection(isPassThrough);
                }
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
        resetStateAfterEjection(isPassThrough);

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

    private void resetStateAfterEjection(boolean wasPassThrough) {
        if (wasPassThrough) {
            if (inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
            } else {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        } else {
            if (outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                    startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
                }
            }
        }
    }


    private void applyRecipeProducts() {
        ItemStack inputStackCopy = inputInventory.getStackInSlot(INPUT_SLOT).copy();
        if (inputStackCopy.isEmpty()) return;

        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(new SingleRecipeInput(inputStackCopy));

        if (recipeHolder.isEmpty()) {
            return;
        }

        PeelingRecipe recipe = recipeHolder.get().value();
        int itemsToProcess = inputStackCopy.getCount();
        inputInventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
        List<ItemStack> collectedPrimaryOutputs = new LinkedList<>();
        List<ItemStack> collectedSecondaryOutputs = new LinkedList<>();

        for (int i = 0; i < itemsToProcess; i++) {
            ItemStack primaryPerLoop = recipe.getPrimaryOutput().copy();
            if (!primaryPerLoop.isEmpty()) {
                ItemHelper.addToList(primaryPerLoop, collectedPrimaryOutputs);
            }
            List<ItemStack> secondariesPerLoop = recipe.rollResultsFor(recipe.getSecondaryOutputs());
            for (ItemStack secondaryStack : secondariesPerLoop) {
                if (!secondaryStack.isEmpty()) {
                    ItemHelper.addToList(secondaryStack.copy(), collectedSecondaryOutputs);
                }
            }
        }

        if (!collectedPrimaryOutputs.isEmpty()) {
            List<ItemStack> condensedPrimaryItems = new ArrayList<>();
            for (ItemStack collectedStack : collectedPrimaryOutputs) {
                ItemHelper.addToList(collectedStack.copy(), condensedPrimaryItems);
            }
            if (!condensedPrimaryItems.isEmpty()) {
                outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, condensedPrimaryItems.get(0).copy());
            } else {
                outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, ItemStack.EMPTY);
            }
        } else {
            outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, ItemStack.EMPTY);
        }

        for (ItemStack secondaryStack : collectedSecondaryOutputs) {
            if (secondaryStack.isEmpty()) continue;
            ItemStack remainderToStore = secondaryStack.copy();
            for (int slot = 1; slot < outputInventory.getSlots(); slot++) {
                remainderToStore = outputInventory.insertItem(slot, remainderToStore, false);
                if (remainderToStore.isEmpty()) break;
            }
            if (!remainderToStore.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 0.75, worldPosition.getZ() + 0.5,
                        remainderToStore);
                itemEntity.setDeltaMovement(VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.1f).add(0, 0.2f, 0));
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
        if (level == null || level.isClientSide || !canProcess() || !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() || !entity.isAlive())
            return;
        ItemStack toInsert = entity.getItem().copy();
        ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, toInsert, false);
        if (!ItemStack.matches(remainder, toInsert)) {
            entity.setItem(remainder);
            if (remainder.isEmpty()) entity.discard();
            if (inputInventory.remainingTime == -1 && !inputInventory.appliedRecipe) {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        }
    }

    // 移除 @Override 注解，因为这不是父类的方法
    protected boolean canProcess() {
        return getSpeed() != 0; // 或者你对“可以处理”有其他条件
    }

    public void processEntity(Entity entity) {
        // 移除 canProcess() 检查，因为外部调用者 (Block.entityInside 或 MovementBehaviour.tick) 应该已经检查过了
        // 或者，如果你想在这里再加一层保险：
        if (level == null || level.isClientSide || entity.isRemoved() || getSpeed() == 0) {
            return;
        }

        UUID entityId = entity.getUUID();
        boolean isArmadilloOrTurtle = entity instanceof Armadillo || entity instanceof Turtle;

        if (isArmadilloOrTurtle) {
            if (isEntityOnCooldown(entityId)) {
                return;
            }
        }

        boolean processedSuccessfully = false;

        if (entity instanceof IShearable shearableTarget) {
            if (shearableTarget.isShearable(null, ItemStack.EMPTY, level, entity.blockPosition())) {
                List<ItemStack> drops = shearableTarget.onSheared(null, ItemStack.EMPTY, level, entity.blockPosition());
                if (drops != null && !drops.isEmpty()) {
                    for (ItemStack drop : drops) {
                        if (!tryStoreItemInSecondaryOutput(drop)) {
                            ItemEntity itemEntity = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), drop);
                            level.addFreshEntity(itemEntity);
                        }
                    }
                    processedSuccessfully = true;
                }
            }
        }

        if (!processedSuccessfully && isArmadilloOrTurtle) {
            if (entity instanceof Armadillo armadillo) { // 直接类型转换以便调用特定方法（如果需要）
                if (tryStoreItemInSecondaryOutput(new ItemStack(Items.ARMADILLO_SCUTE))) {
                    level.playSound(null, armadillo, SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    setEntityCooldown(entityId);
                    processedSuccessfully = true;
                }
            } else if (entity instanceof Turtle turtle) {
                if (tryStoreItemInSecondaryOutput(new ItemStack(Items.TURTLE_SCUTE))) {
                    level.playSound(null, turtle, SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                    setEntityCooldown(entityId);
                    processedSuccessfully = true;
                }
            }
        }

        if (processedSuccessfully) {
            spawnProcessingParticlesEffect();
            setChanged();
        }
    }

    private boolean tryStoreItemInSecondaryOutput(ItemStack stackToStore) {
        if (stackToStore.isEmpty()) {
            return true;
        }
        ItemStack remainder = stackToStore.copy();
        for (int i = 1; i <= MAX_SECONDARY_OUTPUTS_STORAGE; i++) {
            remainder = outputInventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isEntityOnCooldown(UUID entityId) {
        if (level == null) return true;
        return entityCooldowns.containsKey(entityId) &&
                (level.getGameTime() - entityCooldowns.get(entityId)) < ARMADILLO_TURTLE_COOLDOWN_TICKS;
    }

    private void setEntityCooldown(UUID entityId) {
        if (level == null) return;
        entityCooldowns.put(entityId, level.getGameTime());
    }

    private void spawnProcessingParticlesEffect() {
        if (level == null || !level.isClientSide) return;
        RandomSource r = level.random;
        Vec3 center = VecHelper.getCenterOf(this.worldPosition);
        for (int i = 0; i < 5; i++) {
            double motionX = (r.nextDouble() - 0.5D) * 0.1D;
            double motionY = r.nextDouble() * 0.1D + 0.1D;
            double motionZ = (r.nextDouble() - 0.5D) * 0.1D;
            level.addParticle(ParticleTypes.SNOWFLAKE,
                    center.x + (r.nextDouble() - 0.5D) * 0.6D,
                    center.y + 1.1D,
                    center.z + (r.nextDouble() - 0.5D) * 0.6D,
                    motionX, motionY, motionZ);
        }
    }


    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean superAddedInformation = super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        boolean hasAnySecondaryOutput = false;
        for (int i = 1; i < outputInventory.getSlots(); i++) {
            if (!outputInventory.getStackInSlot(i).isEmpty()) {
                hasAnySecondaryOutput = true;
                break;
            }
        }

        if (hasAnySecondaryOutput) {
            if (superAddedInformation) {
                tooltip.add(Component.literal(" "));
            }
            CreateLang.translate("gui.goggles.peeler_stored_outputs").forGoggles(tooltip);
            for (int i = 1; i < outputInventory.getSlots(); i++) {
                ItemStack stack = outputInventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    tooltip.add(CreateLang.text("  ")
                            .add(Component.translatable(stack.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                            .add(CreateLang.text(" x" + stack.getCount()).style(ChatFormatting.GREEN))
                            .component());
                }
            }
        } else {
            if (superAddedInformation) {
                tooltip.add(Component.literal(" "));
            }
            CreateLang.translate("gui.goggles.peeler_stored_outputs").forGoggles(tooltip);
            tooltip.add(CreateLang.text("  ").add(CreateLang.translate("gui.goggles.inventory.empty")).component());
        }
        return true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && !level.isClientSide) {
            ItemHelper.dropContents(level, worldPosition, inputInventory);
            for (int i = 0; i < outputInventory.getSlots(); ++i) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), outputInventory.getStackInSlot(i));
            }
        }
    }

    private static class PeelerItemHandler implements IItemHandler {
        private final MechanicalPeelerBlockEntity be;
        private final ProcessingInventory inputInv;
        private final ItemStackHandler outputInv;

        public PeelerItemHandler(MechanicalPeelerBlockEntity be, ProcessingInventory inputInventory, ItemStackHandler outputInventory) {
            this.be = be;
            this.inputInv = inputInventory;
            this.outputInv = outputInventory;
        }

        @Override
        public int getSlots() {
            return 1 + MAX_SECONDARY_OUTPUTS_STORAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 0) {
                return inputInv.getStackInSlot(INPUT_SLOT);
            }
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.getStackInSlot(slot);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!be.canProcess()) return stack;
            if (slot == 0) {
                if (inputInv.getStackInSlot(INPUT_SLOT).isEmpty() && (inputInv.remainingTime == -1 || inputInv.appliedRecipe)) {
                    if (!simulate) {
                        ItemStack current = inputInv.getStackInSlot(INPUT_SLOT);
                        int insertAmount = Math.min(stack.getCount(), inputInv.getSlotLimit(INPUT_SLOT) - current.getCount());
                        if (insertAmount <= 0 && !current.isEmpty()) return stack;

                        if (current.isEmpty()) {
                            ItemStack toActuallyInsert = stack.copy();
                            toActuallyInsert.setCount(insertAmount);
                            inputInv.setStackInSlot(INPUT_SLOT, toActuallyInsert);
                            stack.shrink(insertAmount);
                        } else if (ItemHelper.canItemStackAmountsStack(current, stack)) {
                            current.grow(insertAmount);
                            stack.shrink(insertAmount);
                        } else {
                            return stack;
                        }

                        if (inputInv.remainingTime == -1 && !inputInv.appliedRecipe) {
                            be.startProcessingRecipe(inputInv.getStackInSlot(INPUT_SLOT));
                        }
                        return stack;
                    } else {
                        ItemStack current = inputInv.getStackInSlot(INPUT_SLOT);
                        if (current.isEmpty()) {
                            int insertAmount = Math.min(stack.getCount(), inputInv.getSlotLimit(INPUT_SLOT));
                            ItemStack remainder = stack.copy();
                            remainder.shrink(insertAmount);
                            return remainder;
                        }
                        if (ItemHelper.canItemStackAmountsStack(current, stack)) {
                            int space = inputInv.getSlotLimit(INPUT_SLOT) - current.getCount();
                            int insertAmount = Math.min(stack.getCount(), space);
                            ItemStack remainder = stack.copy();
                            remainder.shrink(insertAmount);
                            return remainder;
                        }
                    }
                }
                return stack;
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.extractItem(slot, amount, simulate);
            }
            if (slot == 0) {
                if (inputInv.appliedRecipe && inputInv.remainingTime == 0 && be.canProcess()) {
                    return inputInv.extractItem(INPUT_SLOT, amount, simulate);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == 0) {
                return inputInv.getSlotLimit(INPUT_SLOT);
            }
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.getSlotLimit(slot);
            }
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == 0 && be.canProcess()) {
                return be.getMatchingRecipe(new SingleRecipeInput(stack)).isPresent();
            }
            return false;
        }
    }
}