package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalPeelerActorVisual extends ActorVisual {

    protected TransformedInstance rotatingPartInstance;
    protected TransformedInstance shaftInstance;

    private final Direction facing;
    private final Axis kineticAxis;
    private final PartialModel shaftModel;

    private double currentRotationAngle;
    private double prevRotationAngle;

    public MechanicalPeelerActorVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
        super(visualizationContext, simulationWorld, movementContext);

        BlockState blockState = this.context.state;
        this.facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        this.kineticAxis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);
        this.shaftModel = getShaftModelForContraption();

        rotatingPartInstance = instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.HARVESTER_BLADE))
                .createInstance();
        rotatingPartInstance.light(localBlockLight());

        shaftInstance = instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(this.shaftModel))
                .createInstance();
        shaftInstance.light(localBlockLight());

        float initialDegreesPerTick = context.getAnimationSpeed() * 0.3f;
        float initialTimeTicks = AnimationTickHolder.getTicks(this.context.world);
        this.currentRotationAngle = (initialTimeTicks * initialDegreesPerTick) % 360.0;
        this.prevRotationAngle = this.currentRotationAngle;
    }

    private PartialModel getShaftModelForContraption() {
        // 区分上下朝向和水平朝向
        if (this.facing == Direction.UP || this.facing == Direction.DOWN) {
            // 上下朝向：使用完整传动杆
            return AllPartialModels.SHAFT;
        } else {
            // 水平朝向：使用半根传动杆
            return AllPartialModels.SHAFT_HALF;
        }
    }

    @Override
    public void tick() {
        prevRotationAngle = currentRotationAngle;

        if (this.context.disabled || this.context.contraption.stalled) {
            // 不增加旋转
        } else {
            float degreesPerTick = context.getAnimationSpeed() * 0.3f;
            currentRotationAngle += degreesPerTick;
        }
        currentRotationAngle %= 360.0;
    }

    protected double getRenderAngle() {
        return AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks(), prevRotationAngle, currentRotationAngle);
    }

    @Override
    public void beginFrame() {
        float renderAngle = (float) getRenderAngle();

        // 渲染传动杆
        if (shaftInstance != null) {
            shaftInstance.setIdentityTransform()
                    .translate(context.localPos);

            if (this.shaftModel == AllPartialModels.SHAFT_HALF) {
                // 水平朝向：使用半根传动杆
                shaftInstance.center()
                        .rotateToFace(this.facing)
                        .uncenter();
            } else {
                // 上下朝向：完整传动杆，需要根据传动杆轴向调整方向
                if (this.kineticAxis == Axis.X) {
                    shaftInstance.center()
                            .rotateZDegrees(90)
                            .uncenter();
                } else if (this.kineticAxis == Axis.Z) {
                    shaftInstance.center()
                            .rotateXDegrees(-90)
                            .uncenter();
                }
                // Y轴不需要额外旋转
            }

            // 应用传动杆旋转
            shaftInstance.center();
            if (this.kineticAxis == Axis.X) {
                shaftInstance.rotateXDegrees(renderAngle);
            } else if (this.kineticAxis == Axis.Y) {
                shaftInstance.rotateYDegrees(renderAngle);
            } else if (this.kineticAxis == Axis.Z) {
                shaftInstance.rotateZDegrees(renderAngle);
            }
            shaftInstance.uncenter();
        }

        // 渲染旋转刀片
        if (rotatingPartInstance != null) {
            rotatingPartInstance.setIdentityTransform()
                    .translate(context.localPos);

            // 首先调整刀片朝向
            rotatingPartInstance.center()
                    .rotateToFace(this.facing)
                    .uncenter();

            // 应用正确的旋转逻辑
            rotatingPartInstance.center();

            if (this.facing == Direction.UP || this.facing == Direction.DOWN) {
                // 上下朝向：永远以传动杆为轴旋转
                if (this.kineticAxis == Axis.X) {
                    rotatingPartInstance.rotateXDegrees(renderAngle);
                } else if (this.kineticAxis == Axis.Y) {
                    rotatingPartInstance.rotateYDegrees(renderAngle);
                } else if (this.kineticAxis == Axis.Z) {
                    rotatingPartInstance.rotateZDegrees(renderAngle);
                }
            } else {
                // 水平朝向：刀片旋转轴与传动杆旋转轴垂直
                if (this.facing == Direction.NORTH || this.facing == Direction.SOUTH) {
                    // 朝向南北时，旋转轴走向为东西（绕Y轴旋转）
                    rotatingPartInstance.rotateYDegrees(renderAngle);
                } else if (this.facing == Direction.EAST || this.facing == Direction.WEST) {
                    // 朝向东西时，旋转轴走向为南北（绕Z轴旋转）
                    rotatingPartInstance.rotateZDegrees(renderAngle);
                }
            }

            rotatingPartInstance.uncenter();
        }
    }

    @Override
    protected void _delete() {
        if (rotatingPartInstance != null) {
            rotatingPartInstance.delete();
            rotatingPartInstance = null;
        }
        if (shaftInstance != null) {
            shaftInstance.delete();
            shaftInstance = null;
        }
    }
}