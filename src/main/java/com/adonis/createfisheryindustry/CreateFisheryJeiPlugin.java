package com.adonis.createfisheryindustry;

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
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;
import java.util.stream.Collectors;

@JeiPlugin
public class CreateFisheryJeiPlugin implements IModPlugin {
    public static final ResourceLocation ID = CreateFisheryMod.asResource("jei");

    // 简化的RecipeType定义
    public static final RecipeType<PeelingRecipe> PEELING_TYPE_JEI =
            RecipeType.create(CreateFisheryMod.ID, "peeling", PeelingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // 注册简化的PeelingCategory
        registration.addRecipeCategories(new SimplePeelingCategory(registration));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;

        var recipeManager = Minecraft.getInstance().level.getRecipeManager();
        List<PeelingRecipe> peelingRecipes = recipeManager
                .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType())
                .stream()
                .map(RecipeHolder::value)
                .collect(Collectors.toList());

        registration.addRecipes(PEELING_TYPE_JEI, peelingRecipes);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(CreateFisheryBlocks.MECHANICAL_PEELER.asStack(), PEELING_TYPE_JEI);
    }
}