package com.adonis.createfisheryindustry.block.SmartNozzle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;

public class SmartNozzleRenderer extends SmartBlockEntityRenderer<SmartNozzleBlockEntity> {

    public SmartNozzleRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    protected void renderFilterItem(SmartNozzleBlockEntity blockEntity, ItemStack stack, float partialTicks,
                                    PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5); // 居中
        ms.scale(0.5f, 0.5f, 0.5f); // 缩小物品以适应方块
        itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, ms, buffer, null, 0);
        ms.popPose();
    }
}