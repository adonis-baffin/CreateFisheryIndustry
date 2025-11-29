package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import net.createmod.catnip.math.AngleHelper;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalPeelerActorVisual extends ActorVisual {

    TransformedInstance thresherBlade;
    TransformedInstance shaft;
    private final Direction facing;
    private final Axis kineticShaftAxis;
    private final boolean axisAlongFirst;
    private final boolean flipped;

    private double rotation;
    private double previousRotation;

    // 用于粒子效果的pivot偏移
    private static final float PIVOT_OFFSET = 1 / 16f;

    public MechanicalPeelerActorVisual(VisualizationContext visualizationContext, VirtualRenderWorld contraption, MovementContext context) {
        super(visualizationContext, contraption, context);

        BlockState state = context.state;
        facing = state.getValue(MechanicalPeelerBlock.FACING);
        axisAlongFirst = state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        flipped = state.getValue(MechanicalPeelerBlock.FLIPPED);

        // 计算动力学轴 - 与静态渲染器保持一致
        kineticShaftAxis = getKineticShaftAxis(state);

        // 创建刀片实例
        thresherBlade = instancerProvider.instancer(InstanceTypes.TRANSFORMED,
                        Models.partial(CreateFisheryPartialModels.THRESHER_BLADE))
                .createInstance();

        // 创建传动杆实例
        if (facing.getAxis().isHorizontal()) {
            // 水平朝向使用半轴
            shaft = instancerProvider.instancer(InstanceTypes.TRANSFORMED,
                            Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance();
        } else {
            shaft = instancerProvider.instancer(InstanceTypes.TRANSFORMED,
                            Models.partial(AllPartialModels.SHAFT))
                    .createInstance();
        }
    }

    private Axis getKineticShaftAxis(BlockState state) {
        // 与 MechanicalPeelerBlock.getRotationAxis() 保持一致
        if (facing.getAxis().isHorizontal()) {
            return facing.getAxis();
        } else {
            return axisAlongFirst ? Axis.X : Axis.Z;
        }
    }

    @Override
    public void tick() {
        previousRotation = rotation;

        if (context.disabled ||
                VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite()))
            return;

        float deg = context.getAnimationSpeed();
        rotation += deg / 20;
        rotation %= 360;
    }

    @Override
    public void beginFrame() {
        float angle = (float) getRotation();

        // 渲染刀片 - 完全按照静态渲染器的逻辑
        renderBlade(angle);

        // 渲染传动杆
        renderShaft(angle);
    }

    private void renderBlade(float angle) {
        thresherBlade.setIdentityTransform()
                .translate(context.localPos);

        if (facing.getAxis().isHorizontal()) {
            // 水平朝向 - 参考静态渲染器的水平渲染逻辑
            thresherBlade.center()
                    .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                    .uncenter();  // 先完成朝向旋转

            // 应用pivot偏移和旋转 - 与静态渲染器一致
            float rotOffsetY = PIVOT_OFFSET * 8f;
            float rotOffsetZ = PIVOT_OFFSET * 8f;

            thresherBlade.translate(0, rotOffsetY, rotOffsetZ)
                    .rotateXDegrees(angle)
                    .translate(0, -rotOffsetY, -rotOffsetZ);
        } else {
            // 垂直朝向 - 完全按照静态渲染器逻辑
            Direction horizontalFacing;
            if (kineticShaftAxis == Axis.X) {
                horizontalFacing = flipped ? Direction.SOUTH : Direction.NORTH;
            } else {
                horizontalFacing = flipped ? Direction.WEST : Direction.EAST;
            }

            // 使用partialFacingVertical的逻辑
            thresherBlade.center()
                    .rotateYDegrees(AngleHelper.horizontalAngle(horizontalFacing));

            if (facing == Direction.DOWN) {
                thresherBlade.rotateXDegrees(180);
            }

            if (kineticShaftAxis == Axis.X) {
                // 假设这个分支目前是正确的
                thresherBlade.rotateXDegrees(angle);
            } else { // kineticShaftAxis == Axis.Z
                // 你观察到：当前 kineticShaftAxis == Axis.Z 时，
                // `thresherBlade.rotateZDegrees(angle);` 导致绕X轴旋转。
                // 我们需要它绕Z轴旋转。
                // 如果一个操作 (rotateZ) 产生了X的效果，那么另一个操作 (rotateX) 是否会产生Z的效果？
                // 这值得一试。
                thresherBlade.rotateXDegrees(angle); // **** 关键修改点 ****
            }

            thresherBlade.uncenter();
        }

        thresherBlade.setChanged();
    }

    private void renderShaft(float angle) {
        shaft.setIdentityTransform()
                .translate(context.localPos);

        if (facing.getAxis().isHorizontal()) {
            // 水平朝向的半轴渲染 - 朝向传动杆连接的反方向
            Direction oppositeFacing = facing.getOpposite();

            shaft.center();

            shaft.rotateToFace(facing);

            shaft.rotateZDegrees(angle);

            shaft.uncenter();
        } else {
            // 垂直朝向的完整轴渲染
            shaft.center();

            if (kineticShaftAxis == Axis.X) {
                // X轴：需要旋转90度使轴沿X方向
                shaft.rotateZDegrees(90);
                // 然后绕新的轴旋转
                shaft.rotateYDegrees(-angle);  // 负号是因为坐标系的差异
            } else if (kineticShaftAxis == Axis.Z) {
                // Z轴：默认模型已经是Y轴方向，需要旋转到Z方向
                shaft.rotateXDegrees(90);
                // 然后绕新的轴旋转
                shaft.rotateYDegrees(angle);
            }

            shaft.uncenter();
        }

        shaft.setChanged();
    }

    protected double getRotation() {
        return AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks(), previousRotation, rotation);
    }

    @Override
    protected void _delete() {
        thresherBlade.delete();
        shaft.delete();
    }
}