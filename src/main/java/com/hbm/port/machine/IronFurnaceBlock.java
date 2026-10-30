package com.hbm.port.machine;

import com.hbm.port.HbmPort;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** X5687 footprint: at SOUTH, x[-1,0], y[0,1], z[-1,0] relative to the controller. */
public final class IronFurnaceBlock extends BaseEntityBlock implements WorldlyContainerHolder {
    public static final MapCodec<IronFurnaceBlock> CODEC = simpleCodec(IronFurnaceBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public IronFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, 0).setValue(LIT, false));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, PART, LIT);
    }
    public static BlockPos offset(int part, Direction facing) {
        // Bits: west, north, up in the original SOUTH orientation.
        int x = -(part & 1), z = -((part >> 1) & 1), y = (part >> 2) & 1;
        return switch (facing) {
            case NORTH -> new BlockPos(-x, y, -z);
            case EAST -> new BlockPos(z, y, -x);
            case WEST -> new BlockPos(-z, y, x);
            default -> new BlockPos(x, y, z);
        };
    }
    public static BlockPos corePos(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.getValue(PART), state.getValue(FACING)));
    }
    @Nullable public static IronFurnaceBlockEntity controller(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof IronFurnaceBlock)) return null;
        BlockPos core = corePos(pos, state);
        if (!level.hasChunkAt(core)) return null;
        BlockEntity be = level.getBlockEntity(core);
        return be instanceof IronFurnaceBlockEntity furnace && !furnace.isRemoved() ? furnace : null;
    }
    public boolean canAssemble(Level level, BlockPos core, Direction facing, @Nullable BlockPlaceContext context) {
        for (int part = 0; part < 8; part++) {
            BlockPos pos = core.offset(offset(part, facing));
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) return false;
            BlockState state = level.getBlockState(pos);
            if (!(context == null ? state.canBeReplaced() : state.canBeReplaced(BlockPlaceContext.at(context, pos, Direction.UP)))) return false;
            if (!level.isUnobstructed(defaultBlockState(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty())) return false;
        }
        return true;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canAssemble(context.getLevel(), context.getClickedPos(), facing, context)
                ? defaultBlockState().setValue(FACING, facing) : null;
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        for (int part = 1; part < 8; part++) level.setBlock(pos.offset(offset(part, state.getValue(FACING))), state.setValue(PART, part), 3);
        invalidateParts(level, pos, state.getValue(FACING));
    }
    public static void invalidateParts(Level level, BlockPos core, Direction facing) {
        for (int part = 0; part < 8; part++) level.invalidateCapabilities(core.offset(offset(part, facing)));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new IronFurnaceBlockEntity(pos, state) : null;
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || state.getValue(PART) != 0 ? null
                : createTickerHelper(type, HbmPort.IRON_FURNACE_ENTITY.get(), IronFurnaceBlockEntity::tick);
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return state.getValue(PART) == 0 ? RenderShape.MODEL : RenderShape.INVISIBLE; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            IronFurnaceBlockEntity furnace = controller(level, pos, state);
            if (furnace != null) player.openMenu(furnace);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        IronFurnaceBlockEntity furnace = controller(level, pos, state);
        return furnace == null ? EmptyContainer.INSTANCE : furnace;
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide) {
            IronFurnaceBlockEntity furnace = controller(level, pos, state);
            if (furnace != null && !furnace.dismantling) {
                furnace.dismantling = true;
                BlockPos core = corePos(pos, state);
                Containers.dropContents(level, core, furnace);
                furnace.clearContent();
                furnace.dropExperience(null);
                Direction facing = state.getValue(FACING);
                for (int part = 0; part < 8; part++) {
                    BlockPos other = core.offset(offset(part, facing));
                    BlockState otherState = level.getBlockState(other);
                    if (!other.equals(pos) && otherState.is(this) && corePos(other, otherState).equals(core))
                        level.removeBlock(other, false);
                }
                invalidateParts(level, core, facing);
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
    private static final class EmptyContainer extends SimpleContainer implements WorldlyContainer {
        static final EmptyContainer INSTANCE = new EmptyContainer();
        private EmptyContainer() { super(0); }
        public int[] getSlotsForFace(Direction side) { return new int[0]; }
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return false; }
    }
}
