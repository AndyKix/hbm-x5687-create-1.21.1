package com.hbm.port.infrastructure;

import com.hbm.port.machine.IronFurnaceBlock;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/** X5687 socket footprint matches the iron furnace; its eight power faces are bottom-horizontal only. */
public final class EnergySocketBlock extends BaseEntityBlock implements WorldlyContainerHolder {
    public static final MapCodec<EnergySocketBlock> CODEC = simpleCodec(EnergySocketBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    public static final EnumProperty<BatteryKind> PACK = EnumProperty.create("pack", BatteryKind.class);
    public EnergySocketBlock(Properties properties) {
        super(properties); registerDefaultState(stateDefinition.any().setValue(FACING,Direction.SOUTH).setValue(PART,0).setValue(PACK,BatteryKind.EMPTY));
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACING,PART,PACK); }
    public static BlockPos offset(int part, Direction facing) { return IronFurnaceBlock.offset(part,facing); }
    public static BlockPos corePos(BlockPos pos, BlockState state) { return pos.subtract(offset(state.getValue(PART),state.getValue(FACING))); }
    public static EnergySocketBlockEntity controller(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EnergySocketBlock)) return null;
        var core = corePos(pos,state);
        if (!level.hasChunkAt(core)) return null;
        return level.getBlockEntity(core) instanceof EnergySocketBlockEntity socket && !socket.isRemoved() ? socket : null;
    }
    public static boolean isPort(BlockState state, @Nullable Direction side) {
        return state.getValue(PART) < 4 && (side == null || side.getAxis().isHorizontal());
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var facing = context.getHorizontalDirection().getOpposite(); var level = context.getLevel(); var core = context.getClickedPos();
        for (int part=0;part<8;part++) {
            var pos = core.offset(offset(part,facing));
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)
                    || !level.getBlockState(pos).canBeReplaced(BlockPlaceContext.at(context,pos,Direction.UP))
                    || !level.isUnobstructed(defaultBlockState(),pos,net.minecraft.world.phys.shapes.CollisionContext.empty())) return null;
        }
        return defaultBlockState().setValue(FACING,facing);
    }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,@Nullable LivingEntity player,ItemStack stack) {
        for (int part=1;part<8;part++) level.setBlock(pos.offset(offset(part,state.getValue(FACING))),state.setValue(PART,part),3);
        invalidate(level,pos,state.getValue(FACING)); InfrastructureNetworks.dirty(level);
    }
    public static void invalidate(Level level,BlockPos core,Direction facing) {
        for (int part=0;part<8;part++) level.invalidateCapabilities(core.offset(offset(part,facing)));
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return state.getValue(PART)==0 ? new EnergySocketBlockEntity(pos,state) : null; }
    @Override protected RenderShape getRenderShape(BlockState state) { return state.getValue(PART)==0 ? RenderShape.MODEL : RenderShape.INVISIBLE; }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) {
        var socket=controller(level,pos,state);if (!level.isClientSide && socket!=null) player.openMenu(socket);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public WorldlyContainer getContainer(BlockState state,LevelAccessor level,BlockPos pos) {
        var socket=controller(level,pos,state); return socket==null ? Empty.INSTANCE : socket;
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos) {
        var socket=controller(level,pos,state); return socket==null ? 0 : socket.comparator();
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving) {
        if (!state.is(replacement.getBlock()) && !level.isClientSide) {
            var core=corePos(pos,state); var socket=controller(level,pos,state);
            if (socket!=null) Containers.dropContents(level,core,socket);
            for (int part=0;part<8;part++) {
                var other=core.offset(offset(part,state.getValue(FACING))); var otherState=level.getBlockState(other);
                if (!other.equals(pos) && otherState.is(this) && corePos(other,otherState).equals(core)) level.removeBlock(other,false);
            }
            invalidate(level,core,state.getValue(FACING)); InfrastructureNetworks.dirty(level);
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
    private static final class Empty extends SimpleContainer implements WorldlyContainer {
        static final Empty INSTANCE=new Empty(); private Empty(){super(0);}
        public int[] getSlotsForFace(Direction side){return new int[0];}
        public boolean canPlaceItemThroughFace(int slot,ItemStack stack,@Nullable Direction side){return false;}
        public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return false;}
    }
}
