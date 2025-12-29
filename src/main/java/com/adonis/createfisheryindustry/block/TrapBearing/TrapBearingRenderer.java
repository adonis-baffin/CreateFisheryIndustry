//package com.adonis.createfisheryindustry.block.TrapBearing;
//
//import com.mojang.blaze3d.vertex.PoseStack;
//import com.simibubi.create.AllPartialModels;
//import com.simibubi.create.content.contraptions.bearing.BearingRenderer;
//import dev.engine_room.flywheel.api.visualization.VisualizationManager;
//import dev.engine_room.flywheel.lib.model.baked.PartialModel;
//import net.createmod.catnip.math.AngleHelper;
//import net.createmod.catnip.render.CachedBuffers;
//import net.createmod.catnip.render.SuperByteBuffer;
//import net.minecraft.client.renderer.MultiBufferSource;
//import net.minecraft.client.renderer.RenderType;
//import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
//import net.minecraft.core.Direction;
//import net.minecraft.world.level.block.state.properties.BlockStateProperties;
//
//public class TrapBearingRenderer extends BearingRenderer<TrapBearingBlockEntity> {
//
//    public TrapBearingRenderer(BlockEntityRendererProvider.Context context) {
//        super(context);
//    }
//
//    @Override
//    protected void renderSafe(TrapBearingBlockEntity be, float partialTicks, PoseStack ms,
//                              MultiBufferSource buffer, int light, int overlay) {
//
//        if (VisualizationManager.supportsVisualization(be.getLevel()))
//            return;
//
//        // 只渲染轴承主体（不包括顶部），或完全跳过 super
//        // 如果你想保持主体不变，可以手动渲染 BearingRenderer 的其他部分
//        // 但最干净的方式是：不注册这个 Renderer，让它回退到原版 BearingRenderer（只渲染一次顶部）
//
//        // 或者：复制原版 BearingRenderer 的代码，但去掉顶部渲染
//    }
//}