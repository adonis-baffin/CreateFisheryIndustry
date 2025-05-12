package com.adonis.createfisheryindustry.procedures;

import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class PneumaticHarpoonGunItemInHandTickProcedure {
    private static final Logger LOGGER = LoggerFactory.getLogger(PneumaticHarpoonGunItemInHandTickProcedure.class);
    private static final double TRACTION_SPEED = 0.5; // 牵引速度
    private static final float AIR_CONSUMPTION_RATE_ENTITY = 0.05f; // 命中实体时每刻气体消耗
    private static final float AIR_CONSUMPTION_RATE_BLOCK = 0.1f;  // 命中方块时每刻气体消耗
    private static final double MIN_DISTANCE_SQR = 2.0; // 最小牵引距离平方

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
        if (!(entity instanceof Player player)) {
            return;
        }

        CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        boolean tagHooked = customData.copyTag().getBoolean("tagHooked");

        if (!tagHooked) {
            return;
        }

        if (!world.isClientSide()) {
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            int totalAir = backtanks.stream().map(BacktankUtil::getAir).reduce(0, Integer::sum);

            // 确定气体消耗率
            float airConsumptionRate = customData.copyTag().contains("tagHookedEntityId") ? AIR_CONSUMPTION_RATE_ENTITY : AIR_CONSUMPTION_RATE_BLOCK;
            float currAccumulatedAir = customData.copyTag().getFloat("AccumulatedAirConsumption");
            final float newAccumulatedAir = currAccumulatedAir + airConsumptionRate;

            if (newAccumulatedAir >= 1.0f && !backtanks.isEmpty()) {
                BacktankUtil.consumeAir(player, backtanks.get(0), 1);
                final float finalAccumulatedAir = newAccumulatedAir - 1.0f;
                CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putFloat("AccumulatedAirConsumption", finalAccumulatedAir));
            } else {
                CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putFloat("AccumulatedAirConsumption", newAccumulatedAir));
            }

            if (totalAir < 1 || backtanks.isEmpty()) {
                CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                    tag.putBoolean("tagHooked", false);
                    tag.remove("tagHookedEntityId");
                    tag.remove("xPostion");
                    tag.remove("yPostion");
                    tag.remove("zPostion");
                    tag.remove("AccumulatedAirConsumption");
                });
                world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)
                        .forEach(TetheredHarpoonEntity::startRetrieving);
                LOGGER.debug("Insufficient air, retrieving harpoon for player: {}", player.getName().getString());
                return;
            }

            // 实体牵引
            int hookedEntityId = customData.copyTag().getInt("tagHookedEntityId");
            if (hookedEntityId > 0) {
                Entity target = ((Level) world).getEntity(hookedEntityId);
                if (target != null && target.isAlive()) {
                    double distanceSqr = player.distanceToSqr(target);
                    if (distanceSqr > MIN_DISTANCE_SQR) {
                        double distance = Math.sqrt(distanceSqr);
                        Vec3 playerPos = player.position().add(0, player.getBbHeight() * 0.5, 0);
                        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                        Vec3 pull = playerPos.subtract(targetPos).scale(TRACTION_SPEED / distance); // 拉向玩家
                        target.setDeltaMovement(target.getDeltaMovement().multiply(0.8, 0.8, 0.8).add(pull));
                        target.hurtMarked = true;
                        playTractionSound(world, player);
                        LOGGER.debug("Pulling entity {} to player: {}", target, player.getName().getString());
                    } else {
                        CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                            tag.putBoolean("tagHooked", false);
                            tag.remove("tagHookedEntityId");
                            tag.remove("AccumulatedAirConsumption");
                        });
                        world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)
                                .forEach(TetheredHarpoonEntity::startRetrieving);
                        LOGGER.debug("Entity too close, retrieving harpoon for player: {}", player.getName().getString());
                    }
                } else {
                    CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                        tag.putBoolean("tagHooked", false);
                        tag.remove("tagHookedEntityId");
                        tag.remove("AccumulatedAirConsumption");
                    });
                    world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)
                            .forEach(TetheredHarpoonEntity::startRetrieving);
                    LOGGER.debug("Hooked entity invalid, retrieving harpoon for player: {}", player.getName().getString());
                }
                return;
            }

            // 方块牵引
            if (customData.copyTag().contains("xPostion")) {
                double xPos = customData.copyTag().getDouble("xPostion");
                double yPos = customData.copyTag().getDouble("yPostion");
                double zPos = customData.copyTag().getDouble("zPostion");
                BlockPos blockPos = BlockPos.containing(xPos, yPos, zPos);

                if (!world.isEmptyBlock(blockPos)) {
                    double distanceSqr = player.distanceToSqr(xPos, yPos, zPos);
                    if (distanceSqr > MIN_DISTANCE_SQR) {
                        double distance = Math.sqrt(distanceSqr);
                        Vec3 pull = new Vec3(xPos - player.getX(), yPos - player.getY(), zPos - player.getZ())
                                .scale(TRACTION_SPEED / distance);
                        player.setDeltaMovement(player.getDeltaMovement().multiply(0.8, 0.8, 0.8).add(pull));
                        player.hurtMarked = true;
                        if (player.getDeltaMovement().y() >= -0.2) {
                            player.fallDistance = 0.0F;
                        }
                        playTractionSound(world, player);
                        LOGGER.debug("Pulling player {} to block: {}", player.getName().getString(), blockPos);
                    } else {
                        CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                            tag.putBoolean("tagHooked", false);
                            tag.remove("xPostion");
                            tag.remove("yPostion");
                            tag.remove("zPostion");
                            tag.remove("AccumulatedAirConsumption");
                        });
                        world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)
                                .forEach(TetheredHarpoonEntity::startRetrieving);
                        LOGGER.debug("Player too close to block, retrieving harpoon for player: {}", player.getName().getString());
                    }
                } else {
                    CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                        tag.putBoolean("tagHooked", false);
                        tag.remove("xPostion");
                        tag.remove("yPostion");
                        tag.remove("zPostion");
                        tag.remove("AccumulatedAirConsumption");
                    });
                    world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)


                            .forEach(TetheredHarpoonEntity::startRetrieving);
                    LOGGER.debug("Anchored block invalid, retrieving harpoon for player: {}", player.getName().getString());
                }
            }
        }
    }

    private static void playTractionSound(LevelAccessor world, Player player) {
        if (world instanceof Level level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.armor.equip_chain")),
                    SoundSource.PLAYERS, 0.25F, 0.5F);
        }
    }
}