package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.compat.jei.CFIRecipeCategory;
import com.adonis.createfisheryindustry.compat.jei.animations.AnimatedBaitTrap;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * JEI category for Bait Trap recipes.
 * Layout inspired by LiquidBurningCategory from Create Addition.
 */
public class BaitTrapCategory extends CFIRecipeCategory<BaitTrapRecipe> {

    private final AnimatedBaitTrap baitTrap = new AnimatedBaitTrap();

    public BaitTrapCategory(Info<BaitTrapRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, BaitTrapRecipe recipe, IFocusGroup focuses) {
        // Input slot (bait) - left side
        builder.addSlot(RecipeIngredientRole.INPUT, 55, 3)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // Output slots - right side in 2x2 layout
        List<ProcessingOutput> results = recipe.getRollableResults();

        // Position output slots in top-right area
        int baseX = getBackground().getWidth() - 42;
        int baseY = 30;

        int[] xOffsets = {0, 19, 0, 19};
        int[] yOffsets = {0, 0, 19, 19};

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);

            IRecipeSlotBuilder slotBuilder = builder.addSlot(
                            RecipeIngredientRole.OUTPUT,
                            baseX + xOffsets[i],
                            baseY + yOffsets[i])
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack());

            // Add chance tooltip if output has less than 100% chance
            if (output.getChance() < 1.0f) {
                slotBuilder.addRichTooltipCallback(addStochasticTooltip(output));
            }
        }
    }

    @Override
    protected void draw(BaitTrapRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // Draw shadow under the block
        AllGuiTextures.JEI_SHADOW.render(graphics, 76, 43);

        // Draw the bait trap block - aligned with shadow
        baitTrap.draw(graphics, 87, 10);

        // Draw arrow pointing down from input to bait trap
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 88, 8);

        // Draw bait level info - below input, left of shadow
        int baitLevel = recipe.getBaitLevel();
        String levelText = Component.translatable("createfisheryindustry.recipe.bait_trap.bait_level")
                .getString() + ": " + baitLevel;
        graphics.drawString(font, levelText, 10, 45, 0x526878, false);

        // Draw wait time - below bait level
        int minutes = recipe.getLootTimeMinutes();
        String timeText = minutes + " min";
        graphics.drawString(font, timeText, 10, 57, 0x526878, false);
    }
}