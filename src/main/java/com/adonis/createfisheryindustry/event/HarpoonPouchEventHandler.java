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
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    private static final List<ReplenishTask> replenishTasks = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity itemEntity = event.getItemEntity();
        ItemStack stack = itemEntity.getItem();

        // 只处理鱼叉和三叉戟
        if (stack.getItem() != CreateFisheryItems.HARPOON.get() &&
                stack.getItem() != Items.TRIDENT) {
            return;
        }

        // 检查忠诚附魔
        Holder<Enchantment> loyalty = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.LOYALTY);

        if (EnchantmentHelper.getItemEnchantmentLevel(loyalty, stack) > 0) {
            // 忠诚附魔的武器可以进入鱼叉袋
            if (tryInsertHarpoonToPouch(player, stack)) {
                itemEntity.setItem(ItemStack.EMPTY);
                itemEntity.discard();
                event.setCanPickup(TriState.FALSE);
                return;
            }
        }

        // 默认行为
        event.setCanPickup(TriState.TRUE);
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        // 获取投掷者和投掷物
        Player player = null;
        ItemStack thrownStack = null;

        if (event.getEntity() instanceof HarpoonEntity harpoon) {
            player = harpoon.getOwner() instanceof Player ? (Player) harpoon.getOwner() : null;
            thrownStack = harpoon.getPickupItemStackOrigin();
        } else if (event.getEntity() instanceof ThrownTrident trident) {
            player = trident.getOwner() instanceof Player ? (Player) trident.getOwner() : null;
            thrownStack = new ItemStack(Items.TRIDENT);
        }

        if (player == null || thrownStack == null) {
            return;
        }

        // 创造模式不自动补充
        if (player.hasInfiniteMaterials()) {
            return;
        }

        // 检查忠诚和激流附魔
        Holder<Enchantment> loyalty = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.LOYALTY);
        Holder<Enchantment> riptide = player.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.RIPTIDE);

        if (EnchantmentHelper.getItemEnchantmentLevel(loyalty, thrownStack) > 0 ||
                EnchantmentHelper.getItemEnchantmentLevel(riptide, thrownStack) > 0) {
            return;
        }

        // 注册延迟补充任务
        replenishTasks.add(new ReplenishTask(player.getUUID(), 1));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        List<ReplenishTask> tasksToRemove = new ArrayList<>();
        for (ReplenishTask task : replenishTasks) {
            task.ticksRemaining--;
            if (task.ticksRemaining <= 0) {
                Player player = event.getServer().getPlayerList().getPlayer(task.playerUUID);
                if (player != null && !player.hasInfiniteMaterials()) {
                    // 只遍历主背包（0-35），不包括盔甲槽和副手
                    for (int i = 0; i < 36; i++) {
                        ItemStack pouch = player.getInventory().getItem(i);
                        if (pouch.getItem() == CreateFisheryItems.HARPOON_POUCH.get()) {
                            if (!CreateFisheryComponents.HARPOON_POUCH_CONTENTS.isBound()) {
                                continue;
                            }
                            HarpoonPouchContents contents = pouch.getOrDefault(
                                    CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(),
                                    HarpoonPouchContents.EMPTY
                            );
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

                                // 同步数据
                                if (player instanceof ServerPlayer serverPlayer) {
                                    // 计算正确的容器槽位ID
                                    int slotId = getContainerSlotId(i);

                                    serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                                            serverPlayer.containerMenu.containerId,
                                            serverPlayer.containerMenu.incrementStateId(),
                                            slotId,
                                            pouch
                                    ));

                                    int totalHarpoons = countTotalHarpoons(player);
                                    serverPlayer.displayClientMessage(
                                            Component.translatable("create_fishery.harpoon_pouch.remaining", totalHarpoons),
                                            true
                                    );
                                }
                                break;
                            } else {
                                player.playSound(SoundEvents.BUNDLE_DROP_CONTENTS, 0.8F,
                                        0.8F + player.level().getRandom().nextFloat() * 0.4F);
                                if (player instanceof ServerPlayer serverPlayer) {
                                    serverPlayer.displayClientMessage(
                                            Component.translatable("create_fishery.harpoon_pouch.empty"),
                                            true
                                    );
                                }
                            }
                        }
                    }
                }
                tasksToRemove.add(task);
            }
        }
        replenishTasks.removeAll(tasksToRemove);
    }

    public static boolean tryInsertHarpoonToPouch(Player player, ItemStack stack) {
        // 支持鱼叉和三叉戟
        if (stack.getItem() != CreateFisheryItems.HARPOON.get() &&
                stack.getItem() != Items.TRIDENT) {
            return false;
        }

        boolean handled = false;

        // 只遍历主背包
        for (int i = 0; i < 36; i++) {
            ItemStack pouch = player.getInventory().getItem(i);
            if (pouch.getItem() == CreateFisheryItems.HARPOON_POUCH.get()) {
                if (!CreateFisheryComponents.HARPOON_POUCH_CONTENTS.isBound()) {
                    continue;
                }

                HarpoonPouchContents contents = pouch.getOrDefault(
                        CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(),
                        HarpoonPouchContents.EMPTY
                );
                HarpoonPouchContents.Mutable mutable = new HarpoonPouchContents.Mutable(contents);

                int inserted = mutable.tryInsert(stack);
                if (inserted > 0) {
                    playInsertSound(player);
                    pouch.set(CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(), mutable.toImmutable());
                    stack.shrink(inserted);

                    if (player instanceof ServerPlayer serverPlayer) {
                        // 计算正确的容器槽位ID
                        int slotId = getContainerSlotId(i);

                        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                                serverPlayer.containerMenu.containerId,
                                serverPlayer.containerMenu.incrementStateId(),
                                slotId,
                                pouch
                        ));

                        int totalHarpoons = countTotalHarpoons(player);
                        serverPlayer.displayClientMessage(
                                Component.translatable("create_fishery.harpoon_pouch.remaining", totalHarpoons),
                                true
                        );
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
                    int totalHarpoons = countTotalHarpoons(player);
                    serverPlayer.displayClientMessage(
                            Component.translatable("create_fishery.harpoon_pouch.remaining", totalHarpoons),
                            true
                    );
                }
                return true;
            }
        }

        return handled;
    }

    private static int getContainerSlotId(int inventoryIndex) {
        if (inventoryIndex < 9) {
            // 快捷栏 (0-8) -> 容器槽位 36-44
            return inventoryIndex + 36;
        } else {
            // 主背包 (9-35) -> 容器槽位 9-35
            return inventoryIndex;
        }
    }

    private static int countTotalHarpoons(Player player) {
        int totalHarpoons = 0;
        for (int i = 0; i < 36; i++) { // 只计算主背包
            ItemStack pouch = player.getInventory().getItem(i);
            if (pouch.getItem() == CreateFisheryItems.HARPOON_POUCH.get()) {
                if (!CreateFisheryComponents.HARPOON_POUCH_CONTENTS.isBound()) {
                    continue;
                }
                HarpoonPouchContents contents = pouch.getOrDefault(
                        CreateFisheryComponents.HARPOON_POUCH_CONTENTS.get(),
                        HarpoonPouchContents.EMPTY
                );
                for (ItemStack stack : contents.items()) {
                    if (stack.getItem() == CreateFisheryItems.HARPOON.get() ||
                            stack.getItem() == Items.TRIDENT) {
                        totalHarpoons += stack.getCount();
                    }
                }
            }
        }
        return totalHarpoons;
    }

    private static void playInsertSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F,
                0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    private static void playRemoveOneSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F,
                0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    private static class ReplenishTask {
        UUID playerUUID;
        int ticksRemaining;

        ReplenishTask(UUID playerUUID, int ticksRemaining) {
            this.playerUUID = playerUUID;
            this.ticksRemaining = ticksRemaining;
        }
    }
}