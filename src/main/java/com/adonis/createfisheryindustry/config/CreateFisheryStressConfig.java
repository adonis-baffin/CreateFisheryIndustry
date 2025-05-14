package com.adonis.createfisheryindustry.config;

import net.createmod.catnip.config.ConfigBase;
import net.createmod.catnip.registry.RegisteredObjectsHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.nullness.NonNullUnaryOperator;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;

public class CreateFisheryStressConfig extends ConfigBase {
    private static final Map<ResourceLocation, Double> DEFAULT_IMPACTS = new HashMap<>();
    private final Map<ResourceLocation, ModConfigSpec.ConfigValue<Double>> impacts = new HashMap<>();
    private final String modId;

    public CreateFisheryStressConfig() {
        this.modId = CreateFisheryConfig.ID;
    }

    @Override
    public void registerAll(ModConfigSpec.Builder builder) {
        builder.comment("Stress impact configurations", "[in Stress Units]")
                .push("impact");
        DEFAULT_IMPACTS.forEach((id, value) -> {
            String path = id.getPath();
            impacts.put(id, builder.define(path, value));
        });
        builder.pop();
    }

    @Override
    public String getName() {
        return "stressValues.v1";
    }

    @Nullable
    public DoubleSupplier getImpact(Block block) {
        ResourceLocation id = RegisteredObjectsHelper.getKeyOrThrow(block);
        ModConfigSpec.ConfigValue<Double> value = this.impacts.get(id);
        return value == null ? null : value::get;
    }

    public static <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setImpact(double value) {
        return builder -> {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CreateFisheryConfig.ID, builder.getName());
            DEFAULT_IMPACTS.put(id, value);
            return builder;
        };
    }
}