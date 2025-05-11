package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.entity.HarpoonEntity;
import com.adonis.createfisheryindustry.entity.TetheredHarpoonEntity; // 新增导入
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CreateFisheryEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, CreateFisheryMod.ID);

    public static final DeferredHolder<EntityType<?>, EntityType<HarpoonEntity>> HARPOON =
            ENTITY_TYPES.register("harpoon",
                    () -> EntityType.Builder.<HarpoonEntity>of(HarpoonEntity::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("harpoon"));

    // 新增：栓系鱼叉实体
    public static final DeferredHolder<EntityType<?>, EntityType<TetheredHarpoonEntity>> TETHERED_HARPOON =
            ENTITY_TYPES.register("tethered_harpoon",
                    () -> EntityType.Builder.<TetheredHarpoonEntity>of(TetheredHarpoonEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .build("tethered_harpoon"));

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}