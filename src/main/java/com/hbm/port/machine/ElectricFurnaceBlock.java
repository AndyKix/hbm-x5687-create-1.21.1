package com.hbm.port.machine;

import com.hbm.port.infrastructure.InfrastructureNetworks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;

public final class ElectricFurnaceBlock extends BaseEntityBlock {
    public static final MapCodec<ElectricFurnaceBlock> CODEC=simpleCodec(ElectricFurnaceBlock::new);
    public static final DirectionProperty FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    public ElectricFurnaceBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(LIT,false));}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,LIT);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ElectricFurnaceBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide?null:createTickerHelper(type,ElectricFurnaceContent.ENTITY.get(),ElectricFurnaceBlockEntity::tick);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ElectricFurnaceBlockEntity furnace)player.openMenu(furnace);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){
        if(!state.is(replacement.getBlock())){
            if(!level.isClientSide&&level.getBlockEntity(pos) instanceof ElectricFurnaceBlockEntity furnace){Containers.dropContents(level,pos,furnace);furnace.dropExperience(null);}
            InfrastructureNetworks.dirty(level);
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
}
