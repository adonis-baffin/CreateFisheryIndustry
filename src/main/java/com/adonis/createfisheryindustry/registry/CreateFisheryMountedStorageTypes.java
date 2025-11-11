package com.adonis.createfisheryindustry.registry;

import static com.adonis.createfisheryindustry.CreateFisheryMod.REGISTRATE;

import com.adonis.createfisheryindustry.block.MeshTrap.MeshTrapMountedStorageType;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.neoforged.bus.api.IEventBus;

public class CreateFisheryMountedStorageTypes {

    public static final RegistryEntry<MountedItemStorageType<?>, MeshTrapMountedStorageType> MESH_TRAP;

    static {
        MESH_TRAP = REGISTRATE
                .mountedItemStorage("mesh_trap", MeshTrapMountedStorageType::new)
                .register();
    }

    public static void register(IEventBus modBus) {}
}