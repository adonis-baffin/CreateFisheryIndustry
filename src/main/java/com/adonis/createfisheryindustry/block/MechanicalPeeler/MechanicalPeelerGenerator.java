package com.adonis.createfisheryindustry.block.MechanicalPeeler;

import com.simibubi.create.foundation.data.SpecialBlockStateGen;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;

import net.createmod.catnip.math.AngleHelper; // Corrected import
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.generators.ModelFile;

public class MechanicalPeelerGenerator extends SpecialBlockStateGen {

    @Override
    protected int getXRotation(BlockState state) {
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
        return facing == Direction.DOWN ? 180 : (facing.getAxis().isHorizontal() ? 90 : 0);
    }

    @Override
    protected int getYRotation(BlockState state) {
        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);
        if (facing.getAxis().isVertical()) {
            boolean axisAlongFirst = state.getValue(MechanicalPeelerBlock.AXIS_ALONG_FIRST_COORDINATE);
            boolean flipped = state.getValue(MechanicalPeelerBlock.FLIPPED);
            int angle = axisAlongFirst ? 270 : 0; // Base angle for UP when AXIS_ALONG_FIRST_COORDINATE is true (Z) or false (X)
            if (flipped) {
                angle = (angle + 180) % 360;
            }
            // For DOWN facing, it's often 180 degrees Y rotation from UP's equivalent if models are consistent,
            // but AXIS_ALONG_FIRST_COORDINATE might interact differently.
            // This needs careful checking against how your vertical model is oriented.
            // Example: If AXIS_ALONG_FIRST_COORDINATE (true = along Z) means the "front" is South for UP,
            // for DOWN, the "front" might be North.
            // Let's assume simple 180 for flipped for now, and specific handling for DOWN.
            // if (facing == Direction.DOWN) {
            //     angle = (angle + (axisAlongFirst ? 0 : 180)) % 360; // This is a guess, adjust as needed
            // }
            return angle;
        }
        return (int) AngleHelper.horizontalAngle(facing); // Catnip's helper returns float
    }

    @Override
    public <T extends Block> ModelFile getModel(DataGenContext<Block, T> ctx, RegistrateBlockstateProvider prov,
                                                BlockState state) {
        String path = "block/" + ctx.getName() + "/";
        String orientation;

        Direction facing = state.getValue(MechanicalPeelerBlock.FACING);

        if (facing.getAxis().isVertical()) {
            orientation = "vertical";
        } else {
            orientation = "horizontal";
        }
        // Example: createfisheryindustry:block/mechanical_peeler/vertical
        return prov.models().getExistingFile(prov.modLoc(path + orientation));
    }
}