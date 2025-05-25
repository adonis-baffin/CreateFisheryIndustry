package com.adonis.createfisheryindustry.client.renderer;

import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlock;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.client.CreateFisheryPartialModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
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

    private static final Vec3 PIVOT = new Vec3(0, 8, 8); // 刀片的旋转中心点

    public MechanicalPeelerRenderer(BlockEntityRendererProvider.Context context) {}

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

        // 获取正确的旋转轴
        Axis kineticShaftAxis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);

        // 计算角度
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float rawAngle = (time * speed * 6f / 10f);
        float prevTickTimeApproximation = time - 1f + partialTicks;
        float prevRawAngle = (prevTickTimeApproximation * speed * 6f / 10f);
        float angle = AngleHelper.angleLerp(partialTicks, prevRawAngle, rawAngle) % 360f;

        SuperByteBuffer superBuffer;
        float originOffset = 1 / 16f;

        // 根据旋转轴调整旋转中心点
        Vec3 rotOffset;
        if (facing.getAxis().isVertical() && kineticShaftAxis == Axis.Z) {
            // 南北走向传动杆（Z轴）时，需要调整旋转中心
            // 使用X和Y偏移，但Z坐标为0（方块中心）
            rotOffset = new Vec3(PIVOT.x * originOffset, PIVOT.y * originOffset, 0);
        } else {
            // 其他情况保持原有逻辑
            rotOffset = new Vec3(0, PIVOT.y * originOffset, PIVOT.z * originOffset);
        }

        if (facing.getAxis().isHorizontal()) {
            // 水平朝向：使用统一的刀片模型
            superBuffer = CachedBuffers.partial(CreateFisheryPartialModels.THRESHER_BLADE, blockState);

            // 水平朝向的旋转处理
            superBuffer.rotateCentered(AngleHelper.rad(AngleHelper.horizontalAngle(facing)), Direction.UP);
            superBuffer.translate(rotOffset.x, rotOffset.y, rotOffset.z);
            superBuffer.rotate(AngleHelper.rad(angle), Direction.WEST); // 绕X轴旋转
            superBuffer.translate(-rotOffset.x, -rotOffset.y, -rotOffset.z);

        } else {
            // 垂直朝向：参考脱粒机的逻辑，使用 partialFacingVertical
            Direction horizontalFacing;
            if (kineticShaftAxis == Axis.X) {
                // X轴旋转：刀片应该朝南北方向
                horizontalFacing = flipped ? Direction.SOUTH : Direction.NORTH;
            } else { // Axis.Z
                // Z轴旋转：刀片应该朝东西方向
                horizontalFacing = flipped ? Direction.WEST : Direction.EAST;
            }

            superBuffer = CachedBuffers.partialFacingVertical(CreateFisheryPartialModels.THRESHER_BLADE, blockState, horizontalFacing);

            // 如果是朝下，需要额外翻转
            if (facing == Direction.DOWN) {
                superBuffer.rotateCentered(AngleHelper.rad(180), Direction.EAST);
            }

            // 使用标准的运动学旋转变换，就像脱粒机一样
            KineticBlockEntityRenderer.standardKineticRotationTransform(superBuffer, be, light)
                    .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

            // 垂直朝向直接返回，不执行下面的手动旋转逻辑
            return;
        }

        // 水平朝向的渲染
        superBuffer.light(light)
                .overlay(overlay)
                .renderInto(ms, buffer.getBuffer(RenderType.cutoutMipped()));

        // 调试日志
        Axis neighborShaftAxis = getNeighborShaftAxis(be);
        System.out.println("Facing: " + facing + ", AxisAlongFirst: " + axisAlongFirst + ", Flipped: " + flipped +
                ", Speed: " + speed + ", Angle: " + angle + ", ShaftAxis: " + kineticShaftAxis +
                ", NeighborShaftAxis: " + (neighborShaftAxis != null ? neighborShaftAxis : "None") +
                ", AxesMatch: " + (neighborShaftAxis == kineticShaftAxis));
    }

    private Axis getNeighborShaftAxis(MechanicalPeelerBlockEntity be) {
        BlockState blockState = be.getBlockState();
        Direction facing = blockState.getValue(MechanicalPeelerBlock.FACING);
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();

        if (level == null) return null;

        // 检查所有可能连接轴的方向
        for (Direction dir : Direction.values()) {
            if (facing.getAxis().isHorizontal()) {
                // 水平朝向：只检查背面
                if (dir != facing.getOpposite()) continue;
            } else {
                // 垂直朝向：检查水平方向
                if (dir.getAxis() == Axis.Y) continue;
            }

            BlockPos neighborPos = pos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof ShaftBlock && ShaftBlock.isShaft(neighborState)) {
                return neighborState.getValue(ShaftBlock.AXIS);
            }
        }

        return null;
    }

    protected void renderShaft(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        SuperByteBuffer shaftBuffer;

        if (blockState.getValue(MechanicalPeelerBlock.FACING).getAxis().isHorizontal()) {
            // 水平朝向：渲染半轴
            Direction oppositeFacing = blockState.getValue(MechanicalPeelerBlock.FACING).getOpposite();
            shaftBuffer = CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, blockState, oppositeFacing);
        } else {
            // 垂直朝向：渲染完整轴
            Axis axis = ((MechanicalPeelerBlock) blockState.getBlock()).getRotationAxis(blockState);
            shaftBuffer = CachedBuffers.block(KineticBlockEntityRenderer.KINETIC_BLOCK, KineticBlockEntityRenderer.shaft(axis));
        }

        KineticBlockEntityRenderer.standardKineticRotationTransform(shaftBuffer, be, light)
                .renderInto(ms, buffer.getBuffer(RenderType.solid()));
    }

    protected void renderItems(MechanicalPeelerBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.getBlockState().getValue(MechanicalPeelerBlock.FACING) != Direction.UP)
            return;
        if (be.inputInventory.isEmpty())
            return;

        boolean alongZ = !be.getBlockState().getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);

        // 完全复制动力锯的动画计算
        float duration = be.inputInventory.recipeDuration;
        boolean moving = duration != 0;
        float offset = moving ? (float) (be.inputInventory.remainingTime) / duration : 0;
        float processingSpeed = Mth.clamp(Math.abs(be.getSpeed()) / 32, 1, 128);

        if (moving) {
            offset = Mth.clamp(offset + ((-partialTicks + .5f) * processingSpeed) / duration, 0.125f, 1f);
            if (!be.inputInventory.appliedRecipe)
                offset += 1;
            offset /= 2;
        }

        if (be.getSpeed() == 0)
            offset = .5f;
        if (be.getSpeed() < 0 ^ alongZ)
            offset = 1 - offset;

        // 创建一个虚拟的"统一inventory"来模拟动力锯的结构
        ItemStack[] virtualInventory = new ItemStack[2];
        virtualInventory[0] = be.inputInventory.getStackInSlot(0); // 输入槽
        virtualInventory[1] = be.outputInventory.getStackInSlot(MechanicalPeelerBlockEntity.OUTPUT_INV_PRIMARY_SLOT_TEMP); // 输出槽

        // 计算输出物品数量（完全按照动力锯的逻辑）
        int outputs = 0;
        for (int i = 1; i < virtualInventory.length; i++)
            if (virtualInventory[i] != null && !virtualInventory[i].isEmpty())
                outputs++;

        ms.pushPose();
        if (alongZ)
            ms.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        ms.translate(outputs <= 1 ? .5 : .25, 0, offset);
        if (alongZ) ms.translate(-1, 0, 0);

        // 完全复制动力锯的渲染循环
        int renderedI = 0;
        for (int i = 0; i < virtualInventory.length; i++) {
            ItemStack stack = virtualInventory[i];
            if (stack == null || stack.isEmpty())
                continue;

            ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
            BakedModel modelWithOverrides = itemRenderer.getModel(stack, be.getLevel(), null, 0);
            boolean blockItem = modelWithOverrides.isGui3d();

            ms.pushPose();
            ms.translate(0, blockItem ? .925f : 13f / 16f, 0);

            if (i > 0 && outputs > 1) {
                ms.translate((0.5 / (outputs - 1)) * renderedI, 0, 0);
                TransformStack.of(ms).nudge(i * 133);
            }

            ms.scale(.5f, .5f, .5f);
            if (!blockItem)
                ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));

            itemRenderer.render(stack, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, modelWithOverrides);
            renderedI++;
            ms.popPose();
        }

        ms.popPose();
    }
}