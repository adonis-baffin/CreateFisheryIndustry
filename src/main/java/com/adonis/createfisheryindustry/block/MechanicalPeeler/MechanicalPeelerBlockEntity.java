package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.neoforged.neoforge.common.DataMapHooks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    // 1. 首先在类中添加这些字段和常量
    private static final int BLOCK_BREAK_TIME = 60; // 处理方块所需的时间（tick）
    private int blockProcessingTime = 0;
    private boolean isProcessingBlock = false;
    private BlockPos targetBlockPos = null;

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

        Optional<RecipeHolder<PeelingRecipe>> recipeHolder = getMatchingRecipe(new SingleRecipeInput(stackInInputSlot));

        if (recipeHolder.isPresent()) {
            PeelingRecipe recipe = recipeHolder.get().value();
            List<ItemStack> totalPotentialSecondaryProducts = new ArrayList<>();
            for (int i = 0; i < stackInInputSlot.getCount(); i++) {
                totalPotentialSecondaryProducts.addAll(recipe.rollResultsFor(recipe.getSecondaryOutputs()));
            }
            if (!canStoreAllSecondaries(totalPotentialSecondaryProducts)) {
            }
        } else {

        }

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

    private Optional<RecipeHolder<PeelingRecipe>> getMatchingRecipe(SingleRecipeInput input) {
        if (level == null || input.item().isEmpty()) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(CreateFisheryRecipeTypes.PEELING.getType(), input, level);
    }

    private boolean isDirectionValidForAxis(Direction dir) {
        if (!dir.getAxis().isHorizontal()) return false;

        boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);

        if (alongLocalX) {
            return dir == Direction.EAST || dir == Direction.WEST;
        } else {
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

    // 添加一个方法来查找所有需要去皮的原木
    private List<BlockPos> findTreeLogs(BlockPos startPos, BlockState startState) {
        if (level == null) return Collections.emptyList();

        Block startBlock = startState.getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(startBlock);
        if (blockId == null) return Collections.emptyList();

        String blockPath = blockId.getPath();
        // 检查是否是原木
        if (!blockPath.contains("log") || blockPath.contains("stripped")) {
            return Collections.emptyList();
        }

        // 检查是否有对应的去皮版本
        String strippedPath = "stripped_" + blockPath;
        ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                blockId.getNamespace(),
                strippedPath
        );
        if (!BuiltInRegistries.BLOCK.containsKey(strippedId)) {
            return Collections.emptyList();
        }

        // 使用TreeCutter的逻辑来找到整棵树
        TreeCutter.Tree tree = TreeCutter.findTree(level, startPos, startState);

        // 获取所有原木位置
        List<BlockPos> logs = new ArrayList<>();
        try {
            // 通过反射或其他方式获取logs字段
            // 由于Tree类的logs字段是private的，我们需要另一种方法
            // 最简单的方法是自己实现一个简化版的树查找算法
            logs = findConnectedLogs(startPos, startBlock);
        } catch (Exception e) {
            CreateFisheryMod.LOGGER.warn("Failed to find tree logs", e);
            logs.add(startPos); // 至少处理当前方块
        }

        return logs;
    }

    // 实现一个简化的树查找算法
    private List<BlockPos> findConnectedLogs(BlockPos startPos, Block logType) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> toCheck = new LinkedList<>();

        toCheck.add(startPos);

        while (!toCheck.isEmpty() && logs.size() < 256) { // 限制最大数量防止崩溃
            BlockPos current = toCheck.remove(0);
            if (visited.contains(current)) continue;
            visited.add(current);

            BlockState currentState = level.getBlockState(current);
            Block currentBlock = currentState.getBlock();

            // 检查是否是同类型的原木
            ResourceLocation currentId = BuiltInRegistries.BLOCK.getKey(currentBlock);
            if (currentId != null) {
                String currentPath = currentId.getPath();
                if (currentPath.contains("log") && !currentPath.contains("stripped")) {
                    // 检查是否是同一种木头（通过前缀匹配）
                    if (isSameWoodType(logType, currentBlock)) {
                        logs.add(current);

                        // 添加周围的方块到检查列表
                        for (Direction dir : Direction.values()) {
                            BlockPos neighbor = current.relative(dir);
                            if (!visited.contains(neighbor)) {
                                toCheck.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        return logs;
    }

    // 判断是否是同一种木头
    private boolean isSameWoodType(Block block1, Block block2) {
        ResourceLocation id1 = BuiltInRegistries.BLOCK.getKey(block1);
        ResourceLocation id2 = BuiltInRegistries.BLOCK.getKey(block2);

        if (id1 == null || id2 == null) return false;
        if (!id1.getNamespace().equals(id2.getNamespace())) return false;

        String path1 = id1.getPath();
        String path2 = id2.getPath();

        // 提取木头类型（例如 "oak_log" -> "oak"）
        String type1 = extractWoodType(path1);
        String type2 = extractWoodType(path2);

        return type1.equals(type2);
    }

    // 提取木头类型
    private String extractWoodType(String blockPath) {
        if (blockPath.endsWith("_log")) {
            return blockPath.substring(0, blockPath.length() - 4);
        }
        return blockPath;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public void sendData() {
        if (level != null && !level.isClientSide) {
        }
        super.sendData(); // 调用父类方法处理实际的同步
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

        // 11. 在 write 方法中保存方块处理状态
        compound.putInt("BlockProcessingTime", blockProcessingTime);
        compound.putBoolean("IsProcessingBlock", isProcessingBlock);
        if (targetBlockPos != null) {
            compound.putLong("TargetBlockPos", targetBlockPos.asLong());
        }

        super.write(compound, registries, clientPacket); // 确保 super.write 在自定义标签之后，或根据其预期行为调整

        if (!clientPacket) {
            CompoundTag cooldownsTag = new CompoundTag();
            entityCooldowns.forEach((uuid, time) -> cooldownsTag.putLong(uuid.toString(), time));
            compound.put("EntityCooldowns", cooldownsTag);
        }

        if (clientPacket && !playEvent.isEmpty()) {
            compound.put("PlayEvent", playEvent.saveOptional(registries));
            playEvent = ItemStack.EMPTY;
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
        } else {
            currentInputDirection = null; // Ensure reset if not present
        }

        // 12. 在 read 方法中读取方块处理状态
        blockProcessingTime = compound.getInt("BlockProcessingTime");
        isProcessingBlock = compound.getBoolean("IsProcessingBlock");
        if (compound.contains("TargetBlockPos")) {
            targetBlockPos = BlockPos.of(compound.getLong("TargetBlockPos"));
        } else {
            targetBlockPos = null; // Ensure reset if not present
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
                        CreateFisheryMod.LOGGER.warn("Failed to parse UUID from EntityCooldowns: " + key, e);
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
        if (level.getGameTime() % 100 == 0 && inputInventory.remainingTime > 0) {
            // CreateFisheryMod.LOGGER.debug("Peeler Tick (Item): RemainingTime={}, AppliedRecipe={}, InputDir={}",
            //         inputInventory.remainingTime, inputInventory.appliedRecipe, currentInputDirection);
        }
        if (level.getGameTime() % 100 == 0 && isProcessingBlock) {
            // CreateFisheryMod.LOGGER.debug("Peeler Tick (Block): ProcessingTime={}, Target={}",
            //         blockProcessingTime, targetBlockPos);
        }


        // 传送带检测（仅服务端，且机器朝上时）
        if (!level.isClientSide && getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP && level.getGameTime() % 5 == 0) {
            Vec3 itemMovement = getItemMovementVec(); // This might need adjustment if getItemMovementVec depends on currentInputDirection not yet set
            if (!Vec3.ZERO.equals(itemMovement)) { // Ensure there's a valid movement vector
                BlockPos inputPos = worldPosition.offset(BlockPos.containing(itemMovement.reverse()));
                DirectBeltInputBehaviour inputBelt = BlockEntityBehaviour.get(level, inputPos, DirectBeltInputBehaviour.TYPE);
                if (inputBelt != null) {
                    // Potentially try to pull items if conditions are met
                    // CreateFisheryMod.LOGGER.debug("Belt detected at {} for input direction {}", inputPos, itemMovement.reverse());
                }
            }
        }

        // 客户端处理
        if (level.isClientSide) {
            if (!playEvent.isEmpty()) {
                tickAudio();
            }
            // 客户端物品处理动画
            if (inputInventory.remainingTime > 0 && getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP && canProcess()) {
                float speed = Math.abs(getSpeed()) / 24f;
                inputInventory.remainingTime = Math.max(0, inputInventory.remainingTime - speed);
            }
            // 客户端方块处理粒子
            if (isProcessingBlock && targetBlockPos != null && blockProcessingTime > 0 && shouldProcessBlock()) {
                spawnBlockProcessingParticles(level.getBlockState(targetBlockPos));
            }
            return;
        }

        // === 服务端逻辑 ===

        // 物品处理逻辑 (当机器朝上时)
        if (getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP) {
            if (!canProcess() || getSpeed() == 0) { // Not enough speed or cannot process items
                // 如果机器停止但有待处理的物品或已处理的输出
                if (inputInventory.remainingTime <= 0) { // Not in an active processing animation
                    if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
                        ejectInputOrPrimaryOutput();
                    } else if (inputInventory.appliedRecipe && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                        // This case means recipe was applied, but output became empty, and input (which was pass-through) remains.
                        ejectInputOrPrimaryOutput();
                    }
                }
                // If it was processing a block and is now UP, reset block processing (handled further down)
            } else { // Can process items and has speed
                if (inputInventory.remainingTime > 0) { // Item processing/animation in progress
                    float speed = Math.abs(getSpeed()) / 24f;
                    inputInventory.remainingTime -= speed;

                    if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe) { // Input phase particles
                        spawnParticles(inputInventory.getStackInSlot(INPUT_SLOT));
                    } else if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty() && inputInventory.appliedRecipe) { // Output phase particles
                        spawnParticles(outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP));
                    }


                    if (inputInventory.remainingTime <= 0 && !inputInventory.appliedRecipe) { // Input phase finished
                        if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                            playEvent = inputInventory.getStackInSlot(INPUT_SLOT).copy(); // For sound/event particles
                        }
                        Optional<RecipeHolder<PeelingRecipe>> recipeHolder =
                                getMatchingRecipe(new SingleRecipeInput(inputInventory.getStackInSlot(INPUT_SLOT)));
                        if (recipeHolder.isPresent()) {
                            applyRecipeProducts(recipeHolder.get());
                        } else {
                            // No recipe, treat as pass-through if applicable, or handle error
                            // For now, it will just move to appliedRecipe=true and then eject in output phase
                        }
                        inputInventory.appliedRecipe = true;
                        inputInventory.recipeDuration = OUTPUT_ANIMATION_TIME;
                        inputInventory.remainingTime = OUTPUT_ANIMATION_TIME;
                        setChanged();
                        sendData();
                        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

                    } else if (inputInventory.remainingTime <= 0 && inputInventory.appliedRecipe) { // Output phase finished
                        ejectInputOrPrimaryOutput();
                    }
                } else { // Idle state for item processing (remainingTime <= 0)
                    if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe && canAcceptInput()) {
                        startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
                    }
                }
            }
        }

        // 方块处理逻辑 - 简化条件
        if (shouldProcessBlock() && getSpeed() != 0) {
            // 只要是水平放置且有速度就处理方块
            processBlock();
        } else {
            // 如果不应该处理方块了（比如朝向改变或速度为0），并且之前正在处理方块，则重置状态
            if (isProcessingBlock) {
                blockProcessingTime = 0;
                isProcessingBlock = false;
                targetBlockPos = null;
                setChanged(); // 保存重置的状态
                sendData();   // 同步到客户端
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
            currentInputDirection = null; // Reset direction after full cycle
            setChanged();
            sendData();
            return;
        }

        Vec3 itemMovement = getItemMovementVec();
        Vec3 outputMovement = itemMovement.scale(-1);
        Direction outputFacing = Direction.getNearest(outputMovement.x, outputMovement.y, outputMovement.z);

        ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
                .tryExportingToBeltFunnel(stackToEject, outputFacing.getOpposite(), false);

        if (tryExportingToBeltFunnel != null) {
            if (tryExportingToBeltFunnel.getCount() != stackToEject.getCount()) {
                updateEjectedStack(isPassThroughOutput, tryExportingToBeltFunnel);
                return;
            }
            if (!tryExportingToBeltFunnel.isEmpty()) {
                // Export failed, continue
            } else {
                updateEjectedStack(isPassThroughOutput, ItemStack.EMPTY);
                resetStateAfterEjectionOrTryNext(isPassThroughOutput);
                return;
            }
        }

        BlockPos nextPos = worldPosition.offset(BlockPos.containing(outputMovement));
        DirectBeltInputBehaviour beltBehaviour = BlockEntityBehaviour.get(level, nextPos, DirectBeltInputBehaviour.TYPE);

        if (beltBehaviour != null && beltBehaviour.canInsertFromSide(outputFacing)) {
            if (level.isClientSide && !isVirtual())
                return;

            ItemStack currentStackCopy = stackToEject.copy();
            ItemStack beltRemainder = beltBehaviour.handleInsertion(currentStackCopy, outputFacing, false);

            if (!ItemStack.matches(beltRemainder, currentStackCopy)) {
                updateEjectedStack(isPassThroughOutput, beltRemainder);
                if (beltRemainder.isEmpty()) {
                    resetStateAfterEjectionOrTryNext(isPassThroughOutput);
                }
                setChanged();
                sendData();
                return;
            }
        }

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
                currentInputDirection = null;
            } else {
                startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
            }
        } else { // Was primary output
            if (primaryOutputSlotNowEmpty) {
                inputInventory.remainingTime = -1; // Reset for next item or idle
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
                currentInputDirection = null; // Ready for new input direction
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
        if (inputStackForRecipe.isEmpty() || level == null) return; // 添加 level 空检查

        PeelingRecipe recipe = recipeHolder.value();
        int itemsToProcess = inputStackForRecipe.getCount();
        inputInventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY); // 消耗所有输入物品

        ItemStack aggregatedPrimaryOutput = ItemStack.EMPTY;
        List<ItemStack> aggregatedSecondaryOutputs = new LinkedList<>();

        // 获取配方定义的所有潜在产物（ProcessingOutput 列表）
        List<ProcessingOutput> definedOutputs = recipe.getRollableResults();
        if (definedOutputs.isEmpty()) {
            setChanged();
            sendData();
            return;
        }

        // 按照Create的惯例，第一个定义的产物是主产物
        ProcessingOutput primaryDefinedOutput = definedOutputs.get(0);
        // 其余的是副产物
        List<ProcessingOutput> secondaryDefinedOutputs = definedOutputs.size() > 1 ?
                definedOutputs.subList(1, definedOutputs.size()) : Collections.emptyList();

        for (int i = 0; i < itemsToProcess; i++) { // 对原输入槽中的每一个物品进行处理
            // 为主产物掷骰子
            // 注意：recipe.rollResults(List<ProcessingOutput>) 会返回一个 ItemStack 列表
            List<ItemStack> rolledPrimaryList = recipe.rollResults(Collections.singletonList(primaryDefinedOutput));
            if (!rolledPrimaryList.isEmpty()) {
                ItemStack currentPrimaryRolledItem = rolledPrimaryList.get(0); // 通常列表里只有一个元素，或者为空ItemStack
                if (!currentPrimaryRolledItem.isEmpty()) {
                    if (aggregatedPrimaryOutput.isEmpty()) {
                        aggregatedPrimaryOutput = currentPrimaryRolledItem.copy();
                    } else if (ItemStack.isSameItemSameComponents(aggregatedPrimaryOutput, currentPrimaryRolledItem)) {
                        aggregatedPrimaryOutput.grow(currentPrimaryRolledItem.getCount());
                    } else {
                        // 如果roll出了不同类型的主产物（理论上不应该，但为了健壮性）
                        Vec3 dropPos = VecHelper.getCenterOf(worldPosition).add(0, 0.75, 0);
                        ItemEntity overflow = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, currentPrimaryRolledItem);
                        level.addFreshEntity(overflow);
                    }
                }
            }

            // 为所有副产物掷骰子
            if (!secondaryDefinedOutputs.isEmpty()) {
                List<ItemStack> currentSecondaries = recipe.rollResults(secondaryDefinedOutputs);
                for (ItemStack secondaryStack : currentSecondaries) {
                    if (!secondaryStack.isEmpty()) {
                        ItemHelper.addToList(secondaryStack.copy(), aggregatedSecondaryOutputs);
                    }
                }
            }
        }

        // 将聚合后的主产物（如果存在）放入临时输出槽
        outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, aggregatedPrimaryOutput);

        // 存储副产物
        for (ItemStack secondaryStack : aggregatedSecondaryOutputs) {
            if (secondaryStack.isEmpty()) continue;
            ItemStack remainderToStore = secondaryStack.copy();
            for (int slot = 1; slot < outputInventory.getSlots(); slot++) { // 副产物从槽位1开始
                remainderToStore = outputInventory.insertItem(slot, remainderToStore, false);
                if (remainderToStore.isEmpty()) break;
            }
            if (!remainderToStore.isEmpty()) { // 如果副产物存不下，则丢弃在地上
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
            if (currentInputDirection != null && currentInputDirection.getAxis().isHorizontal()) {
                return Vec3.atLowerCornerOf(currentInputDirection.getNormal());
            }
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            if (alongLocalX) {
                return new Vec3(0, 0, 1); // Default North to South if axis is X
            } else {
                return new Vec3(1, 0, 0); // Default West to East if axis is Z
            }
        }
        if (currentInputDirection != null && currentInputDirection.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(currentInputDirection.getNormal());
        }
        if (facing.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(facing.getClockWise().getNormal()); // Arbitrary default for particles if needed
        }

        return Vec3.ZERO; // Fallback
    }

    public void insertFromBelt(ItemStack stack, Direction from) {
        if (!canAcceptInput() || getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP) { // Only accept from belt if UP
            return;
        }
        if (!isDirectionValidForAxis(from)) return;

        currentInputDirection = from;
        ItemStack remainder = inputInventory.insertItem(INPUT_SLOT, stack, false);
        if (remainder.getCount() < stack.getCount() && inputInventory.remainingTime == -1) {
            startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
        }
    }

    public void insertItem(ItemEntity entity) {
        if (level == null || level.isClientSide || !entity.isAlive())
            return;
        if (!canAcceptInput() || getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP) { // Only accept items if UP
            return;
        }

        Vec3 entityPos = entity.position();
        Vec3 blockCenter = VecHelper.getCenterOf(worldPosition);
        Vec3 diff = entityPos.subtract(blockCenter).normalize(); // Normalize for direction

        Direction inputDir;
        if (Math.abs(diff.x()) > Math.abs(diff.z())) {
            inputDir = diff.x() > 0 ? Direction.EAST : Direction.WEST;
        } else {
            inputDir = diff.z() > 0 ? Direction.SOUTH : Direction.NORTH;
        }

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

        if (cachedSpeed == null || cachedSpeed == 0) {
            return false;
        }

        // 垂直放置时处理物品
        if (getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP) {
            return true;
        }

        // 水平放置时也可以工作（处理方块）
        if (getBlockState().getValue(MechanicalPeelerBlock.FACING).getAxis().isHorizontal()) {
            return true;
        }
        return false;
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
        for (int i = 1; i <= MAX_SECONDARY_OUTPUTS_STORAGE; i++) { // Slots 1 to MAX_SECONDARY_OUTPUTS_STORAGE are for secondary
            remainder = outputInventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private boolean isEntityOnCooldown(UUID entityId) {
        if (level == null) return true; // Should not happen if level check is done before call
        return entityCooldowns.containsKey(entityId) &&
                (level.getGameTime() - entityCooldowns.get(entityId)) < ARMADILLO_TURTLE_COOLDOWN_TICKS;
    }

    private void setEntityCooldown(UUID entityId) {
        if (level == null) return;
        entityCooldowns.put(entityId, level.getGameTime());
    }

    private void spawnProcessingParticlesEffect() {
        if (level == null || !level.isClientSide) { // Only spawn on client OR send packet to spawn
            return; // This method seems to try spawning on server, particle methods usually check isClientSide
        }

        RandomSource r = level.random; // level.random is fine
        Vec3 center = VecHelper.getCenterOf(this.worldPosition);
        for (int i = 0; i < 5; i++) {
            double motionX = (r.nextDouble() - 0.5D) * 0.1D;
            double motionY = r.nextDouble() * 0.1D + 0.1D;
            double motionZ = (r.nextDouble() - 0.5D) * 0.1D;
            level.addParticle(ParticleTypes.SNOWFLAKE, // Consider a more fitting particle
                    center.x + (r.nextDouble() - 0.5D) * 0.6D,
                    center.y + 1.1D, // A bit high for horizontal peeler
                    center.z + (r.nextDouble() - 0.5D) * 0.6D,
                    motionX, motionY, motionZ);
        }
    }

    protected boolean shouldProcessBlock() {
        return getBlockState().getValue(MechanicalPeelerBlock.FACING)
                .getAxis()
                .isHorizontal();
    }

    protected BlockPos getTargetBlockPos() {
        return getBlockPos().relative(getBlockState().getValue(MechanicalPeelerBlock.FACING));
    }

    private boolean canProcessBlock(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();

        // 检查除锈
        if (DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        // 检查除蜡
        if (DataMapHooks.INVERSE_WAXABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        // 新增：只检查完全成熟的紫水晶簇（不包括小型、中型、大型）
        if (block == Blocks.AMETHYST_CLUSTER) {
            return true;
        }

        // 检查是否可以去树皮 - 简化逻辑
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null) {
            String blockPath = blockId.getPath();
            // 检查是否包含log且不包含stripped
            if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                String strippedPath = "stripped_" + blockPath;
                ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                        blockId.getNamespace(),
                        strippedPath
                );
                return BuiltInRegistries.BLOCK.containsKey(strippedId);
            }
        }

        return false;
    }

    private Block getProcessedBlock(Block currentBlock) {
        // 除锈
        Block deoxidized = DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.get(currentBlock);
        if (deoxidized != null) {
            return deoxidized;
        }
        // 除蜡
        Block dewaxed = DataMapHooks.INVERSE_WAXABLES_DATAMAP.get(currentBlock);
        if (dewaxed != null) {
            return dewaxed;
        }
        // 去树皮
        ResourceLocation currentId = BuiltInRegistries.BLOCK.getKey(currentBlock);
        if (currentId == null) return currentBlock;
        String blockPath = currentId.getPath();

        if (blockPath.contains("log") && !blockPath.startsWith("stripped_")) {
            String strippedPath = "stripped_" + blockPath;
            ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                    currentId.getNamespace(),
                    strippedPath
            );
            if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                return BuiltInRegistries.BLOCK.get(strippedId);
            }
        }
        return currentBlock;
    }


    // 6. 处理方块的主要逻辑
// 修改后的 processBlock 方法
    private void processBlock() {
        if (level == null || !shouldProcessBlock() || getSpeed() == 0) {
            if (isProcessingBlock) {
                blockProcessingTime = 0;
                isProcessingBlock = false;
                targetBlockPos = null;
                setChanged();
            }
            return;
        }

        BlockPos currentTargetPos = getTargetBlockPos();
        BlockState targetState = level.getBlockState(currentTargetPos);

        if (!canProcessBlock(targetState)) {
            if (isProcessingBlock) {
                blockProcessingTime = 0;
                isProcessingBlock = false;
                targetBlockPos = null;
                setChanged();
            }
            return;
        }

        // 开始处理新方块或继续处理当前方块
        if (!isProcessingBlock || !currentTargetPos.equals(targetBlockPos)) {
            targetBlockPos = currentTargetPos;
            isProcessingBlock = true;
            blockProcessingTime = 0;
            setChanged();
        }

        // 增加处理时间
        float processingSpeedFactor = Mth.clamp(Math.abs(getSpeed()) / 24f, 1f, 128f);
        blockProcessingTime += (int)Math.max(1, processingSpeedFactor);

        // 完成处理
        if (blockProcessingTime >= BLOCK_BREAK_TIME) {
            if (!level.isClientSide) {
                Block targetBlock = targetState.getBlock();

                // 新增：检查是否为完全成熟的紫水晶簇
                if (targetBlock == Blocks.AMETHYST_CLUSTER) {
                    processAmethystCluster(currentTargetPos, targetState);
                } else {
                    // 检查是否是原木去皮操作
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(targetBlock);
                    if (blockId != null) {
                        String blockPath = blockId.getPath();
                        if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                            // 这是一个原木，执行整棵树去皮
                            processWholeTree(currentTargetPos, targetState);
                        } else {
                            // 其他类型的处理（除锈、除蜡）
                            processSingleBlock(currentTargetPos, targetState);
                        }
                    }
                }
            }

            // 重置处理状态
            blockProcessingTime = 0;
            isProcessingBlock = false;
            targetBlockPos = null;
            setChanged();
        } else {
            setChanged();
        }
    }

    private void processAmethystCluster(BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide) return;

        ItemStack amethystShards = new ItemStack(Items.AMETHYST_SHARD, 4);

        if (!tryStoreItemInSecondaryOutput(amethystShards)) {

            Vec3 dropPos = Vec3.atCenterOf(pos).add(0, 0.5, 0);
            ItemEntity itemEntity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, amethystShards);

            itemEntity.setDeltaMovement(
                    (level.random.nextDouble() - 0.5) * 0.1,
                    level.random.nextDouble() * 0.1 + 0.1,
                    (level.random.nextDouble() - 0.5) * 0.1
            );
            level.addFreshEntity(itemEntity);
        }

        // 破坏方块（设置为空气）
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

        // 播放破坏音效
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);

        // 同步数据
        setChanged();
        sendData();
    }


    // 处理单个方块（除锈、除蜡）
    private void processSingleBlock(BlockPos pos, BlockState state) {
        Block processedBlock = getProcessedBlock(state.getBlock());
        if (processedBlock != state.getBlock()) {
            BlockState newState = processedBlock.defaultBlockState();
            // 复制相同的属性
            for (Property<?> property : state.getProperties()) {
                if (newState.hasProperty(property)) {
                    newState = copyPropertyUnchecked(state, newState, property);
                }
            }
            level.setBlock(pos, newState, 3);
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    // 处理整棵树（去皮）
    private void processWholeTree(BlockPos startPos, BlockState startState) {
        List<BlockPos> logs = findTreeLogs(startPos, startState);

        if (logs.isEmpty()) {
            // 如果找不到树，至少处理当前方块
            processSingleBlock(startPos, startState);
            return;
        }

        // 对所有找到的原木进行去皮
        int processedCount = 0;
        for (BlockPos logPos : logs) {
            BlockState logState = level.getBlockState(logPos);
            Block logBlock = logState.getBlock();

            ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(logBlock);
            if (logId != null) {
                String logPath = logId.getPath();
                if (logPath.contains("log") && !logPath.contains("stripped")) {
                    String strippedPath = "stripped_" + logPath;
                    ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                            logId.getNamespace(),
                            strippedPath
                    );

                    if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                        Block strippedBlock = BuiltInRegistries.BLOCK.get(strippedId);
                        BlockState newState = strippedBlock.defaultBlockState();

                        // 复制属性
                        for (Property<?> property : logState.getProperties()) {
                            if (newState.hasProperty(property)) {
                                newState = copyPropertyUnchecked(logState, newState, property);
                            }
                        }

                        level.setBlock(logPos, newState, 3);
                        processedCount++;

                        // 每处理几个方块播放一次音效，避免太吵
                        if (processedCount % 3 == 1) {
                            level.playSound(null, logPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.8F, 1.0F);
                        }
                    }
                }
            }
        }

        // 在起始位置播放一个主音效
        if (processedCount > 0) {
            level.playSound(null, startPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }


    // 7. 辅助方法：复制方块属性
    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyPropertyUnchecked(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }


    private void dropByproducts(BlockPos pos, BlockState originalState, Block processedBlock) {
        // 不掉落任何副产物
    }

    private void spawnBlockProcessingParticles(BlockState state) {
        if (targetBlockPos == null || level == null || !level.isClientSide) return;

        RandomSource random = level.random;
        Vec3 center = Vec3.atCenterOf(targetBlockPos); // Use the actual target block's center

        if (state.getBlock() == Blocks.AMETHYST_CLUSTER) {
            for (int i = 0; i < 2; i++) { // 从5减少到2
                double offsetX = (random.nextDouble() - 0.5) * 0.8;
                double offsetY = (random.nextDouble() - 0.5) * 0.8;
                double offsetZ = (random.nextDouble() - 0.5) * 0.8;

                // 使用不同的粒子类型来营造闪亮效果
                level.addParticle(
                        ParticleTypes.END_ROD, // 闪亮的粒子
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.1D, // Motion x
                        random.nextDouble() * 0.1D + 0.02D, // Motion y (slightly upwards)
                        (random.nextDouble() - 0.5) * 0.1D  // Motion z
                );
            }

            // 额外添加一些方块粒子（减少到30%）
            for (int i = 0; i < 1; i++) { // 从2减少到1
                double offsetX = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getXsize();
                double offsetY = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getYsize();
                double offsetZ = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getZsize();

                level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.2D, // Motion x
                        random.nextDouble() * 0.1D + 0.05D, // Motion y (slightly upwards)
                        (random.nextDouble() - 0.5) * 0.2D  // Motion z
                );
            }
        } else {
            // 原有的普通方块粒子效果
            for (int i = 0; i < 3; i++) { // Number of particles
                double offsetX = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getXsize();
                double offsetY = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getYsize();
                double offsetZ = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getZsize();

                level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.2D, // Motion x
                        random.nextDouble() * 0.1D + 0.05D, // Motion y (slightly upwards)
                        (random.nextDouble() - 0.5) * 0.2D  // Motion z
                );
            }
        }
    }


    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (this.level != null && !this.level.isClientSide) {
        }
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
            if (slot == INPUT_SLOT) { // Slot 0 is input
                return inputInv.getStackInSlot(INPUT_SLOT);
            }
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.getStackInSlot(slot);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot == INPUT_SLOT) {
                if (!be.canAcceptInput() || be.getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP) {
                    return stack;
                }
                ItemStack remainder = inputInv.insertItem(INPUT_SLOT, stack, simulate);
                if (!simulate && remainder.getCount() < stack.getCount() && be.inputInventory.remainingTime == -1) {
                    be.startProcessingRecipe(inputInv.getStackInSlot(INPUT_SLOT));
                }
                return remainder;
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.extractItem(slot, amount, simulate); // outputInv slot 0 is primary_temp, 1+ are secondaries
            }
            return ItemStack.EMPTY; // Cannot extract from input slot
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot == INPUT_SLOT) {
                return inputInv.getSlotLimit(INPUT_SLOT);
            }
            if (slot >= 1 && slot <= MAX_SECONDARY_OUTPUTS_STORAGE) {
                return outputInv.getSlotLimit(slot);
            }
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == INPUT_SLOT) {
                // Check if machine is UP, can accept, and if recipe exists (or allow any if no recipe check needed here)
                return be.getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP &&
                        be.canAcceptInput() &&
                        (be.getMatchingRecipe(new SingleRecipeInput(stack)).isPresent() || be.getMatchingRecipe(new SingleRecipeInput(stack)).isEmpty()); // Allow if recipe or if no recipe (passthrough)
            }
            return false; // Cannot insert into output slots this way
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
            if (blockEntity != null && blockEntity.hasLevel() && !blockEntity.getLevel().isClientSide()) {
                blockEntity.setChanged();
                blockEntity.sendData(); // Send data on server when inventory changes
            }
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag tag = super.serializeNBT(registries);

            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt) {
            super.deserializeNBT(registries, nbt);

        }
    }
}