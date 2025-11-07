package com.adonis.createfisheryindustry.block.FishingBearing;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.AssemblyException;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
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
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.*;

public class FishingBearingBlockEntity extends MechanicalBearingBlockEntity {

    private static final int MIN_FRAME_TRAPS = 8;
    private static final int BASE_COOLDOWN = 200;
    private static final int COOLDOWN_REDUCTION = 5;
    private static final int MIN_COOLDOWN = 80;
    private static final int OPEN_WATER_CHECK_INTERVAL = 100;
    private static final int MAX_FRAME_DISTANCE = 16;
    private static final int MAX_FRAME_COUNT = 64;
    private static final int EFFECTIVE_FRAME_CAP = 64;

    private int totalFrameTraps = 0;
    private int validFrameTraps = 0;
    private Map<BlockPos, FluidType> frameTrapPositions = new HashMap<>();
    private List<BlockPos> initialFrameTrapPositions = new ArrayList<>(); // 新增：初始绝对位置列表

    private int fishingCooldown = 0;
    private int openWaterCheckTimer = 0;
    private boolean isOpenWater = false;

    private int totalCaught = 0;
    private int successfulAttempts = 0;

    private FishingHook virtualHook;
    private FishingBearingFakePlayer fakePlayer;

    private int debugTickCounter = 0;

    public FishingBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    public void reportFrameTrapPosition(BlockPos worldPos, boolean inWater, boolean inLava) {
        FluidType fluidType = FluidType.NONE;
        if (inWater) {
            fluidType = FluidType.WATER;
        } else if (inLava) {
            fluidType = FluidType.LAVA;
        }
        frameTrapPositions.put(worldPos, fluidType);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide && running) {
            frameTrapPositions.clear();
            updateValidFrameTraps();
            updateFishingLogic();

            // 每100 tick输出一次状态
            if (++debugTickCounter >= 100) {
                debugTickCounter = 0;
                System.out.println("=== 钓鱼轴承状态 @ " + worldPosition + " ===");
                System.out.println("总捕鱼框: " + totalFrameTraps);
                System.out.println("有效捕鱼框: " + validFrameTraps);
                System.out.println("当前位置数: " + frameTrapPositions.size());
                System.out.println("冷却时间: " + fishingCooldown);
                System.out.println("开放水域: " + isOpenWater);
                System.out.println("总捕获: " + totalCaught);
                System.out.println("成功尝试: " + successfulAttempts);
                System.out.println("虚拟钩: " + (virtualHook != null));
                System.out.println("假玩家: " + (fakePlayer != null));
                System.out.println("配置启用: " + CreateFisheryCommonConfig.isFishingEnabled());

                // 显示流体类型分布
                Map<FluidType, Long> fluidCounts = new HashMap<>();
                for (FluidType type : frameTrapPositions.values()) {
                    fluidCounts.put(type, fluidCounts.getOrDefault(type, 0L) + 1);
                }
                System.out.println("流体分布: " + fluidCounts);
                System.out.println("========================================");
            }
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
    }

    private void updateValidFrameTraps() {
        validFrameTraps = 0;
        frameTrapPositions.clear();
        for (BlockPos pos : initialFrameTrapPositions) {
            FluidState fluid = level.getFluidState(pos);
            FluidType type = FluidType.NONE;
            if (fluid.is(FluidTags.WATER)) {
                type = FluidType.WATER;
            } else if (fluid.is(FluidTags.LAVA) && CreateFisheryCommonConfig.isLavaFishingEnabled()) {
                type = FluidType.LAVA;
            }
            frameTrapPositions.put(pos, type);
            if (type != FluidType.NONE) {
                validFrameTraps++;
            }
        }
        validFrameTraps = Math.min(validFrameTraps, EFFECTIVE_FRAME_CAP);
    }

    private void updateFishingLogic() {
        if (!CreateFisheryCommonConfig.isFishingEnabled()) {
            System.out.println("[钓鱼] 配置未启用");
            return;
        }

        if (--openWaterCheckTimer <= 0) {
            openWaterCheckTimer = OPEN_WATER_CHECK_INTERVAL;
            checkOpenWater();
        }

        if (validFrameTraps >= MIN_FRAME_TRAPS) {
            if (--fishingCooldown <= 0) {
                System.out.println("[钓鱼] 冷却完成，开始钓鱼...");
                performFishing();
                updateCooldown();
            }

            if (level.getRandom().nextFloat() < 0.1f) {
                showWorkingParticles();
            }
        } else {
            if (debugTickCounter == 0) {
                System.out.println("[钓鱼] 有效捕鱼框不足: " + validFrameTraps + " < " + MIN_FRAME_TRAPS);
            }
        }
    }

    @Override
    public void assemble() {
        System.out.println("=== 开始组装 @ " + worldPosition + " ===");

        if (!scanAndValidateFrameTraps()) {
            System.out.println("[组装] 验证失败");
            sendData();
            return;
        }

        System.out.println("[组装] 验证成功，找到 " + totalFrameTraps + " 个捕鱼框");

        super.assemble();

        if (running && level instanceof ServerLevel serverLevel) {
            System.out.println("[组装] 创建虚拟钩和假玩家...");
            fakePlayer = new FishingBearingFakePlayer(serverLevel);
            virtualHook = new FishingHook(fakePlayer, serverLevel, 0, 0);

            System.out.println("[组装] 虚拟钩创建: " + (virtualHook != null));
            System.out.println("[组装] 假玩家创建: " + (fakePlayer != null));

            updateCooldown();
            checkOpenWater();

            System.out.println("[组装] 初始冷却时间: " + fishingCooldown);
            System.out.println("[组装] 开放水域: " + isOpenWater);

            level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        System.out.println("=== 组装完成 ===");
    }

    @Override
    public void disassemble() {
        System.out.println("=== 开始拆卸 @ " + worldPosition + " ===");

        super.disassemble();

        frameTrapPositions.clear();
        initialFrameTrapPositions.clear(); // 清空初始位置
        totalFrameTraps = 0;
        validFrameTraps = 0;
        fishingCooldown = 0;
        isOpenWater = false;

        if (virtualHook != null) {
            virtualHook.discard();
            virtualHook = null;
        }

        fakePlayer = null;

        System.out.println("=== 拆卸完成 ===");
    }

    private boolean scanAndValidateFrameTraps() {
        Direction facing = getBlockState().getValue(BearingBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);

        System.out.println("[扫描] 正前方位置: " + frontPos);

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(frontPos);
        visited.add(frontPos);

        for (Direction dir : Direction.values()) {
            BlockPos neighbor = frontPos.relative(dir);
            if (!visited.contains(neighbor)) {
                queue.add(neighbor);
                visited.add(neighbor);
            }
        }

        System.out.println("[扫描] 初始队列大小: " + queue.size());

        int count = 0;

        while (!queue.isEmpty() && count < MAX_FRAME_COUNT) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);

            if (state.getBlock() instanceof FrameTrapBlock) {
                count++;
                initialFrameTrapPositions.add(current); // 记录初始绝对位置

                for (Direction dir : Direction.values()) {
                    BlockPos neighbor = current.relative(dir);
                    if (!visited.contains(neighbor) &&
                            neighbor.distSqr(worldPosition) <= MAX_FRAME_DISTANCE * MAX_FRAME_DISTANCE) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }

        totalFrameTraps = count;
        System.out.println("[扫描] 找到捕鱼框数量: " + count);

        if (count < MIN_FRAME_TRAPS) {
            lastException = new AssemblyException(
                    Component.translatable("create_fishery.gui.assembly.insufficient_frame_traps",
                                    count, MIN_FRAME_TRAPS)
                            .withStyle(ChatFormatting.YELLOW)
            );
            return false;
        }

        return true;
    }

    private void checkOpenWater() {
        if (frameTrapPositions.isEmpty()) {
            isOpenWater = false;
            System.out.println("[开放水域] 无捕鱼框位置");
            return;
        }

        Map<FluidType, Long> fluidCounts = new HashMap<>();
        for (FluidType type : frameTrapPositions.values()) {
            fluidCounts.put(type, fluidCounts.getOrDefault(type, 0L) + 1);
        }

        long waterCount = fluidCounts.getOrDefault(FluidType.WATER, 0L);
        long lavaCount = fluidCounts.getOrDefault(FluidType.LAVA, 0L);

        FluidType dominantType = waterCount >= lavaCount ? FluidType.WATER : FluidType.LAVA;
        System.out.println("[开放水域] 主导流体: " + dominantType + " (水:" + waterCount + ", 岩浆:" + lavaCount + ")");

        if (dominantType == FluidType.WATER) {
            isOpenWater = checkOpenWaterArea();
        } else {
            isOpenWater = checkOpenLavaArea();
        }

        System.out.println("[开放水域] 结果: " + isOpenWater);
    }

    private boolean checkOpenWaterArea() {
        List<BlockPos> waterPositions = frameTrapPositions.entrySet().stream()
                .filter(e -> e.getValue() == FluidType.WATER)
                .map(Map.Entry::getKey)
                .toList();

        if (waterPositions.isEmpty()) return false;

        BlockPos sample = waterPositions.get(0);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos checkPos = sample.offset(x, y, z);
                    if (y == -1) {
                        if (!level.getFluidState(checkPos).is(FluidTags.WATER)) {
                            return false;
                        }
                    } else {
                        if (!level.getFluidState(checkPos).is(FluidTags.WATER) &&
                                !level.getBlockState(checkPos).isAir()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private boolean checkOpenLavaArea() {
        List<BlockPos> lavaPositions = frameTrapPositions.entrySet().stream()
                .filter(e -> e.getValue() == FluidType.LAVA)
                .map(Map.Entry::getKey)
                .toList();

        if (lavaPositions.isEmpty()) return false;

        BlockPos sample = lavaPositions.get(0);

        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos checkPos = sample.offset(x, y, z);
                    if (y == -1) {
                        if (!level.getFluidState(checkPos).is(FluidTags.LAVA)) {
                            return false;
                        }
                    } else {
                        if (!level.getFluidState(checkPos).is(FluidTags.LAVA) &&
                                !level.getBlockState(checkPos).isAir()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private void updateCooldown() {
        int effectiveCount = Math.min(validFrameTraps, EFFECTIVE_FRAME_CAP);
        int extraFrames = Math.max(0, effectiveCount - MIN_FRAME_TRAPS);
        fishingCooldown = Math.max(MIN_COOLDOWN, BASE_COOLDOWN - extraFrames * COOLDOWN_REDUCTION);

        System.out.println("[冷却] 更新冷却时间: " + fishingCooldown + " (有效捕鱼框: " + effectiveCount + ")");
    }

    private void performFishing() {
        System.out.println("[钓鱼] === 开始钓鱼流程 ===");

        FluidType dominantFluid = getDominantFluidType();
        System.out.println("[钓鱼] 主导流体类型: " + dominantFluid);

        double successRate = dominantFluid == FluidType.LAVA ?
                CreateFisheryCommonConfig.getLavaFishingSuccessRate() :
                CreateFisheryCommonConfig.getFishingSuccessRate();

        System.out.println("[钓鱼] 成功率: " + successRate);

        float roll = level.getRandom().nextFloat();
        System.out.println("[钓鱼] 随机数: " + roll);

        if (roll >= successRate) {
            System.out.println("[钓鱼] 失败！显示失败粒子");
            showFailureParticles();
            return;
        }

        System.out.println("[钓鱼] 成功！");
        successfulAttempts++;

        ItemStack loot;
        float harpoonRoll = level.getRandom().nextFloat();
        System.out.println("[钓鱼] 鱼叉随机数: " + harpoonRoll);

        if (harpoonRoll < 0.05f) {
            System.out.println("[钓鱼] 生成磨损的鱼叉");
            loot = new ItemStack(CreateFisheryItems.WORN_HARPOON.get());
        } else {
            System.out.println("[钓鱼] 生成钓鱼战利品...");
            loot = generateFishingLoot(dominantFluid);
            System.out.println("[钓鱼] 战利品: " + (loot.isEmpty() ? "空" : loot.getCount() + "x " + loot.getDisplayName().getString()));
        }

        if (!loot.isEmpty()) {
            totalCaught++;
            System.out.println("[钓鱼] 总捕获数: " + totalCaught);

            boolean inserted = tryInsertToContraption(loot);
            System.out.println("[钓鱼] 插入到存储: " + inserted);

            if (!inserted) {
                System.out.println("[钓鱼] 掉落到捕鱼框位置");
                spawnAtRandomFrameTrap(loot);
            }

            spawnExperience();
            playFishingEffects();
        } else {
            System.out.println("[钓鱼] 战利品为空！");
        }

        System.out.println("[钓鱼] === 钓鱼流程结束 ===");
    }

    private FluidType getDominantFluidType() {
        Map<FluidType, Long> fluidCounts = new HashMap<>();
        for (FluidType type : frameTrapPositions.values()) {
            if (type != FluidType.NONE) {
                fluidCounts.put(type, fluidCounts.getOrDefault(type, 0L) + 1);
            }
        }

        long waterCount = fluidCounts.getOrDefault(FluidType.WATER, 0L);
        long lavaCount = fluidCounts.getOrDefault(FluidType.LAVA, 0L);

        return waterCount >= lavaCount ? FluidType.WATER : FluidType.LAVA;
    }

    private ItemStack generateFishingLoot(FluidType fluidType) {
        System.out.println("[战利品] === 生成战利品 ===");

        if (virtualHook == null) {
            System.out.println("[战利品] 错误: virtualHook 为 null");
            return ItemStack.EMPTY;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            System.out.println("[战利品] 错误: 不是 ServerLevel");
            return ItemStack.EMPTY;
        }

        System.out.println("[战利品] 设置虚拟钩位置: " + worldPosition);
        virtualHook.setPos(Vec3.atCenterOf(worldPosition));
        virtualHook.openWater = isOpenWater;
        System.out.println("[战利品] 开放水域: " + isOpenWater);

        LootTable lootTable = getLootTable(serverLevel, fluidType);
        System.out.println("[战利品] 战利品表: " + (lootTable == LootTable.EMPTY ? "空表" : "有效表"));

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

        System.out.println("[战利品] 幸运值: " + luckBonus);

        try {
            LootParams params = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.TOOL, new ItemStack(Items.FISHING_ROD))
                    .withParameter(LootContextParams.THIS_ENTITY, virtualHook)
                    .withLuck(luckBonus)
                    .create(LootContextParamSets.FISHING);

            System.out.println("[战利品] LootParams 创建成功");

            List<ItemStack> items = lootTable.getRandomItems(params);
            System.out.println("[战利品] 获得物品数: " + items.size());

            if (items.isEmpty()) {
                System.out.println("[战利品] 警告: 战利品表返回空列表");
                return ItemStack.EMPTY;
            }

            ItemStack result = items.get(0);
            System.out.println("[战利品] 返回: " + result.getCount() + "x " + result.getDisplayName().getString());
            return result;

        } catch (Exception e) {
            System.out.println("[战利品] 异常: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            return ItemStack.EMPTY;
        }
    }

    private LootTable getLootTable(ServerLevel serverLevel, FluidType fluidType) {
        System.out.println("[战利品表] 流体类型: " + fluidType);

        var registries = serverLevel.getServer().reloadableRegistries();

        if (fluidType == FluidType.LAVA && CreateFisheryCommonConfig.isLavaFishingEnabled()) {
            System.out.println("[战利品表] 熔岩钓鱼已启用");

            if (CreateFisheryCommonConfig.useNDULootTables()) {
                System.out.println("[战利品表] 尝试使用 NDU 战利品表");

                try {
                    ResourceLocation nduLavaFishing = ResourceLocation.fromNamespaceAndPath(
                            "netherdepthsupgrade", "gameplay/lava_fishing");
                    ResourceLocation nduNetherFishing = ResourceLocation.fromNamespaceAndPath(
                            "netherdepthsupgrade", "gameplay/nether_fishing");

                    ResourceKey<LootTable> lavaKey = ResourceKey.create(
                            net.minecraft.core.registries.Registries.LOOT_TABLE, nduLavaFishing);
                    ResourceKey<LootTable> netherKey = ResourceKey.create(
                            net.minecraft.core.registries.Registries.LOOT_TABLE, nduNetherFishing);

                    if (serverLevel.dimension() == Level.NETHER) {
                        System.out.println("[战利品表] 在下界，尝试下界钓鱼表");
                        LootTable netherTable = registries.getLootTable(netherKey);
                        if (netherTable != LootTable.EMPTY) {
                            System.out.println("[战利品表] 使用下界钓鱼表");
                            return netherTable;
                        }
                    }

                    System.out.println("[战利品表] 尝试熔岩钓鱼表");
                    LootTable lavaTable = registries.getLootTable(lavaKey);
                    if (lavaTable != LootTable.EMPTY) {
                        System.out.println("[战利品表] 使用熔岩钓鱼表");
                        return lavaTable;
                    }

                    System.out.println("[战利品表] NDU 表未找到，回退到默认");
                } catch (Exception e) {
                    System.out.println("[战利品表] NDU 异常: " + e.getMessage());
                }
            } else {
                System.out.println("[战利品表] NDU 战利品表已禁用");
            }
        }

        System.out.println("[战利品表] 使用默认钓鱼表");
        return registries.getLootTable(BuiltInLootTables.FISHING);
    }

    private boolean tryInsertToContraption(ItemStack stack) {
        if (movedContraption == null || movedContraption.getContraption() == null)
            return false;

        ItemStack remainder = ItemHandlerHelper.insertItem(
                movedContraption.getContraption().getStorage().getAllItems(),
                stack,
                false
        );

        return remainder.isEmpty();
    }

    private void spawnAtRandomFrameTrap(ItemStack stack) {
        if (frameTrapPositions.isEmpty() || stack.isEmpty())
            return;

        List<BlockPos> validPositions = frameTrapPositions.entrySet().stream()
                .filter(e -> e.getValue() != FluidType.NONE)
                .map(Map.Entry::getKey)
                .toList();

        if (validPositions.isEmpty()) return;

        BlockPos pos = validPositions.get(level.getRandom().nextInt(validPositions.size()));

        ItemEntity entity = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        entity.setDefaultPickUpDelay();
        entity.setDeltaMovement(0, 0.2, 0);
        level.addFreshEntity(entity);
    }

    private void spawnExperience() {
        if (frameTrapPositions.isEmpty())
            return;

        List<BlockPos> validPositions = frameTrapPositions.entrySet().stream()
                .filter(e -> e.getValue() != FluidType.NONE)
                .map(Map.Entry::getKey)
                .toList();

        if (validPositions.isEmpty()) return;

        BlockPos pos = validPositions.get(level.getRandom().nextInt(validPositions.size()));

        int expAmount = isOpenWater ? 2 + level.getRandom().nextInt(3) : 1;
        level.addFreshEntity(new ExperienceOrb(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, expAmount));
    }

    private void playFishingEffects() {
        if (!(level instanceof ServerLevel serverLevel) || frameTrapPositions.isEmpty())
            return;

        List<BlockPos> validPositions = frameTrapPositions.entrySet().stream()
                .filter(e -> e.getValue() != FluidType.NONE)
                .map(Map.Entry::getKey)
                .toList();

        if (validPositions.isEmpty()) return;

        BlockPos pos = validPositions.get(level.getRandom().nextInt(validPositions.size()));

        serverLevel.sendParticles(ParticleTypes.BUBBLE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                15, 0.5, 0.5, 0.5, 0.1);

        level.playSound(null, pos, SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void showWorkingParticles() {
        if (!(level instanceof ServerLevel serverLevel) || frameTrapPositions.isEmpty())
            return;

        for (Map.Entry<BlockPos, FluidType> entry : frameTrapPositions.entrySet()) {
            if (entry.getValue() != FluidType.NONE && level.getRandom().nextFloat() < 0.05f) {
                BlockPos pos = entry.getKey();
                serverLevel.sendParticles(ParticleTypes.BUBBLE_POP,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        1, 0.2, 0.2, 0.2, 0.01);
            }
        }
    }

    private void showFailureParticles() {
        if (!(level instanceof ServerLevel serverLevel) || frameTrapPositions.isEmpty())
            return;

        List<BlockPos> validPositions = frameTrapPositions.entrySet().stream()
                .filter(e -> e.getValue() != FluidType.NONE)
                .map(Map.Entry::getKey)
                .toList();

        if (validPositions.isEmpty()) return;

        BlockPos pos = validPositions.get(level.getRandom().nextInt(validPositions.size()));

        serverLevel.sendParticles(ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                3, 0.1, 0.1, 0.1, 0.02);
    }

    public void setAssembleNextTick(boolean value) {
        assembleNextTick = value;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToTooltip(tooltip, isPlayerSneaking);

        if (running && validFrameTraps >= MIN_FRAME_TRAPS) {
            tooltip.add(Component.translatable("create_fishery.tooltip.fishing_bearing.status",
                    validFrameTraps).withStyle(ChatFormatting.AQUA));
        }

        return true;
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putInt("TotalFrameTraps", totalFrameTraps);
        compound.putInt("ValidFrameTraps", validFrameTraps);
        compound.putInt("FishingCooldown", fishingCooldown);
        compound.putBoolean("OpenWater", isOpenWater);
        compound.putInt("TotalCaught", totalCaught);
        compound.putInt("SuccessfulAttempts", successfulAttempts);

        // 序列化初始位置列表
        ListTag posList = new ListTag();
        for (BlockPos pos : initialFrameTrapPositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            posList.add(posTag);
        }
        compound.put("InitialFrameTrapPositions", posList);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);

        totalFrameTraps = compound.getInt("TotalFrameTraps");
        validFrameTraps = compound.getInt("ValidFrameTraps");
        fishingCooldown = compound.getInt("FishingCooldown");
        isOpenWater = compound.getBoolean("OpenWater");
        totalCaught = compound.getInt("TotalCaught");
        successfulAttempts = compound.getInt("SuccessfulAttempts");

        // 反序列化初始位置列表
        initialFrameTrapPositions.clear();
        ListTag posList = compound.getList("InitialFrameTrapPositions", Tag.TAG_COMPOUND);
        for (int i = 0; i < posList.size(); i++) {
            CompoundTag posTag = posList.getCompound(i);
            initialFrameTrapPositions.add(new BlockPos(
                    posTag.getInt("X"),
                    posTag.getInt("Y"),
                    posTag.getInt("Z")
            ));
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

    private enum FluidType {
        NONE,
        WATER,
        LAVA
    }
}