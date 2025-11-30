package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryJeiPlugin;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.AnimatedMechanicalPeeler;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
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
        // Input slot
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 5)
                .setBackground(BASIC_SLOT, -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        List<ProcessingOutput> results = recipe.getRollableResults();

        // 所有输出都用统一的方式处理
        int[] xPositions = {132, 132, 151, 151};  // 2x2 网格的 x 坐标
        int[] yPositions = {38, 5, 5, 24};        // 调整 y 坐标，第一个是主输出位置

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);

            IRecipeSlotBuilder slotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, xPositions[i], yPositions[i])
                    .setBackground(BASIC_SLOT, -1, -1)
                    .addItemStack(output.getStack());

            // 如果概率不是 100%，添加概率提示
            if (output.getChance() < 1.0f) {
                slotBuilder.addRichTooltipCallback((recipeSlotView, tooltip) -> {
                    float chance = output.getChance();
                    tooltip.add(Component.translatable("create.recipe.processing.chance",
                                    String.format("%.0f", chance * 100))
                            .withStyle(style -> style.withColor(0xFFAA00)));
                });
            }
        }
    }

    @Override
    public void draw(PeelingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics gui, double mouseX, double mouseY) {
        AllGuiTextures.JEI_DOWN_ARROW.render(gui, 65, 10);
        AllGuiTextures.JEI_SHADOW.render(gui, 72 - 17, 42 + 13);
        peeler.draw(gui, 72, 42);
    }
}