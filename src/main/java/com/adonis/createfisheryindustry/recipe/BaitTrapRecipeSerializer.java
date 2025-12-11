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
public class BaitTrapRecipeSerializer implements RecipeSerializer<BaitTrapRecipe> {

    public static final MapCodec<NonNullList<Ingredient>> INGREDIENTS_MAP_CODEC =
            Ingredient.CODEC_NONEMPTY.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .fieldOf("ingredients");

    public static final MapCodec<NonNullList<ProcessingOutput>> RESULTS_MAP_CODEC =
            ProcessingOutput.CODEC.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .fieldOf("results");

    private static final MapCodec<BaitTrapRecipe> CODEC =
            BaitTrapRecipeParams.CODEC.xmap(
                    BaitTrapRecipe::new,
                    BaitTrapRecipe::getParams
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, BaitTrapRecipe> STREAM_CODEC =
            BaitTrapRecipeParams.STREAM_CODEC.map(
                    BaitTrapRecipe::new,
                    BaitTrapRecipe::getParams
            );

    public BaitTrapRecipeSerializer() {}

    @Override
    public MapCodec<BaitTrapRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BaitTrapRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}