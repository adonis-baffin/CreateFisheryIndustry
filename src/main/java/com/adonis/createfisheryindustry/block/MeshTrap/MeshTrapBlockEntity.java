package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.block.common.TrapBlockEntity;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MeshTrapBlockEntity extends TrapBlockEntity implements IHaveGoggleInformation {
    private static final Logger LOGGER = LogManager.getLogger();
    protected static final int PROCESSING_TIME = 20;
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

        be.tick(); // 调用同步计时
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

                LOGGER.info("MeshTrapBlockEntity: Processing entity {} at {} {}", mob.getType().getDescriptionId(), getBlockPos(), isRabbit ? "(Rabbit)" : isPhantom ? "(Phantom)" : "(Aquatic)");
                for (ItemStack stack : loots) {
                    LOGGER.info("MeshTrapBlockEntity: Generated loot: {} x{}", stack.getCount(), stack.getItem().getDescriptionId());
                }

                boolean allInserted = true;
                for (ItemStack stack : loots) {
                    ItemStack remainder = stack.copy();
                    for (int i = 0; i < inventory.getSlots(); i++) {
                        remainder = inventory.insertItem(i, remainder, false);
                        LOGGER.info("MeshTrapBlockEntity: Inserted into slot {}, remaining: {}", i, remainder.isEmpty() ? "None" : remainder.getCount() + "x " + remainder.getItem().getDescriptionId());
                        if (remainder.isEmpty()) break;
                    }
                    if (!remainder.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                        level.addFreshEntity(itemEntity);
                        allInserted = false;
                        LOGGER.info("MeshTrapBlockEntity: Dropped {} x{} at {}", remainder.getCount(), remainder.getItem().getDescriptionId(), mob.position());
                    }
                }

                setChanged();
                sendData();
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);

                if (allInserted) {
                    mob.setRemoved(Entity.RemovalReason.KILLED);
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        LOGGER.info("MeshTrapBlockEntity: addToGoggleTooltip called at {}, isClientSide: {}", getBlockPos(), level != null && level.isClientSide());

        CreateLang.translate("gui.goggles.mesh_trap_contents").forGoggles(tooltip);

        ItemStackHandler inv = getInventory();
        boolean isEmpty = true;

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            LOGGER.info("MeshTrapBlockEntity: Slot {}: {}", i, stack.isEmpty() ? "Empty" : stack.getCount() + "x " + stack.getItem().getDescriptionId());
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

        if (!level.isClientSide()) {
            IItemHandler capInv = level.getCapability(Capabilities.ItemHandler.BLOCK, getBlockPos(), getBlockState(), this, null);
            if (capInv != null) {
                LOGGER.info("MeshTrapBlockEntity: Capability inventory check at {}", getBlockPos());
                for (int i = 0; i < capInv.getSlots(); i++) {
                    ItemStack stack = capInv.getStackInSlot(i);
                    LOGGER.info("MeshTrapBlockEntity: Capability Slot {}: {}", i, stack.isEmpty() ? "Empty" : stack.getCount() + "x " + stack.getItem().getDescriptionId());
                }
            } else {
                LOGGER.warn("MeshTrapBlockEntity: Capability inventory is null at {}", getBlockPos());
            }
        }

        return true;
    }
}