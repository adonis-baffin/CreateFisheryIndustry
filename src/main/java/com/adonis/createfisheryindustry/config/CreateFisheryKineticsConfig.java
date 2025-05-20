package com.adonis.createfisheryindustry.config;

import com.adonis.createfisheryindustry.CreateFisheryMod; // 确保 CreateFisheryMod.ID 可访问
import net.createmod.catnip.config.ConfigBase;

public class CreateFisheryKineticsConfig extends ConfigBase {
    public final CreateFisheryStressConfig stressValues;

    // 无参构造函数是 ConfigBase.nested 所期望的
    // 当 CreateFisheryKineticsConfig::new 被调用时，会执行这个
    public CreateFisheryKineticsConfig() {
        this.stressValues = nested(
                0,
                () -> new CreateFisheryStressConfig(CreateFisheryMod.ID), // 使用 Lambda 传递参数
                "Fine tune the kinetic stats of individual components"
        );
    }

    @Override
    public String getName() {
        return "kinetics";
    }
}