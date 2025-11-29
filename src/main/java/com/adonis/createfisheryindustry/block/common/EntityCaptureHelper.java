package com.adonis.createfisheryindustry.block.common;

import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 实体捕获助手类
 * 提供统一的实体捕获逻辑，包括战利品生成、经验计算、音效和粒子效果
 */
public class EntityCaptureHelper {
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    /**
     * 捕获实体的主入口方法
     *
     * @param mob 被捕获的生物
     * @param level 服务器世界
     * @param capturePos 捕获位置
     * @param itemReceiver 物品接收器
     * @return 是否成功捕获
     */
    public static boolean captureEntity(Mob mob, ServerLevel level, BlockPos capturePos, IItemReceiver itemReceiver) {
        if (mob == null || level == null || !mob.isAlive()) {
            return false;
        }

        // 前置检查：接收器能否接收物品
        if (!itemReceiver.canReceive()) {
            return false;  // 无法接收物品，不捕获实体
        }

        // 1. 生成战利品
        List<ItemStack> loots = generateLoot(mob, level);
        for (ItemStack loot : loots) {
            itemReceiver.receiveItem(loot);
        }

        // 2. 生成经验粒
        ItemStack experienceNugget = generateExperienceNugget(mob, level);
        if (!experienceNugget.isEmpty()) {
            itemReceiver.receiveItem(experienceNugget);
        }

        // 3. 检测流体环境
        FluidEnvironment environment = detectFluidEnvironment(level, capturePos);

        // 4. 播放音效
        playEntityCaptureSound(level, capturePos, environment);

        // 5. 生成粒子效果
        Vec3 mobPos = new Vec3(mob.getX(), mob.getY() + 0.5, mob.getZ());
        spawnEntityCaptureParticles(level, mobPos, environment);

        // 6. 移除实体
        mob.setRemoved(Entity.RemovalReason.KILLED);

        return true;
    }

    /**
     * 检查实体是否应该被捕获
     *
     * @param entity 待检查的实体
     * @return 是否应该捕获
     */
    public static boolean shouldCaptureEntity(Entity entity) {
        // 仅处理 Mob 类型实体
        if (!(entity instanceof Mob)) {
            return false;
        }

        EntityType<?> entityType = entity.getType();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

        // 规则1：黑名单中的实体直接跳过
        if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
            return false;
        }

        // 规则2：白名单中的实体直接捕获
        if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
            return true;
        }

        // 规则3：根据尺寸判断
        AABB collisionBox = entity.getBoundingBox();
        double width = collisionBox.getXsize();
        double height = collisionBox.getYsize();
        double depth = collisionBox.getZsize();

        return width <= MAX_COLLISION_BOX_SIZE &&
                height <= MAX_COLLISION_BOX_SIZE &&
                depth <= MAX_COLLISION_BOX_SIZE;
    }

    /**
     * 生成战利品
     */
    private static List<ItemStack> generateLoot(Mob mob, ServerLevel level) {
        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) {
            return List.of();
        }

        LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());

        // 尝试获取最近的玩家作为击杀者
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            Player nearestPlayer = level.getNearestPlayer(mob, -1);
            if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
            }
        }

        LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);

        return lootTable.getRandomItems(params);
    }

    /**
     * 生成经验粒（使用概率结算）
     */
    private static ItemStack generateExperienceNugget(Mob mob, ServerLevel level) {
        // 获取生物经验值
        int experience = mob.getExperienceReward(level, null);

        if (experience <= 0) {
            return ItemStack.EMPTY;
        }

        // 计算经验粒数量（概率结算）
        int nuggetCount = calculateNuggetCount(experience, level.getRandom());

        if (nuggetCount <= 0) {
            return ItemStack.EMPTY;
        }

        // 获取经验粒物品
        Item expNuggetItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));

        if (expNuggetItem == null ||
                expNuggetItem == BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(expNuggetItem, nuggetCount);
    }

    /**
     * 根据经验值计算应该获取的经验粒数量（使用概率结算）
     *
     * @param experience 经验值
     * @param random 随机数生成器
     * @return 经验粒数量
     */
    private static int calculateNuggetCount(int experience, net.minecraft.util.RandomSource random) {
        // 1. 整除部分：确定性获取
        int guaranteedNuggets = experience / 3;

        // 2. 余数部分：概率性获取
        int remainder = experience % 3;
        int bonusNuggets = 0;

        if (remainder > 0) {
            // remainder = 1 时：1/3 概率获取 1 个
            // remainder = 2 时：2/3 概率获取 1 个
            if (random.nextInt(3) < remainder) {
                bonusNuggets = 1;
            }
        }

        return guaranteedNuggets + bonusNuggets;
    }

    /**
     * 检测流体环境
     */
    private static FluidEnvironment detectFluidEnvironment(ServerLevel level, BlockPos pos) {
        FluidState fluidAtPos = level.getFluidState(pos);

        // 检查是否在岩浆中
        if (fluidAtPos.is(FluidTags.LAVA)) {
            return FluidEnvironment.LAVA;
        }

        // 检查是否在水中
        if (fluidAtPos.is(FluidTags.WATER)) {
            // 检查上方是否也是水（判断水下还是水面）
            BlockPos abovePos = pos.above();
            FluidState fluidAbove = level.getFluidState(abovePos);

            if (fluidAbove.is(FluidTags.WATER)) {
                return FluidEnvironment.UNDERWATER;
            } else {
                return FluidEnvironment.WATER_SURFACE;
            }
        }

        // 其他情况视为干燥环境
        return FluidEnvironment.DRY;
    }

    /**
     * 播放实体捕获音效
     */
    private static void playEntityCaptureSound(ServerLevel level, BlockPos pos, FluidEnvironment environment) {
        if (environment == FluidEnvironment.LAVA) {
            // 岩浆环境：使用岩浆音效
            level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 1.2F);
        } else {
            // 其他环境：统一使用钓鱼音效
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * 生成实体捕获粒子效果
     */
    private static void spawnEntityCaptureParticles(ServerLevel level, Vec3 pos, FluidEnvironment environment) {
        double x = pos.x;
        double y = pos.y;
        double z = pos.z;

        switch (environment) {
            case LAVA:
                // 岩浆环境：熔岩粒子 + 烟雾粒子
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 8, 0.4, 0.4, 0.4, 0.1);
                level.sendParticles(ParticleTypes.SMOKE, x, y, z, 5, 0.3, 0.3, 0.3, 0.05);
                break;

            case UNDERWATER:
                // 水下：气泡粒子
                level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
                break;

            case WATER_SURFACE:
            case DRY:
            default:
                // 水面或陆地：云雾粒子
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);
                break;
        }
    }
}