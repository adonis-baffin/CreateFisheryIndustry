package com.adonis.createfisheryindustry.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import java.util.List;
import java.util.function.Function;

public class PeelingRecipeParams extends ProcessingRecipeParams {

    public PeelingRecipeParams() {
        super(); // 调用无参构造器
        // 在这里设置默认值
        this.ingredients = NonNullList.create();
        this.results = NonNullList.create();
        this.processingDuration = 0;
        this.requiredHeat = HeatCondition.NONE;
        this.fluidIngredients = NonNullList.create();
        this.fluidResults = NonNullList.create();
    }

    // 用于设置参数的便捷构造器
    public PeelingRecipeParams(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            HeatCondition requiredHeat,
            NonNullList<SizedFluidIngredient> fluidIngredients,
            NonNullList<FluidStack> fluidResults) {
        super();
        this.ingredients = ingredients;
        this.results = results;
        this.processingDuration = 0;
        this.requiredHeat = requiredHeat;
        this.fluidIngredients = fluidIngredients;
        this.fluidResults = fluidResults;
    }

    private static PeelingRecipeParams createFromCodec(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            HeatCondition requiredHeat,
            NonNullList<SizedFluidIngredient> fluidIngredients,
            NonNullList<FluidStack> fluidResults) {
        return new PeelingRecipeParams(ingredients, results, requiredHeat, fluidIngredients, fluidResults);
    }

    public static final MapCodec<PeelingRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PeelingRecipeSerializer.INGREDIENTS_MAP_CODEC.forGetter(p -> p.ingredients),
            PeelingRecipeSerializer.RESULTS_MAP_CODEC.forGetter(p -> p.results),
            HeatCondition.CODEC.optionalFieldOf("heatRequirement", HeatCondition.NONE).forGetter(p -> p.requiredHeat),
            SizedFluidIngredient.FLAT_CODEC.listOf().xmap(list -> NonNullList.copyOf(list), Function.identity())
                    .optionalFieldOf("fluidIngredients", NonNullList.create()).forGetter(p -> p.fluidIngredients),
            FluidStack.CODEC.listOf().xmap(list -> NonNullList.copyOf(list), Function.identity())
                    .optionalFieldOf("fluidResults", NonNullList.create()).forGetter(p -> p.fluidResults)
    ).apply(instance, PeelingRecipeParams::createFromCodec));

    public static final StreamCodec<RegistryFriendlyByteBuf, PeelingRecipeParams> STREAM_CODEC = StreamCodec.of(
            PeelingRecipeParams::encodeToBuffer, PeelingRecipeParams::decodeFromBuffer);

    private static void encodeToBuffer(RegistryFriendlyByteBuf buffer, PeelingRecipeParams params) {
        CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).encode(buffer, params.ingredients);
        CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).encode(buffer, params.results);
        // 不编码 processingDuration
        HeatCondition.STREAM_CODEC.encode(buffer, params.requiredHeat);
        CatnipStreamCodecBuilders.nonNullList(SizedFluidIngredient.STREAM_CODEC).encode(buffer, params.fluidIngredients);
        CatnipStreamCodecBuilders.nonNullList(FluidStack.STREAM_CODEC).encode(buffer, params.fluidResults);
    }

    private static PeelingRecipeParams decodeFromBuffer(RegistryFriendlyByteBuf buffer) {
        NonNullList<Ingredient> ingredients = CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).decode(buffer);
        NonNullList<ProcessingOutput> results = CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).decode(buffer);
        // 不解码 processingDuration
        HeatCondition heat = HeatCondition.STREAM_CODEC.decode(buffer);
        NonNullList<SizedFluidIngredient> fluidIngredients = CatnipStreamCodecBuilders.nonNullList(SizedFluidIngredient.STREAM_CODEC).decode(buffer);
        NonNullList<FluidStack> fluidResults = CatnipStreamCodecBuilders.nonNullList(FluidStack.STREAM_CODEC).decode(buffer);
        return new PeelingRecipeParams(ingredients, results, heat, fluidIngredients, fluidResults);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NonNullList<Ingredient> ingredients = NonNullList.create();
        private NonNullList<ProcessingOutput> results = NonNullList.create();
        private HeatCondition requiredHeat = HeatCondition.NONE;
        private NonNullList<SizedFluidIngredient> fluidIngredients = NonNullList.create();
        private NonNullList<FluidStack> fluidResults = NonNullList.create();

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

        public Builder output(ProcessingOutput output) {
            results.add(output);
            return this;
        }

        public Builder withHeat(HeatCondition heat) {
            this.requiredHeat = heat;
            return this;
        }

        public PeelingRecipeParams build() {
            return new PeelingRecipeParams(ingredients, results, requiredHeat, fluidIngredients, fluidResults);
        }
    }
}