

package com.adonis.createfisheryindustry.block.FrameTrap;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

public class FrameTrapMovementBehaviour implements MovementBehaviour {
    @Override
    public void tick(MovementContext context) {
        if (context.world instanceof ServerLevel level) {
            var fishing = getFishingNetContext(context, level);
            if (fishing.timeUntilCatch > 0)
                fishing.timeUntilCatch--;
        }
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        if (context.world instanceof ServerLevel level) {
            var fishing = getFishingNetContext(context, level);
            var inWater = fishing.visitNewPosition(level, pos);
            killNearbyAquaticEntities(context, pos, level);
            collectNearbyItems(context, pos, level);
            if (!inWater || fishing.timeUntilCatch > 0)
                return;
            if (fishing.canCatch()) {
                var params = fishing.buildLootContext(context, level, pos);
                LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
                List<ItemStack> loots = lootTable.getRandomItems(params);
                var event = NeoForge.EVENT_BUS.post(new ItemFishedEvent(loots, 0, fishing.getFishingHook()));
                if (!event.isCanceled()) {
                    loots.forEach(stack -> dropItem(context, stack));
                    spawnBubbles(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                }
            }
            fishing.reset(level);
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof FrameTrapContext fishing && context.world instanceof ServerLevel level) {
            fishing.invalidate(level);
        }
    }

    protected FrameTrapContext getFishingNetContext(MovementContext context, ServerLevel level) {
        if (!(context.temporaryData instanceof FrameTrapContext)) {
            context.temporaryData = new FrameTrapContext(level, new ItemStack(Items.FISHING_ROD));
        }
        return (FrameTrapContext) context.temporaryData;
    }

    protected void collectNearbyItems(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.2);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                dropItem(context, stack.copy());
                itemEntity.discard();
            }
        }
    }

    protected void killNearbyAquaticEntities(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);
        for (Entity entity : entities) {
            boolean isAquatic = entity.getType() == EntityType.COD || entity.getType() == EntityType.SALMON ||
                    entity.getType() == EntityType.TROPICAL_FISH || entity.getType() == EntityType.PUFFERFISH ||
                    entity.getType() == EntityType.SQUID || entity.getType() == EntityType.GLOW_SQUID;
            boolean isRabbit = entity.getType() == EntityType.RABBIT;
            boolean isPhantom = entity.getType() == EntityType.PHANTOM;

            if ((isAquatic || isRabbit || isPhantom) && entity instanceof Mob mob) {
                ResourceKey<LootTable> lootTableKey = mob.getLootTable();
                if (lootTableKey == null) continue;

                LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, mob)
                        .withParameter(LootContextParams.ORIGIN, mob.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
                if (isPhantom) {
                    paramsBuilder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, null);
                }
                LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);

                LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
                List<ItemStack> loots = lootTable.getRandomItems(params);
                for (ItemStack stack : loots) {
                    dropItem(context, stack);
                }

                mob.setRemoved(Entity.RemovalReason.KILLED);
                if (isAquatic) {
                    spawnBubbles(level, mob.getX(), mob.getY() + 0.5, mob.getZ());
                }
            }
        }
    }

    private void spawnBubbles(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.BUBBLE,
                x, y, z,
                30,
                0.5, 0.5, 0.5,
                0.1);

        level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
    }
}
