package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 使用你代码中已有的 Catnip 导入
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;

public class MechanicalPeelerActorVisual extends ActorVisual {

    static final float PIVOT_OFFSET_UNITS = 8f;
    static final float ORIGIN_OFFSET = 1 / 16f;

    protected TransformedInstance rotatingPartInstance;
    protected TransformedInstance shaftInstance;

    private final BlockState blockState;
    private final Direction facing;
    private final Axis kineticAxis;
    private final boolean axisAlongFirst;
    private final boolean flipped;

    private double bladeRotationProgress;
    private double previousBladeRotationProgress;

    public MechanicalPeelerActorVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
        super(visualizationContext, simulationWorld, movementContext);

        this.blockState = this.context.state;
        this.facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        this.axisAlongFirst = blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        this.flipped = blockState.getValue(MechanicalPeelerBlock.FLIPPED);

        MechanicalPeelerBlock block = (MechanicalPeelerBlock) blockState.getBlock();
        this.kineticAxis = block.getRotationAxis(blockState);

        // 传动杆设置
        PartialModel shaftModel = getShaftModelForContraption();
        shaftInstance = instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel))
                .createInstance();
        shaftInstance.light(localBlockLight());
        setupStaticShaftTransform();

        // 刀片设置
        rotatingPartInstance = instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(CreateFisheryPartialModels.THRESHER_BLADE))
                .createInstance();
        rotatingPartInstance.light(localBlockLight());

        this.bladeRotationProgress = 0;
        this.previousBladeRotationProgress = 0;
    }

    private PartialModel getShaftModelForContraption() {
        return MechanicalPeelerBlock.isHorizontal(this.blockState) ? AllPartialModels.SHAFT_HALF : AllPartialModels.SHAFT;
    }

    private void setupStaticShaftTransform() {
        if (shaftInstance == null) return;

        shaftInstance.setIdentityTransform()
                .translate(context.localPos);

        if (MechanicalPeelerBlock.isHorizontal(blockState)) {
            // 水平朝向：完全模仿静态渲染器的逻辑
            // 静态渲染器使用：CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, blockState, oppositeFacing)
            Direction oppositeFacing = facing.getOpposite();

            // 模仿 partialFacing 的行为
            shaftInstance.center();

            // 根据oppositeFacing方向进行旋转，完全按照静态渲染器的逻辑
            if (oppositeFacing == Direction.NORTH) {
                shaftInstance.rotateYDegrees(0).rotateXDegrees(180);
            } else if (oppositeFacing == Direction.SOUTH) {
                shaftInstance.rotateYDegrees(180).rotateXDegrees(180);
            } else if (oppositeFacing == Direction.WEST) {
                shaftInstance.rotateYDegrees(90).rotateXDegrees(180);
            } else if (oppositeFacing == Direction.EAST) {
                shaftInstance.rotateYDegrees(-90).rotateXDegrees(180);
            }

            shaftInstance.uncenter();

        } else {
            // 垂直朝向：完全模仿静态渲染器的逻辑
            // 静态渲染器使用：CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, KineticBlockEntityRenderer.shaft(axis))
            shaftInstance.center();

            if (kineticAxis == Axis.X) {
                shaftInstance.rotateZDegrees(90); // Y轴模型转到X轴
            } else if (kineticAxis == Axis.Z) {
                shaftInstance.rotateXDegrees(90); // Y轴模型转到Z轴
            }
            // 如果kineticAxis是Y，则不需要旋转

            shaftInstance.uncenter();
        }

        shaftInstance.setChanged();
    }

    @Override
    public void tick() {
        super.tick();

        // 动态结构中不进行旋转计算，保持刀片静止
        previousBladeRotationProgress = 0;
        bladeRotationProgress = 0;
    }

    protected float getBladeRenderAngle() {
        // 动态结构中返回固定角度0，保持刀片静止
        return 0f;
    }

    @Override
    public void beginFrame() {
        float bladeRenderAngle = getBladeRenderAngle();
        renderBladeInstance(bladeRenderAngle);
    }

    private void renderBladeInstance(float renderAngle) {
        if (rotatingPartInstance == null) return;

        rotatingPartInstance.setIdentityTransform()
                .translate(context.localPos);

        Vec3 pivot = new Vec3(0.5f, 0.5f, 0.5f);

        if (MechanicalPeelerBlock.isHorizontal(blockState)) {
            // 水平朝向处理
            rotatingPartInstance.center()
                    .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                    .uncenter();

            // 根据传动杆轴向确定pivot和旋转轴
            if (kineticAxis == Axis.X) {
                // 东西走向的传动杆
                pivot = new Vec3(0.5f, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET);
            } else { // kineticAxis == Axis.Z
                // 南北走向的传动杆
                pivot = new Vec3(PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, 0.5f);
            }

            rotatingPartInstance.translate(pivot);
            // 刀片应该绕传动杆轴旋转
            applyRotation(rotatingPartInstance, renderAngle, kineticAxis);
            rotatingPartInstance.translateBack(pivot);

        } else {
            // 垂直朝向处理
            Direction bladeHorizontalFacing;
            if (kineticAxis == Axis.X) {
                bladeHorizontalFacing = flipped ? Direction.SOUTH : Direction.NORTH;
            } else { // kineticAxis == Axis.Z
                bladeHorizontalFacing = flipped ? Direction.WEST : Direction.EAST;
            }

            rotatingPartInstance.center()
                    .rotateYDegrees(AngleHelper.horizontalAngle(bladeHorizontalFacing));

            if (facing == Direction.DOWN) {
                rotatingPartInstance.rotateXDegrees(180);
            }
            rotatingPartInstance.uncenter();

            // 设置pivot点
            if (kineticAxis == Axis.Z) {
                pivot = new Vec3(0.5f, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, 0.5f);
            } else { // kineticAxis == Axis.X
                pivot = new Vec3(0.5f, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET);
            }

            rotatingPartInstance.translate(pivot);
            // 刀片绕传动杆轴旋转（与传动杆轴一致）
            applyRotation(rotatingPartInstance, renderAngle, kineticAxis);
            rotatingPartInstance.translateBack(pivot);
        }

        rotatingPartInstance.setChanged();
    }

    private void applyRotation(TransformedInstance instance, float angle, Axis axis) {
        if (axis == Axis.X) {
            instance.rotateXDegrees(angle);
        } else if (axis == Axis.Y) {
            instance.rotateYDegrees(angle);
        } else if (axis == Axis.Z) {
            instance.rotateZDegrees(angle);
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