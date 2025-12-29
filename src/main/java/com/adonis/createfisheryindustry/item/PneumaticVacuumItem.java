package com.adonis.createfisheryindustry.item;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.List;
import java.util.function.Consumer;

public class PneumaticVacuumItem extends Item {

    public static final int MAX_DAMAGE = 250;
    private static final double RANGE = 8.0;
    private static final double STRENGTH = 0.08;
    private static final int AIR_CONSUMPTION_INTERVAL = 10;

    public PneumaticVacuumItem(Properties properties) {
        super(properties.stacksTo(1).durability(MAX_DAMAGE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    public void onUseTick(Level level, Player player, ItemStack stack, int count) {
        if (level.isClientSide()) {
            spawnParticles(player);
            return;
        }

        int useTicks = getUseDuration(stack, player) - count;

        // 每 AIR_CONSUMPTION_INTERVAL tick 消耗1空气
        if (!BacktankUtil.canAbsorbDamage(player, 1)) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
            player.stopUsingItem(); // 没气了自动停止
            return;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(RANGE));

        AABB searchBox = new AABB(eyePos, endPos).inflate(2.0);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchBox,
                entity -> entity.isAlive() && entity.distanceToSqr(player) > 1.0);

        for (ItemEntity item : items) {
            Vec3 itemPos = item.position();
            Vec3 pullDir = eyePos.subtract(itemPos).normalize();

            double distance = item.distanceTo(player);
            double factor = Math.max(0.1, 1.0 - (distance / RANGE)); // 越近吸力越强

            Vec3 pullVec = pullDir.scale(STRENGTH * factor);
            item.setDeltaMovement(item.getDeltaMovement().add(pullVec));
            item.hurtMarked = true;
            item.setPickUpDelay(0); // 允许立即被玩家捡起
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void spawnParticles(Player player) {
        RandomSource random = player.getRandom();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getViewVector(1.0F);

        for (int i = 0; i < 6; i++) { // 每tick 6个粒子
            Vec3 offset = lookVec.scale(0.5 + random.nextDouble() * (RANGE - 0.5));
            Vec3 particlePos = eyePos.add(offset);

            // 轻微随机偏移，模拟锥形吸气
            particlePos = particlePos.add(
                    (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 0.8,
                    (random.nextDouble() - 0.5) * 0.8
            );

            Vec3 motion = eyePos.subtract(particlePos).normalize().scale(0.2);

            player.level().addParticle(ParticleTypes.POOF,
                    particlePos.x, particlePos.y, particlePos.z,
                    motion.x, motion.y, motion.z);
        }
    }

    public int getUseDuration(ItemStack stack, Player player) {
        return 72000; // 足够长，实际由玩家松手控制
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return BacktankUtil.isBarVisible(stack, 1);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return BacktankUtil.getBarWidth(stack, 1);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BacktankUtil.getBarColor(stack, 1);
    }
}