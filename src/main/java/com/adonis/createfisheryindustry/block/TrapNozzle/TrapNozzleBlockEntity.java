package com.adonis.createfisheryindustry.block.TrapNozzle;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TrapNozzleBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    protected static final int INVENTORY_SLOTS = 9;
    protected static final double COLLECTION_RANGE = 1.5;
    protected static final double ENTITY_PROCESSING_RANGE = 1.5;
    protected static final int PROCESSING_TIME = 10;
    private static final double MAX_COLLISION_BOX_SIZE = 0.8;

    protected final ItemStackHandler inventory;
    protected int processingTicks = 0;
    private List<Entity> pushingEntities = new ArrayList<>();
    private float range;
    private boolean pushing;
    private BlockPos fanPos;

    private final Lazy<IItemHandler> insertionHandler;
    private final Lazy<IItemHandler> extractionHandler;

    public TrapNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = createInventory();
        setLazyTickRate(5);
        this.insertionHandler = Lazy.of(() -> new InsertionOnlyItemHandler(inventory));
        this.extractionHandler = Lazy.of(() -> new ExtractionOnlyItemHandler(inventory));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    protected ItemStackHandler createInventory() {
        return new ItemStackHandler(INVENTORY_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        };
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, TrapNozzleBlockEntity be) {
        be.tickNozzle();
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            be.processingTicks++;
            if (be.processingTicks >= PROCESSING_TIME) {
                be.processingTicks = 0;
                be.collectNearbyItems(serverLevel);
                be.tryProcessEntities(serverLevel);
                be.setChanged();
                be.sendData();
                serverLevel.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    // 修改 tickNozzle 方法
    private void tickNozzle() {
        float range = calcRange();
        if (this.range != range) {
            setRange(range);
        }

        Vec3 center = Vec3.atCenterOf(worldPosition);

        if (level.isClientSide) {
            if (range != 0) {
                int probability = Mth.clamp((10 - (int) range), 1, 10);
                if (level.random.nextInt(probability) == 0) {
                    Vec3 start = VecHelper.offsetRandomly(center, level.random, pushing ? 1 : range / 2);
                    float speedFactor = pushing ? 0.05f : 1.0f;
                    Vec3 motion = center.subtract(start)
                            .normalize()
                            .scale(Mth.clamp(range * speedFactor, 0, 0.5f) * (pushing ? -1 : 1));
                    boolean inWater = getBlockState().getValue(TrapNozzleBlock.WATERLOGGED);
                    if (inWater) {
                        level.addParticle(new FluidParticleData(AllParticleTypes.FLUID_DRIP.get(), new FluidStack(Fluids.WATER, 1000)),
                                start.x, start.y, start.z, motion.x, motion.y, motion.z);
                        CreateFisheryMod.LOGGER.debug("Spawned FLUID_PARTICLE at ({}, {}, {}) with motion ({}, {}, {})",
                                start.x, start.y, start.z, motion.x, motion.y, motion.z);
                    } else {
                        level.addParticle(ParticleTypes.POOF, start.x, start.y, start.z, motion.x, motion.y, motion.z);
                        CreateFisheryMod.LOGGER.debug("Spawned POOF particle at ({}, {}, {}) with motion ({}, {}, {})",
                                start.x, start.y, start.z, motion.x, motion.y, motion.z);
                    }
                }
            }
        }

        for (Iterator<Entity> iterator = pushingEntities.iterator(); iterator.hasNext(); ) {
            Entity entity = iterator.next();
            Vec3 diff = entity.position().subtract(center);

            if (!(entity instanceof Player) && level.isClientSide) {
                continue;
            }

            double distance = diff.length();
            if (distance > range || entity.isShiftKeyDown() || isPlayerCreativeFlying(entity)) {
                iterator.remove();
                continue;
            }

            if (!pushing && distance < 1.5f) {
                continue;
            }

            float factor = (entity instanceof ItemEntity) ? 1 / 128f : 1 / 32f;
            Vec3 pushVec = diff.normalize().scale((range - distance) * (pushing ? 1 : -1));
            entity.setDeltaMovement(entity.getDeltaMovement().add(pushVec.scale(factor)));
            entity.fallDistance = 0;
            entity.hurtMarked = true;
        }

        lazyTickNozzle();
    }

    private void lazyTickNozzle() {
        if (range == 0) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(worldPosition);
        AABB bb = new AABB(center, center).inflate(range / 2f);

        for (Entity entity : level.getEntitiesOfClass(Entity.class, bb)) {
            Vec3 diff = entity.position().subtract(center);

            double distance = diff.length();
            if (distance > range || entity.isShiftKeyDown() || isPlayerCreativeFlying(entity)) {
                continue;
            }

            boolean canSee = canSee(entity);
            if (!canSee) {
                pushingEntities.remove(entity);
                continue;
            }

            if (!pushingEntities.contains(entity)) {
                pushingEntities.add(entity);
            }
        }

        for (Iterator<Entity> iterator = pushingEntities.iterator(); iterator.hasNext(); ) {
            Entity entity = iterator.next();
            if (!entity.isAlive()) {
                iterator.remove();
            }
        }
    }

    private float calcRange() {
        if (fanPos == null) {
            fanPos = worldPosition.relative(getBlockState().getValue(NozzleBlock.FACING).getOpposite());
        }
        BlockEntity be = level.getBlockEntity(fanPos);
        if (!(be instanceof IAirCurrentSource source)) {
            return 0;
        }
        if (source.getAirCurrent() == null || source.getSpeed() == 0) {
            return 0;
        }
        pushing = source.getAirFlowDirection() == source.getAirflowOriginSide();
        return source.getMaxDistance();
    }

    private boolean isPlayerCreativeFlying(Entity entity) {
        return entity instanceof Player player && player.getAbilities().flying;
    }

    private boolean canSee(Entity entity) {
        Vec3 start = entity.position();
        Vec3 end = Vec3.atCenterOf(worldPosition);
        return level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, entity))
                .getBlockPos().equals(worldPosition);
    }

    public void setRange(float range) {
        this.range = range;
        if (range == 0) {
            pushingEntities.clear();
        }
        sendData();
    }

    protected void collectNearbyItems(ServerLevel level) {
        AABB boundingBox = new AABB(getBlockPos()).inflate(COLLECTION_RANGE);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, boundingBox);

        for (ItemEntity itemEntity : items) {
            if (itemEntity == null || !itemEntity.isAlive()) {
                continue;
            }
            ItemStack stack = itemEntity.getItem();
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                if (insertItem(copy)) {
                    itemEntity.discard();
                } else if (copy.getCount() < stack.getCount()) {
                    itemEntity.setItem(copy);
                }
            }
        }
    }

    protected void tryProcessEntities(ServerLevel level) {
        CreateFisheryCommonConfig.refreshCache();

        AABB boundingBox = new AABB(getBlockPos()).inflate(ENTITY_PROCESSING_RANGE);
        List<Entity> entities = level.getEntitiesOfClass(Entity.class, boundingBox);

        for (Entity entity : entities) {
            if (!(entity instanceof Mob mob) || !entity.isAlive()) {
                continue;
            }

            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

            if (CreateFisheryCommonConfig.isEntityBlacklisted(entityId)) {
                continue;
            }

            if (CreateFisheryCommonConfig.isEntityWhitelisted(entityId)) {
                processEntityDrops(level, mob);
                continue;
            }

            AABB collisionBox = entity.getBoundingBox();
            double width = collisionBox.getXsize();
            double height = collisionBox.getYsize();
            double depth = collisionBox.getZsize();

            if (width <= MAX_COLLISION_BOX_SIZE && height <= MAX_COLLISION_BOX_SIZE && depth <= MAX_COLLISION_BOX_SIZE) {
                processEntityDrops(level, mob);
            }
        }
    }

    // 修改 processEntityDrops 方法
    private void processEntityDrops(ServerLevel level, Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        CreateFisheryMod.LOGGER.debug("Processing drops for entity in TrapNozzle: {}", entityId);

        var lootTableKey = mob.getLootTable();
        if (lootTableKey == null) {
            return;
        }

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
            }
        }

        mob.setRemoved(Entity.RemovalReason.KILLED);
        boolean inWater = getBlockState().getValue(TrapNozzleBlock.WATERLOGGED);
        spawnParticles(level, mob.getX(), mob.getY() + 0.5, mob.getZ(), inWater);
        addExperienceNugget(level, mob.getX(), mob.getY() + 0.5, mob.getZ());
        CreateFisheryMod.LOGGER.debug("Successfully captured entity in TrapNozzle: {}", entityId);
    }

    // 新增 spawnParticles 方法
    private void spawnParticles(ServerLevel level, double x, double y, double z, boolean inWater) {
        var particleType = inWater ? ParticleTypes.BUBBLE : ParticleTypes.CLOUD;
        level.sendParticles(particleType, x, y, z, 30, 0.5, 0.5, 0.5, 0.1);
        level.playSound(null, new BlockPos((int) x, (int) y, (int) z),
                SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        CreateFisheryMod.LOGGER.debug("Spawned {} particles at ({}, {}, {}), inWater: {}",
                particleType == ParticleTypes.BUBBLE ? "BUBBLE" : "CLOUD", x, y, z, inWater);
    }

    // 新增 addExperienceNugget 方法
    private void addExperienceNugget(ServerLevel level, double x, double y, double z) {
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
                ItemEntity itemEntity = new ItemEntity(level, x, y, z, remainder);
                level.addFreshEntity(itemEntity);
            }
            CreateFisheryMod.LOGGER.debug("Added experience nugget at ({}, {}, {})", x, y, z);
        } else {
            CreateFisheryMod.LOGGER.warn("Experience nugget item not found, falling back to ExperienceOrb at ({}, {}, {})", x, y, z);
            level.addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(level, x, y, z, 1));
        }
    }

    public boolean insertItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStack remainder = stack.copy();
        for (int i = 0; i < inventory.getSlots(); i++) {
            remainder = inventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                return true;
            }
        }
        return stack.getCount() != remainder.getCount();
    }

    public void dropInventory() {
        if (level instanceof ServerLevel serverLevel) {
            Vec3 pos = Vec3.atCenterOf(worldPosition);
            for (int i = 0; i < inventory.getSlots(); i++) {
                ItemStack stack = inventory.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    ItemEntity itemEntity = new ItemEntity(level, pos.x, pos.y, pos.z, stack);
                    serverLevel.addFreshEntity(itemEntity);
                    inventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("ProcessingTicks", processingTicks);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        processingTicks = compound.getInt("ProcessingTicks");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.trap_nozzle_contents").forGoggles(tooltip);

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

    // 这是NeoForge 1.21新的能力注册方法
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.TRAP_NOZZLE.get(),
                (be, side) -> {
                    if (side == null) {
                        return be.inventory;
                    } else if (side == Direction.DOWN) {
                        return be.extractionHandler.get();
                    } else {
                        return be.insertionHandler.get();
                    }
                }
        );
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