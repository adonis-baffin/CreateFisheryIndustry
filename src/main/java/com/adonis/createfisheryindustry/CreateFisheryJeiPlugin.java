package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingCategory;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
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

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

@JeiPlugin
public class CreateFisheryJeiPlugin implements IModPlugin {
    public static final ResourceLocation ID = CreateFisheryMod.asResource("jei");
    public static final RecipeType<PeelingRecipe> PEELING_TYPE = RecipeType
            .create(CreateFisheryMod.ID, "peeling", PeelingRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new PeelingCategory(new CreateRecipeCategory.Info<>(
                PEELING_TYPE,
                Component.translatable("recipe.createfisheryindustry.peeling"),
                registration.getJeiHelpers().getGuiHelper().createBlankDrawable(177, 70),
                registration.getJeiHelpers().getGuiHelper().createDrawableItemStack(
                        CreateFisheryBlocks.MECHANICAL_PEELER.asStack()),
                (Supplier<List<RecipeHolder<PeelingRecipe>>>) () -> Objects.requireNonNull(Minecraft.getInstance().level)
                        .getRecipeManager()
                        .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType()),
                List.<Supplier<? extends ItemStack>>of(CreateFisheryBlocks.MECHANICAL_PEELER::asStack)
        )));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var recipeManager = Objects.requireNonNull(Minecraft.getInstance().level).getRecipeManager();
        registration.addRecipes(PEELING_TYPE, recipeManager
                .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType())
                .stream()
                .map(RecipeHolder::value)
                .toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(CreateFisheryBlocks.MECHANICAL_PEELER.asStack(), PEELING_TYPE);
    }
}