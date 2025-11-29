package com.adonis.createfisheryindustry.client;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class CreateFisheryPartialModels {

    public static final PartialModel THRESHER_BLADE = block("mechanical_peeler/blade");

    private static PartialModel block(String path) {
        return PartialModel.of(CreateFisheryMod.asResource("block/" + path));
    }

    public static void init() {
    }
}