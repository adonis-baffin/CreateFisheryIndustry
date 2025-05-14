package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.recipe.CreateFisheryRecipeTypes;
import com.adonis.createfisheryindustry.recipe.PeelingRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.recipe.ProcessingInventory;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

public class MechanicalPeelerBlockEntity extends KineticBlockEntity {
    public ProcessingInventory inventory;
    protected ItemStackHandler byproducts;
    private final IItemHandler insertionHandler;
    private final IItemHandler extractionHandler;
    private ItemStack playEvent;

    public MechanicalPeelerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new ProcessingInventory(this::start).withSlotLimit(true);
        byproducts = new ItemStackHandler(9) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
            }
        };
        insertionHandler = new InsertionOnlyItemHandler(inventory);
        extractionHandler = new ExtractionOnlyItemHandler(byproducts);
        playEvent = ItemStack.EMPTY;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .allowingBeltFunnelsWhen(this::canProcess)
                .setInsertionHandler((stack, direction, simulate) -> {
                    if (!simulate) {
                        ItemStack remainder = inventory.insertItem(0, stack.stack.copy(), false);
                        return remainder;
                    }
                    return stack.stack;
                }));
    }

    public IItemHandler getItemHandler(Direction side) {
        if (side == Direction.UP) return insertionHandler;
        if (side == Direction.DOWN) return extractionHandler;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!canProcess() || getSpeed() == 0) return;

        if (inventory.remainingTime == -1 && !inventory.isEmpty()) {
            start(inventory.getStackInSlot(0));
        } else if (inventory.remainingTime > 0) {
            inventory.remainingTime -= Math.abs(getSpeed()) / 24;
            if (inventory.remainingTime > 0) {
                spawnParticles(inventory.getStackInSlot(0));
            }
            if (inventory.remainingTime <= 0 && !inventory.appliedRecipe) {
                applyRecipe();
                inventory.appliedRecipe = true;
                inventory.remainingTime = 20;
                inventory.recipeDuration = 20;
                playEvent = inventory.getStackInSlot(0);
                if (!level.isClientSide) {
                    level.playSound(null, worldPosition, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.BLOCKS, 0.5f, 1.0f);
                    spawnEventParticles(playEvent);
                }
                sendData();
            } else if (inventory.remainingTime <= 0) {
                ejectOutputs();
                inventory.remainingTime = -1;
                inventory.appliedRecipe = false;
                setChanged();
                sendData();
            }
        }
    }

    protected boolean canProcess() {
        // 允许水平和垂直朝向（除了朝下），与 SawBlock 行为一致
        return getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.DOWN;
    }

    public void insertItem(ItemEntity entity) {
        if (!canProcess() || !inventory.isEmpty() || !entity.isAlive() || level.isClientSide) return;
        inventory.clear();
        ItemStack remainder = inventory.insertItem(0, entity.getItem().copy(), false);
        if (remainder.isEmpty()) entity.discard();
        else entity.setItem(remainder);
    }

    protected ItemStack insertItem(ItemStack stack) {
        if (stack.isEmpty() || !canProcess()) return stack;
        ItemStack remainder = inventory.insertItem(0, stack.copy(), false);
        return remainder;
    }

    private void start(ItemStack inserted) {
        if (!canProcess() || inventory.isEmpty() || (level.isClientSide && !isVirtual())) return;

        Optional<RecipeHolder<PeelingRecipe>> recipe = getRecipe(inserted);

        if (recipe.isEmpty()) {
            inventory.remainingTime = inventory.recipeDuration = 10;
            inventory.appliedRecipe = false;
            sendData();
            return;
        }

        inventory.remainingTime = recipe.get().value().getProcessingDuration();
        inventory.recipeDuration = inventory.remainingTime;
        inventory.appliedRecipe = false;
        sendData();
    }

    private void applyRecipe() {
        ItemStack input = inventory.getStackInSlot(0);
        Optional<RecipeHolder<PeelingRecipe>> recipe = getRecipe(input);

        if (recipe.isEmpty()) return;

        List<ItemStack> results = recipe.get().value().rollResults();
        if (!canStoreByproducts(results.subList(1, results.size()))) return;

        inventory.setStackInSlot(0, results.get(0)); // 主输出
        for (int i = 1; i < results.size(); i++) {
            insertByproduct(results.get(i));
        }
    }

    private Optional<RecipeHolder<PeelingRecipe>> getRecipe(ItemStack input) {
        // 优先检查序列组装配方
        Optional<RecipeHolder<PeelingRecipe>> assemblyRecipe = SequencedAssemblyRecipe.getRecipe(
                level, input, CreateFisheryRecipeTypes.PEELING.getType(), PeelingRecipe.class);

        if (assemblyRecipe.isPresent()) {
            return assemblyRecipe;
        }

        // 检查普通配方
        return level.getRecipeManager()
                .getAllRecipesFor(CreateFisheryRecipeTypes.PEELING.getType())
                .stream()
                .filter(r -> r.value() instanceof PeelingRecipe peelingRecipe &&
                        peelingRecipe.getIngredients().getFirst().test(input))
                .map(r -> {
                    PeelingRecipe peelingRecipe = (PeelingRecipe) r.value();
                    return new RecipeHolder<>(r.id(), peelingRecipe);
                })
                .findFirst();
    }

    private boolean canStoreByproducts(List<ItemStack> byproductsList) {
        for (ItemStack stack : byproductsList) {
            ItemStack remainder = stack.copy();
            for (int i = 0; i < byproducts.getSlots(); i++) {
                remainder = byproducts.insertItem(i, remainder, true);
                if (remainder.isEmpty()) break;
            }
            if (!remainder.isEmpty()) return false;
        }
        return true;
    }

    private void insertByproduct(ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int i = 0; i < byproducts.getSlots(); i++) {
            remainder = byproducts.insertItem(i, remainder, false);
            if (remainder.isEmpty()) break;
        }
    }

    private void ejectOutputs() {
        ItemStack output = inventory.getStackInSlot(0);
        if (output.isEmpty()) return;

        Vec3 outPos = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        Vec3 outMotion = new Vec3(0, 0.125, 0);
        ItemEntity entity = new ItemEntity(level, outPos.x, outPos.y, outPos.z, output);
        entity.setDeltaMovement(outMotion);
        level.addFreshEntity(entity);
        inventory.clear();
    }

    protected void spawnEventParticles(ItemStack stack) {
        if (stack.isEmpty() || level.isClientSide) return;

        ItemParticleOption particleData = new ItemParticleOption(ParticleTypes.ITEM, stack);
        RandomSource r = level.random;
        Vec3 pos = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
        for (int i = 0; i < 10; i++) {
            Vec3 motion = new Vec3((r.nextFloat() - 0.5) * 0.1, r.nextFloat() * 0.1, (r.nextFloat() - 0.5) * 0.1);
            level.addParticle(particleData, pos.x, pos.y, pos.z, motion.x, motion.y, motion.z);
        }
    }

    protected void spawnParticles(ItemStack stack) {
        if (stack.isEmpty() || level.isClientSide) return;

        ItemParticleOption particleData = new ItemParticleOption(ParticleTypes.ITEM, stack);
        RandomSource r = level.random;
        Vec3 pos = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.45, worldPosition.getZ() + 0.5);
        float offset = inventory.recipeDuration != 0 ? (float) (inventory.remainingTime) / inventory.recipeDuration : 0;
        offset /= 2;
        if (inventory.appliedRecipe) offset -= 0.5f;
        for (int i = 0; i < 2; i++) {
            Vec3 motion = new Vec3(0, r.nextFloat() * 0.05, 0);
            level.addParticle(particleData, pos.x, pos.y - offset * 0.2, pos.z, motion.x, motion.y, motion.z);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.translate("gui.goggles.mechanical_peeler_contents").forGoggles(tooltip);
        boolean isEmpty = true;

        for (int i = 0; i < byproducts.getSlots(); i++) {
            ItemStack stack = byproducts.getStackInSlot(i);
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
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.put("Byproducts", byproducts.serializeNBT(registries));
        if (!clientPacket && !playEvent.isEmpty()) {
            compound.put("PlayEvent", playEvent.saveOptional(registries));
            playEvent = ItemStack.EMPTY;
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        byproducts.deserializeNBT(registries, compound.getCompound("Byproducts"));
        if (compound.contains("PlayEvent")) {
            playEvent = ItemStack.parseOptional(registries, compound.getCompound("PlayEvent"));
        }
    }

    private static class InsertionOnlyItemHandler implements IItemHandler {
        private final ProcessingInventory wrapped;

        public InsertionOnlyItemHandler(ProcessingInventory wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int getSlots() { return 1; }

        @Override
        public ItemStack getStackInSlot(int slot) { return wrapped.getStackInSlot(slot); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return wrapped.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }

        @Override
        public int getSlotLimit(int slot) { return wrapped.getSlotLimit(slot); }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) { return wrapped.isItemValid(slot, stack); }
    }

    private static class ExtractionOnlyItemHandler implements IItemHandler {
        private final ItemStackHandler wrapped;

        public ExtractionOnlyItemHandler(ItemStackHandler wrapped) {
            this. wrapped = wrapped;
        }

        @Override
        public int getSlots() { return wrapped.getSlots();  }

        @Override
        public ItemStack getStackInSlot(int slot) { return wrapped.getStackInSlot(slot);  }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack;  }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) { return wrapped. extractItem(slot, amount, simulate);  }

        @Override
        public int getSlotLimit(int slot) { return wrapped.getSlotLimit(slot);  }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) { return wrapped. isItemValid(slot, stack);  }
    }
}