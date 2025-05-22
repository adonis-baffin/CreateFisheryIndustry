//package com.adonis.createfisheryindustry.block.MechanicalPeeler;
//
//import com.simibubi.create.AllPartialModels;
//import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
//import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
//import com.simibubi.create.content.kinetics.base.RotatingInstance;
//import com.simibubi.create.foundation.render.AllInstanceTypes;
//import dev.engine_room.flywheel.api.instance.Instance;
//import dev.engine_room.flywheel.api.instance.InstancerProvider;
//import dev.engine_room.flywheel.api.visualization.VisualizationContext;
//import dev.engine_room.flywheel.lib.instance.InstanceTypes;
//import dev.engine_room.flywheel.lib.instance.TransformedInstance;
//import dev.engine_room.flywheel.lib.model.Models;
//import dev.engine_room.flywheel.lib.transform.TransformStack;
//import net.createmod.catnip.animation.AnimationTickHolder; // 需要这个
//import net.createmod.catnip.math.AngleHelper;
//import net.minecraft.core.Direction;
//import net.minecraft.core.Direction.Axis;
//import net.minecraft.world.level.block.state.BlockState;
//import net.minecraft.world.level.block.state.properties.BlockStateProperties; // 用于 RotatedPillarKineticBlock.AXIS
//import com.mojang.blaze3d.vertex.PoseStack;
//
//import java.util.function.Consumer;
//
//public class MechanicalPeelerVisual extends KineticBlockEntityVisual<MechanicalPeelerBlockEntity> {
//
//    protected final RotatingInstance rotatingShaftModel;
//    protected TransformedInstance rotatingPartInstance;
//
//    private double rotatingPartPrevAngle; // 用于平滑插值
//    private double rotatingPartCurrentAngle;
//
//
//    public MechanicalPeelerVisual(VisualizationContext context, MechanicalPeelerBlockEntity blockEntity, float partialTick) {
//        super(context, blockEntity, partialTick); // 调用父类构造，会初始化 instancerProvider
//
//        rotatingShaftModel = createShaftInstance(); // 修改：使用新的辅助方法
//        setupRotatingInstance(rotatingShaftModel, blockEntity);
//
//        rotatingPartInstance = instancerProvider.instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.HARVESTER_BLADE))
//                .createInstance();
//
//        rotatingPartInstance.light(localBlockLight()); // localBlockLight() 是从 KineticBlockEntityVisual 继承的
//        // 初始角度计算
//        this.rotatingPartCurrentAngle = getAngleForVisual(0); // 初始角度，partialTick为0
//        this.rotatingPartPrevAngle = this.rotatingPartCurrentAngle;
//
//        updateRotatingPartTransform(partialTick);
//    }
//
//    // 辅助方法：创建轴实例
//    private RotatingInstance createShaftInstance() {
//        BlockState state = this.blockState; // this.blockState 是从 KineticBlockEntityVisual 继承的
//        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
//        Axis kineticShaftAxis = state.getValue(BlockStateProperties.AXIS); // 使用 RotatedPillarKineticBlock.AXIS
//
//        RotatingInstance instance;
//        // AllInstanceTypes.ROTATING 是 Create 定义的
//        if (facing.getAxis().isHorizontal()) {
//            // 对于水平朝向的方块，轴通常是半轴，连接到其 FACING 的反方向
//            instance = instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
//                    .createInstance()
//                    .rotateToFace(facing.getOpposite());
//        } else { // 垂直
//            instance = instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
//                    .createInstance()
//                    .rotateToAxis(kineticShaftAxis); // 轴直接根据其 AXIS 属性确定方向
//        }
//        return instance;
//    }
//
//    // 辅助方法：设置旋转实例（通常用于轴）
//    protected void setupRotatingInstance(RotatingInstance instance, KineticBlockEntity blockEntity) {
//        // getRotationAxis(), getRotationOffset(), getRotationalSpeed(), getVisualPosition() 都是从 KineticBlockEntityVisual 继承的
//        instance.setRotationAxis(getRotationAxis())
//                .setRotationOffset(getRotationOffset())
//                .setRotationalSpeed(getRotationalSpeed(blockEntity)) // 传递 blockEntity
//                .setPosition(getVisualPosition())
//                .setChanged();
//    }
//
//    // 辅助方法：计算视觉旋转角度，用于平滑
//    private double getAngleForVisual(float partialTicks) {
//        float speed = getRotationalSpeed(this.blockEntity); // 从KBEV获取速度
//        float time = AnimationTickHolder.getRenderTime(this.blockEntity.getLevel());
//        float angleOffset = getRotationOffset(); // 从KBEV获取偏移
//
//        // 逻辑类似 KBEV 的 getRotation, 但不直接调用，因为我们需要控制 prev/current
//        // KBEV内部的rotation是基于当前时间的，不是平滑插值的目标。
//        // (time * speed * 3f / 10 + offset)
//        return (time * speed * 3f / 10f + angleOffset); // 直接返回当前时间的角度
//    }
//
//
//    private void updateRotatingPartTransform(float partialTicks) {
//        if (rotatingPartInstance == null) return;
//
//        // 使用插值后的角度
//        double interpolatedAngle = AngleHelper.angleLerp(partialTicks, rotatingPartPrevAngle, rotatingPartCurrentAngle);
//
//        Direction facing = this.blockState.getValue(MechanicalPeelerBlock.FACING);
//        Axis kineticRotationAxis = this.blockState.getValue(BlockStateProperties.AXIS);
//
//        PoseStack ms = new PoseStack();
//        TransformStack.of(ms)
//                .translate(getVisualPosition())
//                .center();
//
//        TransformStack.of(ms).rotateYDegrees(AngleHelper.horizontalAngle(facing));
//        TransformStack.of(ms).rotateXDegrees(AngleHelper.verticalAngle(facing));
//
//        if (kineticRotationAxis == Axis.X) {
//            TransformStack.of(ms).rotateXDegrees((float)interpolatedAngle);
//        } else if (kineticRotationAxis == Axis.Y) {
//            TransformStack.of(ms).rotateYDegrees((float)interpolatedAngle);
//        } else if (kineticRotationAxis == Axis.Z) {
//            TransformStack.of(ms).rotateZDegrees((float)interpolatedAngle);
//        }
//
//        TransformStack.of(ms).uncenter();
//
//        rotatingPartInstance.setTransform(ms);
//        rotatingPartInstance.setChanged();
//    }
//
//    @Override
//    public void tick() {
//        // super.tick(); // KBEV 的 tick 会更新 prevRotation 和 rotation，我们自己管理 rotatingPart 的
//        rotatingPartPrevAngle = rotatingPartCurrentAngle;
//        rotatingPartCurrentAngle = getAngleForVisual(0); // 更新当前tick的角度
//    }
//
//
//    @Override
//    public void beginFrame(float partialTick) { // KBEV 有这个方法，我们需要覆盖它
//        // super.beginFrame(partialTick); // 调用父类以更新轴
//        setupRotatingInstance(rotatingShaftModel, this.blockEntity); // 更新轴的变换
//        updateRotatingPartTransform(partialTick); // 更新刀片的变换
//    }
//
//
//    @Override
//    public void updateLight(float partialTick) {
//        relight(rotatingShaftModel);
//        if (rotatingPartInstance != null) {
//            relight(rotatingPartInstance);
//        }
//    }
//
//    @Override
//    protected void _delete() {
//        rotatingShaftModel.delete();
//        if (rotatingPartInstance != null) {
//            rotatingPartInstance.delete();
//        }
//    }
//
//    @Override
//    public void collectCrumblingInstances(Consumer<Instance> consumer) {
//        consumer.accept(rotatingShaftModel);
//        if (rotatingPartInstance != null) {
//            consumer.accept(rotatingPartInstance);
//        }
//    }
//}