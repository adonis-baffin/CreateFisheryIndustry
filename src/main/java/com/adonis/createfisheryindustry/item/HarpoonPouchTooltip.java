package com.adonis.createfisheryindustry.item;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record HarpoonPouchTooltip(HarpoonPouchContents contents) implements TooltipComponent {
    public HarpoonPouchContents contents() {
        return this.contents;
    }
}