package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.block.common.TrapBlockEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MeshTrapBlockEntity extends TrapBlockEntity implements IHaveGoggleInformation {
    protected static final int PROCESSING_TIME = 10;
    protected static final double ENTITY_KILL_RANGE = 1.0;
    protected static final float PHANTOM_MAX_HEALTH = 20.0F;
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
        if (level.isClientSide()) {
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

            be.setChanged();
            be.sendData();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        be.tick();
    }

    @Override
    protected void tryProcessFish(ServerLevel level) {
        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_KILL_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (entity == null || !entity.isAlive()) continue;

            boolean isRabbit = entity instanceof Rabbit || entity.getType() == EntityType.RABBIT;
            boolean isPhantom = entity instanceof Phantom || entity.getType() == EntityType.PHANTOM;

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
                if ((isAquatic || isRabbit) && mob.getMaxHealth() >= 10.0F) continue;
                if (isPhantom && mob.getMaxHealth() > PHANTOM_MAX_HEALTH) continue;

                List<ItemStack> loots;
                if (isRabbit) {
                    loots = generateRabbitLoot(level.random);
                } else {
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
                    loots = lootTable.getRandomItems(params);
                }

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
                    }
                }

                mob.setRemoved(Entity.RemovalReason.KILLED);

                setChanged();
                sendData();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    }

    private List<ItemStack> generateRabbitLoot(RandomSource random) {
        List<ItemStack> loots = new ArrayList<>();
        loots.add(new ItemStack(Items.RABBIT, 1)); // 100% chance for 1 raw rabbit
        if (random.nextFloat() < 0.5f) { // 50% chance for 1 rabbit hide
            loots.add(new ItemStack(Items.RABBIT_HIDE, 1));
        }
        if (random.nextFloat() < 0.1f) { // 10% chance for 1 rabbit foot
            loots.add(new ItemStack(Items.RABBIT_FOOT, 1));
        }
        return loots;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.mesh_trap_contents").forGoggles(tooltip);

        ItemStackHandler inv = getInventory();
        boolean isEmpty = true;

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (!stack.isEmpty()) {
                isEmpty = false;
                CreateLang.text("")
                        .add(Component.translatable(stack.getDescriptionId()).withStyle(ChatFormatting.GRAY))
                        .add(CreateLang.text(" x" + stack.getCount()).style(ChatFormatting.GREEN))
                        .forGoggles(tooltip, 1);
            }
        }

        if (isEmpty) {
            CreateLang.translate("gui.goggles.inventory.empty").forGoggles(tooltip, 1);
        }

        return true;
    }

    @Override
    public @Nullable <T> T getCapability(net.neoforged.neoforge.capabilities.BlockCapability<T, @Nullable Direction> capability, @Nullable Direction side) {
        if (capability == Capabilities.ItemHandler.BLOCK) {
            return (T) inventory;
        }
        return null;
    }
}