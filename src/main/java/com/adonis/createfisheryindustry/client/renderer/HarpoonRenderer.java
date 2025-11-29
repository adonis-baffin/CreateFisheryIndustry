package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.HarpoonEntity;
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

public class HarpoonRenderer extends EntityRenderer<HarpoonEntity> {

    private static final ModelResourceLocation MODEL_LOCATION =
            ModelResourceLocation.standalone(CreateFisheryMod.asResource("entity/harpoon"));

    private BakedModel cachedModel;

    public HarpoonRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(HarpoonEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {

        if (cachedModel == null) {
            var modelManager = Minecraft.getInstance().getModelManager();
            cachedModel = modelManager.getModel(MODEL_LOCATION);

            if (cachedModel == modelManager.getMissingModel()) {
                return;
            }
        }

        poseStack.pushPose();

        float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
        float pitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch + 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));

        poseStack.translate(-0.5, -0.5, 0);

        float scale = 1.0F;
        poseStack.scale(scale, scale, scale);

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
    public ResourceLocation getTextureLocation(@NotNull HarpoonEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}