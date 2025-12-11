package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.function.Supplier;

public enum CreateFisheryRecipeTypes implements IRecipeTypeInfo {
    PEELING(PeelingRecipeSerializer::new),
    BAIT_TRAP(BaitTrapRecipeSerializer::new);

    private final ResourceLocation id;
    private DeferredHolder<RecipeSerializer<?>, ? extends RecipeSerializer<?>> serializerObject;
    private DeferredHolder<RecipeType<?>, ? extends RecipeType<?>> typeObject;

    private final Supplier<? extends RecipeSerializer<?>> serializerSupplier;

    <T extends Recipe<?>> CreateFisheryRecipeTypes(Supplier<RecipeSerializer<T>> serializerSupplier) {
        String name = name().toLowerCase(Locale.ROOT);
        this.id = CreateFisheryMod.asResource(name);
        this.serializerSupplier = serializerSupplier;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends RecipeSerializer<?>> S getSerializer() {
        if (serializerObject == null) throw new IllegalStateException("Recipe Serializer for " + id + " not registered yet!");
        return (S) serializerObject.get();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        if (typeObject == null) throw new IllegalStateException("Recipe Type for " + id + " not registered yet!");
        return (RecipeType<R>) typeObject.get();
    }

    public static void register(DeferredRegister<RecipeSerializer<?>> serializersRegistry,
                                DeferredRegister<RecipeType<?>> typesRegistry) {
        for (CreateFisheryRecipeTypes type : values()) {
            type.serializerObject = serializersRegistry.register(type.id.getPath(), type.serializerSupplier);

            final ResourceLocation typeId = type.id;
            type.typeObject = typesRegistry.register(type.id.getPath(), () -> new RecipeType<>() {
                @Override
                public String toString() {
                    return typeId.toString();
                }
            });
        }
    }
}