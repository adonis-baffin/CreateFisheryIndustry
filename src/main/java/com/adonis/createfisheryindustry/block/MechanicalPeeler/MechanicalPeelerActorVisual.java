package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;



public class MechanicalPeelerActorVisual extends ActorVisual {
    private final RotatingInstance shaft;

    public MechanicalPeelerActorVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
        super(visualizationContext, simulationWorld, movementContext);

        var state = movementContext.state;
        var localPos = movementContext.localPos;
        shaft = MechanicalPeelerVisual.shaftInstance(instancerProvider, state);

        var rotationActualAxis = KineticBlockEntityVisual.rotationAxis(state);
        shaft.setRotationAxis(rotationActualAxis)
                .setRotationOffset(KineticBlockEntityVisual.rotationOffset(state, rotationActualAxis, localPos))
                .setPosition(localPos)
                .light(localBlockLight(), 0)
                .setChanged();
    }

    @Override
    protected void _delete() {
        shaft.delete();
    }
}