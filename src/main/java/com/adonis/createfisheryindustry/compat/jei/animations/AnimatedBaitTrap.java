package com.adonis.createfisheryindustry.compat.jei.animations;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.CustomLightingSettings;
import net.createmod.catnip.gui.ILightingSettings;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Animated (static) Bait Trap renderer for JEI category.
 * Similar to AnimatedBlazeBurner but without animation.
 */
public class AnimatedBaitTrap {

    private static final ILightingSettings DEFAULT_LIGHTING = CustomLightingSettings.builder()
            .firstLightRotation(12.5f, -45.0f)
            .secondLightRotation(-20.0f, -50.0f)
            .build();

    private final BlockState baitTrapState;

    public AnimatedBaitTrap() {
        this.baitTrapState = CreateFisheryBlocks.BAIT_TRAP.getDefaultState();
    }

    public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(xOffset, yOffset, 200);
        matrixStack.mulPose(Axis.XP.rotationDegrees(-15.5f));
        matrixStack.mulPose(Axis.YP.rotationDegrees(22.5f));
        int scale = 23;

        GuiGameElement.of(baitTrapState)
                .lighting(DEFAULT_LIGHTING)
                .atLocal(0, 1.65, 0)
                .scale(scale)
                .render(graphics);

        matrixStack.popPose();
    }
}