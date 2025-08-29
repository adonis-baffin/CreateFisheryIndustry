package com.adonis.createfisheryindustry.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.List;
import java.util.Optional;

public record PeelerEntityProcessing(
    EntityType<?> entityType,
    ResourceLocation lootTable,
    int cooldownTicks,
    Optional<String> condition // 用于支持变体等条件
) {
    public static final Codec<PeelerEntityProcessing> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity").forGetter(PeelerEntityProcessing::entityType),
            ResourceLocation.CODEC.fieldOf("loot_table").forGetter(PeelerEntityProcessing::lootTable),
            Codec.INT.optionalFieldOf("cooldown", 1200).forGetter(PeelerEntityProcessing::cooldownTicks),
            Codec.STRING.optionalFieldOf("condition").forGetter(PeelerEntityProcessing::condition)
        ).apply(instance, PeelerEntityProcessing::new)
    );
}