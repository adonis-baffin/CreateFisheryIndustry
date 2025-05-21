package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.simibubi.create.AllPartialModels;
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

import java.util.function.Consumer;

public class MechanicalPeelerVisual extends KineticBlockEntityVisual<MechanicalPeelerBlockEntity> {

    protected final RotatingInstance rotatingModel;

    public MechanicalPeelerVisual(VisualizationContext context, MechanicalPeelerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        rotatingModel = shaftInstance(instancerProvider(), blockState)
                .setup(blockEntity)
                .setPosition(getVisualPosition());
        rotatingModel.setChanged();
    }

    public static RotatingInstance shaftInstance(InstancerProvider instancerProvider, BlockState state) {
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
        Direction.Axis facingAxis = facing.getAxis();

        if (facingAxis.isHorizontal()) {
            Direction align = facing.getOpposite();
            return instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance()
                    .rotateTo(0, 0, 1, align.getStepX(), align.getStepY(), align.getStepZ());
        } else {
            return instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
                    .createInstance()
                    .rotateToFace(state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE) ? Axis.X : Axis.Z);
        }
    }

    private void updateRotatingModelState() {
    }

    @Override
    public void update(float pt) {
        rotatingModel.setup(blockEntity)
                .setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
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