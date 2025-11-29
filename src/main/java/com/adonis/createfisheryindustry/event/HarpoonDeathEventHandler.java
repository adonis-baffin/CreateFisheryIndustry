package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonDeathEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarpoonDeathEventHandler.class);

    // 存储被鱼叉击杀的实体信息
    private static final Map<UUID, HarpoonKillInfo> harpoonKills = new ConcurrentHashMap<>();

    private static class HarpoonKillInfo {
        final int playerId;
        final Vec3 deathPos;
        final long deathTime;
        final int harpoonId; // 存储鱼叉ID而不是引用

        HarpoonKillInfo(int playerId, Vec3 deathPos, long deathTime, int harpoonId) {
            this.playerId = playerId;
            this.deathPos = deathPos;
            this.deathTime = deathTime;
            this.harpoonId = harpoonId;
        }
    }

    /**
     * 在 LivingDeathEvent 中记录击杀信息，但不移除鱼叉
     * 使用 HIGHEST 优先级确保最先执行
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        Entity killCredit = entity.getKillCredit();
        if (!(killCredit instanceof Player player)) {
            return;
        }

        // 查找玩家发射的鱼叉
        for (TetheredHarpoonEntity harpoon : entity.level().getEntitiesOfClass(
                TetheredHarpoonEntity.class,
                entity.getBoundingBox().inflate(10))) {

            if (harpoon.getOwner() == player &&
                    (harpoon.currentState == TetheredHarpoonEntity.HarpoonState.HOOKED_IN_ENTITY ||
                            harpoon.getLastHitEntity() == entity) &&
                    (harpoon.getHitEntity() == entity || harpoon.getLastHitEntity() == entity)) {

                // 记录击杀信息，包括鱼叉ID
                long gameTime = entity.level().getGameTime();
                harpoonKills.put(entity.getUUID(), new HarpoonKillInfo(
                        player.getId(),
                        entity.position(),
                        gameTime,
                        harpoon.getId()
                ));

                LOGGER.debug("Harpoon kill registered: {} killed by {}, harpoon ID: {}",
                        entity.getName().getString(), player.getName().getString(), harpoon.getId());
                break;
            }
        }
    }

    /**
     * 处理掉落物
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        UUID entityUUID = entity.getUUID();
        HarpoonKillInfo killInfo = harpoonKills.get(entityUUID);

        if (killInfo == null) {
            return;
        }

        Player player = (Player) entity.level().getEntity(killInfo.playerId);
        if (player == null) {
            harpoonKills.remove(entityUUID);
            return;
        }

        long tetherEndTime = entity.level().getGameTime() + 200;

        // 给所有掉落物添加牵引标记
        for (ItemEntity drop : event.getDrops()) {
            CompoundTag tag = drop.getPersistentData();
            tag.putBoolean("HarpoonTethered", true);
            tag.putInt("TetherPlayerId", player.getId());
            tag.putLong("TetherEndTime", tetherEndTime);

            // 给予初始速度朝向玩家
            double dx = player.getX() - drop.getX();
            double dy = player.getY() - drop.getY();
            double dz = player.getZ() - drop.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > 0) {
                drop.setDeltaMovement(dx * 0.2, dy * 0.2 + Math.sqrt(distance) * 0.1, dz * 0.2);
            }
            drop.setPickUpDelay(10);
        }

        LOGGER.debug("Marked {} drops for tethering to player {}",
                event.getDrops().size(), player.getName().getString());

        // 收回鱼叉
        Entity harpoonEntity = entity.level().getEntity(killInfo.harpoonId);
        if (harpoonEntity instanceof TetheredHarpoonEntity harpoon) {
            harpoon.startRetrieving();
            LOGGER.debug("Harpoon retrieved after drops processed");
        }

        // 在这里删除记录（掉落物处理完成后）
        harpoonKills.remove(entityUUID);
    }

    /**
     * 处理经验球
     */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof ExperienceOrb orb)) {
            return;
        }

        Vec3 orbPos = orb.position();
        long currentTime = event.getLevel().getGameTime();

        // 查找附近最近的鱼叉击杀记录
        HarpoonKillInfo matchedKill = null;
        // UUID matchedUUID = null;  // 不再需要
        double closestDistance = 3.0;

        for (Map.Entry<UUID, HarpoonKillInfo> entry : harpoonKills.entrySet()) {
            HarpoonKillInfo info = entry.getValue();

            // 检查时间（10 tick内）
            if (currentTime - info.deathTime > 10) {
                continue;
            }

            // 检查距离
            double distance = orbPos.distanceTo(info.deathPos);
            if (distance < closestDistance) {
                closestDistance = distance;
                matchedKill = info;
                // matchedUUID = entry.getKey();
            }
        }

        if (matchedKill == null) {
            return;
        }

        Player player = (Player) event.getLevel().getEntity(matchedKill.playerId);
        if (player == null) {
            return;
        }

        // 给经验球添加牵引标记
        long tetherEndTime = currentTime + 200;
        CompoundTag tag = orb.getPersistentData();
        tag.putBoolean("HarpoonTethered", true);
        tag.putInt("TetherPlayerId", player.getId());
        tag.putLong("TetherEndTime", tetherEndTime);

        // 给予初始速度朝向玩家
        double dx = player.getX() - orb.getX();
        double dy = player.getY() - orb.getY();
        double dz = player.getZ() - orb.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > 0) {
            orb.setDeltaMovement(dx * 0.3, dy * 0.3 + 0.2, dz * 0.3);
        }

        LOGGER.debug("Marked experience orb for tethering to player {}", player.getName().getString());

        // 不要在这里删除记录！让 cleanupOldRecords 自动清理
        // harpoonKills.remove(matchedUUID);  ← 删除这行
    }

    // 定期清理过期的击杀记录
    public static void cleanupOldRecords(long currentGameTime) {
        harpoonKills.entrySet().removeIf(entry ->
                currentGameTime - entry.getValue().deathTime > 100);
    }
}