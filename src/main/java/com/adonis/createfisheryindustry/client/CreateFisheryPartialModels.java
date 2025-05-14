package com.adonis.createfisheryindustry.client;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;

public class CreateFisheryPartialModels {
    public static final PartialModel ROLLER_HORIZONTAL_ACTIVE = partial("block/mechanical_peeler_roller_horizontal_active");
    public static final PartialModel ROLLER_HORIZONTAL_INACTIVE = partial("block/mechanical_peeler_roller_horizontal_inactive");
    public static final PartialModel ROLLER_VERTICAL_ACTIVE = partial("block/mechanical_peeler_roller_vertical_active");
    public static final PartialModel ROLLER_VERTICAL_INACTIVE = partial("block/mechanical_peeler_roller_vertical_inactive");

    private static PartialModel partial(String path) {
        return PartialModel.of(CreateFisheryMod.asResource(path));
    }

    public static void init() {
        // 静态初始化，确保模型注册
    }
}