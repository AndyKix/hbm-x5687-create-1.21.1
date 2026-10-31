package com.hbm.port.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import java.util.List;

public final class StorageCrateBlock extends BaseEntityBlock {
    public static final MapCodec<StorageCrateBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(), Codec.INT.fieldOf("rows").forGetter(StorageCrateBlock::rows)).apply(instance, StorageCrateBlock::new));
    private final int rows;
    public StorageCrateBlock(Properties properties, int rows) {
        super(properties);
        if (rows != 4 && rows != 6) throw new IllegalArgumentException("X5687 crate must have four or six rows");
        this.rows = rows;
    }
    public int rows() { return rows; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new StorageCrateBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof StorageCrateBlockEntity crate) player.openMenu(crate);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof StorageCrateBlockEntity crate && !crate.isEmpty())
            popResource(level, pos, crate.packedItem());
        return super.playerWillDestroy(level, pos, state, player);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        super.onRemove(state, level, pos, replacement, moving);
        if (!state.is(replacement.getBlock())) level.updateNeighbourForOutputSignal(pos, this);
        // The loot table copies container components into exactly one crate item; no loose-item ejection.
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.hbm.crate_slots", rows * 9).withStyle(ChatFormatting.GRAY));
        int shown = 0, total = 0;
        for (ItemStack contents : stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItems()) {
            total++;
            if (shown++ < 5) lines.add(Component.translatable("container.shulkerBox.itemCount", contents.getHoverName(), contents.getCount()));
        }
        if (total > 5) lines.add(Component.translatable("container.shulkerBox.more", total - 5).withStyle(ChatFormatting.ITALIC));
    }
}
