package com.hbm.port.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import javax.annotation.Nullable;
import java.util.stream.IntStream;

/** Base placed storage from X5687: iron 36 slots, steel 54; portable contents enabled as in legacy defaults. */
public final class StorageCrateBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    private final int rows;
    private NonNullList<ItemStack> items;
    private final IItemHandlerModifiable handler = new InvWrapper(this);
    public StorageCrateBlockEntity(BlockPos pos, BlockState state) {
        super(CrateContent.ENTITY.get(), pos, state);
        rows = ((StorageCrateBlock) state.getBlock()).rows();
        items = NonNullList.withSize(rows * 9, ItemStack.EMPTY);
    }
    public int rows() { return rows; }
    public IItemHandlerModifiable itemHandler() { return handler; }
    @Override protected Component getDefaultName() { return getBlockState().getBlock().getName(); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return rows * 9; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new StorageCrateMenu(id, inventory, this, rows); }
    public static boolean canStore(ItemStack stack) { return stack.canFitInsideContainerItems(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return canStore(stack); }
    @Override public int[] getSlotsForFace(Direction side) { return IntStream.range(0, getContainerSize()).toArray(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return true; }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (isEmpty()) {
            // Empty mined crates must stack with freshly crafted crates, including after emptying a formerly filled one.
            builder.set(DataComponents.CONTAINER, null);
            builder.set(DataComponents.MAX_STACK_SIZE, null);
        } else builder.set(DataComponents.MAX_STACK_SIZE, 1);
    }
    public ItemStack packedItem() {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        stack.applyComponents(collectComponents());
        return stack;
    }
    @Override public void startOpen(Player player) {
        if (level != null && !level.isClientSide && !player.isSpectator())
            level.playSound(null, worldPosition, CrateContent.OPEN.get(), SoundSource.BLOCKS, 1, 1);
    }
    @Override public void stopOpen(Player player) {
        if (level != null && !level.isClientSide && !player.isSpectator())
            level.playSound(null, worldPosition, CrateContent.CLOSE.get(), SoundSource.BLOCKS, 1, 1);
    }
}
