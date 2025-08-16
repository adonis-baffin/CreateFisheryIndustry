package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 处理被鱼叉拉取的物品被拾取后的状态更新
 * 由于NeoForge没有直接的物品拾取事件，我们通过监听实体tick来处理
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonItemPickupHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarpoonItemPickupHandler.class);

    /**
     * 监听物品实体的tick事件
     * 当物品被拾取（实体被移除）时重置鱼叉枪状态
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();

        if (entity.level().isClientSide) {
            return;
        }

        // 处理物品实体
        if (entity instanceof ItemEntity itemEntity) {
            handleItemEntity(itemEntity);
        }

        // 处理经验球
        if (entity instanceof ExperienceOrb orb) {
            handleExperienceOrb(orb);
        }
    }

    /**
     * 处理物品实体
     */
    private static void handleItemEntity(ItemEntity itemEntity) {
        // 检查物品是否即将被拾取（检查是否有拾取延迟）
        if (itemEntity.isRemoved() || !itemEntity.hasPickUpDelay()) {
            CompoundTag tag = itemEntity.getPersistentData();

            // 检查是否被鱼叉牵引
            if (!tag.getBoolean("HarpoonTethered")) {
                return;
            }

            // 获取牵引的玩家ID
            int tetherPlayerId = tag.getInt("TetherPlayerId");
            Level world = itemEntity.level();
            Entity playerEntity = world.getEntity(tetherPlayerId);

            if (playerEntity instanceof Player player) {
                // 检查物品是否足够接近玩家（表示即将被拾取）
                double distance = itemEntity.distanceTo(player);
                if (distance < 1.5 || itemEntity.isRemoved()) {
                    // 重置鱼叉枪状态
                    resetHarpoonGunState(player);

                    // 清除牵引标记
                    tag.remove("HarpoonTethered");
                    tag.remove("TetherPlayerId");
                    tag.remove("TetherEndTime");

                    LOGGER.debug("Item {} picked up, resetting harpoon gun for player {}",
                            itemEntity.getItem(), player.getName().getString());
                }
            }
        }
    }

    /**
     * 处理经验球
     */
    private static void handleExperienceOrb(ExperienceOrb orb) {
        // 检查经验球是否即将被拾取
        if (orb.isRemoved()) {
            CompoundTag tag = orb.getPersistentData();

            // 检查是否被鱼叉牵引
            if (!tag.getBoolean("HarpoonTethered")) {
                return;
            }

            // 获取牵引的玩家ID
            int tetherPlayerId = tag.getInt("TetherPlayerId");
            Level world = orb.level();
            Entity playerEntity = world.getEntity(tetherPlayerId);

            if (playerEntity instanceof Player player) {
                // 重置鱼叉枪状态
                resetHarpoonGunState(player);

                // 清除牵引标记
                tag.remove("HarpoonTethered");
                tag.remove("TetherPlayerId");
                tag.remove("TetherEndTime");

                LOGGER.debug("Experience orb picked up, resetting harpoon gun for player {}",
                        player.getName().getString());
            }
        }
    }

    /**
     * 重置玩家手中鱼叉枪的状态
     */
    private static void resetHarpoonGunState(Player player) {
        // 检查主手和副手的鱼叉枪
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        boolean reset = false;

        if (mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            reset = resetItemStack(mainHand, player) || reset;
        }

        if (offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            reset = resetItemStack(offHand, player) || reset;
        }

        if (reset) {
            // 收回所有相关的鱼叉实体
            Level world = player.level();
            List<TetheredHarpoonEntity> harpoons = world.getEntitiesOfClass(
                    TetheredHarpoonEntity.class,
                    player.getBoundingBox().inflate(100),
                    e -> e.getOwner() == player && !e.isRetrieving()
            );

            harpoons.forEach(TetheredHarpoonEntity::startRetrieving);
        }
    }

    /**
     * 重置单个鱼叉枪物品的状态
     * @return 是否成功重置
     */
    private static boolean resetItemStack(ItemStack itemstack, Player player) {
        CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        // 检查是否处于拉取物品状态
        if (customData.copyTag().getBoolean("tagPullingItem")) {
            // 清除所有状态标记
            CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                tag.putBoolean("tagHooked", false);
                tag.putBoolean("tagPullingItem", false);
                tag.remove("tagPulledEntityId");
                tag.remove("tagHookedEntityId");
                tag.remove("xPostion");
                tag.remove("yPostion");
                tag.remove("zPostion");
                tag.remove("AccumulatedAirConsumption");
                // 添加短暂冷却时间
                tag.putLong("CooldownEndTick", player.level().getGameTime() + 5);
            });

            LOGGER.debug("Reset harpoon gun state for player {}", player.getName().getString());
            return true;
        }

        return false;
    }
}