package plus.dragons.createintegratedfarming.common.fishing.net;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.WaterAnimal;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FishingNetMovementBehaviour implements MovementBehaviour {
    private static final Set<String> FISH_ITEM_IDS = new HashSet<>(Arrays.asList(
            "minecraft:cod",
            "minecraft:salmon",
            "minecraft:tropical_fish",
            "minecraft:pufferfish",
            "minecraft:cooked_cod",
            "minecraft:cooked_salmon"
    ));

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
            var inWater = fishing.visitNewPositon(level, pos);
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
                    loots.forEach(stack -> {
                        dropItem(context, stack);
                    });
                }
            }
            fishing.reset(level);
        }
    }

    @Override
    public void stopMoving(MovementContext context) {
        if (context.temporaryData instanceof FishingNetContext fishing && context.world instanceof ServerLevel level) {
            fishing.invalidate(level);
        }
    }

    protected FishingNetContext getFishingNetContext(MovementContext context, ServerLevel level) {
        if (!(context.temporaryData instanceof FishingNetContext)) {
            context.temporaryData = new FishingNetContext(level, new ItemStack(Items.FISHING_ROD));
        }
        return (FishingNetContext) context.temporaryData;
    }

    protected void collectNearbyItems(MovementContext context, BlockPos pos, ServerLevel level) {
        AABB boundingBox = new AABB(pos).inflate(0.5);
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
        if (level == null) return;
        AABB boundingBox = new AABB(pos).inflate(0.75);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);
        for (Entity entity : entities) {
            if (entity == null) continue;
            boolean isAquatic = entity instanceof WaterAnimal ||
                    (entity.getType() == EntityType.COD ||
                            entity.getType() == EntityType.SALMON ||
                            entity.getType() == EntityType.TROPICAL_FISH ||
                            entity.getType() == EntityType.PUFFERFISH);
            if (!isAquatic) {
                String entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                isAquatic = entityId.contains("fish") || entityId.contains("cod") ||
                        entityId.contains("salmon") || entityId.contains("squid");
            }
            if (isAquatic && entity instanceof Mob mob) {
                if (mob.getMaxHealth() >= 10.0F) {
                    continue;
                }
                if (!mob.isAlive()) {
                    continue;
                }
                if (mob.getLootTable() == null) {
                    continue;
                }
                ResourceLocation lootTableLocation = mob.getLootTable().location();
                if (lootTableLocation == null) continue;
                ResourceKey<LootTable> lootTableKey = ResourceKey.create(Registries.LOOT_TABLE, lootTableLocation);
                LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(lootTableKey);
                LootParams.Builder paramsBuilder = new LootParams.Builder(level)
                        .withParameter(LootContextParams.THIS_ENTITY, mob)
                        .withParameter(LootContextParams.ORIGIN, mob.position())
                        .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
                LootParams params = paramsBuilder.create(LootContextParamSets.ENTITY);
                List<ItemStack> loots = lootTable.getRandomItems(params);
                boolean hasFish = false;
                for (ItemStack stack : loots) {
                    if (stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "foods/raw_fish")))) {
                        hasFish = true;
                        break;
                    }
                }
                if (!hasFish) {
                    for (ItemStack stack : loots) {
                        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                        if (FISH_ITEM_IDS.contains(itemId) || itemId.contains("fish") || itemId.contains("cod") || itemId.contains("salmon")) {
                            hasFish = true;
                            break;
                        }
                    }
                }
                if (!hasFish && entity instanceof WaterAnimal) {
                    hasFish = true;
                }
                if (!hasFish) {
                    continue;
                }
                boolean allInserted = true;
                for (ItemStack stack : loots) {
                    dropItem(context, stack);
                }
                if (allInserted) {
                    entity.setRemoved(Entity.RemovalReason.KILLED);
                    spawnBubbles(level, entity);
                }
            }
        }
    }

    private void spawnBubbles(ServerLevel level, Entity entity) {
        if (level == null || entity == null) return;
        level.sendParticles(ParticleTypes.BUBBLE,
                entity.getX(), entity.getY() + 0.5, entity.getZ(),
                15, 0.3, 0.3, 0.3, 0.05);
    }
}