package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlockEntity;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlockEntity;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlockEntity;
import com.adonis.createfisheryindustry.block.SmartBeehive.SmartBeehiveBlockEntity;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerBlockEntity;
import com.adonis.createfisheryindustry.block.MechanicalPeeler.MechanicalPeelerVisual;
import com.adonis.createfisheryindustry.client.renderer.MechanicalPeelerRenderer;
import com.adonis.createfisheryindustry.CFIRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.google.common.collect.ImmutableSet;

public class CreateFisheryBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateFisheryMod.ID);

    private static final CFIRegistrate REGISTRATE = CreateFisheryMod.REGISTRATE;

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, CreateFisheryMod.ID);

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

    public static final BlockEntityEntry<MechanicalPeelerBlockEntity> MECHANICAL_PEELER = REGISTRATE
            .blockEntity("mechanical_peeler", MechanicalPeelerBlockEntity::new)
            .visual(() -> MechanicalPeelerVisual::new)
            .validBlocks(CreateFisheryBlocks.MECHANICAL_PEELER)
            .renderer(() -> MechanicalPeelerRenderer::new)
            .register();

    public static final DeferredHolder<PoiType, PoiType> SMART_BEEHIVE_POI =
            POI_TYPES.register("smart_beehive", () ->
                    new PoiType(
                            ImmutableSet.of(CreateFisheryBlocks.SMART_BEEHIVE.get().defaultBlockState()),
                            1,
                            32
                    ));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
        POI_TYPES.register(bus);
        CreateFisheryMod.LOGGER.info("CreateFisheryBlockEntities registered to event bus");
    }

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MESH_TRAP.get(),
                (be, side) -> be.getCapability(Capabilities.ItemHandler.BLOCK, side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SMART_BEEHIVE.get(),
                (be, side) -> {
                    if (be instanceof SmartBeehiveBlockEntity beehive) {
                        return side == null || side == Direction.UP
                                ? beehive.insertionHandler
                                : beehive.extractionHandler;
                    }
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                SMART_BEEHIVE.get(),
                (be, side) -> {
                    if (be instanceof SmartBeehiveBlockEntity beehive && (side == null || side == Direction.DOWN)) {
                        return beehive.getFluidTank();
                    }
                    return null;
                }
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                MECHANICAL_PEELER.get(), // Use your registered BlockEntityEntry
                (be, context) -> { // Context is the side from which capability is queried
                    if (be instanceof MechanicalPeelerBlockEntity peelerBE) {
                        // Allow item input from Top and Sides, but not Bottom (usually output)
                        if (context != Direction.DOWN) {
                            return peelerBE.inventory;
                        }
                    }
                    return null; // No capability for this side or wrong BE type
                }
        );

        TrapNozzleBlockEntity.registerCapabilities(event);
        SmartMeshBlockEntity.registerCapabilities(event);
    }

}