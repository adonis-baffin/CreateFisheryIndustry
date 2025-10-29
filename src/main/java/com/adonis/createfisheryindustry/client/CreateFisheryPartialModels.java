package com.adonis.createfisheryindustry.client;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

public class CreateFisheryPartialModels {

    public static final PartialModel THRESHER_BLADE = block("mechanical_peeler/blade");
    public static final PartialModel TRAP_BEARING_TOP = block("trap_bearing_top");

    private static PartialModel block(String path) {
        return PartialModel.of(CreateFisheryMod.asResource("block/" + path));
    }

    public static void init() {
    }
}