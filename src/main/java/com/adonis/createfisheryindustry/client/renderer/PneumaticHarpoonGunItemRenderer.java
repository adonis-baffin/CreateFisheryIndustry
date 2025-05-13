package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class PneumaticHarpoonGunItemRenderer extends CustomRenderedItemModelRenderer {

    protected static final PartialModel HOOK = PartialModel.of(CreateFisheryMod.asResource("item/pneumatic_harpoon_gun_hook"));

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer, ItemDisplayContext transformType,
                          PoseStack ms, MultiBufferSource buffer, int light, int overlay) {

        // 渲染基础模型（手柄）
        if (model.getOriginalModel() != null) {
            renderer.render(model.getOriginalModel(), light);
        }

        // 获取玩家和世界，处理空指针情况
        Player player = Minecraft.getInstance().player;
        Level level = player != null ? player.level() : null;

        // 默认参数
        boolean hideHook = false;

        if (player != null && level != null) {
            // 计算背包空气总量
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            int totalAir = backtanks.stream().mapToInt(BacktankUtil::getAir).sum();

            // 根据背包和状态调整参数
            if (!backtanks.isEmpty()) {
                boolean isHooked = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag().getBoolean("tagHooked");
                boolean isRetrieving = level.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100))
                        .stream().anyMatch(hook -> hook.getOwner() == player && hook.isRetrieving());

                if (isHooked || isRetrieving) {
                    hideHook = true;
                }
            }
        }

        // 渲染钩爪（根据状态决定是否隐藏）
        if (!hideHook) {
            ms.pushPose();
            ms.translate(0.0f, -0.125f, 0.0f);
            ms.translate(0.0f, 0.125f, 0.0f);
            if (HOOK.get() != null) {
                renderer.render(HOOK.get(), light);
            }
            ms.popPose();
        }
    }
}