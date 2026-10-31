package com.hbm.port.compat;

import com.hbm.port.storage.CrateContent;
import com.hbm.port.storage.StorageCrateBlockEntity;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage;
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import com.hbm.port.storage.StorageCrateMenu;
import com.simibubi.create.api.contraption.storage.item.menu.StorageInteractionWrapper;
import java.util.function.Predicate;
import java.util.function.Consumer;
import net.neoforged.neoforge.items.IItemHandler;

/** Create owns the mounted inventory; disassembly restores its current contents, not a stale block-entity copy. */
public final class CrateMountedStorage extends SimpleMountedStorage {
    public static final MapCodec<CrateMountedStorage> CODEC = codec(CrateMountedStorage::new);
    public CrateMountedStorage(IItemHandler handler) { super(CrateContent.MOUNTED_CRATE.get(), handler); }
    @Override public boolean isItemValid(int slot, ItemStack stack) { return StorageCrateBlockEntity.canStore(stack) && super.isItemValid(slot, stack); }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return StorageCrateBlockEntity.canStore(stack) ? super.insertItem(slot, stack, simulate) : stack;
    }
    @Override protected MenuProvider createMenuProvider(Component name, IItemHandlerModifiable handler,
            Predicate<Player> stillValid, Consumer<Player> onClose) {
        int rows = handler.getSlots() / 9;
        if (rows != 4 && rows != 6) return null;
        var container = new StorageInteractionWrapper(handler, stillValid, onClose);
        // The generic chest menu does not consult Container.canPlaceItem; use our filtered slots while moving, too.
        return new SimpleMenuProvider((id, inventory, player) -> new StorageCrateMenu(id, inventory, container, rows), name);
    }
    public static final class Type extends SimpleMountedStorageType<CrateMountedStorage> {
        public Type() { super(CrateMountedStorage.CODEC); }
        @Override protected SimpleMountedStorage createStorage(IItemHandler handler) { return new CrateMountedStorage(handler); }
    }
}
