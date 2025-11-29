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

        if (cachedModel == null) {
            var modelManager = Minecraft.getInstance().getModelManager();
            cachedModel = modelManager.getModel(MODEL_LOCATION);

            if (cachedModel == modelManager.getMissingModel()) {
                return;
            }
        }

        poseStack.pushPose();

        double x = Mth.lerp(partialTicks, entity.xo, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yo, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zo, entity.getZ());

        float yaw, pitch;

        if (!entity.isAnchored() && entity.getHitEntity() == null) {
            double deltaX = entity.getX() - entity.xo;
            double deltaY = entity.getY() - entity.yo;
            double deltaZ = entity.getZ() - entity.zo;

            if (deltaX * deltaX + deltaZ * deltaZ > 0.0001) {
                yaw = (float)(Mth.atan2(deltaX, deltaZ) * (180.0 / Math.PI));
                double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                pitch = (float)(Mth.atan2(deltaY, horizontalDistance) * (180.0 / Math.PI));
            } else {
                yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
                pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
            }
        } else {
            yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
            pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        }

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch));
        poseStack.mulPose(Axis.XP.rotationDegrees(180F));

        if (!entity.isAnchored() && entity.getHitEntity() == null) {
            float spin = (entity.tickCount + partialTicks) * 0F;
            poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        }

        float scale = 0.8F;
        poseStack.scale(scale, scale, scale);

        poseStack.translate(-0.5, -0.6, -0.75);

        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));

        PoseStack.Pose pose = poseStack.last();
        RandomSource random = RandomSource.create(42L);
        ModelData modelData = ModelData.EMPTY;

        for (Direction direction : Direction.values()) {
            List<BakedQuad> quads = cachedModel.getQuads(null, direction, random, modelData, null);
            for (BakedQuad quad : quads) {
                vertexConsumer.putBulkData(pose, quad, 1.0F, 1.0F, 1.0F, 1.0F, packedLight, OverlayTexture.NO_OVERLAY);
            }
        }

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