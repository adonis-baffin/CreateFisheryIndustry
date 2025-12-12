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
    private static final int[] LOOT_TIME_TICKS = {
            24000,  // Level 1: 20 minutes
            19200,  // Level 2: 16 minutes
            14400,  // Level 3: 12 minutes
            9600,   // Level 4: 8 minutes
            4800    // Level 5: 4 minutes
    };

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

    /**
     * Get the fixed loot generation time in ticks based on bait level.
     * Level 5: 4 min (4800 ticks)
     * Level 4: 8 min (9600 ticks)
     * Level 3: 12 min (14400 ticks)
     * Level 2: 16 min (19200 ticks)
     * Level 1: 20 min (24000 ticks)
     */
    public int getLootTimeTicks() {
        int level = getBaitLevel();
        // Clamp level to valid range (1-5) and convert to array index (0-4)
        int index = Math.max(0, Math.min(4, level - 1));
        return LOOT_TIME_TICKS[index];
    }

    /**
     * Get the loot generation time in minutes for display purposes.
     */
    public int getLootTimeMinutes() {
        return getLootTimeTicks() / 20 / 60;
    }

    /**
     * @deprecated Use {@link #getLootTimeTicks()} instead. Kept for compatibility.
     */
    @Deprecated
    public int getMinLootTimeTicks() {
        return getLootTimeTicks();
    }

    /**
     * @deprecated Use {@link #getLootTimeTicks()} instead. Kept for compatibility.
     */
    @Deprecated
    public int getMaxLootTimeTicks() {
        return getLootTimeTicks();
    }

    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(CreateFisheryBlocks.BAIT_TRAP.get());
    }
}