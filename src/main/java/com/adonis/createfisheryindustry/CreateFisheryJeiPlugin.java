package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.recipe.BaitTrapCategory;
import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;
import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public class CreateFisheryJeiPlugin implements IModPlugin {

    public static final ResourceLocation ID = CreateFisheryMod.asResource("jei");

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();

    private void loadCategories() {
        allCategories.clear();

        // Peeling category
        allCategories.add(
                new CreateRecipeCategory.Builder<>(PeelingRecipe.class)
                        .addTypedRecipes(CreateFisheryRecipeTypes.PEELING::getType)
                        .catalyst(CreateFisheryBlocks.MECHANICAL_PEELER::get)
                        .itemIcon(CreateFisheryBlocks.MECHANICAL_PEELER.get())
                        .emptyBackground(177, 90)
                        .build(CreateFisheryMod.asResource("peeling"), com.adonis.createfisheryindustry.compat.jei.SimplePeelingCategory::new)
        );

        // Bait Trap category
        allCategories.add(
                new CreateRecipeCategory.Builder<>(BaitTrapRecipe.class)
                        .addTypedRecipes(CreateFisheryRecipeTypes.BAIT_TRAP::getType)
                        .catalyst(CreateFisheryBlocks.BAIT_TRAP::get)
                        .itemIcon(CreateFisheryBlocks.BAIT_TRAP.get())
                        .emptyBackground(177, 70)
                        .build(CreateFisheryMod.asResource("bait_trap"), BaitTrapCategory::new)
        );
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        loadCategories();
        allCategories.forEach(registration::addRecipeCategories);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        allCategories.forEach(category -> category.registerRecipes(registration));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(category -> category.registerCatalysts(registration));
    }
}