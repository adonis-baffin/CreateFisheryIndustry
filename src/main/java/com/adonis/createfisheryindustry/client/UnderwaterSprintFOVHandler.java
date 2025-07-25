package com.adonis.createfisheryindustry.client;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.item.CopperDivingLeggingsItem;
import com.adonis.createfisheryindustry.item.NetheriteDivingLeggingsItem;
import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * 处理水下疾跑的视角变化效果
 * 通过修改FOV来实现类似原版疾跑的视角拉远效果
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CreateFisheryMod.ID, value = Dist.CLIENT)
public class UnderwaterSprintFOVHandler {

    // FOV效果的过渡速度
    private static float currentFovModifier = 1.0f;
    private static float targetFovModifier = 1.0f;

    // 水下疾跑的FOV倍率（从配置文件读取）
    private static float getSprintFovMultiplier() {
        return (float) CreateFisheryCommonConfig.getDivingSprintFovMultiplier();
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        // 检查是否在进行水下疾跑
        boolean isUnderwaterSprinting = isPlayerUnderwaterSprinting(player);

        // 设置目标FOV倍率
        if (isUnderwaterSprinting) {
            targetFovModifier = getSprintFovMultiplier();
        } else {
            targetFovModifier = 1.0f;
        }

        // 平滑过渡
        float transitionSpeed = 0.5f; // 过渡速度，值越大过渡越快
        currentFovModifier += (targetFovModifier - currentFovModifier) * transitionSpeed;

        // 应用FOV修改
        if (Math.abs(currentFovModifier - 1.0f) > 0.01f) {
            float newFov = event.getNewFovModifier() * currentFovModifier;
            event.setNewFovModifier(newFov);
        }
    }

    /**
     * 检查玩家是否正在进行水下疾跑
     */
    private static boolean isPlayerUnderwaterSprinting(Player player) {
        // 检查装备
        boolean hasDivingBoots = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof DivingBootsItem;
        boolean hasDivingLeggings = player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof CopperDivingLeggingsItem
                || player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof NetheriteDivingLeggingsItem;

        // 必须在流体中
        boolean inFluid = player.isInWater() || player.isInLava();

        // 检查水下疾跑标记
        boolean hasUnderwaterSprint = player.getPersistentData().getBoolean("CFI_UnderwaterSprint");

        return hasDivingBoots && hasDivingLeggings && inFluid && hasUnderwaterSprint && player.isSprinting();
    }
}