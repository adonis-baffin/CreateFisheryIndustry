package com.adonis.createfisheryindustry.client;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CreateFisheryPartialModels {

    // 使用更清晰的命名
    public static final PartialModel THRESHER_BLADE = block("mechanical_peeler/blade");

    // 使用与 Create 相同的方法签名
    private static PartialModel block(String path) {
        return PartialModel.of(CreateFisheryMod.asResource("block/" + path));
    }

    public static void init() {
        // Called by FMLClientSetupEvent to initialize models
    }
}