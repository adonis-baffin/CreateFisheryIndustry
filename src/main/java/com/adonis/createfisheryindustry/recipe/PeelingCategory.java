package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.AnimatedMechanicalPeeler;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

import static com.simibubi.create.compat.jei.category.CreateRecipeCategory.addStochasticTooltip;
import static com.simibubi.create.compat.jei.category.CreateRecipeCategory.getRenderedSlot;

@ParametersAreNonnullByDefault
public class PeelingCategory extends CreateRecipeCategory<PeelingRecipe> {

    private final AnimatedMechanicalPeeler peeler = new AnimatedMechanicalPeeler();

    public PeelingCategory(Info<PeelingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PeelingRecipe recipe, IFocusGroup focuses) {
        // Input slot (left-top, adjusted to (27, 5))
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 5)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().getFirst());

        // Primary Output slot (right-bottom, at (132, 38))
        ItemStack primaryOutputStack = recipe.getPrimaryOutput();
        if (!primaryOutputStack.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 38)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addItemStack(primaryOutputStack)
                    .addRichTooltipCallback(addStochasticTooltip(recipe.getRollableResults().isEmpty() ? null : recipe.getRollableResults().getFirst()));
        }

        // Secondary Outputs (right-top, starting at (132, 5), 2x2 grid)
        List<ProcessingOutput> secondaryOutputs = recipe.getSecondaryOutputs();
        int maxDisplaySecondary = 4; // Show up to 2x2 grid
        for (int i = 0; i < Math.min(secondaryOutputs.size(), maxDisplaySecondary); i++) {
            ProcessingOutput output = secondaryOutputs.get(i);
            int xOffset = (i % 2) * 19;
            int yOffset = (i / 2) * 19;
            builder.addSlot(RecipeIngredientRole.OUTPUT, 132 + xOffset, 5 + yOffset)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    public void draw(PeelingRecipe recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 65, 10);
        AllGuiTextures.JEI_SHADOW.render(graphics, 72 - 17, 42 + 13);

        peeler.draw(graphics, 72, 42);
    }
}