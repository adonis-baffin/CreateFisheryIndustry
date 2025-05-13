package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.HarpoonEntity;
import com.adonis.createfisheryindustry.item.HarpoonPouchContents;
import com.adonis.createfisheryindustry.registry.CreateFisheryComponents;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonPouchEventHandler {
    // 任务队列，用于延迟补充鱼叉
    private static final List<ReplenishTask> replenishTasks = new ArrayList<>();

    // 功能1：捡起鱼叉时优先进入鱼叉袋（仅限物品实体，且不允许进入鱼叉袋）
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity itemEntity = event.getItemEntity();
        ItemStack stack = itemEntity.getItem();

        if (stack.getItem() != CreateFisheryItems.HARPOON.get()) {
            return; // 仅处理鱼叉
        }

        // 阻止物品实体的鱼叉进入鱼叉袋，直接允许默认拾取到库存
        event.setCanPickup(TriState.TRUE); // 显式允许默认拾取
    }

    // 功能2：投掷鱼叉后自动补充
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof HarpoonEntity harpoon) || event.getLevel().isClientSide()) {
            return; // 仅处理鱼叉实体且在服务器端
        }

        Player player = harpoon.getOwner() instanceof Player ? (Player) harpoon.getOwner() : null;
        if (player == null) {
            return;
        }

        ItemStack thrownStack = harpoon.getPickupItemStackOrigin();
        if (thrownStack.getItem() != CreateFisheryItems.HARPOON.get()) {
            return; // 仅处理鱼叉
        }

        // 获取附魔的 Holder<Enchantment>
        Holder<Enchantment> loyalty = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.LOYALTY);
        Holder<Enchantment> riptide = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.RIPTIDE);

        // 检查鱼叉是否有忠诚或激流附魔
        if (EnchantmentHelper.getItemEnchantmentLevel(loyalty, thrownStack) > 0 ||
                EnchantmentHelper.getItemEnchantmentLevel(riptide, thrownStack) > 0) {
            return; // 有忠诚或激流附魔，不补充
        }

        // 注册延迟任务
        replenishTasks.add(new ReplenishTask(player.getUUID(), 1));
    }

    // 服务器tick事件处理延迟任务
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<ReplenishTask> tasksToRemove = new ArrayList<>();
        for (ReplenishTask task : replenishTasks) {
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                Player player = event.getServer().getPlayerList().getPlayer(task.playerUUID);
                if (player != null) {
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        ItemStack pouch = player.getInventory().getItem(i);
                        if (pouch.getItem() == CreateFisheryItems.HARPOON_POUCH.get()) {
                            if (!CreateFisheryComponents.HARPOON_POUCH_CONTENTS.isBound()) {
                                continue;
                            }
                            HarpoonPouchContents contents = pouch.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
                            HarpoonPouchContents.Mutable mutable = new HarpoonPouchContents.Mutable(contents);
                            ItemStack replacement = mutable.removeOne();
                            if (replacement != null) {
                                playRemoveOneSound(player);
                                ItemStack mainHand = player.getMainHandItem();
                                if (mainHand.isEmpty()) {
                                    player.setItemInHand(player.getUsedItemHand(), replacement);
                                } else {
                                    player.getInventory().add(replacement);
                                }
                                pouch.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), mutable.toImmutable());
                                // 同步鱼叉袋数据
                                if (player instanceof ServerPlayer serverPlayer) {
                                    serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                                            serverPlayer.containerMenu.containerId, serverPlayer.containerMenu.incrementStateId(), i, pouch));
                                    // 显示剩余鱼叉数量
                                    int totalHarpoons = countTotalHarpoons(player);
                                    serverPlayer.displayClientMessage(Component.literal("Remaining Harpoons:" + totalHarpoons), true);
                                }
                                break;
                            } else {
                                player.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
                            }
                        }
                    }
                }
                tasksToRemove.add(task);
            }
        }
        replenishTasks.removeAll(tasksToRemove);
    }

    // 统计玩家背包中所有鱼叉袋的鱼叉数量总和
    private static int countTotalHarpoons(Player player) {
        int totalHarpoons = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack pouch = player.getInventory().getItem(i);
            if (pouch.getItem() == CreateFisheryItems.HARPOON_POUCH.get()) {
                if (!CreateFisheryComponents.HARPOON_POUCH_CONTENTS.isBound()) {
                    continue;
                }
                HarpoonPouchContents contents = pouch.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
                for (ItemStack stack : contents.items()) {
                    if (stack.getItem() == CreateFisheryItems.HARPOON.get()) {
                        totalHarpoons += stack.getCount();
                    }
                }
            }
        }
        return totalHarpoons;
    }

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playRemoveOneSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    // 延迟任务类
    private static class ReplenishTask {
        UUID playerUUID;
        int ticksRemaining;

        ReplenishTask(UUID playerUUID, int ticksRemaining) {
            this.playerUUID = playerUUID;
            this.ticksRemaining = ticksRemaining;
        }
    }

    // 处理鱼叉实体拾取逻辑
    public static boolean tryInsertHarpoonToPouch(Player player, ItemStack stack) {
        if (stack.getItem() != CreateFisheryItems.HARPOON.get()) {
            return false;
        }

        ItemStack originalStack = stack.copy();
        boolean handled = false;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack pouch = player.getInventory().getItem(i);
            if (pouch.getItem() == CreateFisheryItems.HARPOON_POUCH.get()) {
                if (!CreateFisheryComponents.HARPOON_POUCH_CONTENTS.isBound()) {
                    continue;
                }

                HarpoonPouchContents contents = pouch.getOrDefault(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), HarpoonPouchContents.EMPTY);
                HarpoonPouchContents.Mutable mutable = new HarpoonPouchContents.Mutable(contents);

                int inserted = mutable.tryInsert(stack);
                if (inserted > 0) {
                    playInsertSound(player);
                    pouch.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), mutable.toImmutable());
                    stack.shrink(inserted);

                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                                serverPlayer.containerMenu.containerId, serverPlayer.containerMenu.incrementStateId(), i, pouch));
                        // 显示剩余鱼叉数量
                        int totalHarpoons = countTotalHarpoons(player);
                        serverPlayer.displayClientMessage(Component.literal("Remaining Harpoons:" + totalHarpoons), true);
                    }

                    handled = true;
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        if (!stack.isEmpty() && handled) {
            if (player.getInventory().add(stack)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    // 显示剩余鱼叉数量
                    int totalHarpoons = countTotalHarpoons(player);
                    serverPlayer.displayClientMessage(Component.literal("Remaining Harpoons: " + totalHarpoons), true);
                }
                return true;
            }
        }

        return handled;
    }
}