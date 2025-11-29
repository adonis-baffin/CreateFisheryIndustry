package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 处理被鱼叉牵引的实体
 * 提供持续的牵引力并管理牵引状态
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class TetheredEntityHandler {

    private static final double PULL_STRENGTH = 0.3; // 牵引力强度
    private static final double MAX_PULL_DISTANCE = 50.0; // 最大牵引距离
    private static final double PICKUP_DISTANCE = 1.5; // 拾取距离

    /**
     * 每tick更新被牵引的实体
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();

        // 只在服务端处理
        if (entity.level().isClientSide) {
            return;
        }

        // 只处理物品和经验球
        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            return;
        }

        // 检查是否被牵引
        CompoundTag tag = entity.getPersistentData();
        if (!tag.getBoolean("HarpoonTethered")) {
            return;
        }

        // 检查牵引是否过期
        long currentTime = entity.level().getGameTime();
        long tetherEndTime = tag.getLong("TetherEndTime");
        if (currentTime > tetherEndTime) {
            // 清除牵引状态并重置鱼叉枪
            cleanupTether(entity, tag);
            return;
        }

        // 找到牵引的玩家
        int playerId = tag.getInt("TetherPlayerId");
        Level world = entity.level();
        Entity playerEntity = world.getEntity(playerId);

        if (!(playerEntity instanceof Player player)) {
            // 玩家不存在，清除牵引
            cleanupTether(entity, tag);
            return;
        }

        // 计算到玩家的距离
        Vec3 entityPos = entity.position();
        Vec3 playerPos = player.position().add(0, player.getBbHeight() * 0.5, 0);
        double distance = entityPos.distanceTo(playerPos);

        // 如果超出最大距离，停止牵引
        if (distance > MAX_PULL_DISTANCE) {
            cleanupTether(entity, tag, player);
            return;
        }

        // 如果非常接近玩家，准备拾取
        if (distance < PICKUP_DISTANCE) {
            if (entity instanceof ItemEntity itemEntity) {
                // 设置无拾取延迟
                itemEntity.setPickUpDelay(0);
                // 直接移动到玩家位置以确保拾取
                entity.setPos(playerPos);

                // 检查是否可以被拾取
                if (player.getInventory().add(itemEntity.getItem())) {
                    // 物品被成功添加到背包
                    itemEntity.discard();
                    // 重置鱼叉枪状态
                    resetHarpoonGun(player);
                    return;
                }
            } else if (entity instanceof ExperienceOrb orb) {
                // 经验球会自动被吸引，只需要确保距离足够近
                entity.setPos(playerPos);
            }
        }

        // 应用牵引力
        Vec3 pullDirection = playerPos.subtract(entityPos).normalize();
        Vec3 currentVelocity = entity.getDeltaMovement();

        // 计算牵引速度（距离越远，速度越快）
        double pullSpeed = PULL_STRENGTH * (1.0 + distance * 0.05);
        pullSpeed = Math.min(pullSpeed, 1.0); // 限制最大速度

        // 混合当前速度和牵引速度
        Vec3 newVelocity = currentVelocity.scale(0.8).add(pullDirection.scale(pullSpeed));

        // 限制最大速度
        if (newVelocity.length() > 1.5) {
            newVelocity = newVelocity.normalize().scale(1.5);
        }

        entity.setDeltaMovement(newVelocity);
        entity.hasImpulse = true;

        // 防止物品卡在地面
        if (entity.onGround() && newVelocity.y < 0.1) {
            entity.setDeltaMovement(newVelocity.x, 0.2, newVelocity.z);
        }
    }

    /**
     * 清理牵引状态
     */
    private static void cleanupTether(Entity entity, CompoundTag tag) {
        cleanupTether(entity, tag, null);
    }

    /**
     * 清理牵引状态并重置鱼叉枪
     */
    private static void cleanupTether(Entity entity, CompoundTag tag, Player player) {
        tag.remove("HarpoonTethered");
        tag.remove("TetherPlayerId");
        tag.remove("TetherEndTime");

        if (player != null) {
            resetHarpoonGun(player);
        }
    }

    /**
     * 重置鱼叉枪状态
     */
    private static void resetHarpoonGun(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            resetGunState(mainHand, player);
        }

        if (offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            resetGunState(offHand, player);
        }
    }

    /**
     * 重置单个鱼叉枪的状态
     */
    private static void resetGunState(ItemStack itemstack, Player player) {
        CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);

        if (customData.copyTag().getBoolean("tagPullingItem")) {
            CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                tag.putBoolean("tagHooked", false);
                tag.putBoolean("tagPullingItem", false);
                tag.remove("tagPulledEntityId");
                tag.remove("tagHookedEntityId");
                tag.remove("xPostion");
                tag.remove("yPostion");
                tag.remove("zPostion");
                tag.remove("AccumulatedAirConsumption");
            });
        }
    }
}