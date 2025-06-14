package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

public class TetheredHarpoonRenderer extends EntityRenderer<TetheredHarpoonEntity> {
    public static final ModelLayerLocation HARPOON_LAYER = new ModelLayerLocation(
            CreateFisheryMod.asResource("harpoon"), "main"
    );

    private static final ResourceLocation HARPOON_TEXTURE =
            CreateFisheryMod.asResource("textures/entity/harpoon1.png");

    private final HarpoonModel model;

    public TetheredHarpoonRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new HarpoonModel(context.bakeLayer(HARPOON_LAYER));
    }

    @Override
    public void render(TetheredHarpoonEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // 应用旋转变换
        poseStack.mulPose(Axis.YP.rotationDegrees(
                Mth.lerp(partialTicks, entity.yRotO, entity.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(
                Mth.lerp(partialTicks, entity.xRotO, entity.getXRot()) + 90.0F));

        // 渲染模型
        VertexConsumer vertexConsumer = buffer.getBuffer(
                RenderType.entityCutout(this.getTextureLocation(entity)));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight,
                OverlayTexture.NO_OVERLAY, -1);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull TetheredHarpoonEntity entity) {
        return HARPOON_TEXTURE;
    }

    // 自定义模型类 - 基于三叉戟模型结构
    public static class HarpoonModel extends Model {
        private final ModelPart root;

        public HarpoonModel(ModelPart root) {
            super(RenderType::entitySolid); // 与三叉戟保持一致
            this.root = root;
        }

        public static LayerDefinition createBodyLayer() {
            MeshDefinition meshdefinition = new MeshDefinition();
            PartDefinition partdefinition = meshdefinition.getRoot();

            // 基于三叉戟的结构，主杆部分保持一致
            PartDefinition poleDefinition = partdefinition.addOrReplaceChild("pole",
                    CubeListBuilder.create()
                            .texOffs(0, 6)
                            .addBox(-0.5F, 2.0F, -0.5F, 1.0F, 25.0F, 1.0F),
                    PartPose.ZERO);

//            // 鱼叉尖端 - 主要的尖锐部分
//            poleDefinition.addOrReplaceChild("main_spike",
//                    CubeListBuilder.create()
//                            .texOffs(0, 0)
//                            .addBox(-0.5F, -4.0F, -0.5F, 1.0F, 6.0F, 1.0F),
//                    PartPose.ZERO);
//
//            // 单个倒钩 - 向后倾斜，位于鱼叉尖端
//            poleDefinition.addOrReplaceChild("barb",
//                    CubeListBuilder.create()
//                            .texOffs(4, 0)
//                            .addBox(-0.5F, -3.0F, -0.5F, 1.0F, 3.0F, 1.0F),
//                    PartPose.offsetAndRotation(0.0F, -2.0F, 0.0F,
//                            0.7854F, 0.0F, 0.0F)); // 45度向后倾斜

            return LayerDefinition.create(meshdefinition, 32, 32);
        }

        @Override
        public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight,
                                   int packedOverlay, int color) {
            root.render(poseStack, buffer, packedLight, packedOverlay, color);
        }
    }
}