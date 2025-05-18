package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.AnimatedMechanicalPeeler; // Import your new animation class
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class PeelingCategory extends CreateRecipeCategory<PeelingRecipe> {

    private final AnimatedMechanicalPeeler peeler = new AnimatedMechanicalPeeler();

    // Adjust positions for the new layout
    // Background width: 177, height: 90 (as set in JeiPlugin)
    private static final int INPUT_X = 18;  // Left side
    private static final int INPUT_Y = 60;  // Lower part

    private static final int PRIMARY_OUTPUT_X = 138; // Right side
    private static final int PRIMARY_OUTPUT_Y = 60;  // Lower part

    // Secondary outputs will be to the top right of the peeler
    private static final int SECONDARY_OUTPUT_START_X = 110;
    private static final int SECONDARY_OUTPUT_START_Y = 10;
    private static final int SECONDARY_OUTPUT_SPACING_X = 19;
    private static final int SECONDARY_OUTPUT_SPACING_Y = 19;

    // Peeler animation position (center of the category more or less)
    private static final int PEELER_ANIM_X = 72; // (177 / 2) - (icon_width / 2) roughly
    private static final int PEELER_ANIM_Y = 35; // Vertically centered a bit

    public PeelingCategory(Info<PeelingRecipe> info) {
        super(info);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PeelingRecipe recipe, IFocusGroup focuses) {
        // Input slot (left-bottom)
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, INPUT_Y)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().getFirst());

        // Primary Output slot (right-bottom)
        ItemStack primaryOutputStack = recipe.getPrimaryOutput();
        if (!primaryOutputStack.isEmpty()) {
            IRecipeSlotBuilder primaryOutputSlotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, PRIMARY_OUTPUT_X, PRIMARY_OUTPUT_Y)
                    .setBackground(getRenderedSlot(), -1, -1)
                    .addItemStack(primaryOutputStack);
            if (!recipe.getRollableResults().isEmpty() && recipe.getRollableResults().getFirst().getStack().equals(primaryOutputStack)) {
                primaryOutputSlotBuilder.addRichTooltipCallback(addStochasticTooltip(recipe.getRollableResults().getFirst()));
            }
        }

        // Secondary Outputs (top-right of peeler)
        List<ProcessingOutput> secondaryOutputs = recipe.getSecondaryOutputs();
        int maxDisplaySecondary = 4; // Show up to 2x2 grid
        for (int i = 0; i < Math.min(secondaryOutputs.size(), maxDisplaySecondary); i++) {
            ProcessingOutput output = secondaryOutputs.get(i);
            IRecipeSlotBuilder secondarySlotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT,
                            SECONDARY_OUTPUT_START_X + (i % 2) * SECONDARY_OUTPUT_SPACING_X,
                            SECONDARY_OUTPUT_START_Y + (i / 2) * SECONDARY_OUTPUT_SPACING_Y)
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack());
            secondarySlotBuilder.addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    public void draw(PeelingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        // Shadow for the peeler (adjust x, y to be under your peeler animation)
        AllGuiTextures.JEI_SHADOW.render(graphics, PEELER_ANIM_X - 16, PEELER_ANIM_Y + 13); // Example position

        // Arrow from input to peeler (straight right)
        AllGuiTextures.JEI_ARROW.render(graphics, INPUT_X + 20, INPUT_Y + 2);

        // Arrow from peeler to primary output (straight right)
        AllGuiTextures.JEI_ARROW.render(graphics, PEELER_ANIM_X + 30, PRIMARY_OUTPUT_Y + 2); // From right of peeler to output

        // Arrow from peeler up and then right to secondary outputs
        // Upwards part (using JEI_DOWN_ARROW and positioning it above the peeler, pointing "up")
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, PEELER_ANIM_X + 7, PEELER_ANIM_Y - 12); // x centered, y above peeler

        // Rightwards part (from the "top" of the previous arrow towards secondary outputs)
        AllGuiTextures.JEI_ARROW.render(graphics, PEELER_ANIM_X + 15, SECONDARY_OUTPUT_START_Y + 5); // Adjust X to connect

        // Render the animated peeler
        peeler.draw(graphics, PEELER_ANIM_X, PEELER_ANIM_Y);
    }
}