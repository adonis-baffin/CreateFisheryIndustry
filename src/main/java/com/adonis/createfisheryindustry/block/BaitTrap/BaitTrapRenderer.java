package com.adonis.createfisheryindustry.block.BaitTrap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer for BaitTrapBlockEntity.
 * Renders the current bait item floating inside the trap when baited.
 */
public class BaitTrapRenderer implements BlockEntityRenderer<BaitTrapBlockEntity> {

    public BaitTrapRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(BaitTrapBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        if (!blockEntity.isBaited()) {
            return;
        }

        ItemStack bait = blockEntity.getCurrentBait();
        if (bait.isEmpty()) {
            return;
        }

        poseStack.pushPose();

        // Position in center of block, slightly above center
        poseStack.translate(0.5, 0.4, 0.5);

        // Rotate slowly based on game time
        long gameTime = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0;
        float rotation = (gameTime + partialTick) * 2.0f;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        // Scale down the item
        poseStack.scale(0.35f, 0.35f, 0.35f);

        // Bob up and down
        float bob = (float) Math.sin((gameTime + partialTick) * 0.1) * 0.05f;
        poseStack.translate(0, bob, 0);

        // Render the item
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        itemRenderer.renderStatic(bait, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource, blockEntity.getLevel(), 0);

        poseStack.popPose();
    }
}