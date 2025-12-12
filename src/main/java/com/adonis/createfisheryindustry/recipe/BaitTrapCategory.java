package com.adonis.createfisheryindustry.recipe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class BaitTrapCategory implements IRecipeCategory<BaitTrapRecipe> {

    public static final RecipeType<BaitTrapRecipe> BAIT_TRAP_TYPE =
            RecipeType.create(CreateFisheryMod.ID, "bait_trap", BaitTrapRecipe.class);

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawable slotDrawable;
    private final BlockState baitTrapState;

    public BaitTrapCategory(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        this.background = guiHelper.createBlankDrawable(177, 80);
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
        // Input slot (bait) - left side
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 28)
                .setBackground(slotDrawable, -1, -1)
                .addIngredients(recipe.getIngredients().get(0));

        // Output slots - right side in 2x2 layout
        List<ProcessingOutput> results = recipe.getRollableResults();

        int[] xPositions = {132, 132, 151, 151};
        int[] yPositions = {28, 5, 5, 28};

        for (int i = 0; i < Math.min(results.size(), 4); i++) {
            ProcessingOutput output = results.get(i);

            IRecipeSlotBuilder slotBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, xPositions[i], yPositions[i])
                    .setBackground(slotDrawable, -1, -1)
                    .addItemStack(output.getStack());

            // Add chance tooltip if output has less than 100% chance
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
        // Draw arrow from input to output
        AllGuiTextures.JEI_ARROW.render(graphics, 70, 30);

        // Draw shadow under the block
        AllGuiTextures.JEI_SHADOW.render(graphics, 72 - 17, 27 + 13);

        // Draw the bait trap block in the center (static, no rotation animation)
        PoseStack poseStack = graphics.pose();
        poseStack.pushPose();

        // Position the block in the center
        poseStack.translate(82, 48, 100);
        poseStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
        poseStack.mulPose(Axis.YP.rotationDegrees(22.5f));
        poseStack.scale(20, -20, 20);

        // Render the block
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher blockRenderer = mc.getBlockRenderer();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        blockRenderer.renderSingleBlock(
                baitTrapState,
                poseStack,
                buffer,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY
        );

        buffer.endBatch();
        poseStack.popPose();

        // Draw wait time info in bottom right
        Font font = mc.font;
        int minutes = recipe.getLootTimeMinutes();
        String timeText = formatTime(minutes);

        // "Wait Time" label
        Component waitTimeLabel = Component.translatable("createfisheryindustry.recipe.Trapping.wait_time");
        graphics.drawString(font, waitTimeLabel, 100, 58, 0x888888, false);

        // Time value
        graphics.drawString(font, timeText, 100, 68, 0x526878, false);
    }

    /**
     * Format time for display.
     * @param minutes time in minutes
     * @return formatted string like "4 min"
     */
    private String formatTime(int minutes) {
        return minutes + " min";
    }
}