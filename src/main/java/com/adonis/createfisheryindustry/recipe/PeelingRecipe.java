package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.google.common.collect.ImmutableList;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
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
public class PeelingRecipe extends ProcessingRecipe<SingleRecipeInput> implements IAssemblyRecipe {

    protected final PeelingRecipeParams params; // Store the specific params instance

    public PeelingRecipe(PeelingRecipeParams params) {
        super(CreateFisheryRecipeTypes.PEELING, params);
        this.params = params; // Assign to the field
    }

    // Getter for the serializer to access the specific params instance
    public PeelingRecipeParams getParams() {
        return this.params;
    }

    public ItemStack getPrimaryOutput() {
        if (getRollableResults().isEmpty()) { // getRollableResults() is from ProcessingRecipe
            return ItemStack.EMPTY;
        }
        return getRollableResults().getFirst().getStack();
    }

    public List<ProcessingOutput> getSecondaryOutputs() {
        if (getRollableResults().size() <= 1) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(getRollableResults().subList(1, getRollableResults().size()));
    }

    // This method is to roll results for a specific list of outputs,
    // e.g., only secondary outputs. It calls the protected method from ProcessingRecipe.
    public List<ItemStack> rollResultsFor(List<ProcessingOutput> specificOutputs) {
        return super.rollResults(specificOutputs); // Call the version from ProcessingRecipe that takes List<ProcessingOutput>
    }

    // Default rollResults uses the main results list of the recipe
    @Override
    public List<ItemStack> rollResults() {
        return super.rollResults(); // This uses this.getRollableResults() (which is this.results from ProcessingRecipe)
    }


    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        // This refers to the size of the 'results' list in ProcessingRecipeParams
        return 1 + 8; // Example: 1 primary + up to 8 secondary
    }

    @Override
    protected boolean canSpecifyDuration() {
        return false;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        if (getIngredients().isEmpty()) return false;
        return getIngredients().getFirst().test(input.item());
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