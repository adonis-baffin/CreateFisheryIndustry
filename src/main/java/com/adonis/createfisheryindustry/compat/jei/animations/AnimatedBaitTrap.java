package com.adonis.createfisheryindustry.compat.jei.animations;

import com.adonis.createfisheryindustry.recipe.BaitTrapRecipe;  // 新增导入
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.CustomLightingSettings;
import net.createmod.catnip.gui.ILightingSettings;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Animated (static) Bait Trap renderer for JEI category.
 */
public class AnimatedBaitTrap {

    private static final ILightingSettings DEFAULT_LIGHTING = CustomLightingSettings.builder()
            .firstLightRotation(12.5f, -45.0f)
            .secondLightRotation(-20.0f, -50.0f)
            .build();

    public void draw(GuiGraphics graphics, int xOffset, int yOffset, BaitTrapRecipe recipe) {  // 新增 recipe 参数
        BlockState baitTrapState = CreateFisheryBlocks.BAIT_TRAP.getDefaultState();

        // 如果配方需要水，就设置为 waterlogged = true
        if (recipe.requiresWater() && baitTrapState.hasProperty(BlockStateProperties.WATERLOGGED)) {
            baitTrapState = baitTrapState.setValue(BlockStateProperties.WATERLOGGED, true);
        }

        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(xOffset, yOffset, 200);
        matrixStack.mulPose(Axis.XP.rotationDegrees(-18f));
        matrixStack.mulPose(Axis.YP.rotationDegrees(22f));
        int scale = 23;

        GuiGameElement.of(baitTrapState)
                .lighting(DEFAULT_LIGHTING)
                .atLocal(0, 1.65, 0)
                .scale(scale)
                .render(graphics);

        matrixStack.popPose();
    }
}