package com.hbm.port.infrastructure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class NetworkLinkBlock extends BaseEntityBlock {
    public static final IntegerProperty MASK=IntegerProperty.create("mask",0,63);
    public static final EnumProperty<FluidKind> FLUID=EnumProperty.create("fluid",FluidKind.class);
    public static final MapCodec<NetworkLinkBlock> CODEC=RecordCodecBuilder.mapCodec(instance->instance.group(
            propertiesCodec(),Codec.BOOL.fieldOf("fluid_link").forGetter(b->b.fluid)).apply(instance,NetworkLinkBlock::new));
    private final boolean fluid;
    public NetworkLinkBlock(Properties properties,boolean fluid){super(properties);this.fluid=fluid;registerDefaultState(stateDefinition.any().setValue(MASK,0).setValue(FLUID,FluidKind.NONE));}
    public boolean isFluid(){return fluid;}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(MASK,FLUID);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NetworkLinkBlockEntity(pos,state);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    public static int bit(Direction side){return switch(side){case EAST->32;case WEST->16;case UP->8;case DOWN->4;case SOUTH->2;case NORTH->1;};}
    public static boolean connects(LevelAccessor level,BlockPos pos,Direction side,boolean fluid,FluidKind type){
        var target=pos.relative(side);if(!level.hasChunkAt(target))return false;
        var state=level.getBlockState(target);
        if(fluid){
            if(state.getBlock() instanceof NetworkLinkBlock link)return link.fluid&&state.getValue(FLUID)==type;
            if(level.getBlockEntity(target) instanceof SteelBarrelBlockEntity barrel)return barrel.fluid()==type;
            return type!=FluidKind.NONE&&level instanceof Level world&&world.getCapability(Capabilities.FluidHandler.BLOCK,target,side.getOpposite())!=null;
        }
        if(state.getBlock() instanceof NetworkLinkBlock link)return !link.fluid;
        return state.getBlock() instanceof EnergySocketBlock&&EnergySocketBlock.isPort(state,side.getOpposite());
    }
    private BlockState connections(BlockState state,LevelAccessor level,BlockPos pos){
        int mask=0;for(var side:Direction.values())if(connects(level,pos,side,fluid,state.getValue(FLUID)))mask|=bit(side);
        return state.setValue(MASK,mask);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return connections(defaultBlockState(),context.getLevel(),context.getClickedPos());}
    @Override protected BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){return connections(state,level,pos);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,BlockPos other,boolean moving){
        var updated=connections(state,level,pos);if(updated!=state)level.setBlock(pos,updated,2);
        InfrastructureNetworks.dirty(level);
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState replacement,boolean moving){
        if(!state.is(replacement.getBlock()))InfrastructureNetworks.dirty(level);super.onRemove(state,level,pos,replacement,moving);
    }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        int mask=state.getValue(MASK);double min=fluid?5:5.5,max=16-min;
        if(fluid&&mask==0)return Shapes.block();
        if(fluid&&(mask&~48)==0)return Block.box(0,min,min,16,max,max);
        if(fluid&&(mask&~12)==0)return Block.box(min,0,min,max,16,max);
        if(fluid&&(mask&~3)==0)return Block.box(min,min,0,max,max,16);
        return Block.box((mask&16)!=0?0:min,(mask&4)!=0?0:min,(mask&1)!=0?0:min,
                (mask&32)!=0?16:max,(mask&8)!=0?16:max,(mask&2)!=0?16:max);
    }
}
