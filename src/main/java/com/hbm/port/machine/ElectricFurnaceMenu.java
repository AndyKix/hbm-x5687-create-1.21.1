package com.hbm.port.machine;

import com.hbm.port.infrastructure.BatteryItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class ElectricFurnaceMenu extends AbstractContainerMenu {
    private final Container inventory;
    public final ContainerData data;
    public ElectricFurnaceMenu(int id,Inventory player){this(id,player,new SimpleContainer(4),new SimpleContainerData(4));}
    public ElectricFurnaceMenu(int id,Inventory player,Container inventory,ContainerData data){
        super(ElectricFurnaceContent.MENU.get(),id);this.inventory=inventory;this.data=data;
        addSlot(new Slot(inventory,0,56,53){@Override public boolean mayPlace(ItemStack stack){return stack.getItem() instanceof BatteryItem;}@Override public int getMaxStackSize(){return 1;}});
        addSlot(new Slot(inventory,1,56,17){@Override public boolean mayPlace(ItemStack stack){return IronFurnaceBlockEntity.smeltable(player.player.level(),stack);}});
        addSlot(new FurnaceResultSlot(player.player,inventory,2,116,35){@Override public void onTake(Player player,ItemStack stack){super.onTake(player,stack);if(player instanceof ServerPlayer server&&inventory instanceof ElectricFurnaceBlockEntity furnace)furnace.dropExperience(server);}});
        addSlot(new Slot(inventory,3,147,34){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean isActive(){return false;}});
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,8+col*18,84+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,142));addDataSlots(data);
    }
    public int power(){return(data.get(0)&65535)|((data.get(1)&65535)<<16);}
    @Override public boolean stillValid(Player player){return inventory.stillValid(player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var stack=slot.getItem();var copy=stack.copy();
        if(index<4){if(!moveItemStackTo(stack,4,slots.size(),true))return ItemStack.EMPTY;if(index==2)slot.onQuickCraft(stack,copy);}
        else if(stack.getItem() instanceof BatteryItem){if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;}
        else if(IronFurnaceBlockEntity.smeltable(player.level(),stack)){if(!moveItemStackTo(stack,1,2,false))return ItemStack.EMPTY;}
        else return ItemStack.EMPTY;
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();slot.onTake(player,stack);return copy;
    }
}
