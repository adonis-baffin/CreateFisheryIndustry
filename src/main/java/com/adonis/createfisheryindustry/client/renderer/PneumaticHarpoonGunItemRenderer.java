package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueHandler;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.animation.PhysicalFloat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class PneumaticHarpoonGunItemRenderer extends CustomRenderedItemModelRenderer {

    protected static final PartialModel COG = PartialModel.of(CreateFisheryMod.asResource("item/portable_drill_cog"));
    protected static final PartialModel HOOK = PartialModel.of(CreateFisheryMod.asResource("item/pneumatic_harpoon_gun_hook"));

    private static final PhysicalFloat cogRotation = PhysicalFloat.create().withDrag(0.3);

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer, ItemDisplayContext transformType,
                          PoseStack ms, MultiBufferSource buffer, int light, int overlay) {

        // 渲染基础模型（手柄）
        if (model.getOriginalModel() != null) {
            renderer.render(model.getOriginalModel(), light);
        } else {
        }

        // 获取玩家和世界，处理空指针情况
        Player player = Minecraft.getInstance().player;
        Level level = player != null ? player.level() : null;
        float partialTicks = AnimationTickHolder.getPartialTicks();

        // 默认动画参数
        float rotationSpeed = -0.5f; // 默认-0.5度/刻，约-10度/秒
        boolean hideHook = false;

        if (player != null && level != null) {
            // 计算背包空气总量
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            int totalAir = backtanks.stream().mapToInt(BacktankUtil::getAir).sum();

            // 根据背包和状态调整旋转速度
            if (!backtanks.isEmpty()) {
                rotationSpeed = -0.5f - (totalAir / 800.0f * 0.25f); // 最大-0.75度/刻，约-15度/秒
                boolean isHooked = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag().getBoolean("tagHooked");
                boolean isRetrieving = level.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100))
                        .stream().anyMatch(hook -> hook.getOwner() == player && hook.isRetrieving());

                if (isHooked) {
                    rotationSpeed = -1.0f; // -1.0度/刻，约-20度/秒
                    hideHook = true;
                } else if (isRetrieving) {
                    rotationSpeed = -0.75f; // -0.75度/刻，约-15度/秒
                    hideHook = true;
                }
            }
        } else {
        }

        // 更新PhysicalFloat
        cogRotation.bump(1, rotationSpeed);
        cogRotation.tick();
        float cogAngle = cogRotation.getValue(partialTicks);

        // 渲染齿轮
        ms.pushPose();
        ms.translate(0.0f, -0.125f, 0.0f);
        ms.mulPose(Axis.ZP.rotationDegrees(cogAngle));
        ms.translate(0.0f, 0.125f, 0.0f);
        if (COG.get() != null) {
            renderer.render(COG.get(), light);
        } else {
        }
        ms.popPose();

        // 渲染钩爪（根据状态决定是否隐藏，无旋转）
        if (!hideHook) {
            ms.pushPose();
            ms.translate(0.0f, -0.125f, 0.0f);
            ms.translate(0.0f, 0.125f, 0.0f);
            if (HOOK.get() != null) {
                renderer.render(HOOK.get(), light);
            } else {
            }
            ms.popPose();
        } else {
        }
    }
}