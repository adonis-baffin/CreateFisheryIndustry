package com.adonis.createfisheryindustry.fluid;

import com.simibubi.create.AllFluids;
import com.tterrag.registrate.builders.FluidBuilder.FluidTypeFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class SolidRenderFluidType extends AllFluids.TintedFluidType {

    protected static final int NO_TINT = -1;
    protected static final int NO_ALPHA = 0x00FFFFFF; // 去除 alpha 通道，确保不透明

    private final int tintColor;
    private final int blockTintColor;
    private final Vector3f fogColor;
    private final Supplier<Float> fogDistanceModifier;

    protected SolidRenderFluidType(Properties properties, ResourceLocation stillTexture, ResourceLocation flowingTexture,
                                   int tintColor, Vector3f fogColor, Supplier<Float> fogDistanceModifier) {
        super(properties, stillTexture, flowingTexture);
        this.tintColor = tintColor;
        this.blockTintColor = tintColor & NO_ALPHA; // 块渲染时去除 alpha
        this.fogColor = fogColor;
        this.fogDistanceModifier = fogDistanceModifier;
    }

    public static FluidTypeFactory create(int tintColor, Vector3f fogColor, Supplier<Float> fogDistanceModifier) {
        return (properties, still, flow) -> new SolidRenderFluidType(properties, still, flow,
                tintColor, fogColor, fogDistanceModifier);
    }

    public static FluidTypeFactory create(Vector3f fogColor, Supplier<Float> fogDistanceModifier) {
        return (properties, still, flow) -> new SolidRenderFluidType(properties, still, flow,
                NO_TINT, fogColor, fogDistanceModifier);
    }

    @Override
    protected int getTintColor(FluidStack stack) {
        return tintColor;
    }

    @Override
    protected int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        return blockTintColor;
    }

    @Override
    protected Vector3f getCustomFogColor() {
        return fogColor;
    }

    @Override
    protected float getFogDistanceModifier() {
        return fogDistanceModifier.get();
    }
}