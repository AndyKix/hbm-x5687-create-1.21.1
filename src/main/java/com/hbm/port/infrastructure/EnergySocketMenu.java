package com.hbm.port.infrastructure;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class EnergySocketMenu extends AbstractContainerMenu {
    private final Container inventory;
    public final ContainerData data;
    public EnergySocketMenu(int id,Inventory player){this(id,player,new SimpleContainer(1),new SimpleContainerData(24));}
    public EnergySocketMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(InfrastructureContent.SOCKET_MENU.get(),id);this.inventory=inventory;this.data=data;
        addSlot(new Slot(inventory,0,35,35){@Override public boolean mayPlace(ItemStack stack){return stack.getItem() instanceof BatteryItem&&inventory.canPlaceItem(0,stack);}@Override public int getMaxStackSize(){return 1;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,8+col*18,99+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,157));
        addDataSlots(data);
    }
    public long value(int field){long value=0;for(int i=0;i<4;i++)value|=((long)data.get(field*4+i)&65535)<<(i*16);return value;}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){
        if(!(inventory instanceof EnergySocketBlockEntity socket)||!stillValid(player)||player.isSpectator())return false;
        switch(button){case 0->socket.lowMode=(socket.lowMode+1)%4;case 1->socket.highMode=(socket.highMode+1)%4;case 2->socket.priority=socket.priority%3+1;default->{return false;}}
        socket.setChanged();broadcastChanges();return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var source=slot.getItem();var copy=source.copy();
        if(index==0){if(!moveItemStackTo(source,1,slots.size(),true))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(source,0,1,false))return ItemStack.EMPTY;
        if(source.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,source);return copy;
    }
}
