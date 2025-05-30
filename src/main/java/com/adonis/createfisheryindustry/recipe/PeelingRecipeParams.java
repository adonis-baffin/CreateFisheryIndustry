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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.function.Function;

public class PeelingRecipeParams extends ProcessingRecipeParams {

    private static final ResourceLocation PLACEHOLDER_ID = ResourceLocation.withDefaultNamespace("peeling_params_placeholder");

    public PeelingRecipeParams(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            HeatCondition requiredHeat,
            NonNullList<FluidIngredient> fluidIngredients,
            NonNullList<FluidStack> fluidResults) {
        super(PLACEHOLDER_ID);
        this.ingredients = ingredients;
        this.results = results;
        this.processingDuration = 0; // 始终为0，因为我们不使用处理时间
        this.requiredHeat = requiredHeat;
        this.fluidIngredients = fluidIngredients;
        this.fluidResults = fluidResults;
    }

    // Static factory for the Codec - 移除了 processingDuration 参数
    private static PeelingRecipeParams createFromCodec(
            NonNullList<Ingredient> ingredients,
            NonNullList<ProcessingOutput> results,
            HeatCondition requiredHeat,
            NonNullList<FluidIngredient> fluidIngredients,
            NonNullList<FluidStack> fluidResults) {
        return new PeelingRecipeParams(ingredients, results, requiredHeat, fluidIngredients, fluidResults);
    }

    public static final MapCodec<PeelingRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            PeelingRecipeSerializer.INGREDIENTS_MAP_CODEC.forGetter(p -> p.ingredients),
            PeelingRecipeSerializer.RESULTS_MAP_CODEC.forGetter(p -> p.results),
            // 移除 processingTime 字段的读取
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
        // 不编码 processingDuration
        HeatCondition.STREAM_CODEC.encode(buffer, params.requiredHeat);
        CatnipStreamCodecBuilders.nonNullList(FluidIngredient.STREAM_CODEC).encode(buffer, params.fluidIngredients);
        CatnipStreamCodecBuilders.nonNullList(FluidStack.STREAM_CODEC).encode(buffer, params.fluidResults);
    }

    private static PeelingRecipeParams decodeFromBuffer(RegistryFriendlyByteBuf buffer) {
        NonNullList<Ingredient> ingredients = CatnipStreamCodecBuilders.nonNullList(Ingredient.CONTENTS_STREAM_CODEC).decode(buffer);
        NonNullList<ProcessingOutput> results = CatnipStreamCodecBuilders.nonNullList(ProcessingOutput.STREAM_CODEC).decode(buffer);
        // 不解码 processingDuration
        HeatCondition heat = HeatCondition.STREAM_CODEC.decode(buffer);
        NonNullList<FluidIngredient> fluidIngredients = CatnipStreamCodecBuilders.nonNullList(FluidIngredient.STREAM_CODEC).decode(buffer);
        NonNullList<FluidStack> fluidResults = CatnipStreamCodecBuilders.nonNullList(FluidStack.STREAM_CODEC).decode(buffer);
        return new PeelingRecipeParams(ingredients, results, heat, fluidIngredients, fluidResults);
    }
    // 在 PeelingRecipeParams 类中添加以下代码

// 在 PeelingRecipeParams 类中添加（如果还没有的话）

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private NonNullList<Ingredient> ingredients = NonNullList.create();
        private NonNullList<ProcessingOutput> results = NonNullList.create();
        private HeatCondition requiredHeat = HeatCondition.NONE;
        private NonNullList<FluidIngredient> fluidIngredients = NonNullList.create();
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