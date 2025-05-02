package com.adonis.createfisheryindustry.registry;

import static com.adonis.createfisheryindustry.CreateFisheryMod.REGISTRATE;
import static com.simibubi.create.foundation.data.TagGen.axeOrPickaxe;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapBlock;
import com.adonis.createfisheryindustry.block.FrameTrap.FrameTrapMovementBehaviour;
import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapBlock;
import com.adonis.createfisheryindustry.block.SmartMesh.SmartMeshBlock;
import com.adonis.createfisheryindustry.block.TrapNozzle.TrapNozzleBlock;
import com.simibubi.create.AllTags;
import com.simibubi.create.foundation.data.BlockStateGen;
import com.simibubi.create.foundation.data.SharedProperties;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.SoundType;

public class CreateFisheryBlocks {
    public static final BlockEntry<FrameTrapBlock> FRAME_TRAP = REGISTRATE
            .block("frame_trap", FrameTrapBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.BROWN)
                    .sound(SoundType.SCAFFOLDING)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .tag(AllTags.AllBlockTags.WINDMILL_SAILS.tag)
            .blockstate(BlockStateGen.directionalBlockProvider(false))
            .onRegister(block -> FrameTrapMovementBehaviour.REGISTRY.register(block, new FrameTrapMovementBehaviour()))
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/frame_trap"))))
            .simpleItem()
            .register();

    public static final BlockEntry<MeshTrapBlock> MESH_TRAP = REGISTRATE
            .block("mesh_trap", MeshTrapBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.WHITE)
                    .sound(SoundType.BAMBOO)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/mesh_trap"))))
            .simpleItem()
            .register();

    public static final BlockEntry<SmartMeshBlock> SMART_MESH = REGISTRATE
            .block("smart_mesh", SmartMeshBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.GRAY)
                    .sound(SoundType.NETHER_WOOD)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/smart_mesh"))))
            .simpleItem()
            .register();

    public static final BlockEntry<TrapNozzleBlock> TRAP_NOZZLE = CreateFisheryMod.REGISTRATE
            .block("trap_nozzle", TrapNozzleBlock::new)
            .initialProperties(SharedProperties::wooden)
            .properties(prop -> prop
                    .mapColor(DyeColor.WHITE)
                    .sound(SoundType.BAMBOO)
                    .noOcclusion())
            .transform(axeOrPickaxe())
            .blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models().cubeAll(ctx.getName(), prov.modLoc("block/trap_nozzle"))))
            .simpleItem()
            .register();

    public static void register() {}
}