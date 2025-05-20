package com.adonis.createfisheryindustry.config;

import com.simibubi.create.api.stress.BlockStressValues;
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

// 引入你的模组主类或包含MODID常量的地方
// import com.adonis.createfisheryindustry.CreateFisheryMod;

public class CreateFisheryStressConfig extends ConfigBase {
    private final String modId; // 存储当前模组的ID

    // 这些是非静态的，每个StressConfig实例管理自己的默认值
    private final Map<ResourceLocation, Double> defaultImpacts = new HashMap<>();
    private final Map<ResourceLocation, Double> defaultCapacities = new HashMap<>();

    // 这些用于存储从配置文件加载的值
    private final Map<ResourceLocation, ModConfigSpec.ConfigValue<Double>> impacts = new HashMap<>();
    private final Map<ResourceLocation, ModConfigSpec.ConfigValue<Double>> capacities = new HashMap<>();

    public CreateFisheryStressConfig(String modId) {
        this.modId = modId;
    }

    protected int getVersion() {
        return 1; // 你配置文件的版本
    }

    @Override
    public void registerAll(ModConfigSpec.Builder builder) {
        builder.comment("Stress impact configurations for " + modId, "[in Stress Units]")
                .push("impact");
        // defaultImpacts 将包含由 setImpact 添加的条目
        defaultImpacts.forEach((id, defaultValue) -> {
            // 确保路径正确，不含命名空间
            impacts.put(id, builder.define(id.getPath(), defaultValue));
        });
        builder.pop();

        // 如果你有容量配置
        if (!defaultCapacities.isEmpty()) {
            builder.comment("Stress capacity configurations for " + modId, "[in Stress Units]")
                    .push("capacity");
            defaultCapacities.forEach((id, defaultValue) -> {
                capacities.put(id, builder.define(id.getPath(), defaultValue));
            });
            builder.pop();
        }

        // 关键：注册应力值提供者
        BlockStressValues.IMPACTS.registerProvider(this::getImpact);
        BlockStressValues.CAPACITIES.registerProvider(this::getCapacity); // 总是注册，getCapacity可以返回null
    }

    @Override
    public String getName() {
        // 配置文件名，例如 "createfisheryindustry-stressValues.v1"
        // Catnip 会自动加上 .toml 后缀
        // 确保这个名字对于你的模组是唯一的，通常会包含模组ID
        return modId + "-stressValues.v" + getVersion();
    }

    @Nullable
    public DoubleSupplier getImpact(Block block) {
        ResourceLocation id = RegisteredObjectsHelper.getKeyOrThrow(block);
        // 只为本模组的方块提供应力值
        if (!id.getNamespace().equals(this.modId)) {
            return null;
        }
        ModConfigSpec.ConfigValue<Double> configValue = this.impacts.get(id);
        // 如果配置文件中没有该条目（例如，新增的方块），则返回null
        // Create可能会有默认处理或报错
        return configValue == null ? null : configValue::get;
    }

    @Nullable
    public DoubleSupplier getCapacity(Block block) {
        ResourceLocation id = RegisteredObjectsHelper.getKeyOrThrow(block);
        if (!id.getNamespace().equals(this.modId)) {
            return null;
        }
        ModConfigSpec.ConfigValue<Double> configValue = this.capacities.get(id);
        return configValue == null ? null : configValue::get;
    }

    // --- 非静态的 setImpact 和 setCapacity 方法 ---
    // 这些方法由 Registrate 的 .transform() 调用

    public <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setImpact(double value) {
        return builder -> {
            // 确保这个方块属于我们正在配置的模组
            // builder.getOwner().getModid() 应该是 this.modId
            if (!builder.getOwner().getModid().equals(this.modId)) {
                throw new IllegalStateException("Attempting to set stress impact for block '" + builder.getName()
                        + "' from mod '" + builder.getOwner().getModid() + "' using config for mod '" + this.modId + "'.");
            }
            // builder.getName() 返回的是不带命名空间的路径名
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(this.modId, builder.getName());
            this.defaultImpacts.put(id, value);
            return builder;
        };
    }

    public <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setCapacity(double value) {
        return builder -> {
            if (!builder.getOwner().getModid().equals(this.modId)) {
                throw new IllegalStateException("Attempting to set stress capacity for block '" + builder.getName()
                        + "' from mod '" + builder.getOwner().getModid() + "' using config for mod '" + this.modId + "'.");
            }
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(this.modId, builder.getName());
            this.defaultCapacities.put(id, value);
            return builder;
        };
    }

    public <B extends Block, P> NonNullUnaryOperator<BlockBuilder<B, P>> setNoImpact() {
        return setImpact(0);
    }
}