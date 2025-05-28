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
import com.tterrag.registrate.util.nullness.NonNullConsumer;
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

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class MechanicalPeelerBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

    // 常量定义 - 统一的动画时间
    private static final float INPUT_ANIMATION_TIME = 10f;  // 输入阶段动画时间
    private static final float OUTPUT_ANIMATION_TIME = 10f; // 输出阶段动画时间

    public ProcessingInventory inputInventory;
    public ItemStackHandler outputInventory;

    private ItemStack playEvent;
    public final IItemHandler itemHandler;

    public static final int INPUT_SLOT = 0;
    private static final int MAX_SECONDARY_OUTPUTS_STORAGE = 4;
    public static final int OUTPUT_INV_PRIMARY_SLOT_TEMP = 0;
    private static final int OUTPUT_INV_SIZE = 1 + MAX_SECONDARY_OUTPUTS_STORAGE;

    private final Map<UUID, Long> entityCooldowns = new HashMap<>();
    private static final int ARMADILLO_TURTLE_COOLDOWN_TICKS = 20 * 60 * 1;

    private Float cachedSpeed = null;
    private long lastSpeedCheck = 0;

    // 新增字段：记录当前物品的输入方向
    private Direction currentInputDirection = null;

    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inputInventory = new CustomProcessingInventory(this, this::startProcessingRecipe)
                .withSlotLimit(true);
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

        // 检查配方（但不使用配方的处理时间）
        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(new SingleRecipeInput(stackInInputSlot));

        if (recipeHolder.isPresent()) {
            // 预检查副产物空间
            PeelingRecipe recipe = recipeHolder.get().value();
            List<ItemStack> totalPotentialSecondaryProducts = new ArrayList<>();
            for (int i = 0; i < stackInInputSlot.getCount(); i++) {
                totalPotentialSecondaryProducts.addAll(recipe.rollResultsFor(recipe.getSecondaryOutputs()));
            }
            if (!canStoreAllSecondaries(totalPotentialSecondaryProducts)) {
            }
        } else {
        }

        // 统一的动画时间
        inputInventory.remainingTime = INPUT_ANIMATION_TIME;
        inputInventory.recipeDuration = INPUT_ANIMATION_TIME;
        inputInventory.appliedRecipe = false;

        setChanged();
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

    private boolean isDirectionValidForAxis(Direction dir) {
        if (!dir.getAxis().isHorizontal()) return false;

        boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);

        if (alongLocalX) {
            // 旋转轴是 X 轴（东西向），只允许从东西方向输入（调转90度）
            return dir == Direction.EAST || dir == Direction.WEST;
        } else {
            // 旋转轴是 Z 轴（南北向），只允许从南北方向输入（调转90度）
            return dir == Direction.NORTH || dir == Direction.SOUTH;
        }
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        behaviours.add(new DirectBeltInputBehaviour(this)
                .allowingBeltFunnelsWhen(this::canAcceptInput)
                .setInsertionHandler((transported, side, simulate) -> {
                    Direction inputDir = side.getOpposite();

                    if (!canAcceptInput() || !isDirectionValidForAxis(inputDir))
                        return transported.stack;

                    if (!simulate) {
                        currentInputDirection = inputDir;
                    }

                    ItemStack stack = transported.stack;
                    ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, stack, simulate);

                    if (!simulate && remainder.getCount() < stack.getCount() && inputInventory.remainingTime == -1) {
                        startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
                    }

                    return remainder;
                }));
    }


    private boolean canAcceptInput() {
        return canProcess() &&
                inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() &&
                inputInventory.remainingTime <= 0 &&
                outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        // 确保每次 setChanged 都会触发数据同步
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public void sendData() {
        if (level != null && !level.isClientSide) {
        }
        super.sendData();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.put("InputInventory", inputInventory.serializeNBT(registries));
        compound.put("OutputInventory", outputInventory.serializeNBT(registries));

        compound.putBoolean("AppliedRecipe", inputInventory.appliedRecipe);
        compound.putFloat("RemainingTime", inputInventory.remainingTime);
        compound.putFloat("RecipeDuration", inputInventory.recipeDuration);

        if (currentInputDirection != null) {
            compound.putString("InputDirection", currentInputDirection.getName());
        }

        super.write(compound, registries, clientPacket);

        if (!clientPacket) {
            CompoundTag cooldownsTag = new CompoundTag();
            entityCooldowns.forEach((uuid, time) -> cooldownsTag.putLong(uuid.toString(), time));
            compound.put("EntityCooldowns", cooldownsTag);
        }

        if (clientPacket && !playEvent.isEmpty()) {
            compound.put("PlayEvent", playEvent.saveOptional(registries));
            playEvent = ItemStack.EMPTY;
        }

        if (clientPacket) {

        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        inputInventory.deserializeNBT(registries, compound.getCompound("InputInventory"));
        outputInventory.deserializeNBT(registries, compound.getCompound("OutputInventory"));

        if (compound.contains("AppliedRecipe")) {
            inputInventory.appliedRecipe = compound.getBoolean("AppliedRecipe");
        }
        if (compound.contains("RemainingTime")) {
            inputInventory.remainingTime = compound.getFloat("RemainingTime");
        }
        if (compound.contains("RecipeDuration")) {
            inputInventory.recipeDuration = compound.getFloat("RecipeDuration");
        }

        if (compound.contains("InputDirection")) {
            currentInputDirection = Direction.byName(compound.getString("InputDirection"));
        }

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
                    }
                }
            }
        }

        if (compound.contains("PlayEvent"))
            playEvent = ItemStack.parseOptional(registries, compound.getCompound("PlayEvent"));

        if (clientPacket) {

        }
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(getBlockPos()).inflate(.125f);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (getSpeed() == 0 || playEvent.isEmpty() || level == null)
            return;

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
        // 移除对 speed 和 AXIS_ALONG_FIRST_COORDINATE 的依赖，固定方向
        visualOffsetRatio = Mth.clamp(visualOffsetRatio, 0.125f, 0.875f);

        Vec3 particlePos = center.add(itemMovementVec.x() * (visualOffsetRatio - 0.5), 0.45, itemMovementVec.z() * (visualOffsetRatio - 0.5));
        Vec3 particleMotion = new Vec3(-itemMovementVec.x() * particleSpeed, r.nextFloat() * particleSpeed * 0.5f, -itemMovementVec.z() * particleSpeed);

        level.addParticle(particleData, particlePos.x(), particlePos.y(), particlePos.z(), particleMotion.x, particleMotion.y, particleMotion.z);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null) return;

        // 调试输出（降低频率）
        if (level.getGameTime() % 20 == 0 && inputInventory.remainingTime > 0) {

        }

        // 传送带检测（仅服务端）
        if (!level.isClientSide && level.getGameTime() % 5 == 0) {
            Vec3 itemMovement = getItemMovementVec();
            BlockPos inputPos = worldPosition.offset(BlockPos.containing(itemMovement.reverse()));
            DirectBeltInputBehaviour inputBelt = BlockEntityBehaviour.get(level, inputPos, DirectBeltInputBehaviour.TYPE);
            if (inputBelt != null) {
            }
        }

        // 客户端处理
        if (level.isClientSide) {
            if (!playEvent.isEmpty()) {
                tickAudio();
            }

            // 客户端自行更新动画进度以保持流畅
            if (inputInventory.remainingTime > 0 && canProcess()) {
                float speed = Math.abs(getSpeed()) / 24f;
                inputInventory.remainingTime = Math.max(0, inputInventory.remainingTime - speed);
            }
            return;
        }

        // === 服务端逻辑 ===

        if (!canProcess() || getSpeed() == 0) {
            // 机器停止但有待处理的物品
            if (inputInventory.remainingTime <= 0) {
                if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
                    ejectInputOrPrimaryOutput();
                } else if (inputInventory.appliedRecipe && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                    ejectInputOrPrimaryOutput();
                }
            }
            return;
        }

        // 处理进行中
        if (inputInventory.remainingTime > 0) {
            float speed = Math.abs(getSpeed()) / 24f;
            inputInventory.remainingTime -= speed;

            // 生成粒子
            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                spawnParticles(inputInventory.getStackInSlot(INPUT_SLOT));
            }

            // 输入阶段结束 - 应用配方
            if (inputInventory.remainingTime <= 0 && !inputInventory.appliedRecipe) {

                // 播放音效
                if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                    playEvent = inputInventory.getStackInSlot(INPUT_SLOT).copy();
                }

                // 应用配方
                Optional<RecipeHolder<PeelingRecipe>> recipeHolder =
                        getMatchingRecipe(new SingleRecipeInput(inputInventory.getStackInSlot(INPUT_SLOT)));

                if (recipeHolder.isPresent()) {
                    applyRecipeProducts(recipeHolder.get());
                }

                // 切换到输出阶段
                inputInventory.appliedRecipe = true;
                inputInventory.recipeDuration = OUTPUT_ANIMATION_TIME;
                inputInventory.remainingTime = OUTPUT_ANIMATION_TIME;

                // 强制同步
                setChanged();
                sendData();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

                return;
            }

            // 输出阶段结束
            if (inputInventory.remainingTime <= 0 && inputInventory.appliedRecipe) {
                ejectInputOrPrimaryOutput();
            }
        } else {
            // 空闲状态 - 检查新输入
            if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe) {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
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
            inputInventory.remainingTime = -1;
            inputInventory.appliedRecipe = false;
            inputInventory.recipeDuration = 0;
            setChanged();
            sendData();
            return;
        }

        Vec3 itemMovement = getItemMovementVec();
        // 输出方向是输入方向的反向
        Vec3 outputMovement = itemMovement.scale(-1);
        Direction outputFacing = Direction.getNearest(outputMovement.x, outputMovement.y, outputMovement.z);

        // 首先尝试通过 DirectBeltInputBehaviour 导出到传送带漏斗
        ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
                .tryExportingToBeltFunnel(stackToEject, outputFacing.getOpposite(), false);

        if (tryExportingToBeltFunnel != null) {
            if (tryExportingToBeltFunnel.getCount() != stackToEject.getCount()) {
                // 部分导出成功
                updateEjectedStack(isPassThroughOutput, tryExportingToBeltFunnel);
                return;
            }
            if (!tryExportingToBeltFunnel.isEmpty()) {
                // 导出失败，继续尝试其他方法

            } else {
                // 完全导出成功
                updateEjectedStack(isPassThroughOutput, ItemStack.EMPTY);

                resetStateAfterEjectionOrTryNext(isPassThroughOutput);
                return;
            }
        }

        // 尝试直接输出到传送带
        BlockPos nextPos = worldPosition.offset(BlockPos.containing(outputMovement));
        DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);

        if (beltBehaviour != null && beltBehaviour.canInsertFromSide(outputFacing)) {
            if (level.isClientSide && !isVirtual())
                return;

            ItemStack currentStackCopy = stackToEject.copy();
            ItemStack beltRemainder = beltBehaviour.handleInsertion(currentStackCopy, outputFacing, false);

            if (!ItemStack.matches(beltRemainder, currentStackCopy)) {
                // 有变化说明至少部分物品被接受
                updateEjectedStack(isPassThroughOutput, beltRemainder);
                if (beltRemainder.isEmpty()) {
                    resetStateAfterEjectionOrTryNext(isPassThroughOutput);
                } else {
                }
                setChanged();
                sendData();
                return;
            }
        }

        // 如果无法输出到传送带，则作为实体弹出
        Vec3 outPos = VecHelper.getCenterOf(worldPosition).add(outputMovement.scale(.5f)).add(0, .5, 0);
        Vec3 outMotion = outputMovement.scale(.0625).add(0, .125, 0);
        ItemEntity entityOut = new ItemEntity(level, outPos.x, outPos.y, outPos.z, stackToEject.copy());
        entityOut.setDeltaMovement(outMotion);
        level.addFreshEntity(entityOut);



        updateEjectedStack(isPassThroughOutput, ItemStack.EMPTY);
        resetStateAfterEjectionOrTryNext(isPassThroughOutput);

        level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
    }

    private void updateEjectedStack(boolean isPassThrough, ItemStack remainder) {
        if (isPassThrough) {
            inputInventory.setStackInSlot(INPUT_SLOT, remainder);
        } else {
            outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, remainder);
        }
        setChanged();
        sendData();
    }

    private void resetStateAfterEjectionOrTryNext(boolean wasPassThroughOutput) {
        boolean inputSlotNowEmpty = inputInventory.getStackInSlot(INPUT_SLOT).isEmpty();
        boolean primaryOutputSlotNowEmpty = outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty();

        if (wasPassThroughOutput) {
            if (inputSlotNowEmpty) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
                currentInputDirection = null; // 清除方向记录
            } else {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        } else {
            if (primaryOutputSlotNowEmpty) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
                currentInputDirection = null; // 清除方向记录
                if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                    startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
                }
            }
        }
        setChanged();
        sendData();
    }

    private void applyRecipeProducts(RecipeHolder<PeelingRecipe> recipeHolder) {
        ItemStack inputStackForRecipe = inputInventory.getStackInSlot(INPUT_SLOT).copy();
        if (inputStackForRecipe.isEmpty()) return;



        PeelingRecipe recipe = recipeHolder.value();
        int itemsToProcess = inputStackForRecipe.getCount();

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

        ItemStack finalPrimaryOutput = ItemStack.EMPTY;
        for (ItemStack collectedStack : collectedPrimaryOutputs) {
            if (finalPrimaryOutput.isEmpty()) {
                finalPrimaryOutput = collectedStack.copy();
            } else if (ItemStack.isSameItemSameComponents(finalPrimaryOutput, collectedStack)) {
                int maxStack = finalPrimaryOutput.getMaxStackSize();
                int canAdd = Math.min(collectedStack.getCount(), maxStack - finalPrimaryOutput.getCount());
                if (canAdd > 0) {
                    finalPrimaryOutput.grow(canAdd);
                    collectedStack.shrink(canAdd);
                }

                if (!collectedStack.isEmpty()) {
                    if (!tryStoreItemInSecondaryOutput(collectedStack)) {
                        Vec3 dropPos = VecHelper.getCenterOf(worldPosition).add(0, 0.75, 0);
                        ItemEntity overflow = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, collectedStack);
                        level.addFreshEntity(overflow);
                    }
                }
            }
        }

        outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, finalPrimaryOutput);

        for (ItemStack secondaryStack : collectedSecondaryOutputs) {
            if (secondaryStack.isEmpty()) continue;

            ItemStack remainderToStore = secondaryStack.copy();
            for (int slot = 1; slot < outputInventory.getSlots(); slot++) {
                remainderToStore = outputInventory.insertItem(slot, remainderToStore, false);
                if (remainderToStore.isEmpty()) break;
            }

            if (!remainderToStore.isEmpty()) {
                Vec3 dropPos = VecHelper.getCenterOf(worldPosition).add(0, 0.75, 0);
                ItemEntity overflow = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, remainderToStore);
                level.addFreshEntity(overflow);
            }
        }

        setChanged();
        sendData();
    }

    public Vec3 getItemMovementVec() {
        Direction facing = getBlockState().getValue(MechanicalPeelerBlock.FACING);

        if (facing == Direction.UP) {
            // 如果有记录的输入方向，返回从输入到输出的向量
            if (currentInputDirection != null && currentInputDirection.getAxis().isHorizontal()) {
                // 返回输入方向的向量（物品沿着这个方向移动）
                return Vec3.atLowerCornerOf(currentInputDirection.getNormal());
            }

            // 否则使用默认值（根据轴向）
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            if (alongLocalX) {
                // 旋转轴是 X 轴，默认从北向南移动
                return new Vec3(0, 0, 1);
            } else {
                // 旋转轴是 Z 轴，默认从西向东移动
                return new Vec3(1, 0, 0);
            }
        }

        if (facing.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(facing.getNormal());
        }

        return Vec3.ZERO;
    }

    // 新方法：处理来自传送带的输入
    public void insertFromBelt(ItemStack stack, Direction from) {
        if (!canAcceptInput()) {
            return;
        }

        currentInputDirection = from;


        ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, stack, false);
        if (remainder.getCount() < stack.getCount() && inputInventory.remainingTime == -1) {
            startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
        }
    }

    private Direction getInputDirection() {
        if (level == null) return null;

        // 检查所有水平方向
        for (Direction dir : Direction.values()) {
            if (!dir.getAxis().isHorizontal()) continue;

            BlockPos checkPos = worldPosition.relative(dir);
            DirectBeltInputBehaviour inputBehaviour =
                    BlockEntityBehaviour.get(level, checkPos, DirectBeltInputBehaviour.TYPE);

            if (inputBehaviour != null && inputBehaviour.canInsertFromSide(dir.getOpposite())) {
                // 找到了输入传送带
                return dir;
            }
        }

        // 如果没有找到传送带，根据轴向返回默认方向
        boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        if (alongLocalX) {
            return Direction.WEST; // 默认从西边输入
        } else {
            return Direction.NORTH; // 默认从北边输入
        }
    }

    private Optional<RecipeHolder<PeelingRecipe>> getMatchingRecipe(SingleRecipeInput input) {
        if (level == null || input.item().isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(CreateFisheryRecipeTypes.PEELING.getType(), input, level);
    }

    public void insertItem(ItemEntity entity) {
        if (level == null || level.isClientSide || !entity.isAlive())
            return;

        if (!canAcceptInput()) {

            return;
        }

        // 记录物品的来源方向
        Vec3 entityPos = entity.position();
        Vec3 blockCenter = VecHelper.getCenterOf(worldPosition);
        Vec3 diff = entityPos.subtract(blockCenter);

        Direction inputDir;
        if (Math.abs(diff.x) > Math.abs(diff.z)) {
            inputDir = diff.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            inputDir = diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }

        // 检查输入方向是否有效
        if (!isDirectionValidForAxis(inputDir)) {
            return;
        }

        currentInputDirection = inputDir;

        ItemStack toInsert = entity.getItem().copy();

        ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, toInsert, false);
        if (!ItemStack.matches(remainder, toInsert)) {
            entity.setItem(remainder);
            if (remainder.isEmpty()) entity.discard();

            if (inputInventory.remainingTime == -1 && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        }
    }

    protected boolean canProcess() {
        // 每个 tick 只计算一次速度
        if (level != null && level.getGameTime() != lastSpeedCheck) {
            lastSpeedCheck = level.getGameTime();
            cachedSpeed = getSpeed();
        }

        return cachedSpeed != null && cachedSpeed != 0;
    }

    public void processEntity(Entity entity) {
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
            if (entity instanceof Armadillo armadillo) {
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
            sendData();
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
        if (level == null || level.isClientSide) {
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

    // 内部类：物品处理器
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
            if (!be.canAcceptInput()) return stack;
            if (slot == 0) {
                return inputInv.insertItem(INPUT_SLOT, stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.extractItem(slot, amount, simulate);
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
            if (slot == 0 && be.canAcceptInput()) {
                return be.getMatchingRecipe(new SingleRecipeInput(stack)).isPresent() ||
                        (be.getMatchingRecipe(new SingleRecipeInput(stack)).isEmpty());
            }
            return false;
        }
    }

    // 内部类：自定义处理库存
    private static class CustomProcessingInventory extends ProcessingInventory {
        private final MechanicalPeelerBlockEntity blockEntity;

        public CustomProcessingInventory(MechanicalPeelerBlockEntity be, NonNullConsumer<ItemStack> startProcessing) {
            super(startProcessing);
            this.blockEntity = be;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            super.setStackInSlot(slot, stack);
            if (blockEntity != null) {
                blockEntity.setChanged();
                blockEntity.sendData();
            }
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag tag = super.serializeNBT(registries);
            tag.putBoolean("AppliedRecipe", appliedRecipe);
            tag.putFloat("RemainingTime", remainingTime);
            tag.putFloat("RecipeDuration", recipeDuration);
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt) {
            super.deserializeNBT(registries, nbt);
            appliedRecipe = nbt.getBoolean("AppliedRecipe");
            remainingTime = nbt.getFloat("RemainingTime");
            recipeDuration = nbt.getFloat("RecipeDuration");
        }
    }
}