package com.hbm.port.infrastructure;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class SteelBarrelBlock extends BaseEntityBlock {
    public static final MapCodec<SteelBarrelBlock> CODEC=simpleCodec(SteelBarrelBlock::new);
    public SteelBarrelBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SteelBarrelBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){
        return level.isClientSide?null:createTickerHelper(type,InfrastructureContent.BARREL_ENTITY.get(),SteelBarrelBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(!level.isClientSide&&level.getBlockEntity(pos) instanceof SteelBarrelBlockEntity barrel)player.openMenu(barrel);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){
        if(!state.is(replacement.getBlock())){
            if(level.getBlockEntity(pos) instanceof SteelBarrelBlockEntity barrel)Containers.dropContents(level,pos,barrel);
            InfrastructureNetworks.dirty(level);level.updateNeighbourForOutputSignal(pos,this);
        }
        super.onRemove(state,level,pos,replacement,moving);
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos) instanceof SteelBarrelBlockEntity barrel?barrel.comparator():0;}
}
