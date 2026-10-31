package com.hbm.port.storage;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class StorageCrateMenu extends AbstractContainerMenu {
    private final Container container;
    private final int rows;
    public StorageCrateMenu(int id, Inventory inventory, int rows) { this(id, inventory, new SimpleContainer(rows * 9), rows); }
    public StorageCrateMenu(int id, Inventory inventory, Container container, int rows) {
        super(rows == 4 ? CrateContent.IRON_MENU.get() : CrateContent.STEEL_MENU.get(), id);
        checkContainerSize(container, rows * 9);
        this.rows = rows;
        this.container = container;
        container.startOpen(inventory.player);
        for (int row = 0; row < rows; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(container, row * 9 + col, 8 + col * 18, 18 + row * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return StorageCrateBlockEntity.canStore(stack); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, rows * 18 + 32 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, rows * 18 + 90));
    }
    public int rows() { return rows; }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public void removed(Player player) { super.removed(player); container.stopOpen(player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        int size = rows * 9;
        if (index < size) {
            if (!moveItemStackTo(stack, size, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!StorageCrateBlockEntity.canStore(stack) || !moveItemStackTo(stack, 0, size, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return original;
    }
}
