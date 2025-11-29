package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import javax.annotation.Nullable;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.client.renderer.MechanicalPeelerActorVisual;
import com.adonis.createfisheryindustry.client.renderer.MechanicalPeelerRenderer;
import com.simibubi.create.AllTags;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.common.IShearable;

import java.util.*;

public class MechanicalPeelerMovementBehaviour extends BlockBreakingMovementBehaviour {

    // 实体处理冷却时间（1分钟）
    private static final int ENTITY_COOLDOWN_TICKS = 20 * 60;

    // 方块处理时间（加快到1.5秒）
    private static final int BLOCK_BREAK_TIME = 30;

    @Override
    public boolean isActive(MovementContext context) {
        return super.isActive(context)
                && !VecHelper.isVecPointingTowards(context.relativeMotion,
                context.state.getValue(MechanicalPeelerBlock.FACING).getOpposite());
    }

    @Override
    public Vec3 getActiveAreaOffset(MovementContext context) {
        Direction facing = context.state.getValue(MechanicalPeelerBlock.FACING);
        return Vec3.atLowerCornerOf(facing.getNormal()).scale(.65f);
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        Level world = context.world;
        if (world.isClientSide)
            return;

        Direction facing = context.state.getValue(MechanicalPeelerBlock.FACING);

        // 处理实体（在去皮机前方的区域，所有朝向都可以）
        processEntitiesInArea(context, pos, world, facing);

        // 处理方块（所有朝向都可以）
        BlockState stateVisited = world.getBlockState(pos);
        if (canProcessBlock(stateVisited)) {
            context.data.put("BreakingPos", NbtUtils.writeBlockPos(pos));
            context.stall = true;
        }
    }

    private void processEntitiesInArea(MovementContext context, BlockPos pos, Level world, Direction facing) {
        // 创建实体检测区域
        AABB detectionArea = createEntityDetectionArea(pos, facing);

        List<Entity> entities = world.getEntitiesOfClass(Entity.class, detectionArea,
                entity -> entity.isAlive() && (entity instanceof IShearable ||
                        entity instanceof Armadillo || entity instanceof Turtle));

        for (Entity entity : entities) {
            processEntity(context, entity);
        }
    }

    private AABB createEntityDetectionArea(BlockPos pos, Direction facing) {
        final double interactionDepth = 0.6;
        final double interactionWidth = 0.8;
        final double interactionHeight = 0.8;
        final double offsetFromSurface = 0.1;

        Vec3 min = Vec3.ZERO;
        Vec3 max = Vec3.ZERO;

        switch (facing) {
            case UP:
                min = new Vec3((1 - interactionWidth) / 2, 1 + offsetFromSurface, (1 - interactionHeight) / 2);
                max = new Vec3((1 + interactionWidth) / 2, 1 + offsetFromSurface + interactionDepth, (1 + interactionHeight) / 2);
                break;
            case DOWN:
                min = new Vec3((1 - interactionWidth) / 2, -offsetFromSurface - interactionDepth, (1 - interactionHeight) / 2);
                max = new Vec3((1 + interactionWidth) / 2, -offsetFromSurface, (1 + interactionHeight) / 2);
                break;
            case NORTH:
                min = new Vec3((1 - interactionWidth) / 2, (1 - interactionHeight) / 2, -offsetFromSurface - interactionDepth);
                max = new Vec3((1 + interactionWidth) / 2, (1 + interactionHeight) / 2, -offsetFromSurface);
                break;
            case SOUTH:
                min = new Vec3((1 - interactionWidth) / 2, (1 - interactionHeight) / 2, 1 + offsetFromSurface);
                max = new Vec3((1 + interactionWidth) / 2, (1 + interactionHeight) / 2, 1 + offsetFromSurface + interactionDepth);
                break;
            case WEST:
                min = new Vec3(-offsetFromSurface - interactionDepth, (1 - interactionHeight) / 2, (1 - interactionWidth) / 2);
                max = new Vec3(-offsetFromSurface, (1 + interactionHeight) / 2, (1 + interactionWidth) / 2);
                break;
            case EAST:
                min = new Vec3(1 + offsetFromSurface, (1 - interactionHeight) / 2, (1 - interactionWidth) / 2);
                max = new Vec3(1 + offsetFromSurface + interactionDepth, (1 + interactionHeight) / 2, (1 + interactionWidth) / 2);
                break;
        }

        return new AABB(min.x, min.y, min.z, max.x, max.y, max.z).move(pos);
    }

    private void processEntity(MovementContext context, Entity entity) {
        UUID entityId = entity.getUUID();
        boolean isArmadilloOrTurtle = entity instanceof Armadillo || entity instanceof Turtle;

        // 检查冷却时间
        if (isArmadilloOrTurtle && isEntityOnCooldown(context, entityId)) {
            return;
        }

        boolean processedSuccessfully = false;

        // 处理可剪切的实体
        if (entity instanceof IShearable shearableTarget) {
            if (shearableTarget.isShearable(null, ItemStack.EMPTY, context.world, entity.blockPosition())) {
                List<ItemStack> drops = shearableTarget.onSheared(null, ItemStack.EMPTY, context.world, entity.blockPosition());
                if (drops != null && !drops.isEmpty()) {
                    for (ItemStack drop : drops) {
                        dropItem(context, drop);
                    }
                    processedSuccessfully = true;
                }
            }
        }

        // 处理犰狳和海龟
        if (!processedSuccessfully && isArmadilloOrTurtle) {
            ItemStack scuteStack = null;
            if (entity instanceof Armadillo) {
                scuteStack = new ItemStack(Items.ARMADILLO_SCUTE);
            } else if (entity instanceof Turtle) {
                scuteStack = new ItemStack(Items.TURTLE_SCUTE);
            }

            if (scuteStack != null) {
                dropItem(context, scuteStack);
                context.world.playSound(null, entity.blockPosition(), SoundEvents.ARMADILLO_SCUTE_DROP,
                        SoundSource.NEUTRAL, 1.0F, 1.0F);
                setEntityCooldown(context, entityId);
                processedSuccessfully = true;
            }
        }
    }

    private boolean isEntityOnCooldown(MovementContext context, UUID entityId) {
        CompoundTag data = context.data;
        if (!data.contains("EntityCooldowns"))
            return false;

        CompoundTag cooldowns = data.getCompound("EntityCooldowns");
        if (!cooldowns.contains(entityId.toString()))
            return false;

        long lastProcessTime = cooldowns.getLong(entityId.toString());
        return (context.world.getGameTime() - lastProcessTime) < ENTITY_COOLDOWN_TICKS;
    }

    private void setEntityCooldown(MovementContext context, UUID entityId) {
        CompoundTag data = context.data;
        if (!data.contains("EntityCooldowns"))
            data.put("EntityCooldowns", new CompoundTag());

        CompoundTag cooldowns = data.getCompound("EntityCooldowns");
        cooldowns.putLong(entityId.toString(), context.world.getGameTime());
    }

    @Override
    public void tickBreaker(MovementContext context) {
        CompoundTag data = context.data;
        if (context.world.isClientSide)
            return;
        if (!data.contains("BreakingPos")) {
            context.stall = false;
            return;
        }
        if (context.relativeMotion.equals(Vec3.ZERO)) {
            context.stall = false;
            return;
        }

        // 使用速度影响的计时器系统
        float processingSpeed = getProcessingSpeed(context);
        float currentProgress = data.getFloat("ProcessingProgress");
        currentProgress += processingSpeed;
        data.putFloat("ProcessingProgress", currentProgress);

        Level world = context.world;
        BlockPos breakingPos = NBTHelper.readBlockPos(data, "BreakingPos");
        BlockState stateToBreak = world.getBlockState(breakingPos);

        if (!canProcessBlock(stateToBreak)) {
            data.remove("ProcessingProgress");
            data.remove("BreakingPos");
            context.stall = false;
            return;
        }

        // 处理完成
        if (currentProgress >= BLOCK_BREAK_TIME) {
            if (world instanceof ServerLevel serverLevel) {
                BlockState processedState = processBlock(serverLevel, breakingPos, stateToBreak, context);

                // 检查是否可以继续处理（连续处理支持）
                if (processedState != null && canProcessBlock(processedState)) {
                    // 重置进度，继续处理这个方块
                    data.putFloat("ProcessingProgress", 0f);
                    // 保持 BreakingPos 不变，继续处理
                } else {
                    // 处理完成，清理数据
                    data.remove("ProcessingProgress");
                    data.remove("BreakingPos");
                    context.stall = false;
                }
            } else {
                data.remove("ProcessingProgress");
                data.remove("BreakingPos");
                context.stall = false;
            }
            return;
        }
    }

    private float getProcessingSpeed(MovementContext context) {
        // 基础处理速度为1.0，可以根据装置速度调整
        float baseSpeed = 1.0f;

        // 如果能获取到转速，可以进一步调整速度
        if (context.contraption != null) {
            // 可以根据需要添加基于转速的速度调整
            // float rpm = Math.abs(context.contraption.getSpeed());
            // return baseSpeed * Math.min(rpm / 64f, 2.0f); // 最大2倍速度
        }

        return baseSpeed;
    }

    @Override
    public void tick(MovementContext context) {
        super.tick(context);

        // 客户端粒子效果处理
        if (context.world.isClientSide) {
            CompoundTag data = context.data;
            if (data.contains("BreakingPos") && data.contains("ProcessingProgress")) {
                Level world = context.world;
                BlockPos breakingPos = NBTHelper.readBlockPos(data, "BreakingPos");
                BlockState stateToBreak = world.getBlockState(breakingPos);
                float processingProgress = data.getFloat("ProcessingProgress");

                if (canProcessBlock(stateToBreak) && processingProgress > 0) {
                    // 每3个tick生成一次粒子效果，避免过于频繁
                    if (world.getGameTime() % 3 == 0) {
                        spawnProcessingParticles(world, breakingPos, stateToBreak);
                    }
                }
            }
        }
    }

    private void spawnProcessingParticles(Level world, BlockPos pos, BlockState state) {
        var random = world.random;
        Vec3 center = Vec3.atCenterOf(pos);

        if (state.getBlock() == Blocks.AMETHYST_CLUSTER) {
            // 紫水晶簇的特殊粒子效果
            for (int i = 0; i < 2; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 0.8;
                double offsetY = (random.nextDouble() - 0.5) * 0.8;
                double offsetZ = (random.nextDouble() - 0.5) * 0.8;

                world.addParticle(
                        net.minecraft.core.particles.ParticleTypes.END_ROD,
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.1D,
                        random.nextDouble() * 0.1D + 0.02D,
                        (random.nextDouble() - 0.5) * 0.1D
                );
            }

            // 额外的方块粒子
            for (int i = 0; i < 1; i++) {
                double offsetX = (random.nextDouble() - 0.5) * state.getShape(world, pos).bounds().getXsize();
                double offsetY = (random.nextDouble() - 0.5) * state.getShape(world, pos).bounds().getYsize();
                double offsetZ = (random.nextDouble() - 0.5) * state.getShape(world, pos).bounds().getZsize();

                world.addParticle(
                        new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.2D,
                        random.nextDouble() * 0.1D + 0.05D,
                        (random.nextDouble() - 0.5) * 0.2D
                );
            }
        } else {
            // 普通方块的粒子效果
            for (int i = 0; i < 2; i++) {
                double offsetX = (random.nextDouble() - 0.5) * state.getShape(world, pos).bounds().getXsize();
                double offsetY = (random.nextDouble() - 0.5) * state.getShape(world, pos).bounds().getYsize();
                double offsetZ = (random.nextDouble() - 0.5) * state.getShape(world, pos).bounds().getZsize();

                world.addParticle(
                        new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                        center.x + offsetX,
                        center.y + offsetY,
                        center.z + offsetZ,
                        (random.nextDouble() - 0.5) * 0.2D,
                        random.nextDouble() * 0.1D + 0.05D,
                        (random.nextDouble() - 0.5) * 0.2D
                );
            }
        }
    }

    private BlockState processBlock(ServerLevel world, BlockPos pos, BlockState state, MovementContext context) {
        Block currentBlock = state.getBlock();
        BlockState newState = null;

        // 检查是否是紫水晶簇
        if (currentBlock == Blocks.AMETHYST_CLUSTER) {
            // 掉落紫水晶碎片
            ItemStack amethystShards = new ItemStack(Items.AMETHYST_SHARD, 4);
            dropItem(context, amethystShards);
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            world.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            return null; // 方块被移除，无法继续处理
        }

        // 检查除锈（支持连续处理）
        Block deoxidized = DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.get(currentBlock);
        if (deoxidized != null) {
            newState = copyProperties(state, deoxidized.defaultBlockState());
            world.setBlock(pos, newState, 3);
            world.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return newState; // 返回新状态以便检查是否可以继续处理
        }

        // 检查除蜡（支持连续处理）
        Block dewaxed = DataMapHooks.INVERSE_WAXABLES_DATAMAP.get(currentBlock);
        if (dewaxed != null) {
            newState = copyProperties(state, dewaxed.defaultBlockState());
            world.setBlock(pos, newState, 3);
            world.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
            return newState; // 返回新状态以便检查是否可以继续处理
        }

        // 检查去树皮
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(currentBlock);
        if (blockId != null) {
            String blockPath = blockId.getPath();
            if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                String strippedPath = "stripped_" + blockPath;
                ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                        blockId.getNamespace(), strippedPath);

                if (BuiltInRegistries.BLOCK.containsKey(strippedId)) {
                    Block strippedBlock = BuiltInRegistries.BLOCK.get(strippedId);
                    newState = copyProperties(state, strippedBlock.defaultBlockState());
                    world.setBlock(pos, newState, 3);
                    world.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);

                    // 处理整棵树
                    processConnectedLogs(world, pos, currentBlock, context);
                    return null; // 树木处理不支持连续处理
                }
            }
        }

        return null; // 无法处理
    }

    private void processConnectedLogs(ServerLevel world, BlockPos startPos, Block logType, MovementContext context) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> toCheck = new LinkedList<>();
        toCheck.add(startPos);
        visited.add(startPos);

        int processedCount = 0;
        while (!toCheck.isEmpty() && processedCount < 64) { // 限制处理数量
            BlockPos current = toCheck.poll();

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.relative(dir);
                if (visited.contains(neighbor)) continue;

                BlockState neighborState = world.getBlockState(neighbor);
                if (isSameWoodType(logType, neighborState.getBlock())) {
                    visited.add(neighbor);
                    toCheck.add(neighbor);

                    // 处理这个原木
                    processBlock(world, neighbor, neighborState, context);
                    processedCount++;
                }
            }
        }
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
        if (blockPath.startsWith("stripped_") && blockPath.endsWith("_log")) {
            return blockPath.substring(9, blockPath.length() - 4);
        }
        return blockPath;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperties(BlockState from, BlockState to) {
        for (Property<?> property : from.getProperties()) {
            if (to.hasProperty(property)) {
                to = to.setValue((Property<T>) property, from.getValue((Property<T>) property));
            }
        }
        return to;
    }

    private boolean canProcessBlock(BlockState state) {
        if (state.isAir()) return false;
        Block block = state.getBlock();

        // 检查除锈
        if (DataMapHooks.INVERSE_OXIDIZABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        // 检查除蜡
        if (DataMapHooks.INVERSE_WAXABLES_DATAMAP.containsKey(block)) {
            return true;
        }

        // 检查紫水晶簇
        if (block == Blocks.AMETHYST_CLUSTER) {
            return true;
        }

        // 检查去树皮
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (blockId != null) {
            String blockPath = blockId.getPath();
            if (blockPath.contains("log") && !blockPath.contains("stripped")) {
                String strippedPath = "stripped_" + blockPath;
                ResourceLocation strippedId = ResourceLocation.fromNamespaceAndPath(
                        blockId.getNamespace(), strippedPath);
                return BuiltInRegistries.BLOCK.containsKey(strippedId);
            }
        }

        return false;
    }

    @Override
    public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
        return canProcessBlock(state) && !AllTags.AllBlockTags.TRACKS.matches(state);
    }

    @Override
    protected DamageSource getDamageSource(Level level) {
        return null; // 去皮机不造成伤害
    }

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    @OnlyIn(value = Dist.CLIENT)
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        if (!VisualizationManager.supportsVisualization(context.world))
            MechanicalPeelerRenderer.renderInContraption(context, renderWorld, matrices, buffer);
    }

    @Nullable
    @Override
    public ActorVisual createVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld,
                                    MovementContext movementContext) {
        return new MechanicalPeelerActorVisual(visualizationContext, simulationWorld, movementContext);
    }
}