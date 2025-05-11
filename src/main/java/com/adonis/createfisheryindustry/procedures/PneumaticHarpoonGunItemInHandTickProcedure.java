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
    private static final double TRACTION_SPEED = 0.5;
    private static final float AIR_CONSUMPTION_RATE = 0.2f; // 每刻 0.2 单位
    private static final double MIN_DISTANCE_SQR = 2.0;

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity, ItemStack itemstack) {
        if (!(entity instanceof Player player)) {
            LOGGER.warn("Harpoon traction check failed: entity is not a player");
            return;
        }

        CustomData customData = itemstack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        boolean tagHooked = customData.copyTag().getBoolean("tagHooked");
        LOGGER.debug("Harpoon traction check for player {}: tagHooked={}, customData={}",
                player.getName().getString(), tagHooked, customData.copyTag().toString());

        if (!tagHooked) {
            LOGGER.debug("Harpoon traction skipped for player {}: tagHooked is false", player.getName().getString());
            return;
        }

        if (!world.isClientSide()) {
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            int totalAir = backtanks.stream().map(BacktankUtil::getAir).reduce(0, Integer::sum);
            LOGGER.debug("Harpoon air check for player {}: totalAir={}, required={}",
                    player.getName().getString(), totalAir, AIR_CONSUMPTION_RATE);

            // 累积气体消耗，每 5 刻消耗 1 单位（平均每刻 0.2 单位）
            float currAccumulatedAir = customData.copyTag().getFloat("AccumulatedAirConsumption");
            final float newAccumulatedAir = currAccumulatedAir + AIR_CONSUMPTION_RATE;

            if (newAccumulatedAir >= 1.0f && !backtanks.isEmpty()) {
                BacktankUtil.consumeAir(player, backtanks.get(0), 1);
                // 在lambda表达式外更新累积值，并单独存储最终值
                final float finalAccumulatedAir = newAccumulatedAir - 1.0f;
                CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> tag.putFloat("AccumulatedAirConsumption", finalAccumulatedAir));
                LOGGER.info("Harpoon air consumed for player {}: amount=1, remaining={}",
                        player.getName().getString(), totalAir - 1);
            } else {
                // 如果没有消耗完整的1单位空气，只更新累积值
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
                LOGGER.info("Harpoon reset tagHooked for player {}: no air or backtanks", player.getName().getString());
                return;
            }

            // 实体牵引
            int hookedEntityId = customData.copyTag().getInt("tagHookedEntityId");
            if (hookedEntityId > 0) {
                Entity target = ((Level) world).getEntity(hookedEntityId);
                if (target != null && target.isAlive()) {
                    double distanceSqr = player.distanceToSqr(target);
                    LOGGER.debug("Harpoon traction to entity for player {}: distanceSqr={}",
                            player.getName().getString(), distanceSqr);
                    if (distanceSqr > MIN_DISTANCE_SQR) {
                        double distance = Math.sqrt(distanceSqr);
                        Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                        Vec3 pull = targetPos.subtract(player.position().add(0, player.getBbHeight() * 0.5, 0))
                                .scale(TRACTION_SPEED / distance);
                        target.setDeltaMovement(target.getDeltaMovement().multiply(0.8, 0.8, 0.8).add(pull));
                        target.hurtMarked = true;
                        playTractionSound(world, player);
                        LOGGER.info("Harpoon traction to entity {} for player {}: motion={}, newDelta={}",
                                target.getType().toString(), player.getName().getString(), pull, target.getDeltaMovement());
                    } else {
                        CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                            tag.putBoolean("tagHooked", false);
                            tag.remove("tagHookedEntityId");
                            tag.remove("AccumulatedAirConsumption");
                        });
                        world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)
                                .forEach(TetheredHarpoonEntity::startRetrieving);
                        LOGGER.info("Harpoon reset tagHooked for player {}: reached entity", player.getName().getString());
                    }
                } else {
                    CustomData.update(DataComponents.CUSTOM_DATA, itemstack, tag -> {
                        tag.putBoolean("tagHooked", false);
                        tag.remove("tagHookedEntityId");
                        tag.remove("AccumulatedAirConsumption");
                    });
                    world.getEntitiesOfClass(TetheredHarpoonEntity.class, player.getBoundingBox().inflate(100), e -> e.getOwner() == player)
                            .forEach(TetheredHarpoonEntity::startRetrieving);
                    LOGGER.info("Harpoon reset tagHooked for player {}: entity dead or removed", player.getName().getString());
                }
                return;
            }

            // 方块牵引
            if (customData.copyTag().contains("xPostion")) {
                double xPos = customData.copyTag().getDouble("xPostion");
                double yPos = customData.copyTag().getDouble("yPostion");
                double zPos = customData.copyTag().getDouble("zPostion");
                BlockPos blockPos = BlockPos.containing(xPos, yPos, zPos);
                LOGGER.debug("Harpoon block pos for player {}: xPos={}, yPos={}, zPos={}, blockPos={}",
                        player.getName().getString(), xPos, yPos, zPos, blockPos);

                if (!world.isEmptyBlock(blockPos)) {
                    double distanceSqr = player.distanceToSqr(xPos, yPos, zPos);
                    LOGGER.debug("Harpoon traction to block for player {}: distanceSqr={}",
                            player.getName().getString(), distanceSqr);
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
                        LOGGER.info("Harpoon traction to block for player {}: motion={}, newDelta={}",
                                player.getName().getString(), pull, player.getDeltaMovement());
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
                        LOGGER.info("Harpoon reset tagHooked for player {}: reached block", player.getName().getString());
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
                    LOGGER.info("Harpoon reset tagHooked for player {}: block gone", player.getName().getString());
                }
            }
        }
    }

    private static void playTractionSound(LevelAccessor world, Player player) {
        if (world instanceof Level level) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse("item.armor.equip_chain")),
                    SoundSource.PLAYERS, 0.25F, 0.5F);
            LOGGER.debug("Harpoon traction sound played for player {}", player.getName().getString());
        }
    }
}