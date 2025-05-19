package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;

public class AnimatedMechanicalPeeler extends AnimatedKinetics {

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(xOffset, yOffset, 0);
        matrixStack.translate(0, 0, 200);
        matrixStack.translate(2, 22, 0);
        matrixStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
        matrixStack.mulPose(Axis.YP.rotationDegrees(22.5f + 90));
        int scale = 27;

        blockElement(shaft(Direction.Axis.X))
                .rotateBlock(-getCurrentAngle(), 0, 0)
                .scale(scale)
                .render(graphics);

        blockElement(CreateFisheryBlocks.MECHANICAL_PEELER.getDefaultState()
                .setValue(MechanicalPeelerBlock.FACING, Direction.UP))
                .rotateBlock(0, 0, 0)
                .scale(scale)
                .render(graphics);

        blockElement(CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_ACTIVE)
                .rotateBlock(0, -90, -90)
                .scale(scale)
                .render(graphics);

        matrixStack.popPose();
    }
}