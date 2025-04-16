/*
 * Copyright (C) 2025  DragonsPlus
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package plus.dragons.createintegratedfarming.common.ranching.coop;

import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHandlerWrapper;
import com.simibubi.create.foundation.item.ItemHelper;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import plus.dragons.createintegratedfarming.common.registry.CIFDataMaps;

public class ChickenCoopBlockEntity extends SmartBlockEntity {
    public final ItemStackHandler inventory;
    public final IItemHandler itemHandler;
    public int feedCooldown;
    public int eggTime = 12000;

    public ChickenCoopBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
        this.inventory = new ItemStackHandler();
        this.itemHandler = new ItemHandlerWrapper(inventory) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }
        };
    }

    public @Nullable IItemHandler getItemHandler(@Nullable Direction direction) {
        if (direction == getBlockState().getValue(HorizontalDirectionalBlock.FACING))
            return null;
        return itemHandler;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(side -> side == getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite())
                .considerOccupiedWhen(side -> feedCooldown > 0)
                .setInsertionHandler(this::tryInsertFrom));
    }

    @Override
    public void initialize() {
        assert level != null;
        super.initialize();
        if (eggTime >= 12000) {
            eggTime = 6000 + level.random.nextInt(6000);
        }
    }

    @Override
    public void lazyTick() {
        assert level != null;
        boolean changed = false;
        if (feedCooldown > 0) {
            feedCooldown = Math.max(0, feedCooldown - lazyTickRate);
            changed = true;
        }
        if (eggTime > 0) {
            eggTime = Math.max(0, eggTime - lazyTickRate);
            changed = true;
        }
        if (eggTime <= 0) {
            ItemStack egg = new ItemStack(Items.EGG);
            if (inventory.insertItem(0, egg, false).isEmpty()) {
                eggTime = 6000 + level.random.nextInt(6000);
                level.playSound(
                        null, worldPosition, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS,
                        1.0F, (level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F);
                changed = true;
            }
        }
        if (changed)
            notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.putInt("EggLayTime", eggTime);
        tag.putInt("FeedCooldown", feedCooldown);
    }

    @Override
    protected void read(CompoundTag tag, Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        eggTime = Math.clamp(tag.getInt("EggLayTime"), 0, 12000);
        feedCooldown = tag.getInt("FeedCooldown");
    }

    @Override
    public void destroy() {
        super.destroy();
        ItemHelper.dropContents(level, worldPosition, inventory);
    }

    protected ItemStack tryInsertFrom(TransportedItemStack transported, Direction side, boolean simulate) {
        assert level != null;
        ItemStack stack = transported.stack.copy();
        if (feedItem(stack, simulate)) {
            stack.shrink(1);
        }
        return stack;
    }

    public boolean feedItem(ItemStack stack, boolean simulate) {
        assert level != null;
        if (feedCooldown > 0 || eggTime <= 0)
            return false;
        var food = stack.getItemHolder().getData(CIFDataMaps.CHICKEN_FOOD_ITEMS);
        if (food == null)
            return false;
        if (simulate)
            return true;
        feed(food);
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Vec3 feedPos = Vec3.atBottomCenterOf(worldPosition)
                .add(facing.getStepX() * .5f, 13 / 16f, facing.getStepZ() * .5f);
        food.usingConvertsTo().ifPresent(remainer -> Containers.dropItemStack(
                level, feedPos.x, feedPos.y, feedPos.z, remainer));
        level.addParticle(
                new ItemParticleOption(ParticleTypes.ITEM, stack),
                feedPos.x, feedPos.y, feedPos.z,
                0, 0, 0);
        return true;
    }

    public int feedFluid(FluidStack fluid, boolean simulate) {
        if (feedCooldown > 0 || eggTime <= 0)
            return 0;
        var food = fluid.getFluidHolder().getData(CIFDataMaps.CHICKEN_FOOD_FLUIDS);
        if (food == null)
            return 0;
        if (simulate)
            return food.amount();
        feed(food);
        return food.amount();
    }

    public void feed(ChickenFood food) {
        assert level != null;
        eggTime = Math.max(0, eggTime - food.getProgress(level.random));
        feedCooldown = food.getCooldown(level.random);
        level.playSound(
                null, worldPosition, SoundEvents.CHICKEN_AMBIENT, SoundSource.BLOCKS,
                1.0F, (level.random.nextFloat() - level.random.nextFloat()) * 0.2F + 1.0F);
        notifyUpdate();
    }
}
