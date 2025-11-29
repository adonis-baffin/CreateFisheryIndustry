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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理被鱼叉牵引的实体
 * 提供持续的牵引力并管理牵引状态
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class TetheredEntityHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TetheredEntityHandler.class);

    private static final double PULL_STRENGTH = 0.4;
    private static final double MAX_PULL_DISTANCE = 50.0;
    private static final double PICKUP_DISTANCE = 1.5;

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();

        if (entity.level().isClientSide) {
            return;
        }

        if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
            return;
        }

        CompoundTag tag = entity.getPersistentData();
        if (!tag.getBoolean("HarpoonTethered")) {
            return;
        }

        long currentTime = entity.level().getGameTime();
        long tetherEndTime = tag.getLong("TetherEndTime");
        if (currentTime > tetherEndTime) {
            cleanupTether(entity, tag);
            return;
        }

        int playerId = tag.getInt("TetherPlayerId");
        Level world = entity.level();
        Entity playerEntity = world.getEntity(playerId);

        if (!(playerEntity instanceof Player player)) {
            cleanupTether(entity, tag);
            return;
        }

        Vec3 entityPos = entity.position();
        Vec3 playerPos = player.position().add(0, player.getBbHeight() * 0.5, 0);
        double distance = entityPos.distanceTo(playerPos);

        if (distance > MAX_PULL_DISTANCE) {
            cleanupTether(entity, tag, player);
            return;
        }

        if (distance < PICKUP_DISTANCE) {
            if (entity instanceof ItemEntity itemEntity) {
                itemEntity.setPickUpDelay(0);
                entity.setPos(playerPos.x, playerPos.y, playerPos.z);
                cleanupTether(entity, tag, player);
                return;
            } else if (entity instanceof ExperienceOrb) {
                entity.setPos(playerPos.x, playerPos.y, playerPos.z);
                cleanupTether(entity, tag, player);
                return;
            }
        }

        Vec3 direction = playerPos.subtract(entityPos);
        Vec3 pullDirection = direction.normalize();

        double pullSpeed = PULL_STRENGTH * Math.min(1.0 + distance * 0.03, 1.5);

        Vec3 currentVelocity = entity.getDeltaMovement();
        Vec3 pullVelocity = pullDirection.scale(pullSpeed);

        Vec3 newVelocity = currentVelocity.scale(0.5).add(pullVelocity);

        if (entity.onGround()) {
            newVelocity = new Vec3(newVelocity.x, Math.max(newVelocity.y, 0.3), newVelocity.z);
        }

        double maxSpeed = 1.5;
        if (newVelocity.length() > maxSpeed) {
            newVelocity = newVelocity.normalize().scale(maxSpeed);
        }

        entity.setDeltaMovement(newVelocity);
        entity.hasImpulse = true;
        entity.setOnGround(false);

        // 保持物品有拾取延迟，防止被其他人捡起
        if (entity instanceof ItemEntity itemEntity) {
            if (!itemEntity.hasPickUpDelay()) {
                itemEntity.setPickUpDelay(5);
            }
        }
    }

    private static void cleanupTether(Entity entity, CompoundTag tag) {
        cleanupTether(entity, tag, null);
    }

    private static void cleanupTether(Entity entity, CompoundTag tag, Player player) {
        tag.remove("HarpoonTethered");
        tag.remove("TetherPlayerId");
        tag.remove("TetherEndTime");

        if (player != null) {
            resetHarpoonGun(player);
        }
    }

    private static void resetHarpoonGun(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            resetGunState(mainHand);
        }

        if (offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            resetGunState(offHand);
        }
    }

    private static void resetGunState(ItemStack itemstack) {
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