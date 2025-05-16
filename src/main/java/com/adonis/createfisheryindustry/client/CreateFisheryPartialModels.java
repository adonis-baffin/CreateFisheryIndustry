package com.adonis.createfisheryindustry.client;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation; // Ensure this is imported if CreateFisheryMod.asResource returns it

public class CreateFisheryPartialModels {

    public static final PartialModel PEELER_BLADE_HORIZONTAL_ACTIVE = partial("block/mechanical_peeler/blade_horizontal_active");
    public static final PartialModel PEELER_BLADE_HORIZONTAL_INACTIVE = partial("block/mechanical_peeler/blade_horizontal_inactive");
    public static final PartialModel PEELER_BLADE_HORIZONTAL_REVERSED = partial("block/mechanical_peeler/blade_horizontal_reversed");

    public static final PartialModel PEELER_BLADE_VERTICAL_ACTIVE = partial("block/mechanical_peeler/blade_vertical_active");
    public static final PartialModel PEELER_BLADE_VERTICAL_INACTIVE = partial("block/mechanical_peeler/blade_vertical_inactive");
    public static final PartialModel PEELER_BLADE_VERTICAL_REVERSED = partial("block/mechanical_peeler/blade_vertical_reversed");

    // Shaft models will be referenced directly from com.simibubi.create.AllPartialModels

    private static PartialModel partial(String path) {
        // Use the static factory method 'of'
        return PartialModel.of(CreateFisheryMod.asResource(path));
    }

    public static void init() {
        // This method is called by FMLClientSetupEvent to ensure the static initializers run
        // and models are effectively "loaded" or at least their ResourceLocations are resolved.
    }
}