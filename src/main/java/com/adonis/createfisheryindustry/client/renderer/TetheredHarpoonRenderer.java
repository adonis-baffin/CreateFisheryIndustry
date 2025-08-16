package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TetheredHarpoonRenderer extends EntityRenderer<TetheredHarpoonEntity> {

    private static final ModelResourceLocation MODEL_LOCATION =
            ModelResourceLocation.standalone(CreateFisheryMod.asResource("entity/harpoon"));

    private BakedModel cachedModel;

    public TetheredHarpoonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(TetheredHarpoonEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {

        // 加载模型
        if (cachedModel == null) {
            var modelManager = Minecraft.getInstance().getModelManager();
            cachedModel = modelManager.getModel(MODEL_LOCATION);

            if (cachedModel == modelManager.getMissingModel()) {
                CreateFisheryMod.LOGGER.error("Failed to load harpoon model!");
                return;
            }
        }

        poseStack.pushPose();

        // 计算鱼叉的实际朝向
        // 这是关键部分 - 我们需要根据鱼叉的速度向量来计算旋转，而不是使用实体的rotation

        // 获取插值后的位置
        double x = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zo, entity.getZ());

        // 获取速度向量来计算朝向
        float yaw, pitch;

        if (!entity.isAnchored() && entity.getHitEntity() == null) {
            // 如果鱼叉还在飞行中，根据速度向量计算朝向
            double deltaX = entity.getX() - entity.xo;
            double deltaY = entity.getY() - entity.yo;
            double deltaZ = entity.getZ() - entity.zo;

            // 如果有速度，根据速度计算朝向
            if (deltaX * deltaX + deltaZ * deltaZ > 0.0001) {
                yaw = (float)(Mth.atan2(deltaX, deltaZ) * (180.0 / Math.PI));
                double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                pitch = (float)(Mth.atan2(deltaY, horizontalDistance) * (180.0 / Math.PI));
            } else {
                // 如果速度太小，使用实体的旋转
                yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
                pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            }
        } else {
            // 如果已经锚定或击中目标，使用实体的当前旋转
            yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
            pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        }

        // 应用旋转 - 与原版三叉戟相同的旋转方式
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));

        // 如果你的模型默认是垂直的，添加这个旋转使其水平
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));

        // 可选：飞行时的旋转动画（如果你想要螺旋效果）
        if (!entity.isAnchored() && entity.getHitEntity() == null) {
            float spin = (entity.tickCount + partialTicks) * 0F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        }

        // 缩放
        float scale = 0.8F;
        poseStack.scale(scale, scale, scale);

        // 居中模型 - 根据你的模型尺寸调整
        poseStack.translate(-0.5, -0.6, -0.75);

        // 渲染模型
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));

        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create(42L);
        ModelData modelData = ModelData.EMPTY;

        // 渲染所有面
        for (Direction direction : Direction.values()) {
            List<BakedQuad> quads = cachedModel.getQuads(null, direction, random, modelData, null);
            for (BakedQuad quad : quads) {
                vertexConsumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
            }
        }

        // 渲染无面向的四边形
        List<BakedQuad> quads = cachedModel.getQuads(null, null, random, modelData, null);
        for (BakedQuad quad : quads) {
            vertexConsumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
        }

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull TetheredHarpoonEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}