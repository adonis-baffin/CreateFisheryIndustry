package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonTetherHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarpoonTetherHandler.class);
    private static final double MAX_TETHER_DISTANCE = 1.0; // 到达玩家 1 格内停止牵引
    private static final double TETHER_SPEED = 0.2; // 牵引速度
    private static final double TETHER_UPWARD_FORCE = 0.1; // 向上力，防止卡在地面

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        Level level = entity.level();
        if (level.isClientSide) {
            return;
        }

        // 处理 ItemEntity 和 ExperienceOrb
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            CompoundTag tag = entity.getPersistentData();
            if (tag.getBoolean("HarpoonTethered")) {
                long tetherEndTime = tag.getLong("TetherEndTime");
                int playerId = tag.getInt("TetherPlayerId");
                Player player = (Player) level.getEntity(playerId);

                // 检查是否超时或玩家无效
                if (level.getGameTime() >= tetherEndTime || player == null || !player.isAlive()) {
                    tag.remove("HarpoonTethered");
                    tag.remove("TetherPlayerId");
                    tag.remove("TetherEndTime");
                    return;
                }

                // 计算玩家位置
                Vec3 playerPos = player.position().add(0, player.getEyeHeight() * 0.5, 0);
                Vec3 entityPos = entity.position();
                double dx = playerPos.x - entityPos.x;
                double dy = playerPos.y - entityPos.y;
                double dz = playerPos.z - entityPos.z;
                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

                // 如果距离足够近，停止牵引
                if (distance < MAX_TETHER_DISTANCE) {
                    tag.remove("HarpoonTethered");
                    tag.remove("TetherPlayerId");
                    tag.remove("TetherEndTime");
                    return;
                }

                // 更新运动向量
                Vec3 motion = new Vec3(
                        dx * TETHER_SPEED,
                        dy * TETHER_SPEED + TETHER_UPWARD_FORCE,
                        dz * TETHER_SPEED
                );
                entity.setDeltaMovement(motion);
            }
        }
    }
}