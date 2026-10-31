package com.hbm.port.storage;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

public final class StorageCrateItem extends BlockItem {
    public StorageCrateItem(Block block, Properties properties) { super(block, properties); }
    @Override public boolean canFitInsideContainerItems() { return false; }
}
