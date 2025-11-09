package com.adonis.createfisheryindustry.block.TrapBearing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class TrapBearingRenderer extends BearingRenderer<TrapBearingBlockEntity> {

    public TrapBearingRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(TrapBearingBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {

        // 如果启用了 Flywheel 可视化，跳过传统渲染
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        // 调用父类渲染基础轴
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        // 获取方块朝向
        final Direction facing = be.getBlockState().getValue(BlockStateProperties.FACING);

        // 获取自定义的顶部模型
        PartialModel top = AllPartialModels.BEARING_TOP;
        SuperByteBuffer superBuffer = CachedBuffers.partial(top, be.getBlockState());

        // 计算插值角度
        float interpolatedAngle = be.getInterpolatedAngle(partialTicks - 1);

        // 应用动力学旋转变换
        kineticRotationTransform(superBuffer, be, facing.getAxis(),
                (float) (interpolatedAngle / 180 * Math.PI), light);

        // 根据朝向旋转模型
        if (facing.getAxis().isHorizontal()) {
            superBuffer.rotate(Direction.Axis.Y,
                    AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())));
        }
        superBuffer.rotate(Direction.Axis.X,
                AngleHelper.rad(-90 - AngleHelper.verticalAngle(facing)));

        // 渲染到缓冲区
        superBuffer.light(light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }
}