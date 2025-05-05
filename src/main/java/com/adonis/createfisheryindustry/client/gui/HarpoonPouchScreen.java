package com.adonis.createfisheryindustry.client.gui;

import com.adonis.createfisheryindustry.CreateFisheryMod;
import com.adonis.createfisheryindustry.container.HarpoonPouchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class HarpoonPouchScreen extends AbstractContainerScreen<HarpoonPouchMenu> {
    private static final ResourceLocation HARPOON_POUCH_TEXTURE = CreateFisheryMod.asResource("textures/gui/harpoon_pouch.png");

    public HarpoonPouchScreen(HarpoonPouchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick); // 修改这里，传递所有四个参数
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }


    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        graphics.blit(HARPOON_POUCH_TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }
}