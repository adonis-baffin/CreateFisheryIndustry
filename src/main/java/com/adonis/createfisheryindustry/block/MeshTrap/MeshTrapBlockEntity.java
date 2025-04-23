
package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.block.common.TrapBlockEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

public class MeshTrapBlockEntity extends TrapBlockEntity {
    protected static final int PROCESSING_TIME = 20;
    protected static final double ENTITY_KILL_RANGE = 1.0; // 与 FrameTrapMovementBehaviour 一致
    protected static final float PHANTOM_MAX_HEALTH = 20.0F; // 与 FrameTrapMovementBehaviour 一致
    private static final Set<String> FISH_ITEM_IDS = new HashSet<>(Arrays.asList(
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:tropical_fish",
            "minecraft:pufferfish",
            "minecraft:cooked_cod",
            "minecraft:cooked_salmon"));

    public MeshTrapBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.MESH_TRAP.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MeshTrapBlockEntity be) {
        if (level.isClientSide) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            be.collectNearbyItems(serverLevel);
            be.tryProcessFish(serverLevel);
        }
    }

    @Override
    protected void tryProcessFish(ServerLevel level) {
        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_KILL_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;

            // Check for rabbits and phantoms
            boolean isRabbit = entity instanceof Rabbit || entity.getType() == EntityType.RABBIT;
            boolean isPhantom = entity instanceof Phantom || entity.getType() == EntityType.PHANTOM;

            // Check for aquatic entities
            boolean isAquatic = entity.getType() == EntityType.COD ||
                    entity.getType() == EntityType.SALMON ||
                    entity.getType() == EntityType.TROPICAL_FISH ||
                    entity.getType() == EntityType.PUFFERFISH;

            if (!isAquatic && !isRabbit && !isPhantom) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().toLowerCase();
                isAquatic = entityId.contains("fish") || entityId.contains("cod") ||
                        entityId.contains("salmon") || entityId.contains("squid");
            }

            if ((isAquatic || isRabbit || isPhantom) && entity instanceof Mob mob) {
                // Apply health limit for aquatic entities and rabbits, but not for phantoms
                if ((isAquatic || isRabbit) && mob.getMaxHealth() >= 10.0F) continue;
                if (isPhantom && mob.getMaxHealth() > PHANTOM_MAX_HEALTH) continue;

                // Get the ResourceKey<LootTable> and use it directly
                ResourceKey<LootTable> lootTableKey = mob.getLootTable();
                if (lootTableKey == null) continue;

                // Build LootParams with additional context for phantoms
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

                // Log generated loot for debugging
                for (ItemStack stack : loots) {
                    System.out.println("MeshTrapBlockEntity: Generated loot for " + mob.getType().getDescriptionId() +
                            ": " + stack.getCount() + "x " + stack.getItem().getDescriptionId());
                }

                // For aquatic entities, check if loot contains fish-related items
                boolean isValidLoot = isRabbit || isPhantom;
                if (isAquatic) {
                    boolean hasFish = false;
                    for (ItemStack stack : loots) {
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (FISH_ITEM_IDS.contains(itemId) || itemId.contains("fish") || itemId.contains("cod") || itemId.contains("salmon")) {
                            hasFish = true;
                            break;
                        }
                    }
                    isValidLoot = hasFish;
                }

                if (!isValidLoot) continue;

                // Insert loots into inventory
                boolean allInserted = true;
                for (ItemStack stack : loots) {
                    ItemStack remainder = stack.copy();
                    for (int i = 0; i < inventory.getSlots(); i++) {
                        remainder = inventory.insertItem(i, remainder, false);
                        if (remainder.isEmpty()) break;
                    }
                    if (!remainder.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                        level.addFreshEntity(itemEntity);
                        allInserted = false;
                        System.out.println("MeshTrapBlockEntity: Dropped " + remainder.getCount() + "x " + remainder.getItem().getDescriptionId() + " at " + mob.position());
                    }
                    System.out.println("MeshTrapBlockEntity: Captured entity " + mob.getType().getDescriptionId() +
                            ", dropped " + stack.getCount() + "x " + stack.getItem().getDescriptionId() + " at " + getBlockPos() +
                            (isRabbit ? " (Rabbit)" : isPhantom ? " (Phantom)" : " (Aquatic)"));
                }

                if (allInserted) {
                    mob.setRemoved(Entity.RemovalReason.KILLED);
                }
            }
        }
    }
}
