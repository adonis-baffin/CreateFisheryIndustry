package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.common.TrapBlockEntity;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MeshTrapBlockEntity extends TrapBlockEntity implements IHaveGoggleInformation {
    protected static final int PROCESSING_TIME = 10;
    protected static final double ENTITY_KILL_RANGE = 1.0;
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    private final IItemHandler insertionHandler;
    private final IItemHandler extractionHandler;

    public MeshTrapBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.MESH_TRAP.get(), pos, state);
        this.insertionHandler = new InsertionOnlyItemHandler(inventory);
        this.extractionHandler = new ExtractionOnlyItemHandler(inventory);
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
            be.tryProcessEntities(serverLevel);

            be.setChanged();
            be.sendData();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        be.tick();
    }

    protected void tryProcessEntities(ServerLevel level) {
        CreateFisheryCommonConfig.refreshCache();

        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_KILL_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob) || !entity.isAlive()) {
                continue;
            }

            EntityType<?> entityType = entity.getType();
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);

            if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Found breeze entity in MeshTrap: {}", entityId);
            }

            if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
                if (entityId.toString().equals("minecraft:breeze")) {
                    CreateFisheryMod.LOGGER.debug("Breeze is in blacklist, skipping in MeshTrap");
                }
                continue;
            }

            if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
                if (entityId.toString().equals("minecraft:breeze")) {
                    CreateFisheryMod.LOGGER.debug("Breeze is in whitelist, capturing in MeshTrap");
                }
                processEntityDrops(level, mob);
                continue;
            }

            if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Breeze is neither in whitelist nor blacklist, checking size in MeshTrap");
            }

            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Breeze size in MeshTrap: {}x{}x{}, limit: {}",
                        width, height, depth, MAX_COLLISION_BOX_SIZE);
            }

            if (width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE) {
                if (entityId.toString().equals("minecraft:breeze")) {
                    CreateFisheryMod.LOGGER.debug("Breeze size check passed in MeshTrap, capturing");
                }
                processEntityDrops(level, mob);
            } else if (entityId.toString().equals("minecraft:breeze")) {
                CreateFisheryMod.LOGGER.debug("Breeze size check failed in MeshTrap, not capturing");
            }
        }
    }

    private void processEntityDrops(ServerLevel level, Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        CreateFisheryMod.LOGGER.debug("Processing drops for entity in MeshTrap: {}", entityId);

        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) return;

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

        boolean allInserted = true;
        for (ItemStack stack : loots) {
            ItemStack remainder = stack.copy();
            for (int i = 0; i < inventory.getSlots(); i++) {
                remainder = inventory.insertItem(i, remainder, false);
                if (remainder.isEmpty()) {
                    break;
                }
            }
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                level.addFreshEntity(itemEntity);
                allInserted = false;
            }
        }

        // 生成粒子效果（水下用气泡，陆地用云雾）
        boolean inWater = getBlockState().getValue(MeshTrapBlock.WATERLOGGED);
        var particleType = inWater ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD;
        level.sendParticles(particleType,
                mob.getX(), mob.getY() + 0.5, mob.getZ(),
                15, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, new BlockPos((int) mob.getX(), (int) mob.getY(), (int) mob.getZ()),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        CreateFisheryMod.LOGGER.debug("Spawned {} particles at ({}, {}, {}), inWater: {}",
                particleType == ParticleTypes.BUBBLE ? "BUBBLE" : "CLOUD",
                mob.getX(), mob.getY() + 0.5, mob.getZ(), inWater);

        // 固定生成1个经验颗粒
        Item expNuggetItem = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "experience_nugget"));
        if (expNuggetItem != null && expNuggetItem != BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("air"))) {
            ItemStack expNugget = new ItemStack(expNuggetItem, 1);
            ItemStack remainder = expNugget.copy();
            for (int i = 0; i < inventory.getSlots(); i++) {
                remainder = inventory.insertItem(i, remainder, false);
                if (remainder.isEmpty()) {
                    break;
                }
            }
            if (!remainder.isEmpty()) {
                ItemEntity itemEntity = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), remainder);
                level.addFreshEntity(itemEntity);
            }
        } else {
            // 回退到生成经验球
            CreateFisheryMod.LOGGER.warn("Experience nugget item not found, falling back to ExperienceOrb");
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, mob.getX(), mob.getY(), mob.getZ(), 1));
        }

        mob.setRemoved(Entity.RemovalReason.KILLED);

        CreateFisheryMod.LOGGER.debug("Successfully captured entity in MeshTrap: {}", entityId);

        setChanged();
        sendData();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
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
            if (side == null) {
                return (T) inventory;
            } else if (side == Direction.DOWN) {
                return (T) extractionHandler;
            } else {
                return (T) insertionHandler;
            }
        }
        return super.getCapability(capability, side);
    }

    private static class InsertionOnlyItemHandler implements IItemHandler {
        private final IItemHandler wrapped;

        public InsertionOnlyItemHandler(IItemHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() {
            return wrapped.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return wrapped.insertItem(slot, stack, simulate);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return wrapped.isItemValid(slot, stack);
        }
    }

    private static class ExtractionOnlyItemHandler implements IItemHandler {
        private final IItemHandler wrapped;

        public ExtractionOnlyItemHandler(IItemHandler wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() {
            return wrapped.getSlots();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return wrapped.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return wrapped.isItemValid(slot, stack);
        }
    }
}