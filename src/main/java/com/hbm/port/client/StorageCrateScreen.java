package com.hbm.port.client;

import com.hbm.port.storage.StorageCrateMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class StorageCrateScreen extends AbstractContainerScreen<StorageCrateMenu> {
    private final ResourceLocation texture;
    public StorageCrateScreen(StorageCrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = menu.rows() * 18 + 114;
        inventoryLabelY = imageHeight - 94;
        texture = ResourceLocation.fromNamespaceAndPath("hbm", "textures/gui/gui_crate_" + (menu.rows() == 4 ? "iron" : "steel") + ".png");
    }
    @Override protected void init() { super.init(); titleLabelX = (imageWidth - font.width(title)) / 2; }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
