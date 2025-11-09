package com.adonis.createfisheryindustry.block.TrapBearing;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
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
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.*;

public class TrapBearingBlockEntity extends MechanicalBearingBlockEntity {

    private static final int MIN_FRAME_TRAPS = 8;
    private static final int BASE_COOLDOWN = 200;
    private static final int COOLDOWN_REDUCTION = 5;
    private static final int MIN_COOLDOWN = 80;
    private static final int UPDATE_INTERVAL = 20;
    private static final int MAX_FRAME_DISTANCE = 16;
    private static final int MAX_FRAME_COUNT = 64;
    private static final int EFFECTIVE_FRAME_CAP = 64;

    // 静态数据（组装时确定）
    private int totalFrameTraps = 0;
    private List<BlockPos> initialFrameTrapPositions = new ArrayList<>();

    // 动态数据（运行时更新）
    private int waterFrameCount = 0;
    private int lavaFrameCount = 0;
    private int validFrameCount = 0;
    private FluidType dominantFluid = FluidType.NONE;
    private List<BlockPos> activeFramePositions = new ArrayList<>();

    // 钓鱼状态
    private int fishingCooldown = 0;
    private boolean isFishing = false;
    private boolean isOpenWater = false;

    // 定时器
    private int updateTimer = 0;

    // 统计数据
    private int totalCaught = 0;
    private int successfulAttempts = 0;

    // 虚拟钓鱼工具
    private FishingHook virtualHook;
    private TrapBearingFakePlayer fakePlayer;

    public TrapBearingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    @Override
    public void tick() {
        super.tick();

        if (!level.isClientSide && running) {
            // 每 20 ticks 轮询一次
            if (++updateTimer >= UPDATE_INTERVAL) {
                updateTimer = 0;
                updateFrameTrapStates();
            }

            updateFishingLogic();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
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

        Contraption contraption = movedContraption.getContraption();

        // 遍历装置上的所有方块
        for (var entry : contraption.getBlocks().entrySet()) {
            BlockPos localPos = entry.getKey();
            var blockInfo = entry.getValue();

            // 只处理捕鱼框
            if (!(blockInfo.state().getBlock() instanceof FrameTrapBlock)) {
                continue;
            }

            // 转换为世界坐标（修复核心）
            Vec3 localVec = Vec3.atCenterOf(localPos);  // 用中心点，避免边缘错误
            Vec3 globalVec = movedContraption.toGlobalVector(localVec, 0);  // partialTicks=0 for server-side
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

        // 判断工作模式（不变）
        determineWorkingMode();

        // 更新开放水域状态（不变）
        if (dominantFluid == FluidType.WATER) {
            isOpenWater = (waterFrameCount == totalFrameTraps);
        } else {
            isOpenWater = false;
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
     * 更新钓鱼逻辑
     */
    private void updateFishingLogic() {
        if (!CreateFisheryCommonConfig.isFishingEnabled()) {
            return;
        }

        // 检查有效捕鱼框数量
        if (validFrameCount < MIN_FRAME_TRAPS) {
            // 停止钓鱼
            if (isFishing) {
                isFishing = false;
                fishingCooldown = 0;
            }
            return;
        }

        // 启动钓鱼
        if (!isFishing) {
            isFishing = true;
            updateCooldown();
        }

        // 倒计时
        if (--fishingCooldown <= 0) {
            performFishing();
            updateCooldown();
        }

        // 显示工作粒子
        if (level.getRandom().nextFloat() < 0.1f) {
            showWorkingParticles();
        }
    }

    /**
     * 更新冷却时间
     */
    private void updateCooldown() {
        int effectiveCount = Math.min(validFrameCount, EFFECTIVE_FRAME_CAP);
        int extraFrames = Math.max(0, effectiveCount - MIN_FRAME_TRAPS);
        fishingCooldown = Math.max(MIN_COOLDOWN, BASE_COOLDOWN - extraFrames * COOLDOWN_REDUCTION);
    }

    /**
     * 执行钓鱼
     */
    private void performFishing() {
        // 成功率判定
        double successRate = dominantFluid == FluidType.LAVA ?
                CreateFisheryCommonConfig.getLavaFishingSuccessRate() :
                CreateFisheryCommonConfig.getFishingSuccessRate();

        if (level.getRandom().nextFloat() >= successRate) {
            // 失败，无任何效果
            return;
        }

        successfulAttempts++;

        // 战利品生成
        ItemStack loot;
        if (level.getRandom().nextFloat() < 0.05f) {
            // 5% 概率生成 worn_harpoon
            loot = new ItemStack(CreateFisheryItems.WORN_HARPOON.get());
        } else {
            // 生成钓鱼战利品
            loot = generateFishingLoot();
        }

        if (!loot.isEmpty()) {
            totalCaught++;

            // 插入战利品到装置存储
            boolean inserted = tryInsertToContraption(loot);
            if (!inserted) {
                // 插入失败，掉落到随机捕鱼框位置
                spawnAtRandomFrameTrap(loot);
            }

            // 生成经验颗粒
            spawnExperienceNugget();

            // 播放音效和粒子效果
            playFishingEffects();
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
     * 尝试插入到装置存储
     */
    private boolean tryInsertToContraption(ItemStack stack) {
        if (movedContraption == null || movedContraption.getContraption() == null) {
            return false;
        }

        ItemStack remainder = ItemHandlerHelper.insertItem(
                movedContraption.getContraption().getStorage().getAllItems(),
                stack,
                false
        );

        return remainder.isEmpty();
    }

    /**
     * 掉落物品到随机捕鱼框位置
     */
    private void spawnAtRandomFrameTrap(ItemStack stack) {
        if (activeFramePositions.isEmpty() || stack.isEmpty()) {
            return;
        }

        BlockPos pos = activeFramePositions.get(level.getRandom().nextInt(activeFramePositions.size()));

        ItemEntity entity = new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
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

            ItemStack expNugget = new ItemStack(expNuggetItem, 1);

            // 尝试插入到装置存储
            boolean inserted = tryInsertToContraption(expNugget);

            // 如果插入失败，掉落到随机捕鱼框位置
            if (!inserted) {
                spawnAtRandomFrameTrap(expNugget);
            }
        } else {
            // 如果找不到经验颗粒物品，生成经验球（回退方案）
            if (!activeFramePositions.isEmpty()) {
                BlockPos pos = activeFramePositions.get(
                        level.getRandom().nextInt(activeFramePositions.size()));

                int expAmount = isOpenWater ? 2 + level.getRandom().nextInt(3) : 1;
                level.addFreshEntity(new ExperienceOrb(level,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, expAmount));
            }
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

    /**
     * 尝试组装，返回错误信息（如果有）
     * @return 错误信息，如果成功则返回 null
     */
    public String tryAssemble() {
        if (!scanAndValidateFrameTraps()) {
            if (totalFrameTraps < MIN_FRAME_TRAPS) {
                return Component.translatable(
                        "create_fishery.gui.assembly.insufficient_frame_traps",
                        totalFrameTraps,
                        MIN_FRAME_TRAPS
                ).getString();
            }
            return Component.translatable("create_fishery.trap_bearing.assembly_failed").getString();
        }
        return null;
    }

    @Override
    public void assemble() {
        if (!scanAndValidateFrameTraps()) {
            sendData();
            return;
        }

        super.assemble();

        if (running && level instanceof ServerLevel serverLevel) {
            // 创建虚拟钓鱼工具
            fakePlayer = new TrapBearingFakePlayer(serverLevel);
            virtualHook = new FishingHook(fakePlayer, serverLevel, 0, 0);

            // 立即轮询一次
            updateFrameTrapStates();

            // 检查是否可以启动钓鱼
            if (validFrameCount >= MIN_FRAME_TRAPS) {
                isFishing = true;
                updateCooldown();
            }

            level.playSound(null, worldPosition, SoundEvents.FISHING_BOBBER_THROW,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    public void disassemble() {
        super.disassemble();

        // 清空所有数据
        initialFrameTrapPositions.clear();
        activeFramePositions.clear();
        totalFrameTraps = 0;
        waterFrameCount = 0;
        lavaFrameCount = 0;
        validFrameCount = 0;
        fishingCooldown = 0;
        isFishing = false;
        isOpenWater = false;
        dominantFluid = FluidType.NONE;

        if (virtualHook != null) {
            virtualHook.discard();
            virtualHook = null;
        }

        fakePlayer = null;
    }

    /**
     * 扫描并验证捕鱼框
     */
    private boolean scanAndValidateFrameTraps() {
        Direction facing = getBlockState().getValue(BearingBlock.FACING);
        BlockPos frontPos = worldPosition.relative(facing);

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

        int count = 0;
        initialFrameTrapPositions.clear();

        while (!queue.isEmpty() && count < MAX_FRAME_COUNT) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);

            if (state.getBlock() instanceof FrameTrapBlock) {
                count++;
                initialFrameTrapPositions.add(current);

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

        if (count < MIN_FRAME_TRAPS) {
            return false;
        }

        return true;
    }

    public void setAssembleNextTick(boolean value) {
        assembleNextTick = value;
    }

    @Override
    public boolean addToTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToTooltip(tooltip, isPlayerSneaking);

        if (running && validFrameCount >= MIN_FRAME_TRAPS) {
            tooltip.add(Component.translatable("create_fishery.tooltip.trap_bearing.status",
                    validFrameCount).withStyle(ChatFormatting.AQUA));
        }

        return true;
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);

        compound.putInt("TotalFrameTraps", totalFrameTraps);
        compound.putInt("WaterFrameCount", waterFrameCount);
        compound.putInt("LavaFrameCount", lavaFrameCount);
        compound.putInt("ValidFrameCount", validFrameCount);
        compound.putInt("FishingCooldown", fishingCooldown);
        compound.putBoolean("IsFishing", isFishing);
        compound.putBoolean("OpenWater", isOpenWater);
        compound.putInt("TotalCaught", totalCaught);
        compound.putInt("SuccessfulAttempts", successfulAttempts);
        compound.putInt("DominantFluid", dominantFluid.ordinal());

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
        waterFrameCount = compound.getInt("WaterFrameCount");
        lavaFrameCount = compound.getInt("LavaFrameCount");
        validFrameCount = compound.getInt("ValidFrameCount");
        fishingCooldown = compound.getInt("FishingCooldown");
        isFishing = compound.getBoolean("IsFishing");
        isOpenWater = compound.getBoolean("OpenWater");
        totalCaught = compound.getInt("TotalCaught");
        successfulAttempts = compound.getInt("SuccessfulAttempts");
        dominantFluid = FluidType.values()[compound.getInt("DominantFluid")];

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