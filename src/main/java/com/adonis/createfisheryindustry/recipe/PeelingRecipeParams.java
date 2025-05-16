package com.adonis.createfisheryindustry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.function.Function;

public class PeelingRecipeParams extends ProcessingRecipeParams {

    private static final ResourceLocation PLACEHOLDER_ID = ResourceLocation.withDefaultNamespace("peeling_params_placeholder");

    // This constructor is primarily for the Codec's factory and network deserialization.
    // It receives the actual data fields. The ID passed to super is a placeholder.
    public PeelingRecipeParams(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            int processingDuration,
            HeatCondition requiredHeat,
            NonNullList<FluidIngredient> fluidIngredients,
            NonNullList<FluidStack> fluidResults) {
        super(PLACEHOLDER_ID); // Pass a non-null placeholder ID to super
        this.ingredients = ingredients;
        this.results = results;
        this.processingDuration = processingDuration;
        this.requiredHeat = requiredHeat;
        this.fluidIngredients = fluidIngredients;
        this.fluidResults = fluidResults;
    }

    // Static factory for the Codec
    private static PeelingRecipeParams createFromCodec(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            int processingDuration,
            HeatCondition requiredHeat,
            NonNullList<FluidIngredient> fluidIngredients,
            NonNullList<FluidStack> fluidResults) {
        return new PeelingRecipeParams(ingredients, results, processingDuration, requiredHeat, fluidIngredients, fluidResults);
    }

    public static final MapCodec<PeelingRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PeelingRecipeSerializer.INGREDIENTS_MAP_CODEC.forGetter(p -> p.ingredients),
            PeelingRecipeSerializer.RESULTS_MAP_CODEC.forGetter(p -> p.results),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("processingTime", 100).forGetter(p -> p.processingDuration),
            HeatCondition.CODEC.optionalFieldOf("heatRequirement", HeatCondition.NONE).forGetter(p -> p.requiredHeat),
            FluidIngredient.CODEC.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .optionalFieldOf("fluidIngredients", NonNullList.create()).forGetter(p -> p.fluidIngredients),
            FluidStack.CODEC.listOf().xmap(NonNullList::copyOf, Function.identity())
                    .optionalFieldOf("fluidResults", NonNullList.create()).forGetter(p -> p.fluidResults)
    ).apply(instance, PeelingRecipeParams::createFromCodec));

    public static final StreamCodec<RegistryFriendlyByteBuf, PeelingRecipeParams> STREAM_CODEC = StreamCodec.of(
            PeelingRecipeParams::encodeToBuffer, PeelingRecipeParams::decodeFromBuffer);

    private static void encodeToBuffer(RegistryFriendlyByteBuf buffer, PeelingRecipeParams params) {
        CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).encode(buffer, params.ingredients);
        CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).encode(buffer, params.results);
        ByteBufCodecs.VAR_INT.encode(buffer, params.processingDuration);
        HeatCondition.STREAM_CODEC.encode(buffer, params.requiredHeat);
        CatnipStreamCodecBuilders.nonNullList(FluidIngredient.STREAM_CODEC).encode(buffer, params.fluidIngredients);
        CatnipStreamCodecBuilders.nonNullList(FluidStack.STREAM_CODEC).encode(buffer, params.fluidResults);
    }

    private static PeelingRecipeParams decodeFromBuffer(RegistryFriendlyByteBuf buffer) {
        NonNullList<Ingredient> ingredients = CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).decode(buffer);
        NonNullList<ProcessingOutput> results = CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).decode(buffer);
        int duration = ByteBufCodecs.VAR_INT.decode(buffer);
        HeatCondition heat = HeatCondition.STREAM_CODEC.decode(buffer);
        NonNullList<FluidIngredient> fluidIngredients = CatnipStreamCodecBuilders.nonNullList(FluidIngredient.STREAM_CODEC).decode(buffer);
        NonNullList<FluidStack> fluidResults = CatnipStreamCodecBuilders.nonNullList(FluidStack.STREAM_CODEC).decode(buffer);
        return new PeelingRecipeParams(ingredients, results, duration, heat, fluidIngredients, fluidResults);
    }
}