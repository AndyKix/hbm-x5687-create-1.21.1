package com.hbm.port.client;

import com.hbm.port.infrastructure.FluidIdentifierMenu;
import com.hbm.port.infrastructure.FluidKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Only source fluids with a working implementation are offered. */
public final class FluidIdentifierScreen extends AbstractContainerScreen<FluidIdentifierMenu> {
    public FluidIdentifierScreen(FluidIdentifierMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=224;imageHeight=132;}
    @Override protected void init(){
        super.init();
        for(int column=0;column<2;column++)for(var kind:FluidKind.values()){
            int action=column*3+kind.ordinal();
            addRenderableWidget(Button.builder(kind.label(),button->{if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);})
                    .bounds(leftPos+8+column*108,topPos+48+kind.ordinal()*24,100,20).build());
        }
    }
    @Override protected void renderBg(GuiGraphics graphics,float tick,int x,int y){graphics.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xff353b42);}
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){
        graphics.drawString(font,title,8,8,0xffffff,false);
        graphics.drawString(font,Component.translatable("tooltip.hbm.identifier_primary", ""),8,27,0xffffff,false);
        graphics.drawString(font,Component.translatable("tooltip.hbm.identifier_secondary", ""),116,27,0xffffff,false);
        graphics.drawString(font,menu.primary().label(),8,37,0xffffff,false);
        graphics.drawString(font,menu.secondary().label(),116,37,0xffffff,false);
    }
}
