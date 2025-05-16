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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

public class MechanicalPeelerVisual extends KineticBlockEntityVisual<MechanicalPeelerBlockEntity> {

    protected final RotatingInstance rotatingModel;

    public MechanicalPeelerVisual(VisualizationContext context, MechanicalPeelerBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);
        InstancerProvider instancerProvider = context.instancerProvider();
        rotatingModel = shaftInstance(instancerProvider, blockState)
                .setPosition(getVisualPosition()); // Set initial position from BE's position
        updateRotatingModelState(); // Apply initial speed, offset, and animation axis
    }

    public static RotatingInstance shaftInstance(InstancerProvider instancerProvider, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.FACING); // Overall facing of the block
        RotatingInstance instance;

        if (facing.getAxis().isHorizontal()) { // Block is placed horizontally
            Direction shaftConnectionSide = facing.getOpposite(); // Shaft connects to the back
            instance = instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT_HALF))
                    .createInstance();

            // Set the static (non-animated) orientation of the SHAFT_HALF model.
            // SHAFT_HALF model's default "front" or "connection end" is usually its local +Z.
            // We rotate this local +Z to align with the `shaftConnectionSide`.
            instance.rotateTo(0, 0, 1, // From local +Z
                    shaftConnectionSide.getStepX(), // To world direction X
                    shaftConnectionSide.getStepY(), // To world direction Y
                    shaftConnectionSide.getStepZ());// To world direction Z

            // The animation (rotation) of the shaft will be around the block's FACING axis.
            instance.setRotationAxis(facing.getAxis());

        } else { // Block is placed vertically (FACING is UP or DOWN)
            instance = instancerProvider.instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.SHAFT))
                    .createInstance();

            // 1. Determine the visual orientation of the SHAFT model (which way it "lies").
            //    This is based on AXIS_ALONG_FIRST_COORDINATE.
            //    If true, SHAFT model should visually lie along X. If false, along Z.
            //    `rotateToFace` rotates the model's local Y (its length) to align with the given world axis.
            Axis visualShaftOrientationAxis = state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE) ? Axis.X : Axis.Z;
            instance.rotateToFace(visualShaftOrientationAxis);

            // 2. The animation axis is determined by the block's getRotationAxis() method.
            //    For vertical DAKB: if AXIS_ALONG_FIRST_COORDINATE is true, animation is around Z. If false, around X.
            //    This is set in updateRotatingModelState() via rotatingModel.setup() logic or direct setRotationAxis.
            //    No need to set it here again as updateRotatingModelState will be called.
        }
        return instance;
    }

    private void updateRotatingModelState() {
        // This axis is the actual axis of kinetic rotation for the animation.
        Axis currentAnimationAxis = KineticBlockEntityVisual.rotationAxis(blockState);

        rotatingModel.setRotationAxis(currentAnimationAxis)
                .setRotationalSpeed(blockEntity.getSpeed() * RotatingInstance.SPEED_MULTIPLIER)
                .setRotationOffset(
                        KineticBlockEntityVisual.rotationOffset(blockState, currentAnimationAxis, blockEntity.getBlockPos()) +
                                blockEntity.getRotationAngleOffset(currentAnimationAxis)
                );

        if (KineticDebugger.isActive()) {
            rotatingModel.setColor(blockEntity);
        }
    }

    @Override
    public void update(float pt) {
        updateRotatingModelState();
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