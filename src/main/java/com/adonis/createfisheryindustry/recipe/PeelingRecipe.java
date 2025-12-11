package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public class PeelingRecipe extends ProcessingRecipe<RecipeInput, PeelingRecipeParams> implements IAssemblyRecipe {

    public PeelingRecipe(PeelingRecipeParams params) {
        super(CreateFisheryRecipeTypes.PEELING, params);
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
        return 1 + 8; // 1 primary + up to 8 secondary
    }

    @Override
    protected boolean canSpecifyDuration() {
        return false;
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

    @Override
    public void addAssemblyIngredients(List<Ingredient> list) {
        list.addAll(getIngredients());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getDescriptionForAssembly() {
        return CreateLang.translateDirect("recipe.assembly.peeling");
    }

    @Override
    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(CreateFisheryBlocks.MECHANICAL_PEELER.get());
    }

    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> SequencedAssemblySubCategory.AssemblyCutting::new;
    }
}