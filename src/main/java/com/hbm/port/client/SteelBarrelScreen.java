package com.hbm.port.client;

import com.hbm.port.infrastructure.SteelBarrelMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class SteelBarrelScreen extends AbstractContainerScreen<SteelBarrelMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("hbm","textures/gui/gui_barrel.png");
    public SteelBarrelScreen(SteelBarrelMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Override protected void renderBg(GuiGraphics graphics,float tick,int x,int y){
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);
        int fill=52*menu.data.get(0)/16000;
        graphics.fill(leftPos+71,topPos+69-fill,leftPos+105,topPos+69,0xff000000|menu.fluid().color);
        graphics.blit(TEXTURE,leftPos+151,topPos+34,176,18*menu.data.get(2),18,18);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&isHovering(151,34,18,18,x,y)&&minecraft!=null&&minecraft.gameMode!=null){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick){
        super.render(graphics,x,y,tick);renderTooltip(graphics,x,y);
        if(isHovering(71,17,34,52,x,y))graphics.renderTooltip(font,Component.translatable("tooltip.hbm.barrel_contents",menu.data.get(0),16000,menu.fluid().label()),x,y);
        else if(isHovering(151,34,18,18,x,y))graphics.renderTooltip(font,Component.translatable("mode.hbm."+menu.data.get(2)),x,y);
    }
}
