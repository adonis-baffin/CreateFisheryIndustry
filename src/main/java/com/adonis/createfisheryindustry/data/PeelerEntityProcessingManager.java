package com.adonis.createfisheryindustry.data;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

public class PeelerEntityProcessingManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String FOLDER = "peeler_entity_processing";

    private static final Map<EntityType<?>, PeelerEntityProcessing> PROCESSING_MAP = new HashMap<>();

    public PeelerEntityProcessingManager() {
        super(GSON, FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        PROCESSING_MAP.clear();

        object.forEach((id, json) -> {
            PeelerEntityProcessing.CODEC
                    .parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> CreateFisheryMod.LOGGER.error("Failed to parse peeler entity processing {}: {}", id, error))
                    .ifPresent(processing -> {
                        PROCESSING_MAP.put(processing.entityType(), processing);
                        CreateFisheryMod.LOGGER.info("Loaded peeler entity processing for: {}", processing.entityType());
                    });
        });

        CreateFisheryMod.LOGGER.info("Loaded {} peeler entity processing entries", PROCESSING_MAP.size());
    }

    public static PeelerEntityProcessing getProcessing(EntityType<?> entityType) {
        return PROCESSING_MAP.get(entityType);
    }

    public static boolean hasProcessing(EntityType<?> entityType) {
        return PROCESSING_MAP.containsKey(entityType);
    }
}