package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
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
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MechanicalPeelerRenderer extends SafeBlockEntityRenderer<MechanicalPeelerBlockEntity> {

    private static final Vec3 PIVOT = new Vec3(0, 6, 9); // 用于水平朝向

    public MechanicalPeelerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        renderRotatingPart(be, partialTicks, ms, buffer, light, overlay);
        renderItems(be, partialTicks, ms, buffer, light, overlay);
        renderShaft(be, partialTicks, ms, buffer, light, overlay);
    }

    protected void renderRotatingPart(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        SuperByteBuffer superBuffer = CachedBuffers.partial(AllPartialModels.HARVESTER_BLADE, blockState);

        float speed = be.getSpeed();
        Direction facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        boolean axisAlongFirst = blockState.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        boolean flipped = blockState.getValue(MechanicalPeelerBlock.FLIPPED);

        float originOffset = 1 / 16f;
        Vec3 rotOffset = new Vec3(0, PIVOT.y * originOffset, PIVOT.z * originOffset);

        float angle;
        float logAngle;

        if (facing.getAxis().isHorizontal()) {
            // 水平朝向，与 HarvesterRenderer 一致，速度4倍
            float time = AnimationTickHolder.getRenderTime(be.getLevel()) / 20;
            angle = (time * speed * 4) % 360;
            logAngle = angle;
            superBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing)), Direction.UP)
                    .translate(rotOffset.x, rotOffset.y, rotOffset.z)
                    .rotate(AngleHelper.rad(angle), Direction.WEST)
                    .translate(-rotOffset.x, -rotOffset.y, -rotOffset.z);
        } else {
            // 上下朝向，参考 ThresherRenderer 的逻辑
            Axis kineticShaftAxis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);
            float time = AnimationTickHolder.getRenderTime(be.getLevel());
            float rawAngle = (time * speed * 3f / 10f);
            float prevTickTimeApproximation = time - 1f + partialTicks;
            float prevRawAngle = (prevTickTimeApproximation * speed * 3f / 10f);
            angle = AngleHelper.angleLerp(partialTicks, prevRawAngle, rawAngle) % 360f;
            logAngle = angle;

            // 初始旋转，匹配静态模型
            if (facing == Direction.UP) {
                superBuffer.rotateCentered(AngleHelper.rad((axisAlongFirst ? 270 : 0) + (flipped ? 180 : 0)), Direction.UP);
            } else { // Direction.DOWN
                superBuffer.rotateCentered(AngleHelper.rad(180), Direction.NORTH); // X轴180度
                superBuffer.rotateCentered(AngleHelper.rad((axisAlongFirst ? 270 : 0) + (flipped ? 180 : 0)), Direction.UP);
            }

            // 动态旋转，参考 ThresherRenderer 的垂直朝向逻辑
            superBuffer.translate(rotOffset.x, rotOffset.y, rotOffset.z);
            if (kineticShaftAxis == Axis.X) {
                superBuffer.rotate(AngleHelper.rad(angle), Direction.WEST); // 绕 X 轴
            } else if (kineticShaftAxis == Axis.Z) {
                superBuffer.rotate(AngleHelper.rad(angle), Direction.SOUTH); // 绕 Z 轴
            }
            superBuffer.translate(-rotOffset.x, -rotOffset.y, -rotOffset.z);
        }

        superBuffer.light(light)
                .overlay(overlay)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        // 调试日志
        System.out.println("Facing: " + facing + ", AxisAlongFirst: " + axisAlongFirst + ", Flipped: " + flipped + ", Speed: " + speed + ", Angle: " + logAngle + ", ShaftAxis: " + (facing.getAxis().isHorizontal() ? "N/A" : ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState)));
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        Direction facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        Axis kineticShaftAxis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);

        SuperByteBuffer shaftBuffer;

        if (facing == Direction.UP || facing == Direction.DOWN) {
            BlockState shaftState = KineticBlockEntityRenderer.shaft(kineticShaftAxis);
            shaftBuffer = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, shaftState);
        } else {
            shaftBuffer = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF,
                    blockState.rotate(be.getLevel(), be.getBlockPos(), Rotation.CLOCKWISE_180));
        }

        KineticBlockEntityRenderer.standardKineticRotationTransform(shaftBuffer, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    protected void renderItems(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP)
            return;

        boolean inputEmpty = be.inputInventory.getStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT).isEmpty();
        boolean outputTempPrimaryEmpty = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP).isEmpty();

        ItemStack animatedStack;
        float duration;
        float remainingTime;
        boolean appliedRecipe;

        if (!outputTempPrimaryEmpty) {
            animatedStack = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP);
            duration = be.inputInventory.recipeDuration;
            remainingTime = be.inputInventory.remainingTime;
            appliedRecipe = true;
        } else if (!inputEmpty) {
            animatedStack = be.inputInventory.getStackInSlot(MechanicalPeelerBlockEntity.INPUT_SLOT);
            duration = be.inputInventory.recipeDuration;
            remainingTime = be.inputInventory.remainingTime;
            appliedRecipe = be.inputInventory.appliedRecipe;
        } else {
            return;
        }

        if (animatedStack.isEmpty()) {
            return;
        }

        boolean alongZ = !be.getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
        boolean moving = duration != 0;
        float offset = moving ? remainingTime / duration : 0;
        float processingSpeed = Mth.clamp(Math.abs(be.getSpeed()) / 32f, 1, 128);

        if (moving) {
            offset = Mth.clamp(offset + ((-partialTicks + .5f) * processingSpeed) / duration, 0.125f, 1f);
            if (!appliedRecipe) offset += 1;
            offset /= 2;
        }

        if (be.getSpeed() == 0) offset = .5f;
        if (be.getSpeed() < 0 ^ alongZ) offset = 1 - offset;

        ms.pushPose();
        if (alongZ) ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        ms.translate(0.5, 0, offset);
        if (alongZ) ms.translate(-1, 0, 0);

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel modelWithOverrides = itemRenderer.getModel(animatedStack, be.getLevel(), null, 0);
        boolean blockItem = modelWithOverrides.isGui3d();

        ms.pushPose();
        ms.translate(0, blockItem ? .925f : 13f / 16f, 0);
        ms.scale(.5f, .5f, .5f);
        if (!blockItem) ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));
        itemRenderer.render(animatedStack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, modelWithOverrides);
        ms.popPose();

        ms.popPose();
    }
}