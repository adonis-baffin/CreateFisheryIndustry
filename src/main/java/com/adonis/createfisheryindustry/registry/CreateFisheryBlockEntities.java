package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlockEntity;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.google.common.collect.ImmutableSet;

public class CreateFisheryBlockEntities {

    // 方块实体注册
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateFisheryMod.ID);

    // POI 类型注册
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CreateFisheryMod.ID);

    // 方块实体
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MeshTrapBlockEntity>> MESH_TRAP =
            BLOCK_ENTITIES.register("mesh_trap", () ->
                    BlockEntityType.Builder.of(MeshTrapBlockEntity::new, CreateFisheryBlocks.MESH_TRAP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartMeshBlockEntity>> SMART_MESH =
            BLOCK_ENTITIES.register("smart_mesh", () ->
                    BlockEntityType.Builder.of(SmartMeshBlockEntity::new, CreateFisheryBlocks.SMART_MESH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmartBeehiveBlockEntity>> SMART_BEEHIVE =
            BLOCK_ENTITIES.register("smart_beehive", () ->
                    BlockEntityType.Builder.of(SmartBeehiveBlockEntity::new, CreateFisheryBlocks.SMART_BEEHIVE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrapNozzleBlockEntity>> TRAP_NOZZLE =
            BLOCK_ENTITIES.register("trap_nozzle", () ->
                    BlockEntityType.Builder.of(
                            (pos, state) -> new TrapNozzleBlockEntity(CreateFisheryBlockEntities.TRAP_NOZZLE.get(), pos, state),
                            CreateFisheryBlocks.TRAP_NOZZLE.get()
                    ).build(null));

    // POI 类型
    public static final DeferredHolder<PoiType, PoiType> SMART_BEEHIVE_POI =
            POI_TYPES.register("smart_beehive", () ->
                    new PoiType(
                            ImmutableSet.of(CreateFisheryBlocks.SMART_BEEHIVE.get().defaultBlockState()),
                            1, // ticket count
                            32 // search distance
                    ));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        POI_TYPES.register(bus);
    }
}