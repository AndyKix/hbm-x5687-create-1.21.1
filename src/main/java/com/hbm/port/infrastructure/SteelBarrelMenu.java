package com.hbm.port.infrastructure;

import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class SteelBarrelMenu extends AbstractContainerMenu {
    private final Container inventory;
    public final ContainerData data;
    public SteelBarrelMenu(int id,Inventory player){this(id,player,new SimpleContainer(6),new SimpleContainerData(3));}
    public SteelBarrelMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(InfrastructureContent.BARREL_MENU.get(),id);this.inventory=inventory;this.data=data;
        int[][] positions={{8,17},{8,53},{35,17},{35,53},{125,17},{125,53}};
        for(int slot=0;slot<6;slot++){
            int index=slot;
            addSlot(new Slot(inventory,slot,positions[slot][0],positions[slot][1]){
                @Override public boolean mayPlace(ItemStack stack){return SteelBarrelBlockEntity.validInput(fluid(),index,stack)&&inventory.canPlaceItem(index,stack);}
            });
        }
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));addDataSlots(data);
    }
    public FluidKind fluid(){return FluidKind.values()[Math.clamp(data.get(1),0,FluidKind.values().length-1)];}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public boolean clickMenuButton(Player player,int button){
        if(button!=0||!(inventory instanceof SteelBarrelBlockEntity barrel)||!stillValid(player)||player.isSpectator())return false;
        barrel.setMode((barrel.mode()+1)%4);broadcastChanges();return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index){
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var source=slot.getItem();var copy=source.copy();
        if(index<6){if(!moveItemStackTo(source,6,slots.size(),true))return ItemStack.EMPTY;}
        else{
            int target=source.getItem() instanceof FluidIdentifierItem?0:slots.get(2).mayPlace(source)?2:slots.get(4).mayPlace(source)?4:-1;
            if(target<0||!moveItemStackTo(source,target,target+1,false))return ItemStack.EMPTY;
        }
        if(source.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,source);return copy;
    }
}
