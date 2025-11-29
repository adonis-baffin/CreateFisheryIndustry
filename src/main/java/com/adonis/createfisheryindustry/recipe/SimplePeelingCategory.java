package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryJeiPlugin;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.AnimatedMechanicalPeeler;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class SimplePeelingCategory implements IRecipeCategory<PeelingRecipe> {

    private final IDrawable background;
    private final IDrawable icon;
    private final AnimatedMechanicalPeeler peeler = new AnimatedMechanicalPeeler();
    private static final IDrawable BASIC_SLOT = createSlotDrawable();

    public SimplePeelingCategory(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        this.background = guiHelper.createBlankDrawable(177, 90);
        this.icon = guiHelper.createDrawableItemStack(CreateFisheryBlocks.MECHANICAL_PEELER.asStack());
    }

    private static IDrawable createSlotDrawable() {
        return new IDrawable() {
            @Override
            public int getWidth() {
                return AllGuiTextures.JEI_SLOT.getWidth();
            }

            @Override
            public int getHeight() {
                return AllGuiTextures.JEI_SLOT.getHeight();
            }

            @Override
            public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
                AllGuiTextures.JEI_SLOT.render(graphics, xOffset, yOffset);
            }
        };
    }

    @Override
    public RecipeType<PeelingRecipe> getRecipeType() {
        return CreateFisheryJeiPlugin.PEELING_TYPE_JEI;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("recipe.createfisheryindustry.peeling");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, PeelingRecipe recipe, IFocusGroup focuses) {
        // Input slot (left-top, adjusted to (27, 5))
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 5)
                .setBackground(BASIC_SLOT, -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // Primary Output slot (right-bottom, at (132, 38))
        ItemStack primaryOutputStack = recipe.getPrimaryOutput();
        if (!primaryOutputStack.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 132, 38)
                    .setBackground(BASIC_SLOT, -1, -1)
                    .addItemStack(primaryOutputStack);
        }

        // Secondary Outputs (right-top, starting at (132, 5), 2x2 grid)
        List<ProcessingOutput> secondaryOutputs = recipe.getSecondaryOutputs();
        int maxDisplaySecondary = 4; // Show up to 2x2 grid
        for (int i = 0; i < Math.min(secondaryOutputs.size(), maxDisplaySecondary); i++) {
            ProcessingOutput output = secondaryOutputs.get(i);
            int xOffset = (i % 2) * 19;
            int yOffset = (i / 2) * 19;
            builder.addSlot(RecipeIngredientRole.OUTPUT, 132 + xOffset, 5 + yOffset)
                    .setBackground(BASIC_SLOT, -1, -1)
                    .addItemStack(output.getStack());
        }
    }

    @Override
    public void draw(PeelingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics gui, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(gui, 65, 10);
        AllGuiTextures.JEI_SHADOW.render(gui, 72 - 17, 42 + 13);

        // 绘制动画机械切削机
        peeler.draw(gui, 72, 42);
    }
}