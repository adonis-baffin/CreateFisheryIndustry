package com.adonis.createfisheryindustry.registry;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class CreateFisheryTags {

    public static class Blocks {
        public static final TagKey<Block> TRAP_EXPORT_BLACKLIST = tag("trap_export_blacklist");

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(CreateFisheryMod.ID, name));
        }
    }
}