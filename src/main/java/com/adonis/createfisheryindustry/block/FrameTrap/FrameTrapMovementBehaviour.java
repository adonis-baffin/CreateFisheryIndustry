package com.adonis.createfisheryindustry.block.FrameTrap;

import com.adonis.createfisheryindustry.block.FishingBearing.FishingBearingBlockEntity;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FrameTrapMovementBehaviour implements MovementBehaviour {
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (context.world instanceof ServerLevel level) {
            reportPositionToFishingBearing(context, pos, level);
            killNearbyEntities(context, pos, level);
            collectNearbyItems(context, pos, level);
        }
    }

    private void reportPositionToFishingBearing(MovementContext context, BlockPos worldPos, ServerLevel level) {
        AbstractContraptionEntity entity = context.contraption.entity;
        if (!(entity instanceof ControlledContraptionEntity controlledEntity))
            return;

        boolean inWater = level.getFluidState(worldPos).is(FluidTags.WATER);
        boolean inLava = level.getFluidState(worldPos).is(FluidTags.LAVA) &&
                CreateFisheryCommonConfig.isLavaFishingEnabled();

        Vec3 anchorVec = controlledEntity.getAnchorVec();
        BlockPos anchorPos = BlockPos.containing(anchorVec);

        for (Direction dir : Direction.values()) {
            BlockPos bearingPos = anchorPos.relative(dir);
            BlockEntity be = level.getBlockEntity(bearingPos);

            if (be instanceof FishingBearingBlockEntity fishingBearing) {
                fishingBearing.reportFrameTrapPosition(worldPos, inWater, inLava);
                return;
            }
        }
    }

    protected void collectNearbyItems(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.2);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);

        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                dropItem(context, stack.copy());
                itemEntity.discard();

                level.sendParticles(ParticleTypes.BUBBLE,
                        itemEntity.getX(), itemEntity.getY() + 0.5, itemEntity.getZ(),
                        5, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    protected void killNearbyEntities(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        CreateFisheryCommonConfig.refreshCache();

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob)) {
                continue;
            }

            EntityType<?> entityType = entity.getType();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

            if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
                continue;
            }

            if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
                processEntityCapture(context, level, mob);
                continue;
            }

            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (width <= MAX_COLLISION_BOX_SIZE &&
                    height <= MAX_COLLISION_BOX_SIZE &&
                    depth <= MAX_COLLISION_BOX_SIZE) {
                processEntityCapture(context, level, mob);
            }
        }
    }

    private void processEntityCapture(MovementContext context, ServerLevel level, Mob mob) {
        var lootTableKey = mob.getLootTable();
        if (lootTableKey != null) {
            LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, mob)
                    .withParameter(LootContextParams.ORIGIN, mob.position())
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());

            List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
            if (!players.isEmpty()) {
                Player nearestPlayer = level.getNearestPlayer(mob, -1);
                if (nearestPlayer instanceof ServerPlayer serverPlayer) {
                    paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, serverPlayer);
                }
            }

            LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
            LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
            List<ItemStack> loots = lootTable.getRandomItems(params);

            for (ItemStack stack : loots) {
                dropItem(context, stack);
            }
        }

        mob.setRemoved(Entity.RemovalReason.KILLED);

        addExperienceNugget(context, level, mob.getX(), mob.getY() + 0.5, mob.getZ());
        spawnCaptureParticles(level, mob.getX(), mob.getY() + 0.5, mob.getZ());
    }

    private void addExperienceNugget(MovementContext context, ServerLevel level, double x, double y, double z) {
        Item expNuggetItem = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));
        if (expNuggetItem != null &&
                expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            dropItem(context, expNugget);
        } else {
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, x, y, z, 1));
        }
    }

    private void spawnCaptureParticles(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.BUBBLE, x, y, z, 15, 0.5, 0.5, 0.5, 0.1);

        level.playSound(null, BlockPos.containing(x, y, z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}