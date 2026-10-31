package com.hbm.port.material;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/** Modern counterpart of X5687 api.hbm.item.IDepthRockTool.
 * Advanced tool implementations will arrive with their production chains.
 */
public interface DepthRockTool {
    boolean canBreakRock(BlockGetter level, Player player, ItemStack tool, BlockState rock, BlockPos pos);
}
