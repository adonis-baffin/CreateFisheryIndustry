package com.adonis.createfisheryindustry.item;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.math.Fraction;

@OnlyIn(Dist.CLIENT)
public class ClientHarpoonPouchTooltip implements ClientTooltipComponent {
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "container/pouch/background");
    private static final int MARGIN_Y = 4;
    private static final int BORDER_WIDTH = 1;
    private static final int SLOT_SIZE_X = 18;
    private static final int SLOT_SIZE_Y = 20;
    private final HarpoonPouchContents contents;

    public ClientHarpoonPouchTooltip(HarpoonPouchTooltip tooltip) {
        this.contents = tooltip.contents();
    }

    @Override
    public int getHeight() {
        return this.backgroundHeight() + MARGIN_Y;
    }

    @Override
    public int getWidth(Font font) {
        return this.backgroundWidth();
    }

    private int backgroundWidth() {
        return this.gridSizeX() * SLOT_SIZE_X + 2 * BORDER_WIDTH;
    }

    private int backgroundHeight() {
        return this.gridSizeY() * SLOT_SIZE_Y + 2 * BORDER_WIDTH;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        int gridX = this.gridSizeX();
        int gridY = this.gridSizeY();
        guiGraphics.blitSprite(BACKGROUND_SPRITE, x, y, this.backgroundWidth(), this.backgroundHeight());
        boolean isFull = this.contents.weight().compareTo(Fraction.ONE) >= 0;
        int itemIndex = 0;

        for (int row = 0; row < gridY; ++row) {
            for (int col = 0; col < gridX; ++col) {
                int slotX = x + col * SLOT_SIZE_X + BORDER_WIDTH;
                int slotY = y + row * SLOT_SIZE_Y + BORDER_WIDTH;
                this.renderSlot(slotX, slotY, itemIndex++, isFull, guiGraphics, font);
            }
        }
    }

    private void renderSlot(int x, int y, int itemIndex, boolean isFull, GuiGraphics guiGraphics, Font font) {
        if (itemIndex >= this.contents.size()) {
            this.blit(guiGraphics, x, y, isFull ? Texture.BLOCKED_SLOT : Texture.SLOT);
        } else {
            this.blit(guiGraphics, x, y, Texture.SLOT);
            ItemStack itemstack = this.contents.getItemUnsafe(itemIndex);
            guiGraphics.renderItem(itemstack, x + 1, y + 1, itemIndex);
            guiGraphics.renderItemDecorations(font, itemstack, x + 1, y + 1);
            if (itemIndex == 0) {
                AbstractContainerScreen.renderSlotHighlight(guiGraphics, x + 1, y + 1, 0);
            }
        }
    }

    private void blit(GuiGraphics guiGraphics, int x, int y, Texture texture) {
        guiGraphics.blitSprite(texture.sprite, x, y, 0, texture.w, texture.h);
    }

    private int gridSizeX() {
        return Math.max(2, (int) Math.ceil(Math.sqrt((double) this.contents.size() + 1.0)));
    }

    private int gridSizeY() {
        return (int) Math.ceil(((double) this.contents.size() + 1.0) / (double) this.gridSizeX());
    }

    @OnlyIn(Dist.CLIENT)
    static enum Texture {
        SLOT(ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "container/pouch/slot"), 18, 20),
        BLOCKED_SLOT(ResourceLocation.fromNamespaceAndPath("createfisheryindustry", "container/pouch/blocked_slot"), 18, 20);

        public final ResourceLocation sprite;
        public final int w;
        public final int h;

        private Texture(ResourceLocation sprite, int w, int h) {
            this.sprite = sprite;
            this.w = w;
            this.h = h;
        }
    }
}