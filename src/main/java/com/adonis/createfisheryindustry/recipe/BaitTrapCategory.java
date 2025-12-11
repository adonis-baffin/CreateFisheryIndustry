package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class BaitTrapCategory implements IRecipeCategory<BaitTrapRecipe> {

    public static final RecipeType<BaitTrapRecipe> BAIT_TRAP_TYPE = 
            RecipeType.create(CreateFisheryMod.ID, "bait_trap", BaitTrapRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private static final IDrawable BASIC_SLOT = createSlotDrawable();

    public BaitTrapCategory(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        this.background = guiHelper.createBlankDrawable(177, 80);
        this.icon = guiHelper.createDrawableItemStack(CreateFisheryBlocks.BAIT_TRAP.asStack());
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
    public RecipeType<BaitTrapRecipe> getRecipeType() {
        return BAIT_TRAP_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("recipe.createfisheryindustry.bait_trap");
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
    public void setRecipe(IRecipeLayoutBuilder builder, BaitTrapRecipe recipe, IFocusGroup focuses) {
        // Input slot (bait)
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 28)
                .setBackground(BASIC_SLOT, -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // Output slots
        List<ProcessingOutput> results = recipe.getRollableResults();
        
        int[] xPositions = {132, 132, 151, 151};
        int[] yPositions = {28, 5, 5, 28};

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);

            IRecipeSlotBuilder slotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, xPositions[i], yPositions[i])
                    .setBackground(BASIC_SLOT, -1, -1)
                    .addItemStack(output.getStack());

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
    public void draw(BaitTrapRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics gui, double mouseX, double mouseY) {
        // Draw arrow
        AllGuiTextures.JEI_ARROW.render(gui, 70, 30);
        
        // Draw bait trap icon in center
        AllGuiTextures.JEI_SHADOW.render(gui, 72 - 17, 27 + 13);
        
        // Draw level and time info
        Font font = Minecraft.getInstance().font;
        
        int level = recipe.getBaitLevel();
        int minSeconds = recipe.getMinLootTimeTicks() / 20;
        int maxSeconds = recipe.getMaxLootTimeTicks() / 20;
        
        // Level text
        Component levelText = Component.translatable("gui.createfisheryindustry.bait_trap.level", level);
        gui.drawString(font, levelText, 5, 5, 0x444444, false);
        
        // Time range text
        String timeRange;
        if (minSeconds >= 60 || maxSeconds >= 60) {
            int minMinutes = minSeconds / 60;
            int minSecs = minSeconds % 60;
            int maxMinutes = maxSeconds / 60;
            int maxSecs = maxSeconds % 60;
            
            if (minMinutes > 0 && maxMinutes > 0) {
                timeRange = String.format("%d:%02d - %d:%02d", minMinutes, minSecs, maxMinutes, maxSecs);
            } else {
                timeRange = String.format("%ds - %d:%02d", minSeconds, maxMinutes, maxSecs);
            }
        } else {
            timeRange = String.format("%d - %ds", minSeconds, maxSeconds);
        }
        
        Component timeText = Component.translatable("gui.createfisheryindustry.bait_trap.time", timeRange);
        gui.drawString(font, timeText, 5, 65, 0x666666, false);
    }
}