package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.network.chat.Component;
// import net.minecraft.resources.ResourceLocation; // Removed builder
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

    protected final PeelingRecipeParams peelingParams; // Store our specific params

    public PeelingRecipe(PeelingRecipeParams params) {
        // Pass the IRecipeTypeInfo and the params (which extends ProcessingRecipeParams) to super.
        // The `params.id` field (inherited from ProcessingRecipeParams) should be the PLACEHOLDER_ID here.
        // ProcessingRecipe's constructor will use this for its `this.id` field, and then `validate` will use it.
        // The true Recipe ID (from file path) is managed by RecipeHolder.
        super(CreateFisheryRecipeTypes.PEELING, params);
        this.peelingParams = params;
    }

    // Removed: public static PeelingRecipeBuilder.Builder builder(ResourceLocation id)

    // ... (rest of the methods: getMaxInputCount, matches, etc. remain the same) ...
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