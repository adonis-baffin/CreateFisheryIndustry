package com.adonis.createfisheryindustry.block.TrapBearing;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.tags.BiomeTags;
import joptsimple.internal.Strings;
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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.FishingHook;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TrapBearingBlockEntity extends MechanicalBearingBlockEntity {
    private static final Logger LOGGER = LoggerFactory.getLogger(TrapBearingBlockEntity.class);

    private static final int MIN_FRAME_TRAPS = 8;
    private static final int OPEN_WATER_LEVEL_THRESHOLD = 4;
    private static final int UPDATE_INTERVAL = 20;
    private static final int EFFECTIVE_FRAME_CAP = 64;
    private static final int EXTRACT_INTERVAL = 10;

    // 物品存储 - 27格存储空间
    public static final int INVENTORY_SIZE = 27;
    protected ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!level.isClientSide) {
                sendData();
            }
        }
    };

    // 静态数据（组装时确定）
    private int totalFrameTraps = 0;
    private List<BlockPos> initialFrameTrapPositions = new ArrayList<>();

    // 动态数据（运行时更新）
    private int waterFrameCount = 0;
    private int lavaFrameCount = 0;
    private int validFrameCount = 0;
    private FluidType dominantFluid = FluidType.NONE;
    private List<BlockPos> activeFramePositions = new ArrayList<>();

    // 等级追踪
    private int currentFishingLevel = 0;
    private int previousFishingLevel = 0;

    // 钓鱼状态
    private int fishingCooldown = 0;
    private boolean isFishing = false;
    private boolean isOpenWater = false;
    private boolean shouldResetCooldownNextTime = false;

    // 定时器
    private int updateTimer = 0;
//    private int extractTimer = 0;

    // 统计数据
    private int totalCaught = 0;
    private int successfulAttempts = 0;

    // 虚拟钓鱼工具
    private FishingHook virtualHook;
    private TrapBearingFakePlayer fakePlayer;

    // 添加这个字段，用于记录是否因为库存满而暂停钓鱼
    private boolean fishingPausedDueToFullInventory = false;

    public TrapBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    /**
     * 获取物品处理器，供外部访问（如漏斗、管道）
     */
    public IItemHandler getInventory() {
        return inventory;
    }

    /**
     * 确保虚拟钓鱼工具已初始化
     */
    private void ensureVirtualToolsInitialized() {
        if (virtualHook == null && level instanceof ServerLevel serverLevel) {
            fakePlayer = new TrapBearingFakePlayer(serverLevel);
            virtualHook = new FishingHook(fakePlayer, serverLevel, 0, 0);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide && running) {
            // 确保虚拟工具已初始化
            ensureVirtualToolsInitialized();

            // 每 20 ticks 轮询一次捕鱼框状态
            if (++updateTimer >= UPDATE_INTERVAL) {
                updateTimer = 0;
                updateFrameTrapStates();
            }

            // 每 10 ticks 尝试从装置存储抽取物品
//            if (++extractTimer >= EXTRACT_INTERVAL) {
//                extractTimer = 0;
//                extractItemsFromContraption();
//            }

            updateFishingLogic();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
    }

    /**
     * 从装置存储中抽取物品到自身存储
     * 这个方法是安全的：如果装置存储不可用，直接跳过，等下次再试
     */
//    private void extractItemsFromContraption() {
//        // 安全检查：装置是否就绪
//        if (movedContraption == null) {
//            return;
//        }
//
//        var contraption = movedContraption.getContraption();
//        if (contraption == null || contraption.disassembled) {
//            return;
//        }
//
//        var storage = contraption.getStorage();
//        if (storage == null) {
//            return;
//        }
//
//        var contraptionItems = storage.getAllItems();
//        if (contraptionItems == null || contraptionItems.getSlots() <= 0) {
//            return;
//        }
//
//        // 遍历装置存储中的物品
//        for (int i = 0; i < contraptionItems.getSlots(); i++) {
//            ItemStack stackInSlot = contraptionItems.getStackInSlot(i);
//            if (stackInSlot.isEmpty()) {
//                continue;
//            }
//
//            // 尝试插入到自身存储
//            ItemStack toInsert = stackInSlot.copy();
//            ItemStack remainder = ItemHandlerHelper.insertItem(inventory, toInsert, true); // 先模拟
//
//            if (remainder.getCount() < toInsert.getCount()) {
//                // 可以插入至少一部分
//                int insertCount = toInsert.getCount() - remainder.getCount();
//
//                // 从装置存储中提取
//                ItemStack extracted = contraptionItems.extractItem(i, insertCount, false);
//
//                // 插入到自身存储
//                if (!extracted.isEmpty()) {
//                    ItemHandlerHelper.insertItem(inventory, extracted, false);
//                }
//            }
//
//            // 检查自身存储是否已满
//            if (isInventoryFull()) {
//                break;
//            }
//        }
//    }

    /**
     * 检查自身存储是否已满
     */
    private boolean isInventoryFull() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                return false; // 还有空槽位，未满
            }
        }
        return true; // 所有 27 个槽位都有物品，已满
    }

    /**
     * 轮询 Contraption，更新捕鱼框状态
     */
    private void updateFrameTrapStates() {
        if (movedContraption == null || movedContraption.getContraption() == null) {
            return;
        }

        // 清空动态数据
        waterFrameCount = 0;
        lavaFrameCount = 0;
        activeFramePositions.clear();

        // 重置总捕鱼框计数
        totalFrameTraps = 0;

        Contraption contraption = movedContraption.getContraption();

        // 遍历装置上的所有方块
        for (var entry : contraption.getBlocks().entrySet()) {
            BlockPos localPos = entry.getKey();
            var blockInfo = entry.getValue();

            // 只处理捕鱼框
            if (!(blockInfo.state().getBlock() instanceof FrameTrapBlock)) {
                continue;
            }

            // 统计总捕鱼框数量
            totalFrameTraps++;

            // 转换为世界坐标
            Vec3 localVec = Vec3.atCenterOf(localPos);
            Vec3 globalVec = movedContraption.toGlobalVector(localVec, 0);
            BlockPos worldPos = BlockPos.containing(globalVec);

            // 检查流体状态
            FluidState fluid = level.getFluidState(worldPos);

            if (fluid.is(FluidTags.WATER)) {
                waterFrameCount++;
                activeFramePositions.add(worldPos);
            } else if (fluid.is(FluidTags.LAVA) && CreateFisheryCommonConfig.isLavaFishingEnabled()) {
                lavaFrameCount++;
                activeFramePositions.add(worldPos);
            }
        }

        // 判断工作模式
        determineWorkingMode();

        // 计算当前等级
        previousFishingLevel = currentFishingLevel;
        currentFishingLevel = getFishingLevel();

        // 更新开放水域状态
        updateOpenWaterStatus();

        // 检测等级变化
        if (currentFishingLevel != previousFishingLevel) {
            handleLevelChange();
        }
    }

    /**
     * 判断工作模式和有效捕鱼框数量
     */
    private void determineWorkingMode() {
        // 如果有岩浆中的捕鱼框
        if (lavaFrameCount > 0) {
            // 尝试使用 NDU 模式
            if (CreateFisheryCommonConfig.isLavaFishingEnabled() &&
                    CreateFisheryCommonConfig.useNDULootTables()) {

                // 尝试获取 NDU 战利品表验证是否可用
                if (level instanceof ServerLevel serverLevel) {
                    try {
                        var registries = serverLevel.getServer().reloadableRegistries();
                        ResourceLocation nduLavaFishing = ResourceLocation.fromNamespaceAndPath(
                                "netherdepthsupgrade", "gameplay/lava_fishing");
                        ResourceKey<LootTable> lavaKey = ResourceKey.create(
                                net.minecraft.core.registries.Registries.LOOT_TABLE, nduLavaFishing);

                        LootTable lavaTable = registries.getLootTable(lavaKey);

                        if (lavaTable != LootTable.EMPTY) {
                            // NDU 可用，使用岩浆模式
                            dominantFluid = FluidType.LAVA;
                            validFrameCount = lavaFrameCount;
                            return;
                        }
                    } catch (Exception e) {
                        // NDU 不可用，继续回退
                    }
                }
            }

            // NDU 不可用或未启用，回退到水模式
            dominantFluid = FluidType.WATER;
            validFrameCount = waterFrameCount;
        } else {
            // 没有岩浆中的捕鱼框，使用水模式
            dominantFluid = FluidType.WATER;
            validFrameCount = waterFrameCount;
        }

        // 限制上限
        validFrameCount = Math.min(validFrameCount, EFFECTIVE_FRAME_CAP);
    }

    /**
     * 更新开放水域状态
     */
    private void updateOpenWaterStatus() {
        isOpenWater = (currentFishingLevel >= OPEN_WATER_LEVEL_THRESHOLD);
    }

    /**
     * 处理等级变化
     */
    private void handleLevelChange() {
        if (currentFishingLevel == 0) {
            // 特殊处理：降至空闲，立即停止钓鱼
            isFishing = false;
            fishingCooldown = 0;
            shouldResetCooldownNextTime = false;
        } else if (previousFishingLevel == 0 && currentFishingLevel > 0) {
            // 从空闲恢复到工作状态，立即开始新倒计时
            isFishing = true;
            fishingCooldown = calculateCooldownForLevel(currentFishingLevel);
            shouldResetCooldownNextTime = false;
        } else {
            // 等级在工作范围内变化，标记下次完成后更新
            shouldResetCooldownNextTime = true;
        }
    }

    /**
     * 根据等级计算随机倒计时
     * @param level 钓鱼等级 (1-8)
     * @return 随机倒计时（ticks）
     */
    private int calculateCooldownForLevel(int level) {
        if (level <= 0) {
            return 0;
        }

        int minTicks, maxTicks;

        if (level < 4) {
            // 等级 1-3: min随等级变化，max固定12秒
            minTicks = (9 - level) * 20;  // 1→160, 2→140, 3→120
            maxTicks = 240;  // 12秒
        } else {
            // 等级 4-8: min随等级变化，max固定9秒
            minTicks = (9 - level) * 20;  // 4→100, 5→80, 6→60, 7→40, 8→20
            maxTicks = 180;  // 9秒
        }

        // 在 [min, max] 之间随机
        if (maxTicks == minTicks) {
            return minTicks;
        }
        return minTicks + this.level.getRandom().nextInt(maxTicks - minTicks + 1);
    }

    private void updateFishingLogic() {
        if (!CreateFisheryCommonConfig.isFishingEnabled()) {
            return;
        }

        // 等级为0时不钓鱼
        if (currentFishingLevel == 0) {
            isFishing = false;
            fishingPausedDueToFullInventory = false;
            return;
        }

        boolean currentlyFull = isInventoryFull();

        // 如果库存已满，暂停钓鱼（不递减倒计时）
        if (currentlyFull) {
            if (isFishing && !fishingPausedDueToFullInventory) {
                // 刚变成满，标记暂停状态
                fishingPausedDueToFullInventory = true;
            }
            // 无论如何都不递减 cooldown，也不生成任何东西
            return;
        }

        // 库存有空间

        // 如果之前因为满而暂停，现在恢复
        if (fishingPausedDueToFullInventory) {
            fishingPausedDueToFullInventory = false;
            // 可选：这里可以给玩家一点反馈，比如小粒子，但不必须
        }

        // 确保钓鱼状态已启动
        if (!isFishing) {
            isFishing = true;
            fishingCooldown = calculateCooldownForLevel(currentFishingLevel);
            shouldResetCooldownNextTime = false;
        }

        // 正常递减倒计时
        if (--fishingCooldown <= 0) {
            performFishing();

            // 重置倒计时
            if (shouldResetCooldownNextTime) {
                fishingCooldown = calculateCooldownForLevel(currentFishingLevel);
                shouldResetCooldownNextTime = false;
            } else {
                fishingCooldown = calculateCooldownForLevel(currentFishingLevel);
            }
        }

        // 显示工作中粒子（有空间时才显示，避免满时还有动画误导）
        if (level.getRandom().nextFloat() < 0.1f) {
            showWorkingParticles();
        }
    }

    /**
     * 执行钓鱼
     */
    private void performFishing() {
        // 双重保险：再次检查库存（防止极端并发情况）
        if (isInventoryFull()) {
            return;
        }

        successfulAttempts++;

        // 生成战利品
        ItemStack loot;
        if (level.getRandom().nextFloat() < 0.05f) {
            // 5% 概率生成 worn_harpoon
            loot = new ItemStack(CreateFisheryItems.WORN_HARPOON.get());
        } else {
            loot = generateFishingLoot();
        }

        if (loot.isEmpty()) {
            // 战利品生成失败（极少见），直接结束
            return;
        }

        totalCaught++;

        // 尝试插入到自身库存
        ItemStack remainder = ItemHandlerHelper.insertItem(inventory, loot, false);

        boolean fullyInserted = remainder.isEmpty();

        // 如果没有完全插入（库存在此期间变满），将剩余部分掉落
        if (!fullyInserted) {
            spawnItemAtBestLocation(remainder);
        }

        // 只有至少插入了一部分，才视为“成功钓鱼一次”，生成经验和特效
        if (fullyInserted || remainder.getCount() < loot.getCount()) {
            spawnExperienceNugget();
            playFishingEffects();
        }
        // ===== 额外海洋贻贝奖励（生物群系专属）=====
        if (level instanceof ServerLevel serverLevel) {
            Holder<Biome> biomeHolder = level.getBiome(worldPosition);

            // 只在海洋生物群系中触发额外奖励
            if (biomeHolder.is(BiomeTags.IS_OCEAN)) {
                ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
                if (biomeKey == null) return;

                // 概率：25% 触发额外贻贝
                if (level.random.nextFloat() < 0.1f) {
                    ItemStack musselStack;
                    int amount = 1 + level.random.nextInt(2); // 1~3 个

                    // 暖水海洋：地中海贻贝
                    if (biomeKey.equals(Biomes.WARM_OCEAN) || biomeKey.equals(Biomes.LUKEWARM_OCEAN)) {
                        musselStack = new ItemStack(CreateFisheryItems.RAW_MEDITERRANEAN_MUSSEL.get(), amount);
                    } else {
                        // 其他所有海洋：蓝贻贝
                        musselStack = new ItemStack(CreateFisheryItems.RAW_BLUE_MUSSEL.get(), amount);
                    }

                    // 尝试插入轴承自身库存，失败则掉落世界
                    ItemStack musselRemainder = ItemHandlerHelper.insertItem(inventory, musselStack, false);
                    if (!musselRemainder.isEmpty()) {
                        spawnItemAtBestLocation(musselRemainder);
                    }
                }
            }
        }
    }

    /**
     * 生成钓鱼战利品
     */
    private ItemStack generateFishingLoot() {
        if (virtualHook == null || !(level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }

        virtualHook.setPos(Vec3.atCenterOf(worldPosition));
        virtualHook.openWater = isOpenWater;

        LootTable lootTable = getLootTable(serverLevel);

        float luckBonus = 0.0f;
        if (movedContraption != null && movedContraption.getContraption() != null) {
            luckBonus = EnchantmentHelper.getFishingLuckBonus(
                    serverLevel,
                    new ItemStack(Items.FISHING_ROD),
                    movedContraption.getContraption().entity
            );
        }

        if (isOpenWater) {
            luckBonus += 2.0f;
        }

        try {
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                    .withParameter(LootContextParams.THIS_ENTITY, virtualHook)
                    .withLuck(luckBonus)
                    .create(LootContextParamSets.FISHING);

            List<ItemStack> items = lootTable.getRandomItems(params);

            if (items.isEmpty()) {
                return ItemStack.EMPTY;
            }

            return items.get(0);

        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * 获取战利品表
     */
    private LootTable getLootTable(ServerLevel serverLevel) {
        var registries = serverLevel.getServer().reloadableRegistries();

        // 只有岩浆模式才尝试 NDU
        if (dominantFluid == FluidType.LAVA) {
            if (!CreateFisheryCommonConfig.isLavaFishingEnabled()) {
                return registries.getLootTable(BuiltInLootTables.FISHING);
            }

            if (!CreateFisheryCommonConfig.useNDULootTables()) {
                return registries.getLootTable(BuiltInLootTables.FISHING);
            }

            try {
                ResourceLocation nduLavaFishing = ResourceLocation.fromNamespaceAndPath(
                        "netherdepthsupgrade", "gameplay/lava_fishing");
                ResourceLocation nduNetherFishing = ResourceLocation.fromNamespaceAndPath(
                        "netherdepthsupgrade", "gameplay/nether_fishing");

                ResourceKey<LootTable> lavaKey = ResourceKey.create(
                        net.minecraft.core.registries.Registries.LOOT_TABLE, nduLavaFishing);
                ResourceKey<LootTable> netherKey = ResourceKey.create(
                        net.minecraft.core.registries.Registries.LOOT_TABLE, nduNetherFishing);

                // 下界优先使用下界钓鱼表
                if (serverLevel.dimension() == Level.NETHER) {
                    LootTable netherTable = registries.getLootTable(netherKey);
                    if (netherTable != LootTable.EMPTY) {
                        return netherTable;
                    }
                }

                // 否则使用熔岩钓鱼表
                LootTable lavaTable = registries.getLootTable(lavaKey);
                if (lavaTable != LootTable.EMPTY) {
                    return lavaTable;
                }

            } catch (Exception e) {
                // 任何异常都回退
            }
        }

        // 默认回退到普通钓鱼表
        return registries.getLootTable(BuiltInLootTables.FISHING);
    }

    /**
     * 在最佳位置生成掉落物品
     * 优先掉落到随机捕鱼框位置，如果没有则掉落在轴承位置
     */
    private void spawnItemAtBestLocation(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        BlockPos dropPos;
        if (!activeFramePositions.isEmpty()) {
            // 有活跃的捕鱼框，掉落到随机位置
            dropPos = activeFramePositions.get(level.getRandom().nextInt(activeFramePositions.size()));
        } else {
            // 没有捕鱼框，掉落在轴承位置
            dropPos = worldPosition;
        }

        net.minecraft.world.entity.item.ItemEntity entity = new net.minecraft.world.entity.item.ItemEntity(level,
                dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, stack);
        entity.setDefaultPickUpDelay();
        entity.setDeltaMovement(0, 0.2, 0);
        level.addFreshEntity(entity);
    }

    /**
     * 生成经验颗粒
     */
    private void spawnExperienceNugget() {
        Item expNuggetItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));

        if (expNuggetItem != null &&
                expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {

            // 基础 1 个经验粒
            int nuggetCount = 1;

            // 1/6 概率额外 1 个（模拟原版钓鱼 1-6 点经验，期望 3.5）
            if (level.getRandom().nextInt(6) == 0) {
                nuggetCount = 2;
            }

            ItemStack expNugget = new ItemStack(expNuggetItem, nuggetCount);

            // 直接插入到自身的存储中
            ItemStack remainder = ItemHandlerHelper.insertItem(inventory, expNugget, false);

            // 如果插入失败，掉落到最佳位置
            if (!remainder.isEmpty()) {
                spawnItemAtBestLocation(remainder);
            }
        } else {
            // 如果找不到经验颗粒物品，生成经验球（回退方案）
            BlockPos dropPos;
            if (!activeFramePositions.isEmpty()) {
                dropPos = activeFramePositions.get(level.getRandom().nextInt(activeFramePositions.size()));
            } else {
                dropPos = worldPosition;
            }

            int expAmount = isOpenWater ? 2 + level.getRandom().nextInt(3) : 1;
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level,
                    dropPos.getX() + 0.5, dropPos.getY() + 0.5, dropPos.getZ() + 0.5, expAmount));
        }
    }

    /**
     * 播放钓鱼成功的音效和粒子效果
     */
    private void playFishingEffects() {
        if (!(level instanceof ServerLevel serverLevel) || activeFramePositions.isEmpty()) {
            return;
        }

        BlockPos pos = activeFramePositions.get(level.getRandom().nextInt(activeFramePositions.size()));

        if (dominantFluid == FluidType.WATER) {
            // 水中钓鱼成功
            serverLevel.sendParticles(ParticleTypes.BUBBLE,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    15, 0.5, 0.5, 0.5, 0.1);

            level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        } else if (dominantFluid == FluidType.LAVA) {
            // 熔岩钓鱼成功
            serverLevel.sendParticles(ParticleTypes.LAVA,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    10, 0.3, 0.3, 0.3, 0.1);

            serverLevel.sendParticles(ParticleTypes.FLAME,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    5, 0.2, 0.2, 0.2, 0.05);

            level.playSound(null, pos, SoundEvents.LAVA_POP,
                    SoundSource.BLOCKS, 1.0F, 1.2F);
        }
    }

    /**
     * 显示工作中的粒子效果
     */
    private void showWorkingParticles() {
        if (!(level instanceof ServerLevel serverLevel) || activeFramePositions.isEmpty()) {
            return;
        }

        for (BlockPos pos : activeFramePositions) {
            if (level.getRandom().nextFloat() < 0.05f) {
                serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    @Override
    public void assemble() {
        super.assemble();

        if (running && level instanceof ServerLevel serverLevel) {
            // 使用统一的初始化方法
            ensureVirtualToolsInitialized();

            // 立即轮询一次
            updateFrameTrapStates();

            level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public void disassemble() {
        super.disassemble();

        // 清空运行时数据（但保留存储中的物品）
        initialFrameTrapPositions.clear();
        activeFramePositions.clear();
        totalFrameTraps = 0;
        waterFrameCount = 0;
        lavaFrameCount = 0;
        validFrameCount = 0;
        currentFishingLevel = 0;
        previousFishingLevel = 0;
        fishingCooldown = 0;
        isFishing = false;
        isOpenWater = false;
        dominantFluid = FluidType.NONE;
        shouldResetCooldownNextTime = false;

        if (virtualHook != null) {
            virtualHook.discard();
            virtualHook = null;
        }

        fakePlayer = null;
    }

    public void setAssembleNextTick(boolean value) {
        assembleNextTick = value;
    }

    /**
     * 计算钓鱼等级
     */
    private int getFishingLevel() {
        if (validFrameCount < 8) return 0;  // 空闲
        if (validFrameCount < 16) return 1;
        if (validFrameCount < 24) return 2;
        if (validFrameCount < 32) return 3;
        if (validFrameCount < 40) return 4;
        if (validFrameCount < 48) return 5;
        if (validFrameCount < 56) return 6;
        if (validFrameCount < 64) return 7;
        return 8;  // 最高等级
    }

    /**
     * 获取等级文本组件
     */
    private MutableComponent getLevelTextComponent() {
        int level = currentFishingLevel;
        if (level == 0) {
            return CreateLang.translateDirect("trap_bearing.idle");
        } else if (level == 8) {
            return CreateLang.translateDirect("trap_bearing.max_lvl");
        } else {
            return CreateLang.translateDirect("trap_bearing.lvl", String.valueOf(level));
        }
    }

    /**
     * 获取捕鱼框条形图组件
     */
    private MutableComponent getFrameTrapsComponent() {
        int level = currentFishingLevel;
        int maxLevel = 8;

        return Component.empty()
                .append(bars(level, ChatFormatting.DARK_GREEN))
                .append(bars(maxLevel - level, ChatFormatting.DARK_GRAY));
    }

    /**
     * 获取工作状态组件
     */
    private MutableComponent getWorkingStateComponent() {
        if (currentFishingLevel == 0) {
            if (totalFrameTraps == 0) {
                return CreateLang.translateDirect("trap_bearing.state.idle");
            }
            return CreateLang.translateDirect("trap_bearing.state.insufficient");
        }

        if (dominantFluid == FluidType.LAVA) {
            return CreateLang.translateDirect("trap_bearing.state.lava_fishing");
        }

        if (dominantFluid == FluidType.WATER && isOpenWater) {
            return CreateLang.translateDirect("trap_bearing.state.water_fishing_treasure");
        }

        if (dominantFluid == FluidType.WATER) {
            return CreateLang.translateDirect("trap_bearing.state.water_fishing");
        }

        return CreateLang.translateDirect("trap_bearing.state.unknown");
    }

    /**
     * 生成条形图
     */
    private MutableComponent bars(int count, ChatFormatting format) {
        return Component.literal(Strings.repeat('|', count))
                .withStyle(format);
    }

    /**
     * 计算存储中已使用的槽位数
     */
    private int getUsedSlotCount() {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取存储中的物品总数
     */
    private int getTotalItemCount() {
        int count = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            count += inventory.getStackInSlot(i).getCount();
        }
        return count;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 第一部分：捕鱼轴承标题
        CreateLang.translate("tooltip.trap_bearing.header")
                .forGoggles(tooltip);

        if (!running) {
            CreateLang.translate("tooltip.trap_bearing.not_running")
                    .style(ChatFormatting.DARK_GRAY)
                    .forGoggles(tooltip);
        } else {
            // 显示当前状态
            CreateLang.translate("tooltip.trap_bearing.status", getLevelTextComponent())
                    .style(ChatFormatting.YELLOW)
                    .forGoggles(tooltip);

            // 捕鱼框条形图
            CreateLang.builder()
                    .add(CreateLang.translateDirect("tooltip.trap_bearing.frame_traps")
                            .withStyle(ChatFormatting.GRAY))
                    .add(getFrameTrapsComponent())
                    .forGoggles(tooltip);

            // 工作状态
            CreateLang.translate("tooltip.trap_bearing.working_state", getWorkingStateComponent())
                    .style(ChatFormatting.GRAY)
                    .forGoggles(tooltip);
        }

        // 第二部分：存储内容（无论是否运行都显示）
        int usedSlots = getUsedSlotCount();
        int totalItems = getTotalItemCount();

        if (totalItems > 0) {
            CreateLang.translate("tooltip.trap_bearing.storage", totalItems, usedSlots, INVENTORY_SIZE)
                    .style(ChatFormatting.GREEN)
                    .forGoggles(tooltip);
        }

        // 第三部分：动力状态（仿照机械手）
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

        // 保存存储内容
        compound.put("Inventory", inventory.serializeNBT(registries));

        // 无论是否 clientPacket，都保存这些用于显示的数据
        compound.putInt("TotalFrameTraps", totalFrameTraps);
        compound.putInt("WaterFrameCount", waterFrameCount);
        compound.putInt("LavaFrameCount", lavaFrameCount);
        compound.putInt("ValidFrameCount", validFrameCount);
        compound.putInt("CurrentFishingLevel", currentFishingLevel);
        compound.putInt("PreviousFishingLevel", previousFishingLevel);
        compound.putBoolean("IsFishing", isFishing);
        compound.putBoolean("OpenWater", isOpenWater);
        compound.putInt("DominantFluid", dominantFluid.ordinal());

        // 以下数据可能只在服务端需要
        if (!clientPacket) {
            compound.putInt("FishingCooldown", fishingCooldown);
            compound.putBoolean("ShouldResetCooldownNextTime", shouldResetCooldownNextTime);
            compound.putInt("TotalCaught", totalCaught);
            compound.putInt("SuccessfulAttempts", successfulAttempts);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        // 如果这个方块实体已经被移动（成为装置的一部分），直接调用父类逻辑并返回
        if (wasMoved) {
            super.read(compound, registries, clientPacket);
            return;
        }

        super.read(compound, registries, clientPacket);

        // 加载存储内容
        if (compound.contains("Inventory")) {
            inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        }

        totalFrameTraps = compound.getInt("TotalFrameTraps");
        waterFrameCount = compound.getInt("WaterFrameCount");
        lavaFrameCount = compound.getInt("LavaFrameCount");
        validFrameCount = compound.getInt("ValidFrameCount");
        currentFishingLevel = compound.getInt("CurrentFishingLevel");
        previousFishingLevel = compound.getInt("PreviousFishingLevel");
        isFishing = compound.getBoolean("IsFishing");
        isOpenWater = compound.getBoolean("OpenWater");
        dominantFluid = FluidType.values()[compound.getInt("DominantFluid")];

        // 只在服务端加载完整数据
        if (!clientPacket) {
            fishingCooldown = compound.getInt("FishingCooldown");
            shouldResetCooldownNextTime = compound.getBoolean("ShouldResetCooldownNextTime");
            totalCaught = compound.getInt("TotalCaught");
            successfulAttempts = compound.getInt("SuccessfulAttempts");
        }
    }

    @Override
    public void remove() {
        if (virtualHook != null) {
            virtualHook.discard();
            virtualHook = null;
        }
        fakePlayer = null;
        super.remove();
    }

    @Override
    public void invalidate() {
        super.invalidate();
    }

    /**
     * 掉落存储中的所有物品
     * 当方块被破坏时调用
     */
    public void dropInventoryContents() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack);
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    private enum FluidType {
        NONE,
        WATER,
        LAVA
    }
}