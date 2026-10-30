package com.hbm.port.machine;

import com.hbm.port.HbmPort;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class IronFurnaceMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    public IronFurnaceMenu(int id, Inventory inventory) { this(id, inventory, new SimpleContainer(5), new SimpleContainerData(6)); }
    public IronFurnaceMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(HbmPort.IRON_FURNACE_MENU.get(), id);
        checkContainerSize(container, 5);
        checkContainerDataCount(data, 6);
        this.container = container;
        this.data = data;
        addSlot(new Slot(container, 0, 53, 17) {
            @Override public boolean mayPlace(ItemStack stack) { return IronFurnaceBlockEntity.smeltable(inventory.player.level(), stack); }
        });
        for (int fuel = 1; fuel <= 2; fuel++) addSlot(new Slot(container, fuel, 53 + (fuel - 1) * 18, 53) {
            @Override public boolean mayPlace(ItemStack stack) { return IronFurnaceBlockEntity.fuelTime(stack) > 0; }
        });
        addSlot(new net.minecraft.world.inventory.FurnaceResultSlot(inventory.player, container, 3, 125, 35) {
            @Override public void onTake(Player player, ItemStack stack) {
                super.onTake(player, stack);
                if (player instanceof ServerPlayer server && container instanceof IronFurnaceBlockEntity furnace) furnace.dropExperience(server);
            }
        });
        addSlot(new Slot(container, 4, 17, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean isActive() { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(data);
    }
    public int burnTime() { return (data.get(0) & 0xffff) | ((data.get(1) & 0xffff) << 16); }
    public int maxBurnTime() { return (data.get(2) & 0xffff) | ((data.get(3) & 0xffff) << 16); }
    public int progress() { return data.get(4); }
    public boolean active() { return data.get(5) != 0; }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < 5) {
            if (!moveItemStackTo(stack, 5, 41, true)) return ItemStack.EMPTY;
            if (index == 3) slot.onQuickCraft(stack, original);
        } else if (IronFurnaceBlockEntity.smeltable(player.level(), stack)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (IronFurnaceBlockEntity.fuelTime(stack) > 0) {
            if (!moveItemStackTo(stack, 1, 3, false)) return ItemStack.EMPTY;
        } else if (index < 32) {
            if (!moveItemStackTo(stack, 32, 41, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 5, 32, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
