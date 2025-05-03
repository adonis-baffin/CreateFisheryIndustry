package com.adonis.createfisheryindustry.event;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

@EventBusSubscriber(modid = CreateFisheryMod.ID)
public class MeshTrapInteractionHandler {

    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        ItemStack heldItem = player.getMainHandItem();
        Entity target = event.getTarget();

        // 检查玩家手持物品是否为 MeshTrap 的方块物品
        if (!heldItem.is(CreateFisheryBlocks.MESH_TRAP.asItem())) {
            return;
        }

        // 确保是在服务器端处理
        if (player.level().isClientSide() || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // 确保目标是 Mob 实体
        if (!(target instanceof Mob mob) || !mob.isAlive()) {
            return;
        }

        // 检查实体是否符合捕捉条件
        if (!canCaptureEntity(mob)) {
            return;
        }

        // 处理实体掉落物
        processEntityDrops(serverLevel, mob, player);

        // 移除实体
        mob.setRemoved(Entity.RemovalReason.KILLED);

        // 触发手臂摆动动画
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.swing(InteractionHand.MAIN_HAND, true);
        }

        // 消耗 MeshTrap 物品（可选，根据需求决定是否消耗）
        // heldItem.shrink(1);

        // 取消默认交互事件
        event.setCanceled(true);
    }

    // 检查实体是否可以被 MeshTrap 捕捉
    private static boolean canCaptureEntity(Mob mob) {
        CreateFisheryCommonConfig.refreshCache();

        EntityType<?> entityType = mob.getType();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        // 检查黑名单
        if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
            return false;
        }

        // 检查白名单
        if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
            return true;
        }

        // 检查碰撞箱大小
        var collisionBox = mob.getBoundingBox();
        double width = collisionBox.getXsize();
        double height = collisionBox.getYsize();
        double depth = collisionBox.getZsize();

        return width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE;
    }

    // 处理实体掉落物，优先放入玩家背包
    private static void processEntityDrops(ServerLevel level, Mob mob, Player player) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) return;

        // 构建战利品表参数
        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());

        if (player instanceof ServerPlayer serverPlayer) {
            paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
        }

        LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
        List<ItemStack> loots = lootTable.getRandomItems(params);

        // 优先尝试将掉落物放入玩家背包
        for (ItemStack stack : loots) {
            ItemStack remainder = stack.copy();
            if (!player.getInventory().add(remainder)) {
                // 如果背包满了，生成 ItemEntity
                ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                level.addFreshEntity(itemEntity);
            }
        }

        // 生成经验颗粒（与 MeshTrapBlockEntity 逻辑一致）
        Item expNuggetItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));
        if (expNuggetItem != null && expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            if (!player.getInventory().add(expNugget)) {
                ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), expNugget);
                level.addFreshEntity(itemEntity);
            }
        }

        // 播放音效和粒子效果（与 MeshTrapBlockEntity 一致）
        boolean inWater = level.getBlockState(mob.blockPosition()).getFluidState().isSource();
        var particleType = inWater ? net.minecraft.core.particles.ParticleTypes.BUBBLE : net.minecraft.core.particles.ParticleTypes.CLOUD;
        level.sendParticles(particleType, mob.getX(), mob.getY() + 0.5, mob.getZ(), 15, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, mob.blockPosition(), net.minecraft.sounds.SoundEvents.BUCKET_FILL_FISH, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}