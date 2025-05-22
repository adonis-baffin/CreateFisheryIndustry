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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class MechanicalPeelerMovementBehaviour implements MovementBehaviour {

    // --- 你的 tick, processEntityOnContraption, calculateInteractionAABB logic ---
    private static final int ARMADILLO_TURTLE_COOLDOWN_TICKS_CONTRAPTION = 20 * 30;
    private static final double INTERACTION_DEPTH = 0.7;
    private static final double INTERACTION_WIDTH_XZ = 0.8;
    private static final double INTERACTION_HEIGHT_Y = 0.8;
    private static final double OFFSET_FROM_SURFACE = 0.05;

    @Override
    public void tick(MovementContext context) {
        if (context.world.isClientSide() || context.contraption.stalled) {
            return;
        }
        BlockState blockState = context.state;
        Direction facingProperty = blockState.getValue(MechanicalPeelerBlock.FACING);
        BlockPos contraptionBlockWorldPos = BlockPos.containing(context.position);
        Vec3 localFacingNormal = Vec3.atLowerCornerOf(facingProperty.getNormal());
        Vec3 worldFacingNormal = context.rotation.apply(localFacingNormal);
        Direction worldFacing = Direction.getNearest(worldFacingNormal.x, worldFacingNormal.y, worldFacingNormal.z);
        AABB interactionZone = calculateInteractionAABB(contraptionBlockWorldPos, worldFacing);
        List<Entity> entities = context.world.getEntitiesOfClass(Entity.class, interactionZone,
                (entity) -> entity.isAlive() && (entity instanceof Sheep || entity instanceof Armadillo || entity instanceof Turtle)
        );
        for (Entity entity : entities) {
            processEntityOnContraption(context, entity);
        }
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
    // --- END of your existing logic ---

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
        SuperByteBuffer superBuffer = CachedBuffers.partial(AllPartialModels.HARVESTER_BLADE, state); // 使用刀片模型

        float speedRpm = 0;
        if (context.blockEntityData != null) {
            speedRpm = context.blockEntityData.getFloat("Speed");
        }

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

        // --- 非 Flywheel 动态结构中的轴渲染 ---
        // 注意：这只是一个基础的尝试，轴的精确朝向和与刀片的相对位置可能需要更复杂的变换
        SuperByteBuffer shaftBuffer;
        if (facing.getAxis().isHorizontal()) {
            shaftBuffer = CachedBuffers.partial(AllPartialModels.SHAFT_HALF, state);
        } else {
            shaftBuffer = CachedBuffers.partial(AllPartialModels.SHAFT, state);
        }
        shaftBuffer.transform(matrices.getModel()); // 应用动态结构基础变换

        // 轴的朝向调整（与 ActorVisual 中轴的朝向逻辑类似，但用 SuperByteBuffer API）
        if (facing.getAxis().isHorizontal()) {
            shaftBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing.getOpposite())), Direction.UP);
        } else {
            if (kineticRotationAxis == Axis.X) {
                shaftBuffer.rotateCentered(AngleHelper.rad(90), Direction.SOUTH); // Y -> X (绕Z轴)
            } else if (kineticRotationAxis == Axis.Z) {
                shaftBuffer.rotateCentered(AngleHelper.rad(90), Direction.EAST); // Y -> Z (绕X轴)
            }
        }
        // 轴的旋转（与刀片同步）
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
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.solid())); // 轴通常是 solid
    }

    @Nullable
    @Override
    public ActorVisual createVisual(VisualizationContext visualizationContext, VirtualRenderWorld simulationWorld, MovementContext movementContext) {
        return new MechanicalPeelerActorVisual(visualizationContext, simulationWorld, movementContext);
    }
}