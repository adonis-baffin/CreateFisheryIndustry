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
import net.createmod.catnip.animation.AnimationTickHolder; // 仅用于刀片旋转
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;


public class MechanicalPeelerActorVisual extends ActorVisual {

    static final float PIVOT_OFFSET_UNITS = 8f;
    static final float ORIGIN_OFFSET = 1 / 16f;

    protected TransformedInstance rotatingPartInstance; // 刀片仍然需要旋转
    protected TransformedInstance shaftInstance;    // 传动杆在Actor中应该是不动的

    private final BlockState blockState;
    private final Direction facing;
    private final Axis kineticAxis; // 这个轴更多是用于刀片的旋转，以及传动杆的初始对齐
    private final boolean axisAlongFirst;
    private final boolean flipped;

    // 传动杆不再需要自己的旋转进度
    private double bladeRotationProgress; // 只为刀片保留旋转进度
    private double previousBladeRotationProgress;


    public MechanicalPeelerActorVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
        super(visualizationContext, simulationWorld, movementContext);

        this.blockState = this.context.state;
        this.facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        this.axisAlongFirst = blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        this.flipped = blockState.getValue(MechanicalPeelerBlock.FLIPPED);

        MechanicalPeelerBlock block = (MechanicalPeelerBlock) blockState.getBlock();
        this.kineticAxis = block.getRotationAxis(blockState);

        // --- 传动杆设置 (只在构造函数中设置一次变换) ---
        PartialModel shaftModel = getShaftModelForContraption();
        shaftInstance = instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(shaftModel))
                .createInstance();
        shaftInstance.light(localBlockLight());
        // 在构造函数中设置传动杆的最终静态变换
        setupStaticShaftTransform();


        // --- 刀片设置 ---
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


        shaftInstance.center();
        if (MechanicalPeelerBlock.isHorizontal(blockState)) {

            Direction shaftConnectionDirection = facing.getOpposite();
            float horizontalAngle = AngleHelper.horizontalAngle(shaftConnectionDirection);
            shaftInstance.rotateYDegrees(horizontalAngle); // 对齐水平方向
            Axis shaftModelPrimaryAxis = Axis.Y; // 假设SHAFT_HALF的长度是Y轴
            if (kineticAxis == Axis.X && shaftModelPrimaryAxis == Axis.Y) shaftInstance.rotateZDegrees(90);
            else if (kineticAxis == Axis.Z && shaftModelPrimaryAxis == Axis.Y) shaftInstance.rotateXDegrees(90);

            if (facing.getOpposite() == Direction.NORTH) shaftInstance.rotateYDegrees(0);
            else if (facing.getOpposite() == Direction.SOUTH) shaftInstance.rotateYDegrees(180);
            else if (facing.getOpposite() == Direction.WEST) shaftInstance.rotateYDegrees(90);
            else if (facing.getOpposite() == Direction.EAST) shaftInstance.rotateYDegrees(-90);

            alignModelToShaftDirection(shaftInstance, facing.getOpposite());


        } else {
            // 垂直完整轴，AllPartialModels.SHAFT 默认是 Y 轴
            if (kineticAxis == Axis.X) {
                shaftInstance.rotateZDegrees(90); // Y 轴模型转到 X 轴
            } else if (kineticAxis == Axis.Z) {
                shaftInstance.rotateXDegrees(90); // Y 轴模型转到 Z 轴
            }
            // 如果 kineticAxis 是 Y，则不需要初始旋转轴向
        }
        shaftInstance.uncenter();
        shaftInstance.setChanged(); // 设置一次即可
    }

    // 简化的对齐方法，假设模型（如SHAFT_HALF）的长度沿其局部Y轴，连接端在-Y
    private void alignModelToShaftDirection(TransformedInstance instance, Direction targetDirection) {
        instance.rotateYDegrees(AngleHelper.horizontalAngle(targetDirection)); // 对齐水平
        if (targetDirection.getAxis().isHorizontal()) {
            instance.rotateXDegrees(-90); // 将Y轴长度的杆子放平
        } else if (targetDirection == Direction.DOWN) {
            instance.rotateXDegrees(180); // 如果是朝下，并且Y轴是向上，则翻转
        }

    }


    @Override
    public void tick() {
        super.tick();
        // 只更新刀片的旋转
        previousBladeRotationProgress = bladeRotationProgress;

        if (context.contraption.stalled || context.disabled) {
            return;
        }

        if (MechanicalPeelerBlock.isHorizontal(blockState) && VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite())) {
        }

        double radiusBlocks = (6.5 / 16.0);
        if (Math.abs(radiusBlocks) < 1e-5) return;

        double distanceMoved = context.motion.length();
        double angleChangeRad = distanceMoved / radiusBlocks;
        float angleChangeDeg = (float) net.createmod.catnip.math.AngleHelper.deg(angleChangeRad);

        float rotationDirection = 1.0f;
        if (this.kineticAxis == facing.getAxis() && facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
        }


        bladeRotationProgress += angleChangeDeg * 1.25f * rotationDirection;
        bladeRotationProgress %= 360;
    }

    protected float getBladeRenderAngle() {
        return (float) net.createmod.catnip.math.AngleHelper.angleLerp(AnimationTickHolder.getPartialTicks(), previousBladeRotationProgress, bladeRotationProgress);
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
            rotatingPartInstance.center()
                    .rotateYDegrees(net.createmod.catnip.math.AngleHelper.horizontalAngle(facing))
                    .uncenter();

            if (this.kineticAxis == Axis.X) {
                pivot = new Vec3(0.5f, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET);
            } else { // this.kineticAxis == Axis.Z
                pivot = new Vec3(PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, 0.5f);
            }

            rotatingPartInstance.translate(pivot);
            applyRotation(rotatingPartInstance, renderAngle, this.kineticAxis);
            rotatingPartInstance.translateBack(pivot);

        } else {
            // 垂直朝向 (UP/DOWN)
            Direction bladeHorizontalFacing;
            if (kineticAxis == Axis.X) {
                bladeHorizontalFacing = flipped ? Direction.SOUTH : Direction.NORTH;
            } else { // kineticAxis == Axis.Z
                bladeHorizontalFacing = flipped ? Direction.WEST : Direction.EAST;
            }

            rotatingPartInstance.center()
                    .rotateYDegrees(net.createmod.catnip.math.AngleHelper.horizontalAngle(bladeHorizontalFacing));

            if (facing == Direction.DOWN) {
                rotatingPartInstance.rotateXDegrees(180);
            }
            rotatingPartInstance.uncenter();


            // 垂直时的 Pivot 参考静态渲染器
            if (this.kineticAxis == Axis.Z) { // 静态: (0, 0.5, 0) relative to corner
                pivot = new Vec3(0.5f, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, 0.5f); // Simplified: center X, Z, offset Y
            } else { // kineticAxis == Axis.X. Static: (0, 0.5, 0.5) relative to corner
                pivot = new Vec3(0.5f, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET, PIVOT_OFFSET_UNITS * ORIGIN_OFFSET);
            }

            pivot = new Vec3(0.5, 0.5, 0.5);


            rotatingPartInstance.translate(pivot);
            applyRotation(rotatingPartInstance, renderAngle, this.kineticAxis);
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