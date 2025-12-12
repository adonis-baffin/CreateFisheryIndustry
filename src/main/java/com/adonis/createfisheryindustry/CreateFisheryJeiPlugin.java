package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.recipe.BaitTrapCategory;
import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.recipe.SimplePeelingCategory;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.stream.Collectors;

@JeiPlugin
public class CreateFisheryJeiPlugin implements IModPlugin {
    public static final ResourceLocation ID = CreateFisheryMod.asResource("jei");

    public static final RecipeType<PeelingRecipe> PEELING_TYPE_JEI =
            RecipeType.create(CreateFisheryMod.ID, "peeling", PeelingRecipe.class);

    public static final RecipeType<BaitTrapRecipe> BAIT_TRAP_TYPE_JEI =
            RecipeType.create(CreateFisheryMod.ID, "bait_trap", BaitTrapRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new SimplePeelingCategory(registration));
        registration.addRecipeCategories(new BaitTrapCategory(registration));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;

        var recipeManager = Minecraft.getInstance().level.getRecipeManager();

        // Peeling recipes
        List<PeelingRecipe> peelingRecipes = recipeManager
                .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType())
                .stream()
                .map(holder -> (PeelingRecipe) holder.value())
                .collect(Collectors.toList());
        registration.addRecipes(PEELING_TYPE_JEI, peelingRecipes);

        // Bait Trap recipes
        List<BaitTrapRecipe> baitTrapRecipes = recipeManager
                .getAllRecipesFor(CreateFisheryRecipeTypes.BAIT_TRAP.getType())
                .stream()
                .map(holder -> (BaitTrapRecipe) holder.value())
                .collect(Collectors.toList());
        registration.addRecipes(BAIT_TRAP_TYPE_JEI, baitTrapRecipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(CreateFisheryBlocks.MECHANICAL_PEELER.asStack(), PEELING_TYPE_JEI);
        registration.addRecipeCatalyst(CreateFisheryBlocks.BAIT_TRAP.asStack(), BAIT_TRAP_TYPE_JEI);
    }
}