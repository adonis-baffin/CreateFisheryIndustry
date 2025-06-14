package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FrameTrapContext {
    protected final ItemStack fishingRod;
    protected final FishingHook fishingHook;
    protected final Set<BlockPos> visitedBlocks = new HashSet<>();

    // 基础时间范围（将通过配置倍数调整）
    protected final int baseMinCatchTime = 800;
    protected final int baseMaxCatchTime = 1400;

    public int timeUntilCatch;

    public FrameTrapContext(ServerLevel level, ItemStack fishingRod) {
        this.fishingRod = fishingRod;
        this.fishingHook = new FishingHook(EntityType.FISHING_BOBBER, level);
        this.reset(level);
    }

    public void reset(ServerLevel level) {
        this.visitedBlocks.clear();

        // 使用普通钓鱼的配置作为默认
        double cooldownMultiplier = CreateFisheryCommonConfig.getFishingCooldownMultiplier();
        int adjustedMinTime = (int) (baseMinCatchTime * cooldownMultiplier);
        int adjustedMaxTime = (int) (baseMaxCatchTime * cooldownMultiplier);

        this.timeUntilCatch = Mth.nextInt(fishingHook.getRandom(), adjustedMinTime, adjustedMaxTime);
    }

    /**
     * 根据环境重置（带环境感知）
     */
    public void reset(ServerLevel level, BlockPos currentPos) {
        this.visitedBlocks.clear();

        // 根据当前位置的环境类型选择配置
        boolean isLavaEnvironment = isValidLavaFishingPosition(level, currentPos);
        double cooldownMultiplier = isLavaEnvironment ?
                CreateFisheryCommonConfig.getLavaFishingCooldownMultiplier() :
                CreateFisheryCommonConfig.getFishingCooldownMultiplier();

        int adjustedMinTime = (int) (baseMinCatchTime * cooldownMultiplier);
        int adjustedMaxTime = (int) (baseMaxCatchTime * cooldownMultiplier);

        this.timeUntilCatch = Mth.nextInt(fishingHook.getRandom(), adjustedMinTime, adjustedMaxTime);
    }

    public boolean visitNewPosition(ServerLevel level, BlockPos pos) {
        // 检查是否在水中或熔岩中
        boolean inWater = fishingHook.getOpenWaterTypeForBlock(pos) == FishingHook.OpenWaterType.INSIDE_WATER;
        boolean inLava = isValidLavaFishingPosition(level, pos);

        if (!inWater && !inLava) return false;

        visitedBlocks.add(pos);
        return true;
    }

    /**
     * 检查位置是否适合熔岩钓鱼
     */
    protected boolean isValidLavaFishingPosition(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA) &&
                level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * 获取适当的战利品表
     */
    public LootTable getLootTable(ServerLevel level, BlockPos pos) {
        var registries = level.getServer().reloadableRegistries();

        // 如果在熔岩中且启用了熔岩钓鱼，尝试获取熔岩钓鱼战利品表
        if (isValidLavaFishingPosition(level, pos) && CreateFisheryCommonConfig.isLavaFishingEnabled()) {
            return getLavaFishingLootTable(level, registries);
        }

        // 默认使用普通钓鱼战利品表
        return registries.getLootTable(BuiltInLootTables.FISHING);
    }

    /**
     * 尝试获取NDU熔岩钓鱼战利品表（软依赖方式）
     */
    private LootTable getLavaFishingLootTable(ServerLevel level, net.minecraft.server.ReloadableServerRegistries.Holder registries) {
        // 如果配置禁用NDU战利品表，直接返回默认
        if (!CreateFisheryCommonConfig.useNDULootTables()) {
            return registries.getLootTable(BuiltInLootTables.FISHING);
        }

        try {
            // 正确的NDU战利品表路径（根据源代码确认）
            net.minecraft.resources.ResourceLocation nduLavaFishing =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("netherdepthsupgrade", "gameplay/lava_fishing");
            net.minecraft.resources.ResourceLocation nduNetherFishing =
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("netherdepthsupgrade", "gameplay/nether_fishing");

            net.minecraft.resources.ResourceKey<LootTable> lavaKey =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, nduLavaFishing);
            net.minecraft.resources.ResourceKey<LootTable> netherKey =
                    net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, nduNetherFishing);

            // 如果在下界，优先使用下界钓鱼战利品表
            if (level.dimension() == net.minecraft.world.level.Level.NETHER) {
                LootTable netherTable = registries.getLootTable(netherKey);
                if (netherTable != LootTable.EMPTY) {
                    return netherTable;
                }
            }

            // 否则使用普通熔岩钓鱼战利品表
            LootTable lavaTable = registries.getLootTable(lavaKey);
            if (lavaTable != LootTable.EMPTY) {
                return lavaTable;
            }
        } catch (Exception e) {
            // 如果出现任何错误，静默回退到默认战利品表
        }

        // 回退到默认钓鱼战利品表
        return registries.getLootTable(BuiltInLootTables.FISHING);
    }

    public LootParams buildLootContext(com.simibubi.create.content.contraptions.behaviour.MovementContext context, ServerLevel level, BlockPos pos) {
        fishingHook.setPos(context.position);

        // 根据环境设置开放水域状态
        if (isValidLavaFishingPosition(level, pos)) {
            // 熔岩环境：使用简单的开放区域检查
            fishingHook.openWater = calculateOpenLava(level, pos);
        } else {
            // 水环境：使用原有逻辑
            fishingHook.openWater = fishingHook.getOpenWaterTypeForArea(
                    pos.offset(-2, 0, -2), pos.offset(2, 0, 2)) == FishingHook.OpenWaterType.INSIDE_WATER;
        }

        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, context.position)
                .withParameter(LootContextParams.TOOL, fishingRod)
                .withParameter(LootContextParams.THIS_ENTITY, fishingHook)
                .withLuck(EnchantmentHelper.getFishingLuckBonus(level, fishingRod, context.contraption.entity))
                .create(LootContextParamSets.FISHING);
    }

    /**
     * 计算开放熔岩区域
     */
    private boolean calculateOpenLava(ServerLevel level, BlockPos pos) {
        // 检查5x4x5区域是否为开放熔岩
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos checkPos = pos.offset(x, y, z);

                    if (y == -1) {
                        // 底层必须是熔岩
                        if (!level.getFluidState(checkPos).is(FluidTags.LAVA)) {
                            return false;
                        }
                    } else {
                        // 其他层必须是熔岩或空气
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

    public boolean canCatch() {
        // 使用配置的成功率（普通钓鱼）
        double successRate = CreateFisheryCommonConfig.getFishingSuccessRate();
        return fishingHook.getRandom().nextFloat() < successRate;
    }

    /**
     * 根据环境检查是否可以捕获
     */
    public boolean canCatch(ServerLevel level, BlockPos currentPos) {
        // 根据当前位置的环境类型选择成功率配置
        boolean isLavaEnvironment = isValidLavaFishingPosition(level, currentPos);
        double successRate = isLavaEnvironment ?
                CreateFisheryCommonConfig.getLavaFishingSuccessRate() :
                CreateFisheryCommonConfig.getFishingSuccessRate();

        return fishingHook.getRandom().nextFloat() < successRate;
    }

    public void invalidate(ServerLevel level) {
        reset(level);
        fishingHook.discard();
    }

    public FishingHook getFishingHook() {
        return fishingHook;
    }

    public ItemStack getFishingRod() {
        return fishingRod;
    }
}