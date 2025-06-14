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
                        case UP -> { return VecHelper.voxelSpace(8, 15, 11); }    // 上表面，向喷嘴方向移动1像素与下表面对称
                        case DOWN -> { return VecHelper.voxelSpace(8, 1, 11); }   // 下表面，向喷嘴方向移动1像素
                        case WEST -> { return VecHelper.voxelSpace(1, 8, 11); }   // 左侧面
                        case EAST -> { return VecHelper.voxelSpace(15, 8, 11); }  // 右侧面
                    }
                }
                case SOUTH -> {
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(8, 15, 5); }     // 上表面，向喷嘴方向移动1像素与下表面对称
                        case DOWN -> { return VecHelper.voxelSpace(8, 1, 5); }    // 下表面，向喷嘴方向移动1像素
                        case WEST -> { return VecHelper.voxelSpace(15, 8, 5); }
                        case EAST -> { return VecHelper.voxelSpace(1, 8, 5); }
                    }
                }
                case WEST -> {
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(11, 15, 8); }    // 上表面，向喷嘴方向移动1像素与下表面对称
                        case DOWN -> { return VecHelper.voxelSpace(11, 1, 8); }   // 下表面，向喷嘴方向移动1像素
                        case NORTH -> { return VecHelper.voxelSpace(11, 8, 1); }
                        case SOUTH -> { return VecHelper.voxelSpace(11, 8, 15); }
                    }
                }
                case EAST -> {
                    switch (side) {
                        case UP -> { return VecHelper.voxelSpace(5, 15, 8); }     // 上表面，向喷嘴方向移动1像素与下表面对称
                        case DOWN -> { return VecHelper.voxelSpace(5, 1, 8); }    // 下表面，向喷嘴方向移动1像素
                        case NORTH -> { return VecHelper.voxelSpace(5, 8, 15); }
                        case SOUTH -> { return VecHelper.voxelSpace(5, 8, 1); }
                    }
                }
            }
        }
        // 垂直朝向时，在四个侧面且远离喷嘴
        else {
            float horizontalAngle = AngleHelper.horizontalAngle(side);
            Vec3 baseLocation;
            if (facing == Direction.UP) {
                // 朝上时，在下半部分（远离喷嘴），向喷嘴方向移动1像素与DOWN朝向对称
                baseLocation = VecHelper.voxelSpace(8, 5, 15);
            } else {
                // 朝下时，在上半部分（远离喷嘴），向喷嘴方向移动1像素更接近中心
                baseLocation = VecHelper.voxelSpace(8, 11, 15);
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