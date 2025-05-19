package com.adonis.createfisheryindustry.client.renderer;

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
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalPeelerRenderer extends SafeBlockEntityRenderer<MechanicalPeelerBlockEntity> {

    public MechanicalPeelerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        renderBlade(be, ms, buffer, light);
        renderItems(be, partialTicks, ms, buffer, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        renderShaft(be, ms, buffer, light, overlay);
    }

    protected void renderBlade(MechanicalPeelerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState blockState = be.getBlockState();
        PartialModel partial;
        float speed = be.getSpeed();
        boolean rotate = false;

        if (MechanicalPeelerBlock.isHorizontal(blockState)) {
            if (speed > 0) partial = CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_ACTIVE;
            else if (speed < 0) partial = CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_REVERSED;
            else partial = CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_INACTIVE;
        } else {
            if (speed > 0) partial = CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_ACTIVE;
            else if (speed < 0) partial = CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_REVERSED;
            else partial = CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_INACTIVE;
            if (blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE)) rotate = true;
        }

        SuperByteBuffer superBuffer = CachedBuffers.partialFacing(partial, blockState);
        if (rotate) superBuffer.rotateCentered(Axis.YP.rotationDegrees(90));
        superBuffer.color(0xFFFFFF).light(light).renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        KineticBlockEntityRenderer.renderRotatingBuffer(be, getRotatedShaftModel(be), ms,
                buffer.getBuffer(RenderType.solid()), light);
    }

    protected void renderItems(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP)
            return;

        boolean inputEmpty = be.inputInventory.getStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT).isEmpty();
        boolean outputTempPrimaryEmpty = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty();

        ItemStack animatedStack;
        float duration;
        float remainingTime;
        boolean appliedRecipe;

        if (!outputTempPrimaryEmpty) {
            animatedStack = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP);
            duration = be.inputInventory.recipeDuration;
            remainingTime = be.inputInventory.remainingTime;
            appliedRecipe = true;
        } else if (!inputEmpty) {
            animatedStack = be.inputInventory.getStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT);
            duration = be.inputInventory.recipeDuration;
            remainingTime = be.inputInventory.remainingTime;
            appliedRecipe = be.inputInventory.appliedRecipe;
        } else {
            return;
        }

        if (animatedStack.isEmpty()) {
            return;
        }

        boolean alongZ = !be.getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        boolean moving = duration != 0;
        float offset = moving ? remainingTime / duration : 0;
        float processingSpeed = Mth.clamp(Math.abs(be.getSpeed()) / 32f, 1, 128);

        if (moving) {
            offset = Mth.clamp(offset + ((-partialTicks + .5f) * processingSpeed) / duration, 0.125f, 1f);
            if (!appliedRecipe) offset += 1;
            offset /= 2;
        }

        if (be.getSpeed() == 0) offset = .5f;
        if (be.getSpeed() < 0 ^ alongZ) offset = 1 - offset;

        ms.pushPose();
        if (alongZ) ms.mulPose(Axis.YP.rotationDegrees(90));
        ms.translate(0.5, 0, offset);
        if (alongZ) ms.translate(-1, 0, 0);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel modelWithOverrides = itemRenderer.getModel(animatedStack, be.getLevel(), null, 0);
        boolean blockItem = modelWithOverrides.isGui3d();

        ms.pushPose();
        ms.translate(0, blockItem ? .925f : 13f / 16f, 0);
        ms.scale(.5f, .5f, .5f);
        if (!blockItem) ms.mulPose(Axis.XP.rotationDegrees(90));
        itemRenderer.render(animatedStack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, modelWithOverrides);
        ms.popPose();

        ms.popPose();
    }

    protected SuperByteBuffer getRotatedShaftModel(KineticBlockEntity be) {
        BlockState state = be.getBlockState();
        if (state.getValue(MechanicalPeelerBlock.FACING).getAxis().isHorizontal())
            return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                    state.rotate(be.getLevel(), be.getBlockPos(), Rotation.CLOCKWISE_180));
        return CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, getRenderedBlockState(be));
    }

    protected BlockState getRenderedBlockState(KineticBlockEntity be) {
        return KineticBlockEntityRenderer.shaft(KineticBlockEntityRenderer.getRotationAxisOf(be));
    }
}