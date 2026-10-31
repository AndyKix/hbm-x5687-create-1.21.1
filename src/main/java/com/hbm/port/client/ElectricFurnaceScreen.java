package com.hbm.port.client;

import com.hbm.port.machine.ElectricFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("hbm","textures/gui/gui_electric_furnace.png");
    public ElectricFurnaceScreen(ElectricFurnaceMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void init(){super.init();titleLabelX=(imageWidth-font.width(title))/2;}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int x,int y){
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);
        int fill=52*menu.power()/100_000;if(fill>0)graphics.blit(TEXTURE,leftPos+20,topPos+69-fill,200,52-fill,16,fill);
        int progress=menu.data.get(2);
        if(progress>0)graphics.blit(TEXTURE,leftPos+56,topPos+35,176,0,16,16);
        graphics.blit(TEXTURE,leftPos+79,topPos+34,176,17,24*progress/100+1,17);
        graphics.fill(leftPos+147,topPos+34,leftPos+163,topPos+50,0xff777777);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float partialTick){
        super.render(graphics,x,y,partialTick);renderTooltip(graphics,x,y);
        if(isHovering(20,17,16,52,x,y))graphics.renderTooltip(font,Component.translatable("tooltip.hbm.energy_stored",menu.power(),100_000),x,y);
        else if(isHovering(147,34,16,16,x,y)||isHovering(151,19,8,8,x,y))graphics.renderTooltip(font,Component.translatable("tooltip.hbm.upgrades_pending"),x,y);
    }
}
