package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public enum CreateFisheryRecipeTypes implements IRecipeTypeInfo {
    PEELING(PeelingRecipe::new);

    private final ResourceLocation id;
    private final Supplier<RecipeSerializer<?>> serializerSupplier;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializerObject;
    private final DeferredHolder<RecipeType<?>, RecipeType<PeelingRecipe>> typeObject;
    private final Supplier<RecipeType<PeelingRecipe>> type;

    CreateFisheryRecipeTypes(ProcessingRecipeBuilder.ProcessingRecipeFactory<?> processingFactory) {
        String name = name().toLowerCase();
        this.id = CreateFisheryMod.asResource(name);
        this.serializerSupplier = () -> new ProcessingRecipeSerializer<>(processingFactory);
        this.serializerObject = Registers.SERIALIZER_REGISTER.register(name, serializerSupplier);
        this.typeObject = Registers.TYPE_REGISTER.register(name, () -> new RecipeType<PeelingRecipe>() {
            @Override
            public String toString() {
                return id.toString();
            }
        });
        this.type = typeObject;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializerObject.get();
    }

    @Override
    public RecipeType<PeelingRecipe> getType() {
        return type.get();
    }

    public static void register(DeferredRegister<RecipeSerializer<?>> serializers, DeferredRegister<RecipeType<?>> types) {
        for (CreateFisheryRecipeTypes type : values()) {
            serializers.register(type.name().toLowerCase(), type.serializerSupplier);
            types.register(type.name().toLowerCase(), () -> new RecipeType<PeelingRecipe>() {
                @Override
                public String toString() {
                    return type.id.toString();
                }
            });
        }
    }

    private static class Registers {
        private static final DeferredRegister<RecipeSerializer<?>> SERIALIZER_REGISTER = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_SERIALIZER, CreateFisheryMod.ID);
        private static final DeferredRegister<RecipeType<?>> TYPE_REGISTER = DeferredRegister.create(net.minecraft.core.registries.Registries.RECIPE_TYPE, CreateFisheryMod.ID);
    }
}