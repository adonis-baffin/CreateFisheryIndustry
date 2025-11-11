package com.adonis.createfisheryindustry.block.MeshTrap;

import com.adonis.createfisheryindustry.block.common.EntityCaptureHelper;
import com.adonis.createfisheryindustry.block.common.IItemReceiver;
import com.adonis.createfisheryindustry.config.CreateFisheryCommonConfig;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;
import java.util.Map;

public class MeshTrapMovementBehaviour implements MovementBehaviour {

    private static final double COLLECTION_RANGE = 1.5;
    private static final double ENTITY_PROCESSING_RANGE = 1.0;
    private static final int TICK_INTERVAL = 10;

    @Override
    public void tick(MovementContext context) {
        Level world = context.world;

        if (world.isClientSide) {
            if (world.getGameTime() % 20 == 0 && world.random.nextFloat() < 0.3f) {
                spawnWorkingParticles(context);
            }
            return;
        }

        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }

        if (world.getGameTime() % TICK_INTERVAL != 0) {
            return;
        }

        collectNearbyItems(context, serverLevel);
        captureNearbyEntities(context, serverLevel);

        if (world.getGameTime() % 20 == 0) {
            autoExportToContraptionStorage(context, serverLevel);
        }
    }

    private void collectNearbyItems(MovementContext context, ServerLevel level) {
        AABB effectiveAABB = new AABB(
                context.position.subtract(COLLECTION_RANGE, COLLECTION_RANGE, COLLECTION_RANGE),
                context.position.add(COLLECTION_RANGE, COLLECTION_RANGE, COLLECTION_RANGE)
        );

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, effectiveAABB);

        if (items.isEmpty()) {
            return;
        }

        IItemHandler meshTrapHandler = getMeshTrapHandler(context);
        if (meshTrapHandler == null) {
            return;
        }

        for (ItemEntity itemEntity : items) {
            if (itemEntity == null || !itemEntity.isAlive()) {
                continue;
            }

            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty()) {
                continue;
            }

            ItemStack remainder = insertItemToHandler(meshTrapHandler, stack);

            if (remainder.isEmpty()) {
                itemEntity.discard();
            } else if (remainder.getCount() < stack.getCount()) {
                itemEntity.setItem(remainder);
            }
        }
    }

    private void captureNearbyEntities(MovementContext context, ServerLevel level) {
        CreateFisheryCommonConfig.refreshCache();

        AABB effectiveAABB = new AABB(
                context.position.subtract(ENTITY_PROCESSING_RANGE, ENTITY_PROCESSING_RANGE, ENTITY_PROCESSING_RANGE),
                context.position.add(ENTITY_PROCESSING_RANGE, ENTITY_PROCESSING_RANGE, ENTITY_PROCESSING_RANGE)
        );

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, effectiveAABB);

        if (entities.isEmpty()) {
            return;
        }

        for (Entity entity : entities) {
            if (!entity.isAlive()) {
                continue;
            }

            if (EntityCaptureHelper.shouldCaptureEntity(entity)) {
                IItemReceiver receiver = new MeshTrapItemReceiver(context);
                BlockPos capturePos = BlockPos.containing(context.position);
                EntityCaptureHelper.captureEntity((Mob) entity, level, capturePos, receiver);
            }
        }
    }

    private void autoExportToContraptionStorage(MovementContext context, ServerLevel level) {
        IItemHandler meshTrapHandler = getMeshTrapHandler(context);
        if (meshTrapHandler == null) {
            return;
        }

        var contraptionStorage = context.contraption.getStorage().getAllItems();
        if (contraptionStorage == null) {
            return;
        }

        for (int slot = 0; slot < meshTrapHandler.getSlots(); slot++) {
            ItemStack stackInSlot = meshTrapHandler.extractItem(slot, 64, true);

            if (stackInSlot.isEmpty()) {
                continue;
            }

            ItemStack remainder = ItemHandlerHelper.insertItem(contraptionStorage, stackInSlot, false);

            int transferred = stackInSlot.getCount() - remainder.getCount();
            if (transferred > 0) {
                meshTrapHandler.extractItem(slot, transferred, false);
                break;
            }
        }
    }

    private IItemHandler getMeshTrapHandler(MovementContext context) {
        Map<BlockPos, MountedItemStorage> allItemStorages = context.contraption.getStorage().getAllItemStorages();
        if (allItemStorages == null) {
            return null;
        }

        MountedItemStorage storage = allItemStorages.get(context.localPos);
        if (storage instanceof MeshTrapMountedStorage meshTrapStorage) {
            return meshTrapStorage.getHandler();
        }

        return null;
    }

    private ItemStack insertItemToHandler(IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack.copy();

        for (int slot = 0; slot < handler.getSlots(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);

            if (remainder.isEmpty()) {
                break;
            }
        }

        return remainder;
    }

    private void spawnWorkingParticles(MovementContext context) {
        Level world = context.world;

        double x = context.position.x + (world.random.nextDouble() - 0.5) * 0.5;
        double y = context.position.y + (world.random.nextDouble() - 0.5) * 0.5;
        double z = context.position.z + (world.random.nextDouble() - 0.5) * 0.5;

        world.addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0, 0, 0);
    }

    private static class MeshTrapItemReceiver implements IItemReceiver {
        private final MovementContext context;

        public MeshTrapItemReceiver(MovementContext context) {
            this.context = context;
        }

        @Override
        public boolean receiveItem(ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }

            IItemHandler meshTrapHandler = getMeshTrapHandlerFromContext(context);
            if (meshTrapHandler != null) {
                ItemStack remainder = insertToHandler(meshTrapHandler, stack);

                if (remainder.isEmpty()) {
                    return true;
                }

                stack = remainder;
            }

            var contraptionStorage = context.contraption.getStorage().getAllItems();
            if (contraptionStorage != null) {
                ItemStack remainder = ItemHandlerHelper.insertItem(contraptionStorage, stack, false);
                return remainder.getCount() < stack.getCount();
            }

            return false;
        }

        private IItemHandler getMeshTrapHandlerFromContext(MovementContext ctx) {
            Map<BlockPos, MountedItemStorage> allItemStorages = ctx.contraption.getStorage().getAllItemStorages();
            if (allItemStorages == null) {
                return null;
            }

            MountedItemStorage storage = allItemStorages.get(ctx.localPos);
            if (storage instanceof MeshTrapMountedStorage meshTrapStorage) {
                return meshTrapStorage.getHandler();
            }

            return null;
        }

        private ItemStack insertToHandler(IItemHandler handler, ItemStack stack) {
            ItemStack remainder = stack.copy();

            for (int slot = 0; slot < handler.getSlots(); slot++) {
                remainder = handler.insertItem(slot, remainder, false);

                if (remainder.isEmpty()) {
                    break;
                }
            }

            return remainder;
        }
    }
}