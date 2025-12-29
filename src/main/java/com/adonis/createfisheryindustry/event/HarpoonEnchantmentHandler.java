package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryEnchantments;
import com.adonis.createfisheryindustry.registry.CreateFisheryItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 处理鱼叉枪专属附魔效果
 * - 处决 (Execution): 被勾住的实体生命值低于 3*等级 时直接斩杀
 * - 倒刺 (Barbed): 被勾住的实体每10tick受到 2*等级 的伤害
 */
@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class HarpoonEnchantmentHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(HarpoonEnchantmentHandler.class);

    private static final int BARBED_DAMAGE_INTERVAL = 10; // 倒刺伤害间隔（tick）

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        Entity entity = event.getEntity();

        if (entity.level().isClientSide()) {
            return;
        }

        // 只处理被鱼叉勾住的生物实体
        if (!(entity instanceof LivingEntity livingEntity)) {
            return;
        }

        // 查找勾住该实体的鱼叉
        List<TetheredHarpoonEntity> harpoons = entity.level().getEntitiesOfClass(
                TetheredHarpoonEntity.class,
                entity.getBoundingBox().inflate(5),
                harpoon -> harpoon.getHitEntity() == entity && !harpoon.isRetrieving()
        );

        if (harpoons.isEmpty()) {
            return;
        }

        TetheredHarpoonEntity harpoon = harpoons.get(0);

        if (!(harpoon.getOwner() instanceof Player player)) {
            return;
        }

        // 获取玩家手中的鱼叉枪
        ItemStack harpoonGun = getHarpoonGunFromPlayer(player);
        if (harpoonGun.isEmpty()) {
            return;
        }

        // 处理倒刺附魔
        int barbedLevel = CreateFisheryEnchantments.getBarbedLevel(harpoonGun, player.registryAccess());
        if (barbedLevel > 0) {
            applyBarbedDamage(livingEntity, player, harpoon, barbedLevel);
        }

        // 处理处决附魔
        int executionLevel = CreateFisheryEnchantments.getExecutionLevel(harpoonGun, player.registryAccess());
        if (executionLevel > 0) {
            applyExecution(livingEntity, player, harpoon, executionLevel);
        }
    }

    /**
     * 应用倒刺伤害
     */
    private static void applyBarbedDamage(LivingEntity target, Player player, TetheredHarpoonEntity harpoon, int level) {
        // 使用鱼叉的tickCount来计算间隔
        int ticksSinceHit = harpoon.tickCount - harpoon.getHitTick();

        // 每10tick造成一次伤害
        if (ticksSinceHit > 0 && ticksSinceHit % BARBED_DAMAGE_INTERVAL == 0) {
            float damage = 2.0F * level;
            DamageSource damageSource = target.level().damageSources().thorns(player);
            target.hurt(damageSource, damage);
            target.setLastHurtByPlayer(player);
        }
    }

    /**
     * 应用处决效果
     * 直接击杀目标，让 HarpoonDeathEventHandler 处理掉落物牵引
     */
    private static void applyExecution(LivingEntity target, Player player, TetheredHarpoonEntity harpoon, int level) {
        float healthThreshold = 3.0F * level;

        // 检查目标生命值是否低于阈值
        if (target.getHealth() <= healthThreshold && target.getHealth() > 0) {

            // 设置最后攻击玩家，确保掉落物和经验正确归属
            target.setLastHurtByPlayer(player);

            // 造成足够的伤害直接击杀（使用玩家攻击伤害源）
            // 这样会触发正常的死亡流程，HarpoonDeathEventHandler 会处理掉落物牵引
            DamageSource damageSource = target.level().damageSources().playerAttack(player);
            target.hurt(damageSource, Float.MAX_VALUE);

            // 注意：不需要手动收回鱼叉，HarpoonDeathEventHandler 会处理
        }
    }

    /**
     * 从玩家手中获取鱼叉枪
     */
    private static ItemStack getHarpoonGunFromPlayer(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();

        if (mainHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            return mainHand;
        } else if (offHand.getItem() == CreateFisheryItems.PNEUMATIC_HARPOON_GUN.get()) {
            return offHand;
        }
        return ItemStack.EMPTY;
    }
}