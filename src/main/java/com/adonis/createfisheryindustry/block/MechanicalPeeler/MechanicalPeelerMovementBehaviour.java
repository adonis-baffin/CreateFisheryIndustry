package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.kinetics.saw.SawRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MechanicalPeelerMovementBehaviour implements MovementBehaviour {

    @Override
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld, ContraptionMatrices matrices, MultiBufferSource buffer) {
        BlockState state = context.state;
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);

        Vec3 facingVec = Vec3.atLowerCornerOf(facing.getNormal());
        facingVec = context.rotation.apply(facingVec);

        Direction closestToFacing = Direction.getNearest(facingVec.x, facingVec.y, facingVec.z);
        boolean horizontal = closestToFacing.getAxis().isHorizontal();
        boolean backwards = VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite());
        boolean moving = context.getAnimationSpeed() != 0;
        boolean shouldAnimate = (!context.contraption.stalled && !backwards && moving);

        SuperByteBuffer superBuffer;
        if (MechanicalPeelerBlock.isHorizontal(state)) {
            if (shouldAnimate)
                superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_ACTIVE, state);
            else
                superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_INACTIVE, state);
        } else {
            if (shouldAnimate)
                superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_ACTIVE, state);
            else
                superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_INACTIVE, state);
        }

        superBuffer.transform(matrices.getModel())
                .center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(AngleHelper.verticalAngle(facing));

        if (!MechanicalPeelerBlock.isHorizontal(state)) {
            superBuffer.rotateZDegrees(state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE) ? 90 : 0);
        }

        superBuffer.uncenter()
                .light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                .useLevelLight(context.world, matrices.getWorld())
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.cutoutMipped()));
    }
}