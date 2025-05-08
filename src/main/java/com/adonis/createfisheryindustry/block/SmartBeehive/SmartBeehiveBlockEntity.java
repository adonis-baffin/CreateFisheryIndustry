package com.adonis.createfisheryindustry.block.SmartBeehive;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlockEntities;
import com.google.common.collect.Lists;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Arrays;
import java.util.List;

public class SmartBeehiveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int MAX_OCCUPANTS = 3;
    private static final int PROCESSING_TIME = 20;
    private static final int FLUID_EXPORT_COOLDOWN = 20;
    private static final List<String> IGNORED_BEE_TAGS = Arrays.asList(
            "Air", "ArmorDropChances", "ArmorItems", "Brain", "CanPickUpLoot", "DeathTime", "FallDistance",
            "FallFlying", "Fire", "HandDropChances", "HandItems", "HurtByTimestamp", "HurtTime",
            "LeftHanded", "Motion", "NoGravity", "OnGround", "PortalCooldown", "Pos", "Rotation",
            "SleepingX", "SleepingY", "SleepingZ", "CannotEnterHiveTicks", "TicksSincePollination",
            "CropsGrownSincePollination", "hive_pos", "Passengers", "leash", "UUID"
    );

    private final List<BeeData> stored = Lists.newArrayList();
    private int processingTicks = 0;
    private int fluidExportTicks = 0;
    private BlockPos savedFlowerPos;

    // 物品和流体存储
    protected ItemStackHandler inventory;
    protected Lazy<FluidTank> fluidTank;
    public final IItemHandler insertionHandler;
    public final IItemHandler extractionHandler;

    // 模式切换
    protected ScrollOptionBehaviour<ProcessingMode> mode;

    public SmartBeehiveBlockEntity(BlockPos pos, BlockState state) {
        super(CreateFisheryBlockEntities.SMART_BEEHIVE.get(), pos, state);
        this.inventory = createInventory();
        this.fluidTank = Lazy.of(() -> new SmartFluidTank(1000, this::onFluidUpdate));
        this.insertionHandler = new InsertionOnlyItemHandler(inventory);
        this.extractionHandler = new ExtractionOnlyItemHandler(inventory);
    }

    protected ItemStackHandler createInventory() {
        return new ItemStackHandler(9) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        };
    }

    protected void onFluidUpdate(FluidStack fluid) {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public FluidTank getFluidTank() {
        return fluidTank.get();
    }

    public List<BeeData> getStored() {
        return stored;
    }

    public int getMaxOccupants() {
        return MAX_OCCUPANTS;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(mode = new ScrollOptionBehaviour<>(ProcessingMode.class,
                Component.translatable("createfisheryindustry.smart_beehive.mode"), this,
                new CenteredSideValueBoxTransform((state, d) -> d == Direction.UP)));
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SmartBeehiveBlockEntity be) {
        if (level.isClientSide()) {
            return;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        be.processingTicks++;
        if (be.processingTicks >= PROCESSING_TIME) {
            be.processingTicks = 0;
            tickOccupants(level, pos, state, be.stored, be.savedFlowerPos, be);
            be.setChanged();
            serverLevel.sendBlockUpdated(pos, state, state, 3);
        }

        be.fluidExportTicks++;
        if (be.fluidExportTicks >= FLUID_EXPORT_COOLDOWN) {
            be.fluidExportTicks = 0;
            be.tryExportFluid(serverLevel);
        }
    }

    public void addOccupantFromItem(Player player, ItemStack stack) {
        if (stored.size() < MAX_OCCUPANTS) {
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:bee");
            Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
            if (entity != null) {
                addOccupant(entity);
            }
        }
    }

    public void addOccupant(Entity occupant) {
        if (stored.size() >= MAX_OCCUPANTS) {
            return;
        }

        // 检查入口是否被阻挡
        Direction direction = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos entrancePos = worldPosition.relative(direction);
        boolean isEntranceBlocked = !level.getBlockState(entrancePos).getCollisionShape(level, entrancePos).isEmpty();
        if (isEntranceBlocked) {
            return;
        }

        occupant.stopRiding();
        occupant.ejectPassengers();

        boolean hasNectar = false;
        if (occupant instanceof Bee bee) {
            hasNectar = bee.hasNectar();
            if (bee.hasSavedFlowerPos() && (!hasSavedFlowerPos() || level.random.nextBoolean())) {
                savedFlowerPos = bee.getSavedFlowerPos();
            }
            // 立即处理蜂蜜
            if (hasNectar) {
                processHoneyDelivery();
            }
        }

        // 存储蜜蜂
        Occupant occupantData = Occupant.of(occupant);
        storeBee(occupantData);

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, worldPosition, GameEvent.Context.of(occupant, getBlockState()));
        }

        occupant.discard();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void storeBee(Occupant occupant) {
        stored.add(new BeeData(occupant));
    }

    private void processHoneyDelivery() {
        if (mode.get() == ProcessingMode.HONEY_FLUID) {
            FluidStack honeyFluid = new FluidStack(com.simibubi.create.AllFluids.HONEY.get(), 50);
            int filled = fluidTank.get().fill(honeyFluid, IFluidHandler.FluidAction.EXECUTE);
        } else {
            if (level.random.nextFloat() < 0.6F) {
                ItemStack honeycomb = new ItemStack(Items.HONEYCOMB, 1);
                boolean inserted = false;
                for (int i = 0; i < inventory.getSlots(); i++) {
                    ItemStack slotStack = inventory.getStackInSlot(i);
                    if (slotStack.isEmpty() || (ItemStack.isSameItem(slotStack, honeycomb) && slotStack.getCount() < slotStack.getMaxStackSize())) {
                        inventory.insertItem(i, honeycomb, false);
                        inserted = true;
                        break;
                    }
                }
            }
        }
    }

    public boolean isEmpty() {
        return stored.isEmpty();
    }

    public boolean isFull() {
        return stored.size() >= MAX_OCCUPANTS;
    }

    public void resetHoneyLevel() {
        // 无需 HONEY_LEVEL，保留空方法以兼容可能的外部调用
    }

    private static void tickOccupants(Level level, BlockPos pos, BlockState state, List<BeeData> data, BlockPos savedFlowerPos, SmartBeehiveBlockEntity be) {
        boolean changed = false;
        var iterator = data.iterator();

        while (iterator.hasNext()) {
            BeeData beeData = iterator.next();
            if (beeData.tick()) {
                BeeReleaseStatus status = beeData.hasNectar() ? BeeReleaseStatus.HONEY_DELIVERED : BeeReleaseStatus.BEE_RELEASED;
                if (releaseOccupant(level, pos, state, beeData.toOccupant(), null, status, savedFlowerPos, be)) {
                    changed = true;
                    iterator.remove();
                } else {
                }
            }
        }

        if (changed) {
            be.setChanged();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    private static boolean releaseOccupant(Level level, BlockPos pos, BlockState state, Occupant occupant, List<Entity> storedInHives, BeeReleaseStatus releaseStatus, BlockPos storedFlowerPos, SmartBeehiveBlockEntity be) {
        // 移除夜晚/下雨限制，允许随时释放
        // if ((level.isNight() || level.isRaining()) && releaseStatus != BeeReleaseStatus.EMERGENCY) {
        //     return false;
        // }

        Direction direction = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos frontPos = pos.relative(direction);
        boolean isBlocked = !level.getBlockState(frontPos).getCollisionShape(level, frontPos).isEmpty();
        if (isBlocked && releaseStatus != BeeReleaseStatus.EMERGENCY) {
            return false;
        }

        Entity entity = occupant.createEntity(level, pos);
        if (entity != null) {
            if (entity instanceof Bee bee) {
                if (storedFlowerPos != null && !bee.hasSavedFlowerPos() && level.random.nextFloat() < 0.9F) {
                    bee.setSavedFlowerPos(storedFlowerPos);
                }

                if (storedInHives != null) {
                    storedInHives.add(bee);
                }

                float f = entity.getBbWidth();
                double d0 = pos.getX() + 0.5 + (isBlocked ? 0.0 : 0.55 + f / 2.0) * direction.getStepX();
                double d1 = pos.getY() + 0.5 - (entity.getBbHeight() / 2.0);
                double d2 = pos.getZ() + 0.5 + (isBlocked ? 0.0 : 0.55 + f / 2.0) * direction.getStepZ();
                entity.moveTo(d0, d1, d2, entity.getYRot(), entity.getXRot());
            }

            level.playSound(null, pos, SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(entity, level.getBlockState(pos)));
            boolean added = level.addFreshEntity(entity);
            return added;
        }
        return false;
    }

    protected void tryExportFluid(ServerLevel level) {
        FluidTank tank = fluidTank.get();
        if (tank.getFluidAmount() == 0) {
            return;
        }

        BlockPos belowPos = worldPosition.below();
        IFluidHandler targetTank = level.getCapability(Capabilities.FluidHandler.BLOCK, belowPos, Direction.UP);
        if (targetTank != null) {
            FluidStack toDrain = tank.drain(50, IFluidHandler.FluidAction.SIMULATE);
            if (!toDrain.isEmpty()) {
                int filled = targetTank.fill(toDrain, IFluidHandler.FluidAction.EXECUTE);
                if (filled > 0) {
                    tank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                    setChanged();
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        }
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

    public void releaseAllOccupants(BlockState state, BeeReleaseStatus releaseStatus) {
        List<Entity> list = Lists.newArrayList();
        stored.removeIf(beeData -> releaseOccupant(level, worldPosition, state, beeData.toOccupant(), list, releaseStatus, savedFlowerPos, this));
        if (!list.isEmpty()) {
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private boolean hasSavedFlowerPos() {
        return savedFlowerPos != null;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("createfisheryindustry.smart_beehive.contents").forGoggles(tooltip);

        // 蜜蜂数量
        CreateLang.translate("createfisheryindustry.smart_beehive.bees", stored.size(), MAX_OCCUPANTS)
                .style(ChatFormatting.YELLOW)
                .forGoggles(tooltip, 1);

        // 物品库存
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

        // 流体存储
        FluidTank tank = fluidTank.get();
        if (tank.getFluidAmount() > 0) {
            CreateLang.translate("createfisheryindustry.smart_beehive.fluid",
                            Component.translatable(tank.getFluid().getDescriptionId()),
                            tank.getFluidAmount())
                    .style(ChatFormatting.AQUA)
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.translate("createfisheryindustry.smart_beehive.fluid_empty").forGoggles(tooltip, 1);
        }

        // 模式
        CreateLang.translate("createfisheryindustry.smart_beehive.mode_label")
                .add(Component.translatable(mode.get().getTranslationKey()).withStyle(ChatFormatting.GOLD))
                .forGoggles(tooltip, 1);

        return true;
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.put("FluidTank", fluidTank.get().writeToNBT(registries, new CompoundTag()));
        compound.putInt("ProcessingTicks", processingTicks);
        compound.putInt("FluidExportTicks", fluidExportTicks);

        // 序列化蜜蜂数据
        ListTag bees = new ListTag();
        for (BeeData bee : stored) {
            CompoundTag beeTag = new CompoundTag();
            bee.toOccupant().write(beeTag, registries);
            bees.add(beeTag);
        }
        compound.put("Bees", bees);

        if (hasSavedFlowerPos()) {
            compound.put("FlowerPos", NbtUtils.writeBlockPos(savedFlowerPos));
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        fluidTank.get().readFromNBT(registries, compound.getCompound("FluidTank"));
        processingTicks = compound.getInt("ProcessingTicks");
        fluidExportTicks = compound.getInt("FluidExportTicks");

        // 读取蜜蜂数据
        stored.clear();
        if (compound.contains("Bees")) {
            ListTag bees = compound.getList("Bees", CompoundTag.TAG_COMPOUND);
            for (int i = 0; i < bees.size(); i++) {
                CompoundTag beeTag = bees.getCompound(i);
                Occupant occupant = new Occupant(beeTag, registries);
                stored.add(new BeeData(occupant));
            }
        }

        savedFlowerPos = compound.contains("FlowerPos") ? NbtUtils.readBlockPos(compound, "FlowerPos").orElse(null) : null;
    }

    public static enum BeeReleaseStatus {
        HONEY_DELIVERED,
        BEE_RELEASED,
        EMERGENCY;
    }

    static class BeeData {
        private final Occupant occupant;
        private int ticksInHive;

        BeeData(Occupant occupant) {
            this.occupant = occupant;
            this.ticksInHive = occupant.ticksInHive();
        }

        public boolean tick() {
            return ticksInHive++ > occupant.minTicksInHive;
        }

        public Occupant toOccupant() {
            return new Occupant(occupant.entityData, ticksInHive, occupant.minTicksInHive);
        }

        public boolean hasNectar() {
            return occupant.entityData.getUnsafe().getBoolean("HasNectar");
        }
    }

    public static record Occupant(CustomData entityData, int ticksInHive, int minTicksInHive) {
        public Occupant(CompoundTag tag, HolderLookup.Provider registries) {
            this(CustomData.of(tag.getCompound("entity_data")), tag.getInt("ticks_in_hive"), tag.getInt("min_ticks_in_hive"));
        }

        public void write(CompoundTag tag, HolderLookup.Provider registries) {
            tag.put("entity_data", entityData.copyTag());
            tag.putInt("ticks_in_hive", ticksInHive);
            tag.putInt("min_ticks_in_hive", minTicksInHive);
        }

        public static Occupant of(Entity entity) {
            CompoundTag tag = new CompoundTag();
            entity.save(tag);
            IGNORED_BEE_TAGS.forEach(tag::remove);
            boolean hasNectar = tag.getBoolean("HasNectar");
            return new Occupant(CustomData.of(tag), 0, hasNectar ? 2400 : 600);
        }

        public Entity createEntity(Level level, BlockPos pos) {
            CompoundTag tag = entityData.copyTag();
            IGNORED_BEE_TAGS.forEach(tag::remove);
            Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
            if (entity != null && entity.getType().is(EntityTypeTags.BEEHIVE_INHABITORS)) {
                entity.setNoGravity(true);
                if (entity instanceof Bee bee) {
                    bee.setHivePos(pos);
                    setBeeReleaseData(ticksInHive, bee);
                }
                return entity;
            }
            return null;
        }

        private static void setBeeReleaseData(int ticksInHive, Bee bee) {
            int age = bee.getAge();
            if (age < 0) {
                bee.setAge(Math.min(0, age + ticksInHive));
            } else if (age > 0) {
                bee.setAge(Math.max(0, age - ticksInHive));
            }
            bee.setInLoveTime(Math.max(0, bee.getInLoveTime() - ticksInHive));
        }
    }

    public enum ProcessingMode implements INamedIconOptions {
        HONEY_FLUID("honey_fluid", "createfisheryindustry.smart_beehive.mode.honey_fluid", AllIcons.I_FILL),
        HONEYCOMB("honeycomb", "createfisheryindustry.smart_beehive.mode.honeycomb", AllIcons.I_FLATTEN);

        private final String name;
        private final String translationKey;
        private final AllIcons icon;

        ProcessingMode(String name, String translationKey, AllIcons icon) {
            this.name = name;
            this.translationKey = translationKey;
            this.icon = icon;
        }

        public String getName() {
            return name;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }
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
        public ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return wrapped.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
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
        public ItemStack getStackInSlot(int slot) {
            return wrapped.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return wrapped.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return wrapped.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return wrapped.isItemValid(slot, stack);
        }
    }
}