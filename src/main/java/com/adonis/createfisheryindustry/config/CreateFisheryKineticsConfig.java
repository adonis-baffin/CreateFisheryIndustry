package com.adonis.createfisheryindustry.config;

import net.createmod.catnip.config.ConfigBase;

public class CreateFisheryKineticsConfig extends ConfigBase {
    public final CreateFisheryStressConfig stressValues = nested(0, CreateFisheryStressConfig::new, "Fine tune the kinetic stats of individual components");

    @Override
    public String getName() {
        return "kinetics";
    }
}