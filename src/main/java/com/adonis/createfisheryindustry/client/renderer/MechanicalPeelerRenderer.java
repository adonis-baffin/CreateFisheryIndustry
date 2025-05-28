package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
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
        renderRotatingPart(be, partialTicks, ms, buffer, light, overlay);
        renderItems(be, partialTicks, ms, buffer, light, overlay);
        renderShaft(be, partialTicks, ms, buffer, light, overlay);
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

        if (facing.getAxis().isHorizontal()) {
            superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.THRESHER_BLADE, blockState);
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
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        SuperByteBuffer shaftBuffer;

        if (blockState.getValue(MechanicalPeelerBlock.FACING).getAxis().isHorizontal()) {
            Direction oppositeFacing = blockState.getValue(MechanicalPeelerBlock.FACING).getOpposite();
            shaftBuffer = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, blockState, oppositeFacing);
        } else {
            Axis axis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);
            shaftBuffer = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, KineticBlockEntityRenderer.shaft(axis));
        }

        KineticBlockEntityRenderer.standardKineticRotationTransform(shaftBuffer, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
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

        // 计算物品位置
        // progress: 1.0 = 输入侧, 0.5 = 中心, 0.0 = 输出侧
        // 调转动画方向（180度），所以直接使用 progress
        float positionOffset = progress - 0.5f; // 0.5 到 -0.5（反向）

        // 应用移动向量
        // itemMovement 指向物品移动的方向（从输入到输出）
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

}