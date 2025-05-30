package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class CreateFisheryRecipeManager {

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        RecipeManager recipeManager = server.getRecipeManager();

        try {
            List<RecipeHolder<?>> allRecipes = new ArrayList<>(recipeManager.getRecipes());

            int deoxidationCount = addDeoxidationRecipes(allRecipes);
            int dewaxingCount = addDewaxingRecipes(allRecipes);
            int strippingCount = addLogStrippingRecipes(allRecipes);

            recipeManager.replaceRecipes(allRecipes);



        } catch (Exception e) {

        }
    }

    private static int addDeoxidationRecipes(List<RecipeHolder<?>> recipes) {
        int count = 0;

        if (DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.isEmpty()) {
            return 0;
        }

        for (var entry : DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.entrySet()) {
            var oxidized = entry.getKey();
            var deoxidized = entry.getValue();

            var oxidizedItem = oxidized.asItem();
            var deoxidizedItem = deoxidized.asItem();
            if (oxidizedItem == Items.AIR || deoxidizedItem == Items.AIR) {
                continue;
            }

            var oxidizedId = BuiltInRegistries.BLOCK.getKey(oxidized);
            var recipeId = CreateFisheryMod.asResource("auto_deoxidation/" + oxidizedId.getPath());

            boolean exists = recipes.stream().anyMatch(holder -> holder.id().equals(recipeId));
            if (exists) {
                continue;
            }

            try {
                PeelingRecipeParams params = PeelingRecipeParams.builder()
                        .require(oxidizedItem)
                        .output(deoxidizedItem)
                        .build();

                PeelingRecipe recipe = new PeelingRecipe(params);
                RecipeHolder<PeelingRecipe> holder = new RecipeHolder<>(recipeId, recipe);

                recipes.add(holder);
                count++;
            } catch (Exception e) {
            }
        }
        return count;
    }

    private static int addDewaxingRecipes(List<RecipeHolder<?>> recipes) {
        int count = 0;

        if (DataMapHooks.INVERSE_WAXABLES_DATAMAP.isEmpty()) {
            return 0;
        }

        for (var entry : DataMapHooks.INVERSE_WAXABLES_DATAMAP.entrySet()) {
            var waxed = entry.getKey();
            var dewaxed = entry.getValue();

            var waxedItem = waxed.asItem();
            var dewaxedItem = dewaxed.asItem();
            if (waxedItem == Items.AIR || dewaxedItem == Items.AIR) {
                continue;
            }

            var waxedId = BuiltInRegistries.BLOCK.getKey(waxed);
            var recipeId = CreateFisheryMod.asResource("auto_dewaxing/" + waxedId.getPath());

            boolean exists = recipes.stream().anyMatch(holder -> holder.id().equals(recipeId));
            if (exists) {
                continue;
            }

            try {
                PeelingRecipeParams params = PeelingRecipeParams.builder()
                        .require(waxedItem)
                        .output(dewaxedItem)
                        .build();

                PeelingRecipe recipe = new PeelingRecipe(params);
                RecipeHolder<PeelingRecipe> holder = new RecipeHolder<>(recipeId, recipe);

                recipes.add(holder);
                count++;
            } catch (Exception e) {
            }
        }
        return count;
    }

    private static int addLogStrippingRecipes(List<RecipeHolder<?>> recipes) {
        int count = 0;

        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            Block block = entry.getValue();
            ResourceLocation blockId = entry.getKey().location();
            String path = blockId.getPath();

            // 检查是否是原木（包含"log"但不包含"stripped"）
            if (path.contains("log") && !path.contains("stripped")) {
                // 寻找对应的去皮原木
                String strippedPath = "stripped_" + path;
                ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(blockId.getNamespace(), strippedPath);

                if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                    Block strippedBlock = BuiltInRegistries.BLOCK.get(strippedId);

                    var logItem = block.asItem();
                    var strippedItem = strippedBlock.asItem();

                    if (logItem == Items.AIR || strippedItem == Items.AIR) {
                        continue;
                    }

                    var recipeId = CreateFisheryMod.asResource("auto_stripping/" + blockId.getNamespace() + "/" + path);

                    boolean exists = recipes.stream().anyMatch(holder -> holder.id().equals(recipeId));
                    if (exists) {
                        continue;
                    }

                    try {
                        PeelingRecipeParams params = PeelingRecipeParams.builder()
                                .require(logItem)
                                .output(strippedItem)
                                .build();

                        PeelingRecipe recipe = new PeelingRecipe(params);
                        RecipeHolder<PeelingRecipe> holder = new RecipeHolder<>(recipeId, recipe);

                        recipes.add(holder);
                        count++;
                    } catch (Exception e) {
                    }
                }
            }
        }
        return count;
    }
}