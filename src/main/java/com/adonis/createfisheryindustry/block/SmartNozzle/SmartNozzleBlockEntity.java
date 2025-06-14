package com.adonis.createfisheryindustry.block.SmartNozzle;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.simibubi.create.AllParticleTypes;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.particle.FluidParticleData;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SmartNozzleBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    protected static final int INVENTORY_SLOTS = 9;
    protected static final double COLLECTION_RANGE = 1.5;
    protected static final int PROCESSING_TIME = 10;

    protected final ItemStackHandler inventory;
    protected int processingTicks = 0;
    private List<ItemEntity> pullingItems = new ArrayList<>();
    private List<Entity> pushingEntities = new ArrayList<>();
    private float range;
    private boolean pushing;
    private BlockPos fanPos;

    private final Lazy<IItemHandler> insertionHandler;
    private final Lazy<IItemHandler> extractionHandler;
    protected FilteringBehaviour filtering;

    public SmartNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.inventory = createInventory();
        setLazyTickRate(5);
        this.insertionHandler = Lazy.of(() -> new InsertionOnlyItemHandler(inventory));
        this.extractionHandler = Lazy.of(() -> new ExtractionOnlyItemHandler(inventory));
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(filtering = new FilteringBehaviour(this, new SmartNozzleFilterSlotPositioning()));
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

    public FilteringBehaviour getFiltering() {
        return filtering;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SmartNozzleBlockEntity be) {
        be.tickNozzle();
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            be.processingTicks++;
            if (be.processingTicks >= PROCESSING_TIME) {
                be.processingTicks = 0;
                be.collectNearbyItems(serverLevel);
                be.setChanged();
                be.sendData();
                serverLevel.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

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
                    boolean inWater = getBlockState().getValue(SmartNozzleBlock.WATERLOGGED);
                    if (inWater) {
                        level.addParticle(new FluidParticleData(AllParticleTypes.FLUID_DRIP.get(), new FluidStack(Fluids.WATER, 1000)),
                                start.x, start.y, start.z, motion.x, motion.y, motion.z);
                    } else {
                        level.addParticle(ParticleTypes.POOF, start.x, start.y, start.z, motion.x, motion.y, motion.z);
                    }
                }
            }
        }

        for (Iterator<ItemEntity> iterator = pullingItems.iterator(); iterator.hasNext(); ) {
            ItemEntity item = iterator.next();
            Vec3 diff = item.position().subtract(center);

            double distance = diff.length();
            if (distance > range || !item.isAlive()) {
                iterator.remove();
                continue;
            }

            if (!pushing && distance < 1.5f) {
                continue;
            }

            Vec3 pullVec = diff.normalize().scale((range - distance) * (pushing ? 1 : -1));
            item.setDeltaMovement(item.getDeltaMovement().add(pullVec.scale(1 / 128f)));
            item.fallDistance = 0;
            item.hurtMarked = true;
        }

        lazyTickNozzle();
    }

    private void lazyTickNozzle() {
        if (range == 0) {
            return;
        }

        Vec3 center = Vec3.atCenterOf(worldPosition);
        AABB bb = new AABB(center, center).inflate(range / 2f);

        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, bb)) {
            Vec3 diff = item.position().subtract(center);

            double distance = diff.length();
            if (distance > range || !item.isAlive()) {
                continue;
            }

            // 关键修改：如果设置了过滤器，只对符合过滤条件的物品产生气流效果
            if (filtering != null && !filtering.getFilter().isEmpty()) {
                if (!filtering.test(item.getItem())) {
                    pullingItems.remove(item); // 确保不符合条件的物品从列表中移除
                    continue; // 完全跳过不符合过滤条件的物品
                }
            }

            boolean canSee = canSee(item);
            if (!canSee) {
                pullingItems.remove(item);
                continue;
            }

            if (!pullingItems.contains(item)) {
                pullingItems.add(item);
            }
        }

        for (Iterator<ItemEntity> iterator = pullingItems.iterator(); iterator.hasNext(); ) {
            ItemEntity item = iterator.next();
            if (!item.isAlive()) {
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

    private boolean canSee(ItemEntity item) {
        Vec3 start = item.position();
        Vec3 end = Vec3.atCenterOf(worldPosition);
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, item))
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
            if (!stack.isEmpty() && canAcceptItem(stack)) {
                ItemStack copy = stack.copy();
                if (insertItem(copy)) {
                    itemEntity.discard();
                } else if (copy.getCount() < stack.getCount()) {
                    itemEntity.setItem(copy);
                }
            }
        }
    }

    protected boolean canAcceptItem(ItemStack stack) {
        return filtering.test(stack);
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
        CreateLang.translate("gui.goggles.smart_nozzle_contents").forGoggles(tooltip);

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

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                CreateFisheryBlockEntities.SMART_NOZZLE.get(),
                (be, side) -> be.inventory
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