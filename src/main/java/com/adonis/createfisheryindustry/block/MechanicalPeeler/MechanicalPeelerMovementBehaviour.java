package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;
import java.util.*;

public class MechanicalPeelerMovementBehaviour implements MovementBehaviour {

    private static final int ARMADILLO_TURTLE_COOLDOWN_TICKS_CONTRAPTION = 20 * 30;
    private static final double INTERACTION_DEPTH = 0.7;
    private static final double INTERACTION_WIDTH_XZ = 0.8;
    private static final double INTERACTION_HEIGHT_Y = 0.8;
    private static final double OFFSET_FROM_SURFACE = 0.05;

    // 方块处理相关常量
    private static final int BLOCK_BREAK_TIME = 30; // 在移动装置上处理更快
    private static final String BLOCK_PROCESSING_TIME_KEY = "BlockProcessingTime";
    private static final String TARGET_BLOCK_KEY = "TargetBlock";
    private static final String IS_PROCESSING_BLOCK_KEY = "IsProcessingBlock";

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide() || context.contraption.stalled) {
            return;
        }

        BlockState blockState = context.state;
        Direction facingProperty = blockState.getValue(MechanicalPeelerBlock.FACING);

        // 处理实体（垂直朝向时）
        if (facingProperty == Direction.UP) {
            processEntities(context);
        }
        // 处理方块（水平朝向时）
        else if (facingProperty.getAxis().isHorizontal()) {
            processBlocks(context);
        }
    }

    private void processEntities(MovementContext context) {
        BlockPos contraptionBlockWorldPos = BlockPos.containing(context.position);
        Direction worldFacing = Direction.UP; // 垂直朝向时处理实体
        AABB interactionZone = calculateInteractionAABB(contraptionBlockWorldPos, worldFacing);

        List<Entity> entities = context.world.getEntitiesOfClass(Entity.class, interactionZone,
                (entity) -> entity.isAlive() && (entity instanceof Sheep || entity instanceof Armadillo || entity instanceof Turtle)
        );

        for (Entity entity : entities) {
            processEntityOnContraption(context, entity);
        }
    }

    private void processBlocks(MovementContext context) {
        CompoundTag data = context.data;
        Level level = context.world;
        BlockState blockState = context.state;
        Direction facing = blockState.getValue(MechanicalPeelerBlock.FACING);

        // 获取目标方块位置
        Vec3 localFacingNormal = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 worldFacingNormal = context.rotation.apply(localFacingNormal);
        Direction worldFacing = Direction.getNearest(worldFacingNormal.x, worldFacingNormal.y, worldFacingNormal.z);

        BlockPos targetPos = BlockPos.containing(context.position).relative(worldFacing);
        BlockState targetState = level.getBlockState(targetPos);

        // 检查是否可以处理这个方块
        if (!canProcessBlock(targetState)) {
            // 重置处理状态
            data.putInt(BLOCK_PROCESSING_TIME_KEY, 0);
            data.putBoolean(IS_PROCESSING_BLOCK_KEY, false);
            data.remove(TARGET_BLOCK_KEY);
            return;
        }

        // 获取或初始化处理状态
        boolean isProcessing = data.getBoolean(IS_PROCESSING_BLOCK_KEY);
        int processingTime = data.getInt(BLOCK_PROCESSING_TIME_KEY);

        // 检查目标是否改变
        long storedTarget = data.getLong(TARGET_BLOCK_KEY);
        long currentTarget = targetPos.asLong();

        if (!isProcessing || storedTarget != currentTarget) {
            // 开始处理新方块
            data.putLong(TARGET_BLOCK_KEY, currentTarget);
            data.putBoolean(IS_PROCESSING_BLOCK_KEY, true);
            data.putInt(BLOCK_PROCESSING_TIME_KEY, 0);
            processingTime = 0;
        }

        // 增加处理时间
        float speed = Math.abs(getSpeed(context));
        int progress = Math.max(1, (int)(speed / 24f));
        processingTime += progress;
        data.putInt(BLOCK_PROCESSING_TIME_KEY, processingTime);

        // 检查是否完成处理
        if (processingTime >= BLOCK_BREAK_TIME) {
            Block targetBlock = targetState.getBlock();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(targetBlock);

            if (blockId != null) {
                String blockPath = blockId.getPath();
                if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                    // 处理整棵树
                    processWholeTree(level, targetPos, targetState);
                } else {
                    // 处理单个方块
                    processSingleBlock(level, targetPos, targetState);
                }
            }

            // 重置状态
            data.putInt(BLOCK_PROCESSING_TIME_KEY, 0);
            data.putBoolean(IS_PROCESSING_BLOCK_KEY, false);
            data.remove(TARGET_BLOCK_KEY);
        }
    }

    private boolean canProcessBlock(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();

        // 检查是否可以除锈
        if (DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        // 检查是否可以除蜡
        if (DataMapHooks.INVERSE_WAXABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        // 检查是否可以去树皮
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null) {
            String blockPath = blockId.getPath();
            if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                String strippedPath = "stripped_" + blockPath;
                ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                        blockId.getNamespace(),
                        strippedPath
                );
                return BuiltInRegistries.BLOCK.containsKey(strippedId);
            }
        }

        return false;
    }

    private void processSingleBlock(Level level, BlockPos pos, BlockState state) {
        Block processedBlock = getProcessedBlock(state.getBlock());
        if (processedBlock != state.getBlock()) {
            BlockState newState = processedBlock.defaultBlockState();

            // 复制属性
            for (Property<?> property : state.getProperties()) {
                if (newState.hasProperty(property)) {
                    newState = copyPropertyUnchecked(state, newState, property);
                }
            }

            level.setBlock(pos, newState, 3);
            level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void processWholeTree(Level level, BlockPos startPos, BlockState startState) {
        List<BlockPos> logs = findTreeLogs(level, startPos, startState);

        if (logs.isEmpty()) {
            processSingleBlock(level, startPos, startState);
            return;
        }

        int processedCount = 0;
        for (BlockPos logPos : logs) {
            BlockState logState = level.getBlockState(logPos);
            Block logBlock = logState.getBlock();

            ResourceLocation logId = BuiltInRegistries.BLOCK.getKey(logBlock);
            if (logId != null) {
                String logPath = logId.getPath();
                if (logPath.contains("log") && !logPath.contains("stripped")) {
                    String strippedPath = "stripped_" + logPath;
                    ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                            logId.getNamespace(),
                            strippedPath
                    );

                    if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                        Block strippedBlock = BuiltInRegistries.BLOCK.get(strippedId);
                        BlockState newState = strippedBlock.defaultBlockState();

                        // 复制属性
                        for (Property<?> property : logState.getProperties()) {
                            if (newState.hasProperty(property)) {
                                newState = copyPropertyUnchecked(logState, newState, property);
                            }
                        }

                        level.setBlock(logPos, newState, 3);
                        processedCount++;

                        if (processedCount % 3 == 1) {
                            level.playSound(null, logPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 0.8F, 1.0F);
                        }
                    }
                }
            }
        }

        if (processedCount > 0) {
            level.playSound(null, startPos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private Block getProcessedBlock(Block currentBlock) {
        // 除锈
        Block deoxidized = DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.get(currentBlock);
        if (deoxidized != null) {
            return deoxidized;
        }

        // 除蜡
        Block dewaxed = DataMapHooks.INVERSE_WAXABLES_DATAMAP.get(currentBlock);
        if (dewaxed != null) {
            return dewaxed;
        }

        // 去树皮
        ResourceLocation currentId = BuiltInRegistries.BLOCK.getKey(currentBlock);
        if (currentId == null) return currentBlock;
        String blockPath = currentId.getPath();

        if (blockPath.contains("log") && !blockPath.contains("stripped")) {
            String strippedPath = "stripped_" + blockPath;
            ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                    currentId.getNamespace(),
                    strippedPath
            );
            if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                return BuiltInRegistries.BLOCK.get(strippedId);
            }
        }

        return currentBlock;
    }

    private List<BlockPos> findTreeLogs(Level level, BlockPos startPos, BlockState startState) {
        Block startBlock = startState.getBlock();
        List<BlockPos> logs = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        List<BlockPos> toCheck = new LinkedList<>();

        toCheck.add(startPos);

        while (!toCheck.isEmpty() && logs.size() < 256) {
            BlockPos current = toCheck.remove(0);
            if (visited.contains(current)) continue;
            visited.add(current);

            BlockState currentState = level.getBlockState(current);
            Block currentBlock = currentState.getBlock();

            ResourceLocation currentId = BuiltInRegistries.BLOCK.getKey(currentBlock);
            if (currentId != null) {
                String currentPath = currentId.getPath();
                if (currentPath.contains("log") && !currentPath.contains("stripped")) {
                    if (isSameWoodType(startBlock, currentBlock)) {
                        logs.add(current);

                        for (Direction dir : Direction.values()) {
                            BlockPos neighbor = current.relative(dir);
                            if (!visited.contains(neighbor)) {
                                toCheck.add(neighbor);
                            }
                        }
                    }
                }
            }
        }

        return logs;
    }

    private boolean isSameWoodType(Block block1, Block block2) {
        ResourceLocation id1 = BuiltInRegistries.BLOCK.getKey(block1);
        ResourceLocation id2 = BuiltInRegistries.BLOCK.getKey(block2);

        if (id1 == null || id2 == null) return false;
        if (!id1.getNamespace().equals(id2.getNamespace())) return false;

        String type1 = extractWoodType(id1.getPath());
        String type2 = extractWoodType(id2.getPath());

        return type1.equals(type2);
    }

    private String extractWoodType(String blockPath) {
        if (blockPath.endsWith("_log")) {
            return blockPath.substring(0, blockPath.length() - 4);
        }
        return blockPath;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyPropertyUnchecked(BlockState from, BlockState to, Property<T> property) {
        return to.setValue(property, from.getValue(property));
    }

    private float getSpeed(MovementContext context) {
        if (context.blockEntityData != null) {
            return context.blockEntityData.getFloat("Speed");
        }
        return 0;
    }

    private AABB calculateInteractionAABB(BlockPos anchorPos, Direction worldFacing) {
        Vec3 min, max;
        switch (worldFacing) {
            case UP:
                min = new Vec3(anchorPos.getX() + (1 - INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() + 1 + OFFSET_FROM_SURFACE, anchorPos.getZ() + (1 - INTERACTION_WIDTH_XZ) / 2);
                max = new Vec3(anchorPos.getX() + (1 + INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() + 1 + OFFSET_FROM_SURFACE + INTERACTION_DEPTH, anchorPos.getZ() + (1 + INTERACTION_WIDTH_XZ) / 2);
                break;
            case DOWN:
                min = new Vec3(anchorPos.getX() + (1 - INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() - OFFSET_FROM_SURFACE - INTERACTION_DEPTH, anchorPos.getZ() + (1 - INTERACTION_WIDTH_XZ) / 2);
                max = new Vec3(anchorPos.getX() + (1 + INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() - OFFSET_FROM_SURFACE, anchorPos.getZ() + (1 + INTERACTION_WIDTH_XZ) / 2);
                break;
            case NORTH:
                min = new Vec3(anchorPos.getX() + (1 - INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() + (1 - INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() - OFFSET_FROM_SURFACE - INTERACTION_DEPTH);
                max = new Vec3(anchorPos.getX() + (1 + INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() + (1 + INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() - OFFSET_FROM_SURFACE);
                break;
            case SOUTH:
                min = new Vec3(anchorPos.getX() + (1 - INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() + (1 - INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() + 1 + OFFSET_FROM_SURFACE);
                max = new Vec3(anchorPos.getX() + (1 + INTERACTION_WIDTH_XZ) / 2, anchorPos.getY() + (1 + INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() + 1 + OFFSET_FROM_SURFACE + INTERACTION_DEPTH);
                break;
            case WEST:
                min = new Vec3(anchorPos.getX() - OFFSET_FROM_SURFACE - INTERACTION_DEPTH, anchorPos.getY() + (1 - INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() + (1 - INTERACTION_WIDTH_XZ) / 2);
                max = new Vec3(anchorPos.getX() - OFFSET_FROM_SURFACE, anchorPos.getY() + (1 + INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() + (1 + INTERACTION_WIDTH_XZ) / 2);
                break;
            case EAST:
                min = new Vec3(anchorPos.getX() + 1 + OFFSET_FROM_SURFACE, anchorPos.getY() + (1 - INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() + (1 - INTERACTION_WIDTH_XZ) / 2);
                max = new Vec3(anchorPos.getX() + 1 + OFFSET_FROM_SURFACE + INTERACTION_DEPTH, anchorPos.getY() + (1 + INTERACTION_HEIGHT_Y) / 2, anchorPos.getZ() + (1 + INTERACTION_WIDTH_XZ) / 2);
                break;
            default:
                return new AABB(anchorPos);
        }
        return new AABB(min, max);
    }

    private void processEntityOnContraption(MovementContext context, Entity entity) {
        Level level = context.world;
        UUID entityId = entity.getUUID();
        CompoundTag data = context.data;
        boolean isArmadilloOrTurtle = entity instanceof Armadillo || entity instanceof Turtle;
        if (isArmadilloOrTurtle) {
            String cooldownKey = "Cooldown_" + entityId.toString();
            long lastShearedTime = data.getLong(cooldownKey);
            if (level.getGameTime() - lastShearedTime < ARMADILLO_TURTLE_COOLDOWN_TICKS_CONTRAPTION) {
                return;
            }
        }
        boolean processedSuccessfully = false;
        List<ItemStack> collectedDrops = new java.util.ArrayList<>();
        if (entity instanceof IShearable shearableTarget) {
            if (shearableTarget.isShearable(null, ItemStack.EMPTY, level, entity.blockPosition())) {
                List<ItemStack> drops = shearableTarget.onSheared(null, ItemStack.EMPTY, level, entity.blockPosition());
                if (drops != null && !drops.isEmpty()) {
                    collectedDrops.addAll(drops);
                    processedSuccessfully = true;
                }
            }
        }
        if (!processedSuccessfully && isArmadilloOrTurtle) {
            if (entity instanceof Armadillo) {
                collectedDrops.add(new ItemStack(Items.ARMADILLO_SCUTE));
                level.playSound(null, entity.blockPosition(), SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                processedSuccessfully = true;
            } else if (entity instanceof Turtle) {
                collectedDrops.add(new ItemStack(Items.TURTLE_SCUTE));
                level.playSound(null, entity.blockPosition(), SoundEvents.ARMADILLO_SCUTE_DROP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                processedSuccessfully = true;
            }
        }
        if (processedSuccessfully) {
            if (isArmadilloOrTurtle) {
                data.putLong("Cooldown_" + entityId.toString(), level.getGameTime());
            }
            for (ItemStack drop : collectedDrops) {
                ItemStack remainder = ItemHandlerHelper.insertItem(context.contraption.getStorage().getAllItems(), drop, false);
                if (!remainder.isEmpty()) {
                    Vec3 dropPos = context.position != null ? context.position.add(0,0.5,0) : entity.position().add(0,0.5,0);
                    ItemEntity itemEntity = new ItemEntity(level, dropPos.x(), dropPos.y(), dropPos.z(), remainder);
                    itemEntity.setDeltaMovement(VecHelper.offsetRandomly(Vec3.ZERO, level.random, 0.1f));
                    level.addFreshEntity(itemEntity);
                }
            }
        }
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (VisualizationManager.supportsVisualization(context.world)) {
            return;
        }

        BlockState state = context.state;
        SuperByteBuffer superBuffer = CachedBuffers.partial(AllPartialModels.HARVESTER_BLADE, state);

        float speedRpm = getSpeed(context);
        if (context.contraption.stalled) {
            speedRpm = 0;
        }

        float angle = (AnimationTickHolder.getRenderTime(context.world) * speedRpm * 0.3f) % 360f;

        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
        Axis kineticRotationAxis = ((MechanicalPeelerBlock) state.getBlock()).getRotationAxis(state);

        superBuffer.transform(matrices.getModel());

        superBuffer.center();
        superBuffer.rotateYDegrees(AngleHelper.horizontalAngle(facing));
        superBuffer.rotateXDegrees(AngleHelper.verticalAngle(facing));

        if (kineticRotationAxis == Axis.X) {
            superBuffer.rotateXDegrees(angle);
        } else if (kineticRotationAxis == Axis.Y) {
            superBuffer.rotateYDegrees(angle);
        } else if (kineticRotationAxis == Axis.Z) {
            superBuffer.rotateZDegrees(angle);
        }
        superBuffer.uncenter();

        superBuffer.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                .useLevelLight(context.world, matrices.getWorld())
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.cutoutMipped()));

        // 轴渲染
        SuperByteBuffer shaftBuffer;
        if (facing.getAxis().isHorizontal()) {
            shaftBuffer = CachedBuffers.partial(AllPartialModels.SHAFT_HALF, state);
        } else {
            shaftBuffer = CachedBuffers.partial(AllPartialModels.SHAFT, state);
        }
        shaftBuffer.transform(matrices.getModel());

        if (facing.getAxis().isHorizontal()) {
            shaftBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        } else {
            if (kineticRotationAxis == Axis.X) {
                shaftBuffer.rotateCentered(AngleHelper.rad(90), Direction.SOUTH);
            } else if (kineticRotationAxis == Axis.Z) {
                shaftBuffer.rotateCentered(AngleHelper.rad(90), Direction.EAST);
            }
        }

        if (facing.getAxis().isHorizontal()) {
            if (kineticRotationAxis == Axis.X) shaftBuffer.rotateCentered(AngleHelper.rad(angle), Direction.EAST);
            else if (kineticRotationAxis == Axis.Y) shaftBuffer.rotateCentered(AngleHelper.rad(angle), Direction.UP);
            else if (kineticRotationAxis == Axis.Z) shaftBuffer.rotateCentered(AngleHelper.rad(angle), Direction.SOUTH);
        } else {
            if (kineticRotationAxis == Axis.X) shaftBuffer.rotateCentered(AngleHelper.rad(angle), Direction.EAST);
            else if (kineticRotationAxis == Axis.Y) shaftBuffer.rotateCentered(AngleHelper.rad(angle), Direction.UP);
            else if (kineticRotationAxis == Axis.Z) shaftBuffer.rotateCentered(AngleHelper.rad(angle), Direction.SOUTH);
        }

        shaftBuffer.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                .useLevelLight(context.world, matrices.getWorld())
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.solid()));
    }

    @Nullable
    @Override
    public ActorVisual createVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
        return new MechanicalPeelerActorVisual(visualizationContext, simulationWorld, movementContext);
    }
}