package com.adonis.createfisheryindustry;

import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TooltipHandler {
    // 存储物品的描述
    private static final Map<Item, ItemDescription> DESCRIPTIONS = new HashMap<>();
    private static String cachedLanguage = null;

    // 初始化物品描述
    public static void init() {
        // 为每个物品添加本地化描述
        addDescription(CreateFisheryBlocks.FRAME_TRAP.asItem(),
                "tooltip.createfishery.frame_trap",
                List.of(
                        Pair.of("tooltip.createfishery.when_in_water", "tooltip.createfishery.frame_trap.capture")
                ));
        addDescription(CreateFisheryBlocks.MESH_TRAP.asItem(),
                "tooltip.createfishery.mesh_trap",
                List.of(
//                        Pair.of("tooltip.createfishery.when_in_water", "tooltip.createfishery.mesh_trap.capture"),
                        Pair.of("tooltip.createfishery.right_click", "tooltip.createfishery.mesh_trap.extract")
                ));
        addDescription(CreateFisheryBlocks.TRAP_NOZZLE.asItem(),
                "tooltip.createfishery.trap_nozzle",
                List.of(
//                        Pair.of("tooltip.createfishery.when_attached", "tooltip.createfishery.trap_nozzle.guide"),
                        Pair.of("tooltip.createfishery.right_click", "tooltip.createfishery.trap_nozzle.extract")
                ));
        addDescription(CreateFisheryBlocks.SMART_MESH.asItem(),
                "tooltip.createfishery.smart_mesh",
                List.of(
//                        Pair.of("tooltip.createfishery.when_in_water", "tooltip.createfishery.smart_mesh.capture"),
                        Pair.of("tooltip.createfishery.right_click_empty", "tooltip.createfishery.smart_mesh.extract")
                ));
        addDescription(CreateFisheryItems.COPPER_DIVING_LEGGINGS.get(),
                "tooltip.createfishery.copper_leggings",
                List.of(
                        Pair.of("tooltip.createfishery.when_worn", "tooltip.createfishery.copper_leggings.effect")
                ));
        addDescription(CreateFisheryItems.NETHERITE_DIVING_LEGGINGS.get(),
                "tooltip.createfishery.netherite_leggings",
                List.of(
                        Pair.of("tooltip.createfishery.when_worn", "tooltip.createfishery.netherite_leggings.effect")
                ));
    }

    // 添加物品描述
    // 添加物品描述
    private static void addDescription(Item item, String summaryTranslationKey, List<Pair<String, String>> behaviourTranslationKeys) {
        List<Component> lines = new ArrayList<>();
        List<Component> linesOnShift = new ArrayList<>();
        List<Component> linesOnCtrl = new ArrayList<>();

        // 默认提示，模仿机械动力
        MutableComponent holdShift = CreateLang.translateDirect("tooltip.holdForDescription",
                        CreateLang.translateDirect("tooltip.keyShift").withStyle(Screen.hasShiftDown() ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                .withStyle(ChatFormatting.DARK_GRAY);
        lines.add(holdShift);

        // 构建 Shift 描述
        linesOnShift.add(holdShift);
        linesOnShift.add(Component.empty());  // 添加空行

        // 使用 TooltipHelper 切割并格式化本地化的 summary
        Style primaryStyle = Style.EMPTY.applyFormat(ChatFormatting.GRAY);
        Style highlightStyle = Style.EMPTY.applyFormat(ChatFormatting.GRAY);
        // 使用 Component.translatable 获取翻译后的文本
        Component translatedSummary = Component.translatable(summaryTranslationKey);
        linesOnShift.addAll(TooltipHelper.cutTextComponent(translatedSummary, primaryStyle, highlightStyle, 0));

        if (!behaviourTranslationKeys.isEmpty()) {
            linesOnShift.add(Component.empty()); // 添加空行
            for (Pair<String, String> behaviour : behaviourTranslationKeys) {
                // 添加条件部分，使用灰色（从翻译键获取文本）
                linesOnShift.add(Component.translatable(behaviour.getLeft()).withStyle(ChatFormatting.GRAY));
                // 添加行为描述，使用 TooltipHelper 处理高亮（从翻译键获取文本）
                linesOnShift.addAll(TooltipHelper.cutTextComponent(
                        Component.translatable(behaviour.getRight()),
                        primaryStyle,
                        highlightStyle,
                        10
                ));
            }
        }

        // 构建 ItemDescription
        DESCRIPTIONS.put(item, new ItemDescription(
                com.google.common.collect.ImmutableList.copyOf(lines),
                com.google.common.collect.ImmutableList.copyOf(linesOnShift),
                com.google.common.collect.ImmutableList.copyOf(linesOnCtrl)
        ));
    }

    // 处理 Tooltip 事件
    public static void onTooltip(ItemTooltipEvent event) {
        Item item = event.getItemStack().getItem();
        if (!DESCRIPTIONS.containsKey(item)) {
            return;
        }

        // 检查语言变化
        String currentLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        if (!currentLanguage.equals(cachedLanguage)) {
            cachedLanguage = currentLanguage;
            init(); // 重新初始化描述
        }

        // 添加当前描述到 tooltip
        ItemDescription description = DESCRIPTIONS.get(item);
        List<Component> lines = Screen.hasShiftDown() ? description.linesOnShift() : description.lines();
        event.getToolTip().addAll(1, lines);
    }
}