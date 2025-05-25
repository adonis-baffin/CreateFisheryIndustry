package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.CreateFisheryMod;
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
import net.createmod.catnip.math.VecHelper; // 确保这个导入是正确的
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
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
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
import net.neoforged.neoforge.common.IShearable;
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
    public static final int OUTPUT_INV_PRIMARY_SLOT_TEMP = 0; // 主输出的临时槽位
    private static final int OUTPUT_INV_SIZE = 1 + MAX_SECONDARY_OUTPUTS_STORAGE; // 1个主输出临时槽 + 多个副产物槽

    private final Map<UUID, Long> entityCooldowns = new HashMap<>();
    private static final int ARMADILLO_TURTLE_COOLDOWN_TICKS = 20 * 60 * 1; // 1分钟冷却

    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inputInventory = new ProcessingInventory(this::startProcessingRecipe)
                .withSlotLimit(true);
        // inputInventory.remainingTime = -1; // 默认是-1，由ProcessingInventory构造函数处理

        outputInventory = new ItemStackHandler(OUTPUT_INV_SIZE) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sendData();
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (slot == OUTPUT_INV_PRIMARY_SLOT_TEMP) {
                    return stack; // 不允许外部直接插入主输出临时槽
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
                inputInventory.remainingTime = 10;
                inputInventory.recipeDuration = 10;
                inputInventory.appliedRecipe = false; // 让它走输入动画
                sendData();
                return;
            }

            int timePerItem = recipe.getProcessingDuration();
            if (timePerItem == 0) timePerItem = 100;
            inputInventory.remainingTime = timePerItem * stackInInputSlot.getCount();
            inputInventory.recipeDuration = inputInventory.remainingTime;
            inputInventory.appliedRecipe = false; // 初始为 false
        } else {
            // 无配方物品
            inputInventory.remainingTime = 10;
            inputInventory.recipeDuration = 10;
            inputInventory.appliedRecipe = false; // 初始为 false
        }
        sendData();
    }

    private boolean canStoreAllSecondaries(List<ItemStack> secondaryProducts) {
        if (secondaryProducts.isEmpty()) {
            return true;
        }
        ItemStackHandler tempSecondaryInv = new ItemStackHandler(MAX_SECONDARY_OUTPUTS_STORAGE);
        for (int i = 0; i < MAX_SECONDARY_OUTPUTS_STORAGE; i++) {
            // 复制当前副产物槽的内容到临时库存
            tempSecondaryInv.setStackInSlot(i, outputInventory.getStackInSlot(i + 1).copy());
        }

        for (ItemStack product : secondaryProducts) {
            ItemStack remainder = product.copy();
            for (int i = 0; i < tempSecondaryInv.getSlots(); i++) {
                remainder = tempSecondaryInv.insertItem(i, remainder.copy(), false); // 模拟插入到临时库存
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) { // 如果模拟插入后仍有剩余，说明存不下
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
            if (clientPacket) playEvent = ItemStack.EMPTY; // 客户端发送后清空
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
        // 客户端只在速度不为0且playEvent非空时播放声音和粒子
        if (getSpeed() == 0 || playEvent.isEmpty() || level == null)
            return;

        boolean isWood = false;
        Item item = playEvent.getItem();
        if (item instanceof BlockItem) {
            Block block = ((BlockItem) item).getBlock();
            isWood = block.getSoundType(block.defaultBlockState(), level, worldPosition, null) == SoundType.WOOD;
        }
        spawnEventParticles(playEvent); // 产生事件粒子
        playEvent = ItemStack.EMPTY; // 清空事件标记

        if (!isWood)
            AllSoundEvents.SAW_ACTIVATE_STONE.playAt(level, worldPosition, 3, 1, true);
        else
            AllSoundEvents.SAW_ACTIVATE_WOOD.playAt(level, worldPosition, 3, 1, true);
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
            Vec3 m = randomOffset.add(0, 0.25f, 0); // 稍微向上
            level.addParticle(particleData, v.x, v.y, v.z, m.x, m.y, m.z);
        }
    }

    protected void spawnParticles(ItemStack stack) {
        if (stack == null || stack.isEmpty() || level == null || !canProcess()) return;

        ParticleOptions particleData;
        float particleSpeed = 0.125f;
        if (stack.getItem() instanceof BlockItem blockItem) {
            particleData = new BlockParticleOption(ParticleTypes.BLOCK, blockItem.getBlock().defaultBlockState());
            particleSpeed = 0.2f; // 方块粒子速度稍快
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
        // 调整offset计算以匹配物品在轨道上的位置
        // 如果尚未应用配方 (输入阶段)，物品从1.0 -> 0.5 (视觉上)
        // 如果已应用配方 (输出阶段)，物品从0.5 -> 0.0 (视觉上)
        if (!inputInventory.appliedRecipe) {
            offsetRatio = 1.0f - (0.5f * (1.0f - offsetRatio)); // 映射到 [0.5, 1.0] 区间
        } else {
            offsetRatio *= 0.5f; // 映射到 [0, 0.5] 区间
        }
        // 上述offsetRatio是物品从起始点到终点的进度（0到1）
        // 实际渲染时，这个offset会被再次处理

        // 我们需要粒子从当前物品的视觉位置发出
        // 渲染器中的offset计算：
        // if (!be.inputInventory.appliedRecipe) offset_render += 1; offset_render /= 2;
        // 所以，如果 appliedRecipe = false, 视觉offset = (逻辑offset+1)/2
        // 如果 appliedRecipe = true, 视觉offset = 逻辑offset/2
        float visualOffsetRatio;
        if (inputInventory.recipeDuration != 0) {
            visualOffsetRatio = (float) (inputInventory.remainingTime) / inputInventory.recipeDuration;
        } else {
            visualOffsetRatio = 0;
        }

        if (!inputInventory.appliedRecipe) {
            visualOffsetRatio = (visualOffsetRatio + 1f) / 2f;
        } else {
            visualOffsetRatio /= 2f;
        }
        if (getSpeed() < 0 ^ (!getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE))) {
            visualOffsetRatio = 1f - visualOffsetRatio;
        }
        // Clamp to avoid particles spawning too far
        visualOffsetRatio = Mth.clamp(visualOffsetRatio, 0.125f, 0.875f);


        // 粒子位置应基于物品的视觉位置，不是简单的中心点加偏移
        // itemMovementVec 指向物品移动方向
        // 粒子应该从物品当前位置发出，向 itemMovementVec 的反方向运动
        Vec3 particlePos = center.add(itemMovementVec.x() * (visualOffsetRatio - 0.5), 0.45, itemMovementVec.z() * (visualOffsetRatio - 0.5));


        Vec3 particleMotion = new Vec3(-itemMovementVec.x() * particleSpeed, r.nextFloat() * particleSpeed * 0.5f, -itemMovementVec.z() * particleSpeed); // 减小Y轴速度

        level.addParticle(particleData, particlePos.x(), particlePos.y(), particlePos.z(), particleMotion.x, particleMotion.y, particleMotion.z);
    }


    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        if (level.isClientSide) {
            if (!playEvent.isEmpty()) {
                tickAudio();
            }
            return;
        }

        // --- Server-side logic ---

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

        float processingSpeed = Mth.clamp(Math.abs(getSpeed()) / 24f, 1f, 128f);
        inputInventory.remainingTime -= processingSpeed;

        if (inputInventory.remainingTime > 0 && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
            spawnParticles(inputInventory.getStackInSlot(INPUT_SLOT));
        }

        if (inputInventory.remainingTime < 5 && !inputInventory.appliedRecipe) {
            Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(new SingleRecipeInput(inputInventory.getStackInSlot(INPUT_SLOT)));

            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()){
                playEvent = inputInventory.getStackInSlot(INPUT_SLOT).copy();
            }

            if (recipeHolder.isPresent()) {
                PeelingRecipe recipe = recipeHolder.get().value();
                List<ItemStack> totalPotentialSecondaryProducts = new ArrayList<>();
                for (int i = 0; i < inputInventory.getStackInSlot(INPUT_SLOT).getCount(); i++) {
                    totalPotentialSecondaryProducts.addAll(recipe.rollResultsFor(recipe.getSecondaryOutputs()));
                }
                if (canStoreAllSecondaries(totalPotentialSecondaryProducts)) {
                    applyRecipeProducts(recipeHolder.get());
                }
            }
            // For no-recipe items, applyRecipeProducts is not called.
            // The input item remains in the input slot (logically) until ejected.

            inputInventory.appliedRecipe = true;
            inputInventory.recipeDuration = 30; // Output animation time
            inputInventory.remainingTime = inputInventory.recipeDuration;
            sendData();
            return;
        }

        if (inputInventory.remainingTime <= 0) {
            inputInventory.remainingTime = 0;
            ejectInputOrPrimaryOutput();
        }
    }

    private void ejectInputOrPrimaryOutput() {
        ItemStack stackToEject;
        boolean isPassThroughOutput;

        if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
            stackToEject = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP);
            isPassThroughOutput = false;
        } else if (inputInventory.appliedRecipe && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
            stackToEject = inputInventory.getStackInSlot(INPUT_SLOT);
            isPassThroughOutput = true;
        } else {
            if (inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() &&
                    outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
                sendData();
            }
            return;
        }

        Vec3 itemMovement = getItemMovementVec();
        Direction itemMovementFacing = Direction.getNearest(itemMovement.x, itemMovement.y, itemMovement.z);

        DirectBeltInputBehaviour funnelBehaviour = getBehaviour(DirectBeltInputBehaviour.TYPE);
        if (funnelBehaviour != null) {
            ItemStack currentStackCopy = stackToEject.copy(); // Work with a copy for funnel
            ItemStack funnelRemainder = funnelBehaviour.tryExportingToBeltFunnel(currentStackCopy, itemMovementFacing.getOpposite(), false);
            if (funnelRemainder != null && funnelRemainder.getCount() < currentStackCopy.getCount()) {
                updateEjectedStack(isPassThroughOutput, funnelRemainder);
                if (funnelRemainder.isEmpty()) {
                    resetStateAfterEjectionOrTryNext(isPassThroughOutput);
                }
                // No sendData here, resetStateAfterEjectionOrTryNext will handle it
                return;
            }
        }

        BlockPos nextPos = worldPosition.offset(BlockPos.containing(itemMovement));
        DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);
        if (beltBehaviour != null && beltBehaviour.canInsertFromSide(itemMovementFacing)) {
            ItemStack currentStackCopy = stackToEject.copy(); // Work with a copy for belt
            ItemStack beltRemainder = beltBehaviour.handleInsertion(currentStackCopy, itemMovementFacing, false);
            if (beltRemainder.getCount() < currentStackCopy.getCount()) {
                updateEjectedStack(isPassThroughOutput, beltRemainder);
                if (beltRemainder.isEmpty()) {
                    resetStateAfterEjectionOrTryNext(isPassThroughOutput);
                }
                // No sendData here
                return;
            }
        }

        Vec3 outPos = VecHelper.getCenterOf(worldPosition).add(itemMovement.scale(.5f)).add(0, .5, 0);
        Vec3 outMotion = itemMovement.scale(.0625).add(0, .125, 0);
        ItemEntity entityOut = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stackToEject.copy());
        entityOut.setDeltaMovement(outMotion);
        level.addFreshEntity(entityOut);

        updateEjectedStack(isPassThroughOutput, ItemStack.EMPTY);
        resetStateAfterEjectionOrTryNext(isPassThroughOutput);

        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        // sendData() is handled by resetStateAfterEjectionOrTryNext
    }

    private void updateEjectedStack(boolean isPassThrough, ItemStack remainder) {
        if (isPassThrough) {
            inputInventory.setStackInSlot(INPUT_SLOT, remainder);
        } else {
            outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, remainder);
        }
        // setChanged and sendData will be called by the calling context (e.g., resetStateAfterEjectionOrTryNext)
    }

    private void resetStateAfterEjectionOrTryNext(boolean wasPassThroughOutput) {
        boolean inputSlotNowEmpty = inputInventory.getStackInSlot(INPUT_SLOT).isEmpty();
        boolean primaryOutputSlotNowEmpty = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty();

        if (wasPassThroughOutput) {
            if (inputSlotNowEmpty) { // Input item fully passed through and ejected
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
            } else { // Part of input item passed through, start processing remainder
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        } else { // Was a primary output ejection
            if (primaryOutputSlotNowEmpty) { // Primary output fully ejected
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
                // Check if new items in input slot to start next recipe
                if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                    startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
                }
            }
            // If primary output slot is not empty (e.g. partial ejection),
            // next tick's ejectInputOrPrimaryOutput will handle it.
        }
        setChanged();
        sendData();
    }

    private void applyRecipeProducts(RecipeHolder<PeelingRecipe> recipeHolder) {
        ItemStack inputStackForRecipe = inputInventory.getStackInSlot(INPUT_SLOT).copy();
        if (inputStackForRecipe.isEmpty()) return;

        PeelingRecipe recipe = recipeHolder.value();
        int itemsToProcess = inputStackForRecipe.getCount();

        inputInventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY); // Consume input

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
            ItemStack finalPrimaryOutput = ItemStack.EMPTY;
            // Consolidate primary outputs if they are the same item
            for (ItemStack collectedStack : collectedPrimaryOutputs) {
                if (finalPrimaryOutput.isEmpty()) {
                    finalPrimaryOutput = collectedStack.copy();
                } else if (ItemStack.isSameItemSameComponents(finalPrimaryOutput, collectedStack)) {
                    finalPrimaryOutput.grow(collectedStack.getCount());
                } else {
                    // Handle multiple different primary outputs - drop extras for now
                    CreateFisheryMod.LOGGER.warn("Peeling recipe resulted in multiple different primary output types. Dropping extras.");
                    ItemEntity itemEntity = new ItemEntity(level,
                            worldPosition.getX() + 0.5, worldPosition.getY() + 0.75, worldPosition.getZ() + 0.5,
                            collectedStack.copy());
                    itemEntity.setDeltaMovement(VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.1f).add(0, 0.2f, 0));
                    level.addFreshEntity(itemEntity);
                }
            }
            outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, finalPrimaryOutput);
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
        // sendData(); // Will be handled by tick or resetStateAfterEjectionOrTryNext
    }

    public Vec3 getItemMovementVec() {
        Direction facing = getBlockState().getValue(MechanicalPeelerBlock.FACING);
        if (facing == Direction.UP) {
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            int speedSign = getSpeed() < 0 ? 1 : -1; // Inverted to match saw's visual: positive speed moves item "forward"
            return new Vec3(speedSign * (alongLocalX ? 1 : 0), 0, speedSign * (alongLocalX ? 0 : 1));
        }
        if (facing.getAxis().isHorizontal()) {
            // For horizontal peelers, item movement is typically "out" of the peeler face
            return Vec3.atLowerCornerOf(facing.getNormal());
        }
        return Vec3.ZERO; // Should not happen for UP or Horizontal
    }

    private Optional<RecipeHolder<PeelingRecipe>> getMatchingRecipe(SingleRecipeInput input) {
        if (level == null || input.item().isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(CreateFisheryRecipeTypes.PEELING.getType(), input, level);
    }

    public void insertItem(ItemEntity entity) {
        if (level == null || level.isClientSide || !canProcess() || !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() || !entity.isAlive())
            return;
        ItemStack toInsert = entity.getItem().copy();
        ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, toInsert, false); // ProcessingInventory's insertItem handles this
        if (!ItemStack.matches(remainder, toInsert)) {
            entity.setItem(remainder);
            if (remainder.isEmpty()) entity.discard();
            // startProcessingRecipe will be called by ProcessingInventory's onContentsChanged if a new item is inserted
            // and remainingTime is -1. We don't need to explicitly call it here if using ProcessingInventory's callback.
            // However, if we want to force start immediately:
            if (inputInventory.remainingTime == -1 && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe) {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        }
    }

    protected boolean canProcess() {
        return getSpeed() != 0;
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
        for (int i = 1; i <= MAX_SECONDARY_OUTPUTS_STORAGE; i++) { // Slot 0 is primary temp, 1 to MAX_SECONDARY_OUTPUTS_STORAGE are secondaries
            remainder = outputInventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false; // Could not store all of it
    }

    private boolean isEntityOnCooldown(UUID entityId) {
        if (level == null) return true; // Can't check game time
        return entityCooldowns.containsKey(entityId) &&
                (level.getGameTime() - entityCooldowns.get(entityId)) < ARMADILLO_TURTLE_COOLDOWN_TICKS;
    }

    private void setEntityCooldown(UUID entityId) {
        if (level == null) return;
        entityCooldowns.put(entityId, level.getGameTime());
    }

    private void spawnProcessingParticlesEffect() {
        // This method is client-side only due to its current OnlyIn(Dist.CLIENT) in the original,
        // but entity processing logic is server-side.
        // For server-side particle spawning, use ServerLevel.sendParticles
        // For simplicity, we'll keep the client-side check here, but ideally, send a packet.
        if (level == null || level.isClientSide) { // Check if client side to spawn particles
            RandomSource r = level.random;
            Vec3 center = VecHelper.getCenterOf(this.worldPosition);
            for (int i = 0; i < 5; i++) {
                double motionX = (r.nextDouble() - 0.5D) * 0.1D;
                double motionY = r.nextDouble() * 0.1D + 0.1D;
                double motionZ = (r.nextDouble() - 0.5D) * 0.1D;
                level.addParticle(ParticleTypes.SNOWFLAKE, // Or a more fitting particle
                        center.x + (r.nextDouble() - 0.5D) * 0.6D,
                        center.y + 1.1D, // Above the peeler
                        center.z + (r.nextDouble() - 0.5D) * 0.6D,
                        motionX, motionY, motionZ);
            }
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
        // No explicit capability invalidation needed with NeoForge's system unless doing something very custom.
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && !level.isClientSide) {
            ItemHelper.dropContents(level, worldPosition, inputInventory); // Drops input inventory
            // Drop output inventory (including primary temp and secondaries)
            for (int i = 0; i < outputInventory.getSlots(); ++i) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), outputInventory.getStackInSlot(i));
            }
        }
    }

    // PeelerItemHandler: This is for exposing items to pipes/hoppers.
    // Needs to be carefully considered how input/output is handled.
    // Input should only go to INPUT_SLOT.
    // Output should only come from secondary output slots (1 to MAX_SECONDARY_OUTPUTS_STORAGE).
    // The OUTPUT_INV_PRIMARY_SLOT_TEMP is internal and shouldn't be directly accessible for extraction by pipes.
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
            // Expose 1 input slot + secondary output slots
            return 1 + MAX_SECONDARY_OUTPUTS_STORAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 0) { // Input slot
                return inputInv.getStackInSlot(INPUT_SLOT);
            }
            // Slots 1 to MAX_SECONDARY_OUTPUTS_STORAGE for secondary outputs
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.getStackInSlot(slot); // outputInv slot 'i' maps to handler slot 'i'
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!be.canProcess()) return stack;
            if (slot == 0) { // Only allow insertion into the logical input slot
                // Let ProcessingInventory handle the insertion logic, it will call startProcessingRecipe
                return inputInv.insertItem(INPUT_SLOT, stack, simulate);
            }
            return stack; // Do not allow insertion into output slots via this handler
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Allow extraction from secondary output slots
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.extractItem(slot, amount, simulate);
            }
            // Do not allow extraction from input slot or primary temp output slot via this handler
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
                // Check if there's a recipe for the item.
                // This also prevents inserting items if there's no recipe,
                // which aligns with the goal of having a full animation pass even for "no recipe" items.
                // If we want to allow "no recipe" items to pass through, this check might be too strict.
                // However, for automated input, usually, we only want valid recipe inputs.
                return be.getMatchingRecipe(new SingleRecipeInput(stack)).isPresent() ||
                        (be.getMatchingRecipe(new SingleRecipeInput(stack)).isEmpty()); // Allow if no recipe for passthrough
            }
            return false; // Output slots are not valid for insertion via this handler
        }
    }
}