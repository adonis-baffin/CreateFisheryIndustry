package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@ParametersAreNonnullByDefault
public class PeelingRecipe extends ProcessingRecipe<SingleRecipeInput> implements IAssemblyRecipe {
    public PeelingRecipe(ProcessingRecipeBuilder.ProcessingRecipeParams params) {
        super(CreateFisheryRecipeTypes.PEELING, params);
    }

    public static Builder builder(ResourceLocation id) {
        return new Builder(id);
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 4;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredients.getFirst().test(input.item());
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

    public static class Builder extends ProcessingRecipeBuilder<PeelingRecipe> {
        protected Builder(ResourceLocation id) {
            super(PeelingRecipe::new, id);
        }
    }
}