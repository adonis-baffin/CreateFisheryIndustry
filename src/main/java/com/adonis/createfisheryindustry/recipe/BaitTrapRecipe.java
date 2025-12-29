package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Set;

@ParametersAreNonnullByDefault
public class BaitTrapRecipe extends ProcessingRecipe<RecipeInput, BaitTrapRecipeParams> {
    private static final int[] MAX_EXTRA_DELAY_TICKS = {
            21600,  // Level 1: 0-18 min
            16800,  // Level 2: 0-14 min
            12000,  // Level 3: 0-10 min
            7200,   // Level 4: 0-6 min
            2400    // Level 5: 0-2 min
    };

    private static final int FIXED_WAIT_TICKS = 2400;

    /**
     * 获取固定等待时间（所有等级均为2分钟）
     */
    public int getFixedWaitTicks() {
        return FIXED_WAIT_TICKS;
    }

    /**
     * 获取该等级的最大额外随机延迟（包含）
     */
    public int getMaxExtraDelayTicks() {
        int level = getBaitLevel();
        int index = Math.max(0, Math.min(4, level - 1));
        return MAX_EXTRA_DELAY_TICKS[index];
    }

    /**
     * 获取该等级的最长可能间隔时间（固定 + 最大额外）
     * 用于兼容旧代码或未来扩展
     */
    public int getMaxTotalTicks() {
        return getFixedWaitTicks() + getMaxExtraDelayTicks();
    }

    /**
     * 获取该等级的最短可能间隔时间（仅固定部分）
     */
    public int getMinTotalTicks() {
        return getFixedWaitTicks();
    }

    /**
     * 用于JEI显示：返回分钟范围，如 "2-4 min"
     */
    public String getLootTimeMinutesRange() {
        int minMin = getFixedWaitTicks() / 20 / 60;
        int maxMin = getMaxTotalTicks() / 20 / 60;
        return minMin + "-" + maxMin + " min";
    }

    public BaitTrapRecipe(BaitTrapRecipeParams params) {
        super(CreateFisheryRecipeTypes.BAIT_TRAP, params);
    }

    /**
     * Get the bait level (1-5).
     * Higher level = faster loot generation.
     * Level 5: 4 min, Level 4: 8 min, Level 3: 12 min, Level 2: 16 min, Level 1: 20 min
     */
    public int getBaitLevel() {
        return getParams().getBaitLevel();
    }

    public ItemStack getPrimaryOutput() {
        if (getRollableResults().isEmpty()) {
            return ItemStack.EMPTY;
        }
        return getRollableResults().get(0).getStack();
    }

    public List<ProcessingOutput> getSecondaryOutputs() {
        if (getRollableResults().size() <= 1) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(getRollableResults().subList(1, getRollableResults().size()));
    }

    public List<ItemStack> rollResultsFor(List<ProcessingOutput> specificOutputs, RandomSource randomSource) {
        return super.rollResults(specificOutputs, randomSource);
    }

    @Override
    public List<ItemStack> rollResults(RandomSource randomSource) {
        return super.rollResults(randomSource);
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 4; // Up to 4 possible outputs
    }

    public boolean requiresWater() {
        return getParams().requiresWater();
    }

    @Override
    protected boolean canSpecifyDuration() {
        return false; // Duration is calculated from bait level
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        if (getIngredients().isEmpty()) return false;

        if (input instanceof SingleRecipeInput singleInput) {
            return getIngredients().get(0).test(singleInput.item());
        }

        if (input.size() > 0) {
            return getIngredients().get(0).test(input.getItem(0));
        }

        return false;
    }

    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(CreateFisheryBlocks.BAIT_TRAP.get());
    }
}