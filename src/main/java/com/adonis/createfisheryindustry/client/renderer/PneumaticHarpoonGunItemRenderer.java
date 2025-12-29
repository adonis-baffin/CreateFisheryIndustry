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
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {

        // 渲染基础模型
        renderer.render(model.getOriginalModel(), light);

        // 直接读 tagHooked：没有 hooked（未发射）时才显示 hook
        boolean hooked = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean("tagHooked");

        if (!hooked && HOOK.get() != null) {
            renderer.render(HOOK.get(), light);
        }

        // 删除所有 translate、背罐、实体搜索等代码（包括那两行抵消的 translate）
    }
}