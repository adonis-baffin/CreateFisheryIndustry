package com.adonis.createfisheryindustry.client.renderer;

import static net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction; // Ensure this is imported
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class MechanicalPeelerRenderer extends SafeBlockEntityRenderer<MechanicalPeelerBlockEntity> {

    public MechanicalPeelerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                              int overlay) {
        renderBlade(be, ms, buffer, light);
        renderItems(be, partialTicks, ms, buffer, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        renderShaft(be, ms, buffer, light, overlay);
    }

    protected void renderBlade(MechanicalPeelerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState blockState = be.getBlockState();
        PartialModel partialToRender = null;
        float speed = be.getSpeed();
        boolean rotateVerticalBlade = false;

        if (MechanicalPeelerBlock.isHorizontal(blockState)) {
            if (speed > 0) partialToRender = CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_ACTIVE;
            else if (speed < 0) partialToRender = CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_REVERSED;
            else partialToRender = CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_INACTIVE;
        } else {
            if (speed > 0) partialToRender = CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_ACTIVE;
            else if (speed < 0) partialToRender = CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_REVERSED;
            else partialToRender = CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_INACTIVE;

            if (blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE))
                rotateVerticalBlade = true;
        }

        if (partialToRender == null) return;

        SuperByteBuffer superBuffer = CachedBuffers.partialFacing(partialToRender, blockState);
        if (rotateVerticalBlade && !MechanicalPeelerBlock.isHorizontal(blockState)) {
            superBuffer.center()
                    .rotate(Direction.Axis.Y, AngleHelper.rad(90)) // Use Direction.Axis.Y
                    .uncenter();
        }
        superBuffer.color(0xFFFFFF)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        KineticBlockEntityRenderer.renderRotatingBuffer(be,
                getRotatedShaftModel(be, be.getBlockState()),
                ms,
                buffer.getBuffer(RenderType.solid()),
                light);
    }

    protected void renderItems(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light,
                               int overlay) {
        if (be.getBlockState().getValue(FACING) != Direction.UP)
            return;
        if (be.inventory.isEmpty())
            return;

        boolean alongLocalXRender = !be.getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);

        float duration = be.inventory.recipeDuration;
        boolean isProcessing = duration != 0;
        float progress = 0.5f;

        if (isProcessing) {
            float effectiveRemainingTime = be.inventory.remainingTime;
            if (Math.abs(be.getSpeed()) > 0 && duration > 0) {
                effectiveRemainingTime -= (partialTicks * Mth.clamp(Math.abs(be.getSpeed()) / 32f, 1f, 128f));
            }
            progress = duration > 0 ? 1f - Mth.clamp(effectiveRemainingTime / duration, 0f, 1f) : (be.inventory.appliedRecipe ? 1f:0f) ;
        }


        float displayOffset;
        if (be.inventory.appliedRecipe) {
            displayOffset = (progress * 0.5f);
        } else {
            displayOffset = (1f - progress) * 0.5f;
        }
        if (be.getSpeed() == 0 && !isProcessing) displayOffset = 0f;

        int itemMoveDirection = (be.getSpeed() < 0) ? 1 : -1;
        displayOffset *= itemMoveDirection;


        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        ms.pushPose();
        if (alongLocalXRender) {
            ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90)); // Use com.mojang.math.Axis
        }

        ItemStack inputStack = be.inventory.getStackInSlot(0);
        if (!inputStack.isEmpty() && !be.inventory.appliedRecipe) {
            ms.pushPose();
            ms.translate(0.5, 0.05, 0.5 + displayOffset);
            renderSingleItem(ms, buffer, light, overlay, inputStack, itemRenderer);
            ms.popPose();
        }

        if (be.inventory.appliedRecipe) {
            List<ItemStack> outputStacks = new ArrayList<>();
            for (int i = 1; i < be.inventory.getSlots(); i++) {
                ItemStack stack = be.inventory.getStackInSlot(i);
                if (!stack.isEmpty()) outputStacks.add(stack);
            }

            if (!outputStacks.isEmpty()) {
                int outputCount = outputStacks.size();
                float totalWidthForOutputs = outputCount > 1 ? 0.4f : 0f;
                float spacing = outputCount > 1 ? totalWidthForOutputs / (outputCount - 1) : 0f;
                float startPos = 0.5f - totalWidthForOutputs / 2f;

                for (int i = 0; i < outputCount; i++) {
                    ItemStack outputStack = outputStacks.get(i);
                    ms.pushPose();
                    ms.translate(startPos + i * spacing, 0.05, 0.5 + displayOffset);
                    TransformStack.of(ms).nudge(i * 133);
                    renderSingleItem(ms, buffer, light, overlay, outputStack, itemRenderer);
                    ms.popPose();
                }
            }
        }
        ms.popPose();
    }

    private void renderSingleItem(PoseStack ms, MultiBufferSource buffer, int light, int overlay, ItemStack stack, ItemRenderer itemRenderer) {
        BakedModel modelWithOverrides = itemRenderer.getModel(stack, null, null, 0);
        boolean blockItem = modelWithOverrides.isGui3d();

        ms.pushPose();
        ms.translate(0, blockItem ? (0.925f / 2f) : ((13f / 16f) / 2f), 0);
        ms.scale(0.5f, 0.5f, 0.5f);
        if(!blockItem) ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90)); // Use com.mojang.math.Axis

        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, modelWithOverrides);
        ms.popPose();
    }

    protected SuperByteBuffer getRotatedShaftModel(KineticBlockEntity be, BlockState state) {
        if (state.getValue(FACING).getAxis().isHorizontal()) {
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                    state.rotate(Rotation.CLOCKWISE_180));
        }
        return CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, getRenderedBlockState(be));
    }

    protected BlockState getRenderedBlockState(KineticBlockEntity be) {
        return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be));
    }
}