package com.adonis.createfisheryindustry.recipe;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import java.util.function.Function;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class PeelingRecipeSerializer implements RecipeSerializer<PeelingRecipe> {

    public static final MapCodec<NonNullList<Ingredient>> INGREDIENTS_MAP_CODEC =
            Ingredient.CODEC_NONEMPTY.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .fieldOf("ingredients");

    public static final MapCodec<NonNullList<ProcessingOutput>> RESULTS_MAP_CODEC =
            ProcessingOutput.CODEC.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .fieldOf("results");

    // The CODEC should map from PeelingRecipeParams to PeelingRecipe and back.
    // PeelingRecipe::new is the factory.
    // The second argument to xmap should be a Function<PeelingRecipe, PeelingRecipeParams>
    private static final MapCodec<PeelingRecipe> CODEC =
            PeelingRecipeParams.CODEC.xmap(
                    PeelingRecipe::new,
                    PeelingRecipe::getParams // Use the getter method
            );

    // Similarly for the STREAM_CODEC
    public static final StreamCodec<RegistryFriendlyByteBuf, PeelingRecipe> STREAM_CODEC =
            PeelingRecipeParams.STREAM_CODEC.map(
                    PeelingRecipe::new,
                    PeelingRecipe::getParams // Use the getter method
            );

    public PeelingRecipeSerializer() {}

    @Override
    public MapCodec<PeelingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, PeelingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}