package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.item.HarpoonItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonDropHandler {

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        ItemEntity itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItem();

        if (stack.getItem() instanceof HarpoonItem) {
            Player player = event.getPlayer();
            if (player != null) {
                // 获取玩家的视线方向
                Vec3 lookVec = player.getLookAngle();

                // 添加一些随机性
                float spread = 1.0F;
                double motionX = lookVec.x * 0.3 + (player.getRandom().nextFloat() - 0.5) * spread;
                double motionY = lookVec.y * 0.3 + 0.2; // 添加向上的速度
                double motionZ = lookVec.z * 0.3 + (player.getRandom().nextFloat() - 0.5) * spread;

                // 设置物品实体的速度
                itemEntity.setDeltaMovement(motionX, motionY, motionZ);

                // 设置拾取延迟，防止立即被捡回
                itemEntity.setPickUpDelay(40);

                // 确保物品实体的物理属性正确
                itemEntity.setNoGravity(false);
            }
        }
    }
}