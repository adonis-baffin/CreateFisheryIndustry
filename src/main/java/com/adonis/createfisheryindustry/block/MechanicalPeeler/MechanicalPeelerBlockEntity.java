package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.data.PeelerEntityProcessing;
import com.adonis.createfisheryindustry.data.PeelerEntityProcessingManager;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.data.PeelerEntityProcessingManager;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.saw.TreeCutter;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.fml.ModList;
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

    public static final double ENTITY_SCAN_RANGE = 0.5; // 可以轻松调整

    private static final float INPUT_ANIMATION_TIME = 10f;
    private static final float OUTPUT_ANIMATION_TIME = 10f;
    private static final int BLOCK_BREAK_TIME = 60;
    private static final int ARMADILLO_TURTLE_COOLDOWN_TICKS = 20 * 60 * 1;

    public static final int INPUT_SLOT = 0;
    private static final int MAX_SECONDARY_OUTPUTS_STORAGE = 4;
    public static final int OUTPUT_INV_PRIMARY_SLOT_TEMP = 0;
    private static final int OUTPUT_INV_SIZE = 1 + MAX_SECONDARY_OUTPUTS_STORAGE;

    public ProcessingInventory inputInventory;
    public ItemStackHandler outputInventory;
    private ItemStack playEvent;
    public final IItemHandler itemHandler;

    private final Map<UUID, Long> entityCooldowns = new HashMap<>();
    private Float cachedSpeed = null;
    private long lastSpeedCheck = 0;
    private Direction currentInputDirection = null;

    private int blockProcessingTime = 0;
    private boolean isProcessingBlock = false;
    private BlockPos targetBlockPos = null;

    private static Item FARMERS_DELIGHT_TREE_BARK = null;
    private static boolean farmersDelightChecked = false;
    private static final String FARMERS_DELIGHT_MOD_ID = "farmersdelight";
    private static final String TREE_BARK_ITEM_ID = "tree_bark";

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

    private static Item getFarmersDelightTreeBark() {
        if (!farmersDelightChecked) {
            farmersDelightChecked = true;
            if (ModList.get().isLoaded(FARMERS_DELIGHT_MOD_ID)) {
                Item barkItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(FARMERS_DELIGHT_MOD_ID, TREE_BARK_ITEM_ID));
                if (barkItem != null && barkItem != Items.AIR) {
                    FARMERS_DELIGHT_TREE_BARK = barkItem;
                }
            }
        }
        return FARMERS_DELIGHT_TREE_BARK;
    }

    private List<BlockPos> findTreeLogs(BlockPos startPos, BlockState startState) {
        if (level == null) return Collections.emptyList();

        Block startBlock = startState.getBlock();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(startBlock);
        if (blockId == null) return Collections.emptyList();

        String blockPath = blockId.getPath();
        if (!blockPath.contains("log") || blockPath.contains("stripped")) {
            return Collections.emptyList();
        }

        String strippedPath = "stripped_" + blockPath;
        ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                blockId.getNamespace(),
                strippedPath
        );
        if (!BuiltInRegistries.BLOCK.containsKey(strippedId)) {
            return Collections.emptyList();
        }

        TreeCutter.Tree tree = TreeCutter.findTree(level, startPos, startState);

        List<BlockPos> logs = new ArrayList<>();
        try {
            logs = findConnectedLogs(startPos, startBlock);
        } catch (Exception e) {
            logs.add(startPos);
        }

        return logs;
    }

    private List<BlockPos> findConnectedLogs(BlockPos startPos, Block logType) {
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> toCheck = new LinkedList<>();

        toCheck.add(startPos);

        while (!toCheck.isEmpty() && logs.size() < 256) {
            BlockPos current = toCheck.remove(0);
            if (visited.contains(current)) continue;
            visited.add(current);

            BlockState currentState = level.getBlockState(current);
            Block currentBlock = currentState.getBlock();

            ResourceLocation currentId = BuiltInRegistries.BLOCK.getKey(currentBlock);
            if (currentId != null) {
                String currentPath = currentId.getPath();
                if (currentPath.contains("log") && !currentPath.contains("stripped")) {
                    if (isSameWoodType(logType, currentBlock)) {
                        logs.add(current);

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

    private boolean isSameWoodType(Block block1, Block block2) {
        ResourceLocation id1 = BuiltInRegistries.BLOCK.getKey(block1);
        ResourceLocation id2 = BuiltInRegistries.BLOCK.getKey(block2);

        if (id1 == null || id2 == null) return false;
        if (!id1.getNamespace().equals(id2.getNamespace())) return false;

        String path1 = id1.getPath();
        String path2 = id2.getPath();

        String type1 = extractWoodType(path1);
        String type2 = extractWoodType(path2);

        return type1.equals(type2);
    }

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

        compound.putInt("BlockProcessingTime", blockProcessingTime);
        compound.putBoolean("IsProcessingBlock", isProcessingBlock);
        if (targetBlockPos != null) {
            compound.putLong("TargetBlockPos", targetBlockPos.asLong());
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
            currentInputDirection = null;
        }

        blockProcessingTime = compound.getInt("BlockProcessingTime");
        isProcessingBlock = compound.getBoolean("IsProcessingBlock");
        if (compound.contains("TargetBlockPos")) {
            targetBlockPos = BlockPos.of(compound.getLong("TargetBlockPos"));
        } else {
            targetBlockPos = null;
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

        if (!level.isClientSide && level.getGameTime() % 10 == 0 && getSpeed() != 0) {
            AABB scanArea = new AABB(worldPosition).inflate(ENTITY_SCAN_RANGE);

            // 更新实体过滤条件以支持数据包
            List<Entity> entities = level.getEntitiesOfClass(Entity.class, scanArea,
                    (e) -> e.isAlive() && (e instanceof Sheep || PeelerEntityProcessingManager.hasProcessing(e.getType())));

            for (Entity entity : entities) {
                processEntity(entity);
            }
        }

        if (!level.isClientSide && getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP && level.getGameTime() % 5 == 0) {
            Vec3 itemMovement = getItemMovementVec();
            if (!Vec3.ZERO.equals(itemMovement)) {
                BlockPos inputPos = worldPosition.offset(BlockPos.containing(itemMovement.reverse()));
                DirectBeltInputBehaviour inputBelt = BlockEntityBehaviour.get(level, inputPos, DirectBeltInputBehaviour.TYPE);
            }
        }

        if (level.isClientSide) {
            if (!playEvent.isEmpty()) {
                tickAudio();
            }

            if (inputInventory.remainingTime > 0 && getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP && canProcess()) {
                float speed = Math.abs(getSpeed()) / 24f;
                inputInventory.remainingTime = Math.max(0, inputInventory.remainingTime - speed);

                if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe) {
                    spawnParticles(inputInventory.getStackInSlot(INPUT_SLOT));
                } else if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty() && inputInventory.appliedRecipe) {
                    spawnParticles(outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP));
                }
            }

            if (isProcessingBlock && targetBlockPos != null && blockProcessingTime > 0 && shouldProcessBlock()) {
                if (level.getGameTime() % 3 == 0) {
                    spawnBlockProcessingParticles(level.getBlockState(targetBlockPos));
                }
            }
            return;
        }

        if (getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP) {
            if (!canProcess() || getSpeed() == 0) {
                if (inputInventory.remainingTime <= 0) {
                    if (!outputInventory.getStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty()) {
                        ejectInputOrPrimaryOutput();
                    } else if (inputInventory.appliedRecipe && !inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                        ejectInputOrPrimaryOutput();
                    }
                }
            } else {
                if (inputInventory.remainingTime > 0) {
                    float speed = Math.abs(getSpeed()) / 24f;
                    inputInventory.remainingTime -= speed;

                    if (inputInventory.remainingTime <= 0 && !inputInventory.appliedRecipe) {
                        if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty()) {
                            playEvent = inputInventory.getStackInSlot(INPUT_SLOT).copy();
                        }
                        Optional<RecipeHolder<PeelingRecipe>> recipeHolder =
                                getMatchingRecipe(new SingleRecipeInput(inputInventory.getStackInSlot(INPUT_SLOT)));
                        if (recipeHolder.isPresent()) {
                            applyRecipeProducts(recipeHolder.get());
                        }
                        inputInventory.appliedRecipe = true;
                        inputInventory.recipeDuration = OUTPUT_ANIMATION_TIME;
                        inputInventory.remainingTime = OUTPUT_ANIMATION_TIME;
                        setChanged();
                        sendData();
                        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

                    } else if (inputInventory.remainingTime <= 0 && inputInventory.appliedRecipe) {
                        ejectInputOrPrimaryOutput();
                    }
                } else {
                    if (!inputInventory.getStackInSlot(INPUT_SLOT).isEmpty() && !inputInventory.appliedRecipe && canAcceptInput()) {
                        startProcessingRecipe(inputInventory.getStackInSlot(INPUT_SLOT));
                    }
                }
            }
        }

        if (shouldProcessBlock() && getSpeed() != 0) {
            processBlock();
        } else {
            if (isProcessingBlock) {
                blockProcessingTime = 0;
                isProcessingBlock = false;
                targetBlockPos = null;
                setChanged();
                sendData();
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
            currentInputDirection = null;
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
        } else {
            if (primaryOutputSlotNowEmpty) {
                inputInventory.remainingTime = -1;
                inputInventory.appliedRecipe = false;
                inputInventory.recipeDuration = 0;
                currentInputDirection = null;
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
        if (inputStackForRecipe.isEmpty() || level == null) return;

        PeelingRecipe recipe = recipeHolder.value();
        int itemsToProcess = inputStackForRecipe.getCount();
        inputInventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);

        ItemStack aggregatedPrimaryOutput = ItemStack.EMPTY;
        List<ItemStack> aggregatedSecondaryOutputs = new LinkedList<>();

        List<ProcessingOutput> definedOutputs = recipe.getRollableResults();
        if (definedOutputs.isEmpty()) {
            setChanged();
            sendData();
            return;
        }

        ProcessingOutput primaryDefinedOutput = definedOutputs.get(0);
        List<ProcessingOutput> secondaryDefinedOutputs = definedOutputs.size() > 1 ?
                definedOutputs.subList(1, definedOutputs.size()) : Collections.emptyList();

        for (int i = 0; i < itemsToProcess; i++) {
            List<ItemStack> rolledPrimaryList = recipe.rollResults(Collections.singletonList(primaryDefinedOutput));
            if (!rolledPrimaryList.isEmpty()) {
                ItemStack currentPrimaryRolledItem = rolledPrimaryList.get(0);
                if (!currentPrimaryRolledItem.isEmpty()) {
                    if (aggregatedPrimaryOutput.isEmpty()) {
                        aggregatedPrimaryOutput = currentPrimaryRolledItem.copy();
                    } else if (ItemStack.isSameItemSameComponents(aggregatedPrimaryOutput, currentPrimaryRolledItem)) {
                        aggregatedPrimaryOutput.grow(currentPrimaryRolledItem.getCount());
                    } else {
                        Vec3 dropPos = VecHelper.getCenterOf(worldPosition).add(0, 0.75, 0);
                        ItemEntity overflow = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, currentPrimaryRolledItem);
                        level.addFreshEntity(overflow);
                    }
                }
            }

            if (!secondaryDefinedOutputs.isEmpty()) {
                List<ItemStack> currentSecondaries = recipe.rollResults(secondaryDefinedOutputs);
                for (ItemStack secondaryStack : currentSecondaries) {
                    if (!secondaryStack.isEmpty()) {
                        ItemHelper.addToList(secondaryStack.copy(), aggregatedSecondaryOutputs);
                    }
                }
            }

            if (inputStackForRecipe.is(ItemTags.LOGS_THAT_BURN)) {
                Item bark = getFarmersDelightTreeBark();
                if (bark != null) {
                    ItemHelper.addToList(new ItemStack(bark, 1), aggregatedSecondaryOutputs);
                }
            }
        }

        outputInventory.setStackInSlot(OUTPUT_INV_PRIMARY_SLOT_TEMP, aggregatedPrimaryOutput);

        for (ItemStack secondaryStack : aggregatedSecondaryOutputs) {
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
            if (currentInputDirection != null && currentInputDirection.getAxis().isHorizontal()) {
                return Vec3.atLowerCornerOf(currentInputDirection.getNormal());
            }
            boolean alongLocalX = !getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            if (alongLocalX) {
                return new Vec3(0, 0, 1);
            } else {
                return new Vec3(1, 0, 0);
            }
        }
        if (currentInputDirection != null && currentInputDirection.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(currentInputDirection.getNormal());
        }
        if (facing.getAxis().isHorizontal()) {
            return Vec3.atLowerCornerOf(facing.getClockWise().getNormal());
        }

        return Vec3.ZERO;
    }

    public void insertFromBelt(ItemStack stack, Direction from) {
        if (!canAcceptInput() || getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP) {
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
        if (!canAcceptInput() || getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP) {
            return;
        }

        Vec3 entityPos = entity.position();
        Vec3 blockCenter = VecHelper.getCenterOf(worldPosition);
        Vec3 diff = entityPos.subtract(blockCenter).normalize();

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
        if (level != null && level.getGameTime() != lastSpeedCheck) {
            lastSpeedCheck = level.getGameTime();
            cachedSpeed = getSpeed();
        }

        if (cachedSpeed == null || cachedSpeed == 0) {
            return false;
        }
        return true;
    }

    public void processEntity(Entity entity) {
        if (level == null || level.isClientSide || entity.isRemoved() || getSpeed() == 0) {
            return;
        }

        UUID entityId = entity.getUUID();

        // 检查冷却
        if (isEntityOnCooldown(entityId)) {
            return;
        }

        boolean processedSuccessfully = false;

        // 首先检查是否是羊（保持原版剪羊毛机制）
        if (entity instanceof Sheep sheep && sheep instanceof IShearable shearableTarget) {
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
                    // 羊没有冷却时间，因为原版机制已经处理了
                }
            }
        }

        else if (PeelerEntityProcessingManager.hasProcessing(entity.getType())) {
            PeelerEntityProcessing processing = PeelerEntityProcessingManager.getProcessing(entity.getType());

            if (processing.condition().isPresent()) {
                // 条件检查逻辑
            }

            // 创建 ResourceKey
            ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, processing.lootTable());

            // 获取战利品表
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);

            if (lootTable != null && lootTable != LootTable.EMPTY) {
                // 创建 LootParams - 添加 damage_source 参数
                LootParams params = new LootParams.Builder((ServerLevel)level)
                        .withParameter(LootContextParams.THIS_ENTITY, entity)
                        .withParameter(LootContextParams.ORIGIN, entity.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic()) // 添加这行
                        .create(LootContextParamSets.ENTITY);

                List<ItemStack> drops = lootTable.getRandomItems(params);

                for (ItemStack drop : drops) {
                    if (!tryStoreItemInSecondaryOutput(drop)) {
                        ItemEntity itemEntity = new ItemEntity(level, entity.getX(), entity.getY() + 0.5, entity.getZ(), drop);
                        level.addFreshEntity(itemEntity);
                    }
                }

                level.playSound(null, entity, SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 1.0F, 1.0F);

                entityCooldowns.put(entityId, level.getGameTime() + processing.cooldownTicks());
                processedSuccessfully = true;
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
                level.getGameTime() < entityCooldowns.get(entityId);
    }

    private void setEntityCooldown(UUID entityId) {
        if (level == null) return;
        entityCooldowns.put(entityId, level.getGameTime());
    }

    private void spawnProcessingParticlesEffect() {
        if (level == null || !level.isClientSide) {
            return;
        }

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

    protected boolean shouldProcessBlock() {
        return true;
    }

    protected BlockPos getTargetBlockPos() {
        return getBlockPos().relative(getBlockState().getValue(MechanicalPeelerBlock.FACING));
    }

    private boolean canProcessBlock(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();

        if (DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        if (DataMapHooks.INVERSE_WAXABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        if (block == Blocks.AMETHYST_CLUSTER) {
            return true;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null) {
            String blockPath = blockId.getPath();
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
        Block deoxidized = DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.get(currentBlock);
        if (deoxidized != null) {
            return deoxidized;
        }
        Block dewaxed = DataMapHooks.INVERSE_WAXABLES_DATAMAP.get(currentBlock);
        if (dewaxed != null) {
            return dewaxed;
        }
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

        if (!isProcessingBlock || !currentTargetPos.equals(targetBlockPos)) {
            targetBlockPos = currentTargetPos;
            isProcessingBlock = true;
            blockProcessingTime = 0;
            setChanged();
        }

        float processingSpeedFactor = Mth.clamp(Math.abs(getSpeed()) / 24f, 1f, 128f);
        blockProcessingTime += (int)Math.max(1, processingSpeedFactor);

        if (blockProcessingTime >= BLOCK_BREAK_TIME) {
            if (!level.isClientSide) {
                Block targetBlock = targetState.getBlock();

                if (targetBlock == Blocks.AMETHYST_CLUSTER) {
                    processAmethystCluster(currentTargetPos, targetState);
                } else {
                    ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(targetBlock);
                    if (blockId != null) {
                        String blockPath = blockId.getPath();
                        if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                            processWholeTree(currentTargetPos, targetState);
                        } else {
                            processSingleBlock(currentTargetPos, targetState);
                        }
                    }
                }
            }

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

        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);

        setChanged();
        sendData();
    }

    private void tryDropBarkForLog(BlockPos logPos, BlockState originalLogState) {
        if (level == null || level.isClientSide) return;

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(originalLogState.getBlock());
        if (blockId == null || !blockId.getPath().contains("log") || blockId.getPath().contains("stripped")) {
            return;
        }

        Item bark = getFarmersDelightTreeBark();
        if (bark != null) {
            ItemStack barkDrop = new ItemStack(bark, 1);

            if (!tryStoreItemInSecondaryOutput(barkDrop)) {
                Vec3 dropPosition = Vec3.atCenterOf(logPos).add(0, 0.25, 0);
                ItemEntity itemEntity = new ItemEntity(level, dropPosition.x, dropPosition.y, dropPosition.z, barkDrop);
                itemEntity.setDeltaMovement(
                        (level.random.nextDouble() - 0.5) * 0.1,
                        level.random.nextDouble() * 0.1 + 0.05,
                        (level.random.nextDouble() - 0.5) * 0.1
                );
                level.addFreshEntity(itemEntity);
            }
        }
    }

    private void processSingleBlock(BlockPos pos, BlockState state) {
        Block processedBlock = getProcessedBlock(state.getBlock());
        if (processedBlock != state.getBlock()) {
            BlockState newState = processedBlock.defaultBlockState();
            for (Property<?> property : state.getProperties()) {
                if (newState.hasProperty(property)) {
                    newState = copyPropertyUnchecked(state, newState, property);
                }
            }
            level.setBlock(pos, newState, 3);
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);

            tryDropBarkForLog(pos, state);
        }
    }

    private void processWholeTree(BlockPos startPos, BlockState startState) {
        List<BlockPos> logs = findTreeLogs(startPos, startState);

        if (logs.isEmpty()) {
            processSingleBlock(startPos, startState);
            return;
        }

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

                        for (Property<?> property : logState.getProperties()) {
                            if (newState.hasProperty(property)) {
                                newState = copyPropertyUnchecked(logState, newState, property);
                            }
                        }

                        level.setBlock(logPos, newState, 3);
                        processedCount++;

                        tryDropBarkForLog(logPos, logState);

                        if (processedCount % 3 == 1) {
                            level.playSound(null, logPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.8F, 1.0F);
                        }
                    }
                }
            }
        }

        if (processedCount > 0) {
            level.playSound(null, startPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyPropertyUnchecked(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    private void dropByproducts(BlockPos pos, BlockState originalState, Block processedBlock) {
    }

    private void spawnBlockProcessingParticles(BlockState state) {
        if (targetBlockPos == null || level == null || !level.isClientSide) return;

        RandomSource random = level.random;
        Vec3 center = Vec3.atCenterOf(targetBlockPos);

        if (state.getBlock() == Blocks.AMETHYST_CLUSTER) {
            for (int i = 0; i < 2; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.8;
                double offsetY = (random.nextDouble() - 0.5) * 0.8;
                double offsetZ = (random.nextDouble() - 0.5) * 0.8;

                level.addParticle(
                        ParticleTypes.END_ROD,
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.1D,
                        random.nextDouble() * 0.1D + 0.02D,
                        (random.nextDouble() - 0.5) * 0.1D
                );
            }

            for (int i = 0; i < 1; i++) {
                double offsetX = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getXsize();
                double offsetY = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getYsize();
                double offsetZ = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getZsize();

                level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.2D,
                        random.nextDouble() * 0.1D + 0.05D,
                        (random.nextDouble() - 0.5) * 0.2D
                );
            }
        } else {
            for (int i = 0; i < 3; i++) {
                double offsetX = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getXsize();
                double offsetY = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getYsize();
                double offsetZ = (random.nextDouble() - 0.5) * state.getShape(level, targetBlockPos).bounds().getZsize();

                level.addParticle(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.2D,
                        random.nextDouble() * 0.1D + 0.05D,
                        (random.nextDouble() - 0.5) * 0.2D
                );
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
            if (slot == INPUT_SLOT) {
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
                return outputInv.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
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
                return be.getBlockState().getValue(MechanicalPeelerBlock.FACING) == Direction.UP &&
                        be.canAcceptInput() &&
                        (be.getMatchingRecipe(new SingleRecipeInput(stack)).isPresent() || be.getMatchingRecipe(new SingleRecipeInput(stack)).isEmpty());
            }
            return false;
        }
    }

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
                blockEntity.sendData();
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