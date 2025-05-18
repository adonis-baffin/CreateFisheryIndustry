package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.animations.AnimatedKinetics;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class AnimatedMechanicalPeeler extends AnimatedKinetics {

    @Override
    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(xOffset, yOffset, 0); // Basic translation
        matrixStack.translate(10, 20, 0); // Fine-tune position within the 177x90 box
                                                // This places the peeler visually centered-ish.
                                                // You'll need to adjust these based on your JEI category size and element placement.

        // Get the default state of your Mechanical Peeler, facing UP
        BlockState defaultState = CreateFisheryBlocks.MECHANICAL_PEELER.get()
                .defaultBlockState()
                .setValue(com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock.FACING, Direction.UP);


        GuiGameElement.of(defaultState)
                .scale(24) // Adjust scale as needed
                .lighting(AnimatedKinetics.DEFAULT_LIGHTING)
                .render(graphics);

        matrixStack.popPose();
    }
}