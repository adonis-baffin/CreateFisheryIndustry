package com.adonis.createfisheryindustry.block.SmartMesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class SmartMeshRenderer extends SmartBlockEntityRenderer<SmartMeshBlockEntity> {

    public SmartMeshRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(SmartMeshBlockEntity blockEntity, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(blockEntity, partialTicks, ms, buffer, light, overlay);

        // 获取过滤器中的物品
        FilteringBehaviour filtering = blockEntity.filtering;
        if (filtering == null || filtering.getFilter().isEmpty()) {
            return;
        }

        ItemStack filterItem = filtering.getFilter();
        if (filterItem.isEmpty()) {
            return;
        }

        // 渲染过滤器物品
        renderFilterItem(filterItem, partialTicks, ms, buffer, light, overlay);
    }

    protected void renderFilterItem(ItemStack stack, float partialTicks, PoseStack ms,
                                    MultiBufferSource buffer, int light, int overlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5); // 居中
        ms.scale(0.5f, 0.5f, 0.5f); // 缩小物品以适应方块
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, null, 0);
        ms.popPose();
    }
}