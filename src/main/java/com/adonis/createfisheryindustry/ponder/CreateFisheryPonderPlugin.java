package com.adonis.createfisheryindustry.ponder;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.ponder.scenes.FrameTrapScenes;
import com.adonis.createfisheryindustry.ponder.scenes.MechanicalPeelerScenes;
import com.adonis.createfisheryindustry.ponder.scenes.MeshTrapScenes;
import com.adonis.createfisheryindustry.registry.CreateFisheryBlocks;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class CreateFisheryPonderPlugin implements PonderPlugin {

    @Override
    public String getModId() {
        return CreateFisheryMod.ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerScenes(helper);

        // 注册Frame Trap场景
        helper.forComponents(CreateFisheryBlocks.FRAME_TRAP.getId())
                .addStoryBoard("frame_trap", FrameTrapScenes::frameTrap)
                .addStoryBoard("frame_trap_underwater", FrameTrapScenes::frameTrapUnderwater);

        // 注册Mesh Trap场景
        helper.forComponents(CreateFisheryBlocks.MESH_TRAP.getId())
                .addStoryBoard("mesh_trap", MeshTrapScenes::meshTrap);

        // 注册Mechanical Peeler场景
        helper.forComponents(CreateFisheryBlocks.MECHANICAL_PEELER.getId())
                .addStoryBoard("peeler", MechanicalPeelerScenes::processing)
                .addStoryBoard("peeler_tree", MechanicalPeelerScenes::treeProcessing);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderPlugin.super.registerTags(helper);

        // 将Frame Trap添加到机械动力的既有标签中
        helper.addToTag(AllCreatePonderTags.CONTRAPTION_ACTOR)
                .add(CreateFisheryBlocks.FRAME_TRAP.getId())
                .add(CreateFisheryBlocks.MESH_TRAP.getId())
                .add(CreateFisheryBlocks.SMART_MESH.getId());
    }
}