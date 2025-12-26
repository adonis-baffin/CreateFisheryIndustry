package com.adonis.createfisheryindustry.fluid;

import com.tterrag.registrate.builders.FluidBuilder.FluidTypeFactory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ThickSoupFluidType extends SolidRenderFluidType {
    private static final Supplier<Float> DEFAULT_FOG_MODIFIER = () -> 0.035f; // 数值越小视野越短，越浓稠

    private ThickSoupFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture,
                               int tintColor, Vector3f fogColor) {
        super(properties, stillTexture, flowingTexture, tintColor, fogColor, DEFAULT_FOG_MODIFIER);
    }

    public static FluidTypeFactory create(int color) {
        Vector3f fogColor = new Vector3f(
                ((color >> 16) & 255) / 255f,
                ((color >> 8) & 255) / 255f,
                (color & 255) / 255f
        );
        return (properties, still, flow) -> new ThickSoupFluidType(properties, still, flow, NO_TINT, fogColor);
    }

    @Override
    public boolean isVaporizedOnPlacement(Level level, BlockPos pos, FluidStack stack) {
        return level.dimensionType().ultraWarm();
    }
}