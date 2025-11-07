package com.adonis.createfisheryindustry.block.FishingBearing;

import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.OrientedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.function.Consumer;

public class FishingBearingVisual extends OrientedRotatingVisual<FishingBearingBlockEntity> implements SimpleDynamicVisual {
    final OrientedInstance topInstance;
    final Axis rotationAxis;
    final Quaternionf blockOrientation;

    public FishingBearingVisual(VisualizationContext context, FishingBearingBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick, Direction.SOUTH,
                blockEntity.getBlockState().getValue(BlockStateProperties.FACING).getOpposite(),
                Models.partial(AllPartialModels.SHAFT_HALF));

        Direction facing = blockState.getValue(BlockStateProperties.FACING);
        rotationAxis = Axis.of(Direction.get(Direction.AxisDirection.POSITIVE, rotationAxis()).step());
        blockOrientation = getBlockStateOrientation(facing);

        // 🎯 关键：使用你的自定义模型
        topInstance = instancerProvider().instancer(InstanceTypes.ORIENTED,
                        Models.partial(CreateFisheryPartialModels.FISHING_BEARING_TOP))
                .createInstance();

        topInstance.position(getVisualPosition())
                .rotation(blockOrientation)
                .setChanged();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        float interpolatedAngle = blockEntity.getInterpolatedAngle(ctx.partialTick() - 1);
        Quaternionf rot = rotationAxis.rotationDegrees(interpolatedAngle);
        rot.mul(blockOrientation);
        topInstance.rotation(rot).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        super.updateLight(partialTick);
        relight(topInstance);
    }

    @Override
    protected void _delete() {
        super._delete();
        topInstance.delete();
    }

    static Quaternionf getBlockStateOrientation(Direction facing) {
        Quaternionf orientation;
        if (facing.getAxis().isHorizontal()) {
            orientation = Axis.YP.rotationDegrees(AngleHelper.horizontalAngle(facing.getOpposite()));
        } else {
            orientation = new Quaternionf();
        }
        orientation.mul(Axis.XP.rotationDegrees(-90 - AngleHelper.verticalAngle(facing)));
        return orientation;
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        super.collectCrumblingInstances(consumer);
        consumer.accept(topInstance);
    }
}