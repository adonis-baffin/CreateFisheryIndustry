package com.adonis.createfisheryindustry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.function.Function;

public class BaitTrapRecipeParams extends ProcessingRecipeParams {

    private final int baitLevel;

    public BaitTrapRecipeParams() {
        super();
        this.ingredients = NonNullList.create();
        this.results = NonNullList.create();
        this.processingDuration = 0;
        this.requiredHeat = HeatCondition.NONE;
        this.fluidIngredients = NonNullList.create();
        this.fluidResults = NonNullList.create();
        this.baitLevel = 1;
    }

    public BaitTrapRecipeParams(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            int baitLevel) {
        super();
        this.ingredients = ingredients;
        this.results = results;
        this.processingDuration = 0;
        this.requiredHeat = HeatCondition.NONE;
        this.fluidIngredients = NonNullList.create();
        this.fluidResults = NonNullList.create();
        this.baitLevel = Math.max(1, Math.min(5, baitLevel)); // Clamp to 1-5
    }

    public int getBaitLevel() {
        return baitLevel;
    }

    private static BaitTrapRecipeParams createFromCodec(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            int baitLevel) {
        return new BaitTrapRecipeParams(ingredients, results, baitLevel);
    }

    public static final MapCodec<BaitTrapRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BaitTrapRecipeSerializer.INGREDIENTS_MAP_CODEC.forGetter(p -> p.ingredients),
            BaitTrapRecipeSerializer.RESULTS_MAP_CODEC.forGetter(p -> p.results),
            Codec.INT.optionalFieldOf("baitLevel", 1).forGetter(p -> p.baitLevel)
    ).apply(instance, BaitTrapRecipeParams::createFromCodec));

    public static final StreamCodec<RegistryFriendlyByteBuf, BaitTrapRecipeParams> STREAM_CODEC = StreamCodec.of(
            BaitTrapRecipeParams::encodeToBuffer, BaitTrapRecipeParams::decodeFromBuffer);

    private static void encodeToBuffer(RegistryFriendlyByteBuf buffer, BaitTrapRecipeParams params) {
        CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).encode(buffer, params.ingredients);
        CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).encode(buffer, params.results);
        buffer.writeInt(params.baitLevel);
    }

    private static BaitTrapRecipeParams decodeFromBuffer(RegistryFriendlyByteBuf buffer) {
        NonNullList<Ingredient> ingredients = CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).decode(buffer);
        NonNullList<ProcessingOutput> results = CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).decode(buffer);
        int baitLevel = buffer.readInt();
        return new BaitTrapRecipeParams(ingredients, results, baitLevel);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NonNullList<Ingredient> ingredients = NonNullList.create();
        private NonNullList<ProcessingOutput> results = NonNullList.create();
        private int baitLevel = 1;

        public Builder require(ItemLike item) {
            return require(Ingredient.of(item));
        }

        public Builder require(Ingredient ingredient) {
            ingredients.add(ingredient);
            return this;
        }

        public Builder output(ItemLike item) {
            return output(item, 1);
        }

        public Builder output(ItemLike item, int count) {
            results.add(new ProcessingOutput(new ItemStack(item, count), 1.0f));
            return this;
        }

        public Builder output(ItemLike item, int count, float chance) {
            results.add(new ProcessingOutput(new ItemStack(item, count), chance));
            return this;
        }

        public Builder output(ProcessingOutput output) {
            results.add(output);
            return this;
        }

        public Builder baitLevel(int level) {
            this.baitLevel = Math.max(1, Math.min(5, level));
            return this;
        }

        public BaitTrapRecipeParams build() {
            return new BaitTrapRecipeParams(ingredients, results, baitLevel);
        }
    }
}