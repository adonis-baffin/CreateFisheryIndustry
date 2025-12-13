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
        builder.addSlot(RecipeIngredientRole.INPUT, 55, 3)
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

        AllGuiTextures.JEI_SHADOW.render(graphics, 76, 43);
        baitTrap.draw(graphics, 87, 10);
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 88, 8);

        // 等级
        int baitLevel = recipe.getBaitLevel();
        graphics.drawString(font,
                Component.translatable("createfisheryindustry.recipe.bait_trap.bait_level")
                        .append(": " + baitLevel),
                10, 35, 0x526878, false);

        // 生成间隔
        graphics.drawString(font,
                recipe.getLootTimeMinutes() + " min",
                10, 55, 0x526878, false);

        // ==================== 环境需求（位置不变，样式完全同步 Basin 加热条）===================
        boolean needsWater = recipe.requiresWater();

        // 1. 画底条（和加热条一模一样的资源、大小、风格）
        AllGuiTextures bar = needsWater ? AllGuiTextures.JEI_HEAT_BAR : AllGuiTextures.JEI_NO_HEAT_BAR;
        bar.render(graphics, 8 - 7, 55 - 6);   // 微调让文字完美居中在条里（原版就是这么对齐的）

        // 2. 文字（颜色、加粗、位置完全模仿原版加热条文字）
        String key = needsWater
                ? "createfisheryindustry.recipe.bait_trap.requires_water"
                : "createfisheryindustry.recipe.bait_trap.land_only";

        int textColor = needsWater ? 0x3AB3A8 : 0x888888; // 水色 vs 原版无加热灰色

        graphics.drawString(font,
                Component.translatable(key).withStyle(ChatFormatting.BOLD),
                118, 55, textColor, false); // 位置完全不动
    }
}