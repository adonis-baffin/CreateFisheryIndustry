package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonDeathEventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarpoonDeathEventHandler.class);

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }

        // 检查实体是否被玩家击杀
        Entity killCredit = entity.getKillCredit();
        if (killCredit instanceof Player player) {
            // 查找玩家发射的鱼叉
            boolean foundHarpoon = false;
            for (TetheredHarpoonEntity harpoon : entity.level().getEntitiesOfClass(TetheredHarpoonEntity.class, entity.getBoundingBox().inflate(10))) {
                if (harpoon.getOwner() == player &&
                        (harpoon.currentState == TetheredHarpoonEntity.HarpoonState.HOOKED_IN_ENTITY || harpoon.getLastHitEntity() == entity) &&
                        (harpoon.getHitEntity() == entity || harpoon.getLastHitEntity() == entity) &&
                        harpoon.getHitTick() >= harpoon.tickCount - 10) {
                    foundHarpoon = true;
                    // 阻止默认掉落
                    event.setCanceled(true); // 取消事件以阻止默认掉落
                    entity.skipDropExperience(); // 阻止经验掉落

                    // 调用 pullLootToPlayer 生成自定义掉落
                    harpoon.pullLootToPlayer(entity);

                    // 收回鱼叉
                    harpoon.startRetrieving();

                    break;
                }
            }
            if (!foundHarpoon) {
            }
        } else {
        }
    }
}