package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

import static com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.getRotationAxisOf;
import static com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer.shaft;

public class MechanicalPeelerRenderer extends SafeBlockEntityRenderer<MechanicalPeelerBlockEntity> {
    public MechanicalPeelerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        renderRoller(be, ms, buffer, light);
        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        renderShaft(be, ms, buffer, light, overlay);
    }

    protected void renderRoller(MechanicalPeelerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        BlockState blockState = be.getBlockState();
        PartialModel partial;
        float speed = be.getSpeed();
        boolean rotate = false;

        if (MechanicalPeelerBlock.isHorizontal(blockState)) {
            if (speed > 0) {
                partial = CreateFisheryPartialModels.ROLLER_HORIZONTAL_ACTIVE;
            } else if (speed < 0) {
                partial = CreateFisheryPartialModels.ROLLER_HORIZONTAL_ACTIVE; // 暂无反向模型，复用正向
            } else {
                partial = CreateFisheryPartialModels.ROLLER_HORIZONTAL_INACTIVE;
            }
        } else {
            if (speed > 0) {
                partial = CreateFisheryPartialModels.ROLLER_VERTICAL_ACTIVE;
            } else if (speed < 0) {
                partial = CreateFisheryPartialModels.ROLLER_VERTICAL_ACTIVE; // 暂无反向模型，复用正向
            } else {
                partial = CreateFisheryPartialModels.ROLLER_VERTICAL_INACTIVE;
            }
            if (blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE)) {
                rotate = true;
            }
        }

        SuperByteBuffer superBuffer = CachedBuffers.partialFacing(partial, blockState);
        if (rotate) {
            superBuffer.rotateCentered(AngleHelper.rad(90), Direction.UP);
        }
        superBuffer.color(0xFFFFFF)
                .light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        KineticBlockEntityRenderer.renderRotatingBuffer(be, getRotatedModel(be), ms, buffer.getBuffer(RenderType.solid()), light);
    }

    protected SuperByteBuffer getRotatedModel(MechanicalPeelerBlockEntity be) {
        BlockState state = be.getBlockState();
        if (MechanicalPeelerBlock.isHorizontal(state)) {
            return CachedBuffers.partialFacing(
                    com.simibubi.create.AllPartialModels.SHAFT_HALF,
                    state.rotate(be.getLevel(), be.getBlockPos(), Rotation.CLOCKWISE_180)
            );
        }
        return CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, shaft(getRotationAxisOf(be)));
    }
}