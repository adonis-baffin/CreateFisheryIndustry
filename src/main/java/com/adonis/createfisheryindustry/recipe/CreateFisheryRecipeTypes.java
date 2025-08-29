package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public enum CreateFisheryRecipeTypes implements IRecipeTypeInfo {
    PEELING; // No longer passing PeelingRecipe::new directly to enum constructor

    private final ResourceLocation id;
    private DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PeelingRecipe>> serializerObject;
    private DeferredHolder<RecipeType<?>, RecipeType<PeelingRecipe>> typeObject;

    private final Supplier<RecipeSerializer<PeelingRecipe>> serializerSupplier;
    private final Supplier<RecipeType<PeelingRecipe>> typeSupplier;

    CreateFisheryRecipeTypes() {
        String name = name().toLowerCase();
        this.id = CreateFisheryMod.asResource(name);

        // Provide a supplier for your new PeelingRecipeSerializer
        this.serializerSupplier = PeelingRecipeSerializer::new; // Assumes PeelingRecipeSerializer has a no-arg constructor

        this.typeSupplier = () -> new RecipeType<PeelingRecipe>() {
            @Override
            public String toString() {
                return id.toString();
            }
        };
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

    // Optional: specific getter if needed elsewhere, but generic should work
    public RecipeSerializer<PeelingRecipe> getActualSerializer() {
        if (serializerObject == null) throw new IllegalStateException("Recipe Serializer for " + id + " not registered yet!");
        return serializerObject.get();
    }

    @Override
    public RecipeType<PeelingRecipe> getType() {
        if (typeObject == null) throw new IllegalStateException("Recipe Type for " + id + " not registered yet!");
        return typeObject.get();
    }

    public static void register(DeferredRegister<RecipeSerializer<?>> serializersRegistry,
                                DeferredRegister<RecipeType<?>> typesRegistry) {
        for (CreateFisheryRecipeTypes type : values()) {
            type.serializerObject = serializersRegistry.register(type.id.getPath(), type.serializerSupplier);
            type.typeObject = typesRegistry.register(type.id.getPath(), type.typeSupplier);
        }
    }
}