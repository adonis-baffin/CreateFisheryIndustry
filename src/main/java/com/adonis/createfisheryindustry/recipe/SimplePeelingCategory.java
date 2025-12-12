package com.adonis.createfisheryindustry.compat.jei;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.AnimatedMechanicalPeeler;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

/**
 * JEI category for Peeling recipes.
 * Refactored to use CFIRecipeCategory base class.
 */
@ParametersAreNonnullByDefault
public class SimplePeelingCategory extends CFIRecipeCategory<PeelingRecipe> {

    private final AnimatedMechanicalPeeler peeler = new AnimatedMechanicalPeeler();

    public SimplePeelingCategory(Info<PeelingRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, PeelingRecipe recipe, IFocusGroup focuses) {
        // Input slot
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 5)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // Output slots - 2x2 layout on the right
        List<ProcessingOutput> results = recipe.getRollableResults();

        int[] xPositions = {132, 132, 151, 151};
        int[] yPositions = {38, 5, 5, 24};

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);

            IRecipeSlotBuilder slotBuilder = builder.addSlot(
                            RecipeIngredientRole.OUTPUT,
                            xPositions[i],
                            yPositions[i])
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack());

            // Add chance tooltip if output has less than 100% chance
            if (output.getChance() < 1.0f) {
                slotBuilder.addRichTooltipCallback(addStochasticTooltip(output));
            }
        }
    }

    @Override
    protected void draw(PeelingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics gui, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(gui, 65, 10);
        AllGuiTextures.JEI_SHADOW.render(gui, 72 - 17, 42 + 13);
        peeler.draw(gui, 72, 42);
    }
}