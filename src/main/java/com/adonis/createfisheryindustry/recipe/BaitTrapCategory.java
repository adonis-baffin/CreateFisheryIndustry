package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.compat.jei.CFIRecipeCategory;
import com.adonis.createfisheryindustry.compat.jei.animations.AnimatedBaitTrap;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.List;

public class BaitTrapCategory extends CFIRecipeCategory<BaitTrapRecipe> {

    private final AnimatedBaitTrap baitTrap = new AnimatedBaitTrap();

    public BaitTrapCategory(Info<BaitTrapRecipe> info) {
        super(info);
    }

    @Override
    protected void setRecipe(IRecipeLayoutBuilder builder, BaitTrapRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 63, 3)
                .setBackground(getRenderedSlot(), -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        List<ProcessingOutput> results = recipe.getRollableResults();
        int baseX = getBackground().getWidth() - 42;
        int baseY = 30;
        int[] xOffsets = {0, 19, 0, 19};
        int[] yOffsets = {0, 0, 19, 19};

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);
            builder.addSlot(RecipeIngredientRole.OUTPUT, baseX + xOffsets[i], baseY + yOffsets[i])
                    .setBackground(getRenderedSlot(output), -1, -1)
                    .addItemStack(output.getStack())
                    .addRichTooltipCallback(addStochasticTooltip(output));
        }
    }

    @Override
    protected void draw(BaitTrapRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        AllGuiTextures.JEI_SHADOW.render(graphics, 79, 43);
        baitTrap.draw(graphics, 90, 10);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 91, 8);

        // 等级
        int baitLevel = recipe.getBaitLevel();
        graphics.drawString(font,
                Component.translatable("createfisheryindustry.recipe.bait_trap.bait_level")
                        .append(": " + baitLevel),
                9, 35, 0x526878, false);

        // 生成间隔时间
        graphics.drawString(font,
                recipe.getLootTimeMinutes() + " min",
                10, 55, 0x526878, false);

// ==================== 环境需求文字（完美对齐原版加热条）===================
        boolean needsWater = recipe.requiresWater();
        AllGuiTextures.JEI_NO_HEAT_BAR.render(graphics, 4, 49);

        Component text = Component.translatable(
                        needsWater ? "createfisheryindustry.recipe.bait_trap.requires_water"
                                : "createfisheryindustry.recipe.bait_trap.land_only")
                .withStyle(ChatFormatting.BOLD);

        int textColor = needsWater ? 0x3AB3A8 : 0x888888;
        int textWidth = font.width(text);

// 关键：中心点用 66（和 Create 官方完全一致）
        int centeredX = 143 - (textWidth / 2);

// 可选阴影（原版也有）
        graphics.drawString(font, text, centeredX + 1, 56, 0x000000, false);
        graphics.drawString(font, text, centeredX,     55, textColor,   false);
    }
}