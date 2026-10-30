package com.hbm.port.client;

import com.hbm.port.machine.IronFurnaceBlockEntity;
import com.hbm.port.machine.IronFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class IronFurnaceScreen extends AbstractContainerScreen<IronFurnaceMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("hbm", "textures/gui/gui_furnace_iron.png");
    public IronFurnaceScreen(IronFurnaceMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }
    @Override protected void init() { super.init(); titleLabelX = (imageWidth - font.width(title)) / 2; }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        graphics.blit(TEXTURE, leftPos + 53, topPos + 36, 176, 18, 70 * menu.progress() / IronFurnaceBlockEntity.PROCESSING_TIME, 5);
        if (menu.maxBurnTime() > 0) graphics.blit(TEXTURE, leftPos + 53, topPos + 45, 176, 23,
                (int) (70L * menu.burnTime() / menu.maxBurnTime()), 5);
        if (menu.active()) graphics.blit(TEXTURE, leftPos + 70, topPos + 16, 176, 0, 18, 18);
        graphics.fill(leftPos + 17, topPos + 35, leftPos + 33, topPos + 51, 0xff777777);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(17, 35, 16, 16, mouseX, mouseY))
            graphics.renderTooltip(font, Component.translatable("tooltip.hbm.upgrades_pending"), mouseX, mouseY);
    }
}
