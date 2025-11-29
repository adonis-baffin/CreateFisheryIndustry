package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ContraptionMatrices;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MechanicalPeelerRenderer extends SafeBlockEntityRenderer<MechanicalPeelerBlockEntity> {

    private static final Vec3 PIVOT = new Vec3(0, 8, 8);

    public MechanicalPeelerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        try {
            renderRotatingPart(be, partialTicks, ms, buffer, light, overlay);
            renderItems(be, partialTicks, ms, buffer, light, overlay);
            renderShaft(be, partialTicks, ms, buffer, light, overlay);
        } catch (Exception e) {
        }
    }

    protected void renderRotatingPart(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        Direction facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        boolean axisAlongFirst = blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        boolean flipped = blockState.getValue(MechanicalPeelerBlock.FLIPPED);
        float speed = be.getSpeed();

        Axis kineticShaftAxis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);

        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float rawAngle = (time * speed * 6f / 10f);
        float prevTickTimeApproximation = time - 1f + partialTicks;
        float prevRawAngle = (prevTickTimeApproximation * speed * 6f / 10f);
        float angle = AngleHelper.angleLerp(partialTicks, prevRawAngle, rawAngle) % 360f;

        SuperByteBuffer superBuffer;
        float originOffset = 1 / 16f;

        Vec3 rotOffset;
        if (facing.getAxis().isVertical() && kineticShaftAxis == Axis.Z) {
            rotOffset = new Vec3(PIVOT.x * originOffset, PIVOT.y * originOffset, 0);
        } else {
            rotOffset = new Vec3(0, PIVOT.y * originOffset, PIVOT.z * originOffset);
        }

        try {
            if (facing.getAxis().isHorizontal()) {
                superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.THRESHER_BLADE, blockState);
                if (superBuffer == null) {
                    return;
                }
                superBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing)), Direction.UP);
                superBuffer.translate(rotOffset.x, rotOffset.y, rotOffset.z);
                superBuffer.rotate(AngleHelper.rad(angle), Direction.WEST);
                superBuffer.translate(-rotOffset.x, -rotOffset.y, -rotOffset.z);
            } else {
                Direction horizontalFacing;
                if (kineticShaftAxis == Axis.X) {
                    horizontalFacing = flipped ? Direction.SOUTH : Direction.NORTH;
                } else {
                    horizontalFacing = flipped ? Direction.WEST : Direction.EAST;
                }

                superBuffer = CachedBuffers.partialFacingVertical(CreateFisheryPartialModels.THRESHER_BLADE, blockState, horizontalFacing);
                if (superBuffer == null) {
                    return;
                }

                if (facing == Direction.DOWN) {
                    superBuffer.rotateCentered(AngleHelper.rad(180), Direction.EAST);
                }

                KineticBlockEntityRenderer.standardKineticRotationTransform(superBuffer, be, light)
                        .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
                return;
            }

            superBuffer.light(light)
                    .overlay(overlay)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));
        } catch (Exception e) {
        }
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        SuperByteBuffer shaftBuffer;

        try {
            if (blockState.getValue(MechanicalPeelerBlock.FACING).getAxis().isHorizontal()) {
                Direction oppositeFacing = blockState.getValue(MechanicalPeelerBlock.FACING).getOpposite();
                shaftBuffer = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, blockState, oppositeFacing);
            } else {
                Axis axis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);
                shaftBuffer = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, KineticBlockEntityRenderer.shaft(axis));
            }

            if (shaftBuffer != null) {
                KineticBlockEntityRenderer.standardKineticRotationTransform(shaftBuffer, be, light)
                        .renderInto(ms, buffer.getBuffer(RenderType.solid()));
            }
        } catch (Exception e) {
        }
    }

    protected void renderItems(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP)
            return;

        ItemStack itemToRender = getItemToRender(be);
        if (itemToRender.isEmpty())
            return;

        if (be.getLevel().getGameTime() % 20 == 0) {

        }

        // 使用与 getItemMovementVec() 相同的逻辑
        Vec3 itemMovement = be.getItemMovementVec();
        float animationProgress = calculateAnimationProgress(be, partialTicks);

        if (be.getLevel().getGameTime() % 10 == 0 && be.inputInventory.remainingTime > 0) {

        }

        renderSingleItem(ms, buffer, light, overlay, itemToRender, animationProgress, itemMovement, be);
    }

    private ItemStack getItemToRender(MechanicalPeelerBlockEntity be) {
        if (be.inputInventory.remainingTime > 0) {
            if (!be.inputInventory.appliedRecipe) {
                return be.inputInventory.getStackInSlot(0);
            } else {
                ItemStack primaryOutput = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP);
                if (!primaryOutput.isEmpty()) {
                    return primaryOutput;
                }
                return be.inputInventory.getStackInSlot(0);
            }
        } else {
            ItemStack primaryOutput = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP);
            if (!primaryOutput.isEmpty()) {
                return primaryOutput;
            }
            return be.inputInventory.getStackInSlot(0);
        }
    }

    private float calculateAnimationProgress(MechanicalPeelerBlockEntity be, float partialTicks) {
        float duration = be.inputInventory.recipeDuration;
        if (duration == 0 || be.getSpeed() == 0) {
            return 0.5f;
        }

        float remainingTime = be.inputInventory.remainingTime;
        float baseProgress = 1.0f - (remainingTime / duration);
        float processingSpeed = Mth.clamp(Math.abs(be.getSpeed()) / 32f, 1f, 128f);
        float interpolatedProgress = baseProgress + (partialTicks * processingSpeed / duration);
        interpolatedProgress = Mth.clamp(interpolatedProgress, 0f, 1f);

        float visualProgress;
        if (!be.inputInventory.appliedRecipe) {
            // 输入阶段：从 1.0 到 0.5
            visualProgress = 1.0f - (interpolatedProgress * 0.5f);
        } else {
            // 输出阶段：从 0.5 到 0.0
            visualProgress = 0.5f - (interpolatedProgress * 0.5f);
        }

        return Mth.clamp(visualProgress, 0f, 1f);
    }

    private void renderSingleItem(PoseStack ms, MultiBufferSource buffer, int light, int overlay,
                                  ItemStack stack, float progress, Vec3 itemMovement, MechanicalPeelerBlockEntity be) {
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel modelWithOverrides = itemRenderer.getModel(stack, be.getLevel(), null, 0);
        boolean blockItem = modelWithOverrides.isGui3d();

        ms.pushPose();
        float positionOffset = progress - 0.5f; // 0.5 到 -0.5（反向）

        ms.translate(
                0.5 + itemMovement.x * positionOffset,
                0,
                0.5 + itemMovement.z * positionOffset
        );

        // 根据移动方向旋转物品
        if (Math.abs(itemMovement.x) > 0) {
            // 沿 X 轴移动（东西向）
            ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        }
        // 如果沿 Z 轴移动（南北向），不需要额外旋转

        // 调整高度和缩放
        ms.translate(0, blockItem ? .925f : 13f / 16f, 0);
        ms.scale(.5f, .5f, .5f);

        if (!blockItem) {
            ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        }

        itemRenderer.render(stack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, modelWithOverrides);

        ms.popPose();
    }

// 在 MechanicalPeelerRenderer 类中修改以下静态方法

    public static void renderInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                           ContraptionMatrices matrices, MultiBufferSource buffer) {

        BlockState state = context.state;
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
        boolean axisAlongFirst = state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        boolean flipped = state.getValue(MechanicalPeelerBlock.FLIPPED);

        // 渲染刀片
        renderBladeInContraption(context, renderWorld, matrices, buffer, state, facing, axisAlongFirst, flipped);

        // 渲染传动杆
        renderShaftInContraption(context, renderWorld, matrices, buffer, state, facing, axisAlongFirst);
    }

    private static void renderBladeInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                                 ContraptionMatrices matrices, MultiBufferSource buffer, BlockState state, Direction facing,
                                                 boolean axisAlongFirst, boolean flipped) {

        SuperByteBuffer superBuffer;

        if (facing.getAxis().isHorizontal()) {
            superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.THRESHER_BLADE, state);
        } else {
            // 垂直朝向使用partialFacingVertical
            Direction horizontalFacing;
            Axis kineticAxis = axisAlongFirst ? Axis.X : Axis.Z;
            if (kineticAxis == Axis.X) {
                horizontalFacing = flipped ? Direction.SOUTH : Direction.NORTH;
            } else {
                horizontalFacing = flipped ? Direction.WEST : Direction.EAST;
            }
            superBuffer = CachedBuffers.partialFacingVertical(CreateFisheryPartialModels.THRESHER_BLADE, state, horizontalFacing);
        }

        if (superBuffer == null) {
            return;
        }

        // 计算旋转速度和角度
        float speed = (float) (context.contraption.stalled
                || !VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite())
                ? context.getAnimationSpeed() : 0);

        float time = AnimationTickHolder.getRenderTime() / 20;
        float angle = (float) (((time * speed * 6f / 10f) % 360));

        // 应用变换
        superBuffer.transform(matrices.getModel());

        if (facing.getAxis().isHorizontal()) {
            // 水平放置的变换 - 与静态渲染器完全一致
            superBuffer.center()
                    .rotateYDegrees(AngleHelper.horizontalAngle(facing))
                    .uncenter();  // 先完成朝向旋转

            // 应用pivot偏移和旋转
            float originOffset = 1 / 16f;
            float rotOffsetY = 8 * originOffset;
            float rotOffsetZ = 8 * originOffset;

            superBuffer.translate(0, rotOffsetY, rotOffsetZ)
                    .rotateXDegrees(angle)
                    .translate(0, -rotOffsetY, -rotOffsetZ);
        } else {
            // 垂直放置的变换 - 与静态渲染器完全一致
            if (facing == Direction.DOWN) {
                superBuffer.center()
                        .rotateXDegrees(180)
                        .uncenter();
            }

            // 使用standardKineticRotationTransform逻辑
            superBuffer.center();

            // 垂直朝向时，刀片的旋转轴应该与传动杆轴一致
            Axis kineticAxis = axisAlongFirst ? Axis.X : Axis.Z;
            if (kineticAxis == Axis.X) {
                superBuffer.rotateXDegrees(angle);
            } else {
                superBuffer.rotateZDegrees(angle);
            }

            superBuffer.uncenter();
        }

        superBuffer.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                .useLevelLight(context.world, matrices.getWorld())
                .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.cutoutMipped()));
    }

    private static void renderShaftInContraption(MovementContext context, VirtualRenderWorld renderWorld,
                                                 ContraptionMatrices matrices, MultiBufferSource buffer, BlockState state, Direction facing,
                                                 boolean axisAlongFirst) {

        SuperByteBuffer shaftBuffer;

        try {
            if (facing.getAxis().isHorizontal()) {
                // 水平朝向使用半轴
                Direction oppositeFacing = facing.getOpposite();
                shaftBuffer = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, oppositeFacing);
            } else {
                // 垂直朝向使用完整轴
                shaftBuffer = CachedBuffers.partial(AllPartialModels.SHAFT, state);
            }

            if (shaftBuffer != null) {
                // 计算旋转速度和角度
                float speed = (float) (context.contraption.stalled
                        || !VecHelper.isVecPointingTowards(context.relativeMotion, facing.getOpposite())
                        ? context.getAnimationSpeed() : 0);

                float time = AnimationTickHolder.getRenderTime() / 20;
                float angle = (float) (((time * speed * 6f / 10f) % 360));

                shaftBuffer.transform(matrices.getModel());

                if (facing.getAxis().isHorizontal()) {
                    // 水平朝向：使用standardKineticRotationTransform逻辑
                    // 注意：CachedBuffers.partialFacing已经处理了朝向，所以只需要旋转
                    shaftBuffer.center();
                    shaftBuffer.rotateYDegrees(angle);  // 沿Y轴旋转（因为已经朝向正确方向）
                    shaftBuffer.uncenter();
                } else {
                    // 垂直朝向：根据轴向旋转
                    shaftBuffer.center();

                    Axis kineticAxis = axisAlongFirst ? Axis.X : Axis.Z;
                    if (kineticAxis == Axis.X) {
                        // X轴：需要先旋转90度使轴沿X方向
                        shaftBuffer.rotateZDegrees(90);
                        // 然后绕新的轴旋转（Y轴变成了X轴）
                        shaftBuffer.rotateYDegrees(-angle);  // 负号是因为坐标系的差异
                    } else {
                        // Z轴：需要先旋转90度使轴沿Z方向
                        shaftBuffer.rotateXDegrees(90);
                        // 然后绕新的轴旋转（Y轴变成了Z轴）
                        shaftBuffer.rotateYDegrees(angle);
                    }

                    shaftBuffer.uncenter();
                }

                shaftBuffer.light(LevelRenderer.getLightColor(renderWorld, context.localPos))
                        .useLevelLight(context.world, matrices.getWorld())
                        .renderInto(matrices.getViewProjection(), buffer.getBuffer(RenderType.solid()));
            }
        } catch (Exception e) {
        }
    }
}