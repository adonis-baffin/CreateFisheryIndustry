package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.KineticDebugger;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.instance.InstancerProvider;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.BlockState;
// 移除 BlockStateProperties.FACING 的导入，我们将使用 MechanicalPeelerBlock.FACING

import java.util.function.Consumer;

public class MechanicalPeelerVisual extends KineticBlockEntityVisual<MechanicalPeelerBlockEntity> {

    protected final RotatingInstance rotatingModel;

    public MechanicalPeelerVisual(VisualizationContext context, MechanicalPeelerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        // instancerProvider() 是父类 KineticBlockEntityVisual 提供的
        rotatingModel = shaftInstance(instancerProvider(), blockState)
                .setup(blockEntity) // setup 会处理速度、颜色（如果调试开启）、旋转偏移等
                .setPosition(getVisualPosition()); // 设置在世界中的位置
        rotatingModel.setChanged();
    }

    // 与 SawVisual.shaft 方法保持高度一致
    public static RotatingInstance shaftInstance(InstancerProvider instancerProvider, BlockState state) {
        // 使用 MechanicalPeelerBlock 中定义的 FACING 和 AXIS_ALONG_FIRST_COORDINATE 属性
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
        Direction.Axis facingAxis = facing.getAxis();

        if (facingAxis.isHorizontal()) {
            Direction align = facing.getOpposite(); // 轴应该与facing相反
            return instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance()
                    // rotateTo(angle, axisX, axisY, axisZ) - Saw这里用的是(0,0,1)作为参考轴旋转到align
                    // 这通常意味着SHAFT_HALF模型默认是沿着某个特定方向（比如Z轴），然后通过这个旋转对齐
                    .rotateTo(0, 0, 1, align.getStepX(), align.getStepY(), align.getStepZ());
        } else { // 垂直方向 (UP or DOWN)
            return instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
                    .createInstance()
                    // AXIS_ALONG_FIRST_COORDINATE 决定了垂直轴是沿X轴还是Z轴
                    .rotateToFace(state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE) ? Axis.X : Axis.Z);
        }
    }

    // 这个方法在原版SawVisual中没有，但在你的代码中有，保持它以更新旋转状态
    // KineticBlockEntityVisual 的 update 方法会调用 setupSources，其中会处理速度等
    // SawVisual 的 update 直接调用 rotatingModel.setup(blockEntity).setChanged();
    // 我们的 rotatingModel.setup(blockEntity) 已经在构造函数和 update 中调用
    private void updateRotatingModelState() {
        // setup(blockEntity) 已经包含了速度、颜色和旋转偏移的设置
        // rotatingModel.setup(blockEntity); // 可以在 update 中再次调用以确保所有状态最新

        // 如果 KineticDebugger.isActive()，setup 会处理颜色
        // rotatingModel.setChanged(); // setup 内部通常会调用 setChanged
    }

    @Override
    public void update(float pt) {
        // 与 SawVisual 一致，直接调用 setup，它会处理速度、旋转偏移、颜色等
        rotatingModel.setup(blockEntity)
                .setChanged();
        // updateRotatingModelState(); // 这个方法的内容现在由 rotatingModel.setup(blockEntity) 覆盖
    }

    @Override
    public void updateLight(float partialTick) {
        // relight 是 KineticBlockEntityVisual 的方法，用于重新计算光照
        relight(rotatingModel);
    }

    @Override
    protected void _delete() {
        rotatingModel.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(rotatingModel);
    }
}