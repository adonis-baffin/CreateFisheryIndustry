package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
// PoseStack 从 com.mojang.blaze3d.vertex 包导入
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import net.createmod.catnip.math.VecHelper;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.AngleHelper;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
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

import java.util.List;
import java.util.UUID;

public class MechanicalPeelerMovementBehaviour implements MovementBehaviour {

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

    @Override
    public boolean disableBlockEntityRendering() {
        return true;
    }

    @Override
    public void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                    ContraptionMatrices matrices, MultiBufferSource buffer) {
        BlockState state = context.state;
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING); // 使用方块自己的FACING

        boolean moving = context.relativeMotion.lengthSqr() > 1e-4;
        boolean backwards = VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite());
        boolean shouldAnimate = !context.contraption.stalled && !backwards && moving;

        SuperByteBuffer superBuffer;
        if (MechanicalPeelerBlock.isHorizontal(state)) {
            superBuffer = CachedBuffers.partial(shouldAnimate ?
                    CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_ACTIVE :
                    CreateFisheryPartialModels.PEELER_BLADE_HORIZONTAL_INACTIVE, state);
        } else {
            superBuffer = CachedBuffers.partial(shouldAnimate ?
                    CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_ACTIVE :
                    CreateFisheryPartialModels.PEELER_BLADE_VERTICAL_INACTIVE, state);
        }

        // 与 SawRenderer.renderInContraption 一致的 SuperByteBuffer 变换
        superBuffer.transform(matrices.getModel())
                .center()
                .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                .rotateXDegrees(AngleHelper.verticalAngle(facing));

        if (!MechanicalPeelerBlock.isHorizontal(state)) { // 即垂直 (UP/DOWN)
            // AXIS_ALONG_FIRST_COORDINATE 是你方块的状态属性
            superBuffer.rotateZDegrees(state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE) ? 90 : 0);
        }

        superBuffer.uncenter()
                .light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                .useLevelLight(context.world, matrices.getWorld())
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.cutoutMipped()));
    }
}