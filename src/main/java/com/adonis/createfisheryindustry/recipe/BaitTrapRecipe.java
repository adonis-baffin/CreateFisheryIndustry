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

    public BaitTrapRecipe(BaitTrapRecipeParams params) {
        super(CreateFisheryRecipeTypes.BAIT_TRAP, params);
    }

    /**
     * Get the bait level (1-5).
     * Level 5 = slowest (60-300 seconds)
     * Each level down halves the time.
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
     * Calculate the minimum time in ticks to produce loot based on bait level.
     * Level 5: 1200 ticks (60 seconds)
     * Each level down halves the time.
     */
    public int getMinLootTimeTicks() {
        int baseMinTicks = 1200; // 60 seconds at level 5
        double multiplier = Math.pow(0.5, 5 - getBaitLevel());
        return Math.max(20, (int) (baseMinTicks * multiplier));
    }

    /**
     * Calculate the maximum time in ticks to produce loot based on bait level.
     * Level 5: 6000 ticks (300 seconds)
     * Each level down halves the time.
     */
    public int getMaxLootTimeTicks() {
        int baseMaxTicks = 6000; // 300 seconds at level 5
        double multiplier = Math.pow(0.5, 5 - getBaitLevel());
        int minTicks = getMinLootTimeTicks();
        return Math.max(minTicks + 20, (int) (baseMaxTicks * multiplier));
    }

    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(CreateFisheryBlocks.BAIT_TRAP.get());
    }
}