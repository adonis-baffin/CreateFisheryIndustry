package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingCategory;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory; // Keep this
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
// import net.minecraft.world.item.crafting.RecipeManager; // Not needed if using getAllRecipesFor

import java.util.Collections; // For Collections.emptyList() if recipe fetch fails
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@JeiPlugin
public class CreateFisheryJeiPlugin implements IModPlugin {
    public static final ResourceLocation ID = CreateFisheryMod.asResource("jei");

    // Keep your JEI RecipeType distinct from your game RecipeType if they serve different roles or classes
    // If PeelingRecipe is the class for both, this is fine.
    public static final RecipeType<PeelingRecipe> PEELING_TYPE_JEI = RecipeType
            .create(CreateFisheryMod.ID, "peeling", PeelingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // Adjust background size for the new layout.
        // Width: Input(20+18) + Arrow(42) + Peeler(approx 30) + Arrow(42) + Output(18) + Padding = ~170-180
        // Height: SecondaryOutputs(10+18+space+18) + Peeler(30) + Input/Output(50+18) = ~70-90 (depending on secondary outputs)
        // Let's try 177 width, 90 height. You'll need to fine-tune this.
        int backgroundWidth = 177;
        int backgroundHeight = 90; // Increased height for secondary outputs

        registration.addRecipeCategories(new PeelingCategory(new CreateRecipeCategory.Info<>(
                PEELING_TYPE_JEI, // Use the JEI specific RecipeType here
                Component.translatable("recipe.createfisheryindustry.peeling"),
                registration.getJeiHelpers().getGuiHelper().createBlankDrawable(backgroundWidth, backgroundHeight), // Adjusted size
                registration.getJeiHelpers().getGuiHelper().createDrawableItemStack( // Icon for the category
                        CreateFisheryBlocks.MECHANICAL_PEELER.asStack()),
                // Supplier for recipes. Ensure this path is correct.
                () -> {
                    try {
                        return Minecraft.getInstance().level.getRecipeManager()
                                .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType()) // Game's RecipeType
                                .stream().collect(Collectors.toList()); // Ensure it's a List<RecipeHolder<PeelingRecipe>>>
                    } catch (NullPointerException e) { // Safety for server-side calls or early init
                        return Collections.emptyList();
                    }
                },
                List.of(CreateFisheryBlocks.MECHANICAL_PEELER::asStack) // List of catalysts
        )));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return; // Guard against null level

        var recipeManager = Minecraft.getInstance().level.getRecipeManager();
        List<PeelingRecipe> peelingRecipes = recipeManager
                .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType()) // Game's RecipeType
                .stream()
                .map(RecipeHolder::value)
                .collect(Collectors.toList());

        registration.addRecipes(PEELING_TYPE_JEI, peelingRecipes); // Use JEI's RecipeType
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(CreateFisheryBlocks.MECHANICAL_PEELER.asStack(), PEELING_TYPE_JEI); // Use JEI's RecipeType
    }
}