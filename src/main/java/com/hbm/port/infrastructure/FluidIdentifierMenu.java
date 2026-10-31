package com.hbm.port.infrastructure;

import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class FluidIdentifierMenu extends AbstractContainerMenu {
    private final Inventory inventory;
    private final int selected;
    private final ItemStack original;
    public FluidIdentifierMenu(int id,Inventory inventory){
        super(InfrastructureContent.IDENTIFIER_MENU.get(),id);this.inventory=inventory;selected=inventory.selected;original=inventory.getItem(selected);
        // A tracked, inactive slot synchronizes the edited held stack using native menu packets.
        addSlot(new Slot(inventory,selected,-10000,-10000){@Override public boolean isActive(){return false;}@Override public boolean mayPickup(Player player){return false;}@Override public boolean mayPlace(ItemStack stack){return false;}});
    }
    public FluidKind primary(){return FluidIdentifierItem.primary(inventory.getItem(selected));}
    public FluidKind secondary(){return FluidIdentifierItem.secondary(inventory.getItem(selected));}
    @Override public boolean stillValid(Player player){return inventory.selected==selected&&inventory.getItem(selected)==original&&original.getItem() instanceof FluidIdentifierItem;}
    @Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
    @Override public boolean clickMenuButton(Player player,int button){
        if(button<0||button>=6||!stillValid(player)||player.isSpectator())return false;
        original.set(button<3?InfrastructureContent.PRIMARY.get():InfrastructureContent.SECONDARY.get(),FluidKind.values()[button%3]);
        inventory.setChanged();broadcastChanges();return true;
    }
}
