package com.adonis.createfisheryindustry.block.MeshTrap;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MeshTrapMountedStorageType extends MountedItemStorageType<MeshTrapMountedStorage> {

    public MeshTrapMountedStorageType() {
        super(MeshTrapMountedStorage.CODEC);
    }

    @Override
    public @Nullable MeshTrapMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MeshTrapBlockEntity meshTrap) {
            return MeshTrapMountedStorage.fromMeshTrap(meshTrap);
        }
        return null;
    }
}