package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;

public class HarpoonISTER extends BlockEntityWithoutLevelRenderer {
    public static final HarpoonISTER RENDERER = new HarpoonISTER(
            Minecraft.getInstance().getBlockEntityRenderDispatcher(),
            Minecraft.getInstance().getEntityModels()
    );

    private static final ModelResourceLocation MODEL_LOCATION =
            ModelResourceLocation.standalone(CreateFisheryMod.asResource("entity/harpoon"));

    public HarpoonISTER(BlockEntityRenderDispatcher renderDispatcher, EntityModelSet modelSet) {
        super(renderDispatcher, modelSet);
    }

    @Override
    public void onResourceManagerReload(@NotNull ResourceManager resourceManager) {

    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext displayContext,
                             @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer,
                             int light, int overlayLight) {

        var modelManager = Minecraft.getInstance().getModelManager();
        BakedModel model = modelManager.getModel(MODEL_LOCATION);

        if (model == modelManager.getMissingModel()) {
            return;
        }

        Minecraft.getInstance().getItemRenderer().render(
                stack,
                displayContext,
                false,
                poseStack,
                buffer,
                light,
                overlayLight,
                model
        );
    }
}