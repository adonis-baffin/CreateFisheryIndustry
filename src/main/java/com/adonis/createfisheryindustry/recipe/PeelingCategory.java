package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

public class PeelingCategory extends CreateRecipeCategory<PeelingRecipe> {
    public PeelingCategory(Info<PeelingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PeelingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 8)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().getFirst());

        List<ProcessingOutput> results = recipe.getRollableResults();
        for (int i = 0; i < results.size(); i++) {
            ProcessingOutput output = results.get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 73 + (i % 2) * 19, 8 + (i / 2) * 19)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    public void draw(PeelingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(guiGraphics, 48, 4);
        AllGuiTextures.JEI_SHADOW.render(guiGraphics, 42, 38);

        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(46, 34, 0);
        pose.scale(.6f, .6f, 1);
        guiGraphics.renderItem(CreateFisheryBlocks.MECHANICAL_PEELER.asStack(), 0, 0);
        pose.popPose();
    }

    @Override
    public List<Component> getTooltipStrings(PeelingRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseX > 40 && mouseX < 56 && mouseY > 28 && mouseY < 44) {
            return List.of(Component.translatable("block.createfisheryindustry.mechanical_peeler"));
        }
        return super.getTooltipStrings(recipe, recipeSlotsView, mouseX, mouseY);
    }
}