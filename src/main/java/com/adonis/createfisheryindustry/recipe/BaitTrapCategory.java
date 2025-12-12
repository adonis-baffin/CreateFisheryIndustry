package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class BaitTrapCategory implements IRecipeCategory<BaitTrapRecipe> {

    public static final RecipeType<BaitTrapRecipe> BAIT_TRAP_TYPE =
            RecipeType.create(CreateFisheryMod.ID, "bait_trap", BaitTrapRecipe.class);

    // 背景尺寸 - 使用Create标准尺寸
    private static final int BACKGROUND_WIDTH = 177;
    private static final int BACKGROUND_HEIGHT = 70;

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotDrawable;
    private final BlockState baitTrapState;

    public BaitTrapCategory(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        this.background = guiHelper.createBlankDrawable(BACKGROUND_WIDTH, BACKGROUND_HEIGHT);
        this.icon = guiHelper.createDrawableItemStack(CreateFisheryBlocks.BAIT_TRAP.asStack());
        this.slotDrawable = createSlotDrawable();
        this.baitTrapState = CreateFisheryBlocks.BAIT_TRAP.getDefaultState();
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
        return Component.translatable("createfisheryindustry.recipe.Trapping");
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
        // 布局计算
        int centerX = BACKGROUND_WIDTH / 2;  // 88
        int centerY = 28;

        // 输入槽 - 左侧
        int inputX = centerX - 50;  // 38
        builder.addSlot(RecipeIngredientRole.INPUT, inputX, centerY)
                .setBackground(slotDrawable, -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // 输出槽 - 右侧，2x2布局
        List<ProcessingOutput> results = recipe.getRollableResults();
        int outputStartX = centerX + 32;  // 120
        int outputStartY = centerY - 10;  // 18

        // 2x2 输出槽位置
        int[][] outputPositions = {
                {outputStartX, outputStartY},           // 左上
                {outputStartX + 19, outputStartY},      // 右上
                {outputStartX, outputStartY + 19},      // 左下
                {outputStartX + 19, outputStartY + 19}  // 右下
        };

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);

            IRecipeSlotBuilder slotBuilder = builder.addSlot(
                            RecipeIngredientRole.OUTPUT,
                            outputPositions[i][0],
                            outputPositions[i][1])
                    .setBackground(slotDrawable, -1, -1)
                    .addItemStack(output.getStack());

            // 添加概率提示
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
    public void draw(BaitTrapRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        int centerX = BACKGROUND_WIDTH / 2;  // 88
        int centerY = 28;

        // 1. 绘制箭头 - 在输入槽和方块之间
        AllGuiTextures.JEI_ARROW.render(graphics, centerX - 26, centerY + 1);

        // 2. 绘制阴影
        AllGuiTextures.JEI_SHADOW.render(graphics, centerX - 17, centerY + 13);

        // 3. 绘制诱捕网方块（使用GuiGameElement静态渲染）
        AnimatedKinetics.defaultBlockElement(baitTrapState)
                .atLocal(0, 0, 0)
                .scale(20)
                .at(centerX - 7, centerY + 10)
                .render(graphics);

        // 4. 绘制第二个箭头 - 在方块和输出槽之间
        AllGuiTextures.JEI_ARROW.render(graphics, centerX + 8, centerY + 1);

        // 5. 绘制等待时间信息 - 底部居中
        int minutes = recipe.getLootTimeMinutes();
        String timeText = formatTime(minutes);

        Component waitTimeLabel = Component.translatable("createfisheryindustry.recipe.Trapping.wait_time");
        String fullText = waitTimeLabel.getString() + ": " + timeText;

        int textWidth = mc.font.width(fullText);
        int textX = (BACKGROUND_WIDTH - textWidth) / 2;
        int textY = BACKGROUND_HEIGHT - 12;

        graphics.drawString(mc.font, fullText, textX, textY, 0x888888, false);
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(int minutes) {
        return minutes + " min";
    }
}