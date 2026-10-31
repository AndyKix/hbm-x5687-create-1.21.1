package com.hbm.port.client;

import com.hbm.port.infrastructure.EnergySocketMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class EnergySocketScreen extends AbstractContainerScreen<EnergySocketMenu> {
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("hbm","textures/gui/gui_battery_socket.png");
    public EnergySocketScreen(EnergySocketMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageHeight=181;inventoryLabelY=87;}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){
        float scale=Math.min(1F,112F/Math.max(1,font.width(title)));
        graphics.pose().pushPose();graphics.pose().scale(scale,scale,1);
        graphics.drawString(font,title,(int)((imageWidth/2F-font.width(title)*scale/2)/scale),(int)(6/scale),0x404040,false);
        graphics.pose().popPose();graphics.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0x404040,false);
    }
    @Override protected void renderBg(GuiGraphics graphics,float tick,int x,int y){
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);
        int fill=menu.value(1)<=0?0:(int)(52D*menu.value(0)/menu.value(1));
        graphics.blit(TEXTURE,leftPos+62,topPos+69-fill,176,52-fill,34,fill);
        graphics.blit(TEXTURE,leftPos+106,topPos+16,176,52+18*menu.data.get(20),18,18);
        graphics.blit(TEXTURE,leftPos+106,topPos+52,176,52+18*menu.data.get(21),18,18);
        graphics.blit(TEXTURE,leftPos+125,topPos+35,194,52+16*(menu.data.get(22)-1),16,16);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&minecraft!=null&&minecraft.gameMode!=null){
            int action=isHovering(106,16,18,18,x,y)?0:isHovering(106,52,18,18,x,y)?1:isHovering(125,35,16,16,x,y)?2:-1;
            if(action>=0){minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public void render(GuiGraphics graphics,int x,int y,float tick){
        super.render(graphics,x,y,tick);renderTooltip(graphics,x,y);
        Component tip=null;
        if(isHovering(62,17,34,52,x,y)){
            graphics.renderTooltip(font,java.util.List.of(Component.translatable("tooltip.hbm.energy_stored",menu.value(0),menu.value(1)),
                    Component.translatable("tooltip.hbm.energy_rates",menu.value(2),menu.value(3)),Component.translatable("tooltip.hbm.energy_delta",menu.value(4))),java.util.Optional.empty(),x,y);return;
        }
        else if(isHovering(106,16,18,18,x,y))tip=Component.translatable("tooltip.hbm.low_mode",Component.translatable("mode.hbm."+menu.data.get(20)));
        else if(isHovering(106,52,18,18,x,y))tip=Component.translatable("tooltip.hbm.high_mode",Component.translatable("mode.hbm."+menu.data.get(21)));
        else if(isHovering(125,35,16,16,x,y))tip=Component.translatable("tooltip.hbm.priority",Component.translatable("priority.hbm."+menu.data.get(22)));
        if(tip!=null)graphics.renderTooltip(font,tip,x,y);
    }
}
