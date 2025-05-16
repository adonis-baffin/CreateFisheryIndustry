package com.adonis.createfisheryindustry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders; // Ensure this import is valid
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.MethodsReturnNonnullByDefault;
import java.util.function.Function;
import net.minecraft.network.codec.StreamCodec; // For public static final STREAM_CODEC

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class PeelingRecipeSerializer implements RecipeSerializer<PeelingRecipe> {

    // These are used by PeelingRecipeParams.CODEC
    public static final MapCodec<NonNullList<Ingredient>> INGREDIENTS_MAP_CODEC =
            Ingredient.CODEC_NONEMPTY.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .fieldOf("ingredients");

    public static final MapCodec<NonNullList<ProcessingOutput>> RESULTS_MAP_CODEC =
            ProcessingOutput.CODEC.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .fieldOf("results");

    private static final MapCodec<PeelingRecipe> CODEC =
            PeelingRecipeParams.CODEC.xmap(
                    PeelingRecipe::new, // Factory: PeelingRecipeParams -> PeelingRecipe
                    recipe -> recipe.peelingParams // Getter: PeelingRecipe -> PeelingRecipeParams
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, PeelingRecipe> STREAM_CODEC =
            PeelingRecipeParams.STREAM_CODEC.map(
                    PeelingRecipe::new,
                    recipe -> recipe.peelingParams
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