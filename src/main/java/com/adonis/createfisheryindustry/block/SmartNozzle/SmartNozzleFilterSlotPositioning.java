package com.adonis.createfisheryindustry.block.SmartNozzle;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;

public class SmartNozzleFilterSlotPositioning extends ValueBoxTransform.Sided {

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(NozzleBlock.FACING);
        Direction side = getSide();

        // 水平朝向时，在上下左右四个面且远离喷嘴
        if (facing.getAxis().isHorizontal()) {
            // 以原来的上表面位置为基准
            Vec3 baseOffset;
            switch (facing) {
                case NORTH -> {
                    // 远离喷嘴（向南），在四个面显示
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(8, 14, 12); }    // 上表面（原位置）
                        case DOWN -> { return VecHelper.voxelSpace(8, 2, 12); }   // 下表面
                        case WEST -> { return VecHelper.voxelSpace(2, 8, 12); }   // 左侧面
                        case EAST -> { return VecHelper.voxelSpace(14, 8, 12); }  // 右侧面
                    }
                }
                case SOUTH -> {
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(8, 14, 4); }
                        case DOWN -> { return VecHelper.voxelSpace(8, 2, 4); }
                        case WEST -> { return VecHelper.voxelSpace(14, 8, 4); }
                        case EAST -> { return VecHelper.voxelSpace(2, 8, 4); }
                    }
                }
                case WEST -> {
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(12, 14, 8); }
                        case DOWN -> { return VecHelper.voxelSpace(12, 2, 8); }
                        case NORTH -> { return VecHelper.voxelSpace(12, 8, 2); }
                        case SOUTH -> { return VecHelper.voxelSpace(12, 8, 14); }
                    }
                }
                case EAST -> {
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(4, 14, 8); }
                        case DOWN -> { return VecHelper.voxelSpace(4, 2, 8); }
                        case NORTH -> { return VecHelper.voxelSpace(4, 8, 14); }
                        case SOUTH -> { return VecHelper.voxelSpace(4, 8, 2); }
                    }
                }
            }
        }
        // 垂直朝向时，在四个侧面且远离喷嘴
        else {
            float horizontalAngle = AngleHelper.horizontalAngle(side);
            Vec3 baseLocation;
            if (facing == Direction.UP) {
                // 朝上时，在下半部分（远离喷嘴），向外移动避免内嵌
                baseLocation = VecHelper.voxelSpace(8, 4, 14);
            } else {
                // 朝下时，在上半部分（远离喷嘴），向外移动避免内嵌
                baseLocation = VecHelper.voxelSpace(8, 12, 14);
            }
            return VecHelper.rotateCentered(baseLocation, horizontalAngle, Direction.Axis.Y);
        }

        return VecHelper.voxelSpace(8, 14, 8);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction direction) {
        Direction facing = state.getValue(NozzleBlock.FACING);

        // 水平朝向时，在上下左右四个面显示（排除前后）
        if (facing.getAxis().isHorizontal()) {
            // 排除喷嘴方向和相反方向
            return direction != facing && direction != facing.getOpposite();
        }
        // 垂直朝向时，在四个水平面显示
        else {
            return direction.getAxis().isHorizontal();
        }
    }

    @Override
    protected Vec3 getSouthLocation() {
        return Vec3.ZERO;
    }
}