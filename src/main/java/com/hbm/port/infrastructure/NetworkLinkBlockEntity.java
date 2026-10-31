package com.hbm.port.infrastructure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public final class NetworkLinkBlockEntity extends BlockEntity {
    private final IFluidHandler handler=new PipeHandler();
    public NetworkLinkBlockEntity(BlockPos pos,BlockState state){super(InfrastructureContent.LINK_ENTITY.get(),pos,state);}
    @Override public void onLoad(){super.onLoad();InfrastructureNetworks.register(this);}
    @Override public void setRemoved(){InfrastructureNetworks.unregister(this);super.setRemoved();}
    public boolean isFluid(){return getBlockState().getBlock() instanceof NetworkLinkBlock block&&block.isFluid();}
    public FluidKind fluid(){return getBlockState().getValue(NetworkLinkBlock.FLUID);}
    public void configure(FluidKind kind){
        if(level==null||level.isClientSide||!isFluid()||kind==fluid())return;
        level.setBlock(worldPosition,getBlockState().setValue(NetworkLinkBlock.FLUID,kind),3);
        level.invalidateCapabilities(worldPosition);InfrastructureNetworks.dirty(level);setChanged();
    }
    public IFluidHandler fluidHandler(){return handler;}
    private final class PipeHandler implements IFluidHandler {
        private java.util.List<SteelBarrelBlockEntity> tanks(){return InfrastructureNetworks.pipeTanks(NetworkLinkBlockEntity.this);}
        public int getTanks(){return tanks().size();}
        public FluidStack getFluidInTank(int tank){var tanks=tanks();return tank<0||tank>=tanks.size()?FluidStack.EMPTY:tanks.get(tank).contents();}
        public int getTankCapacity(int tank){return tank>=0&&tank<getTanks()?16_000:0;}
        public boolean isFluidValid(int tank,FluidStack resource){return tank>=0&&tank<getTanks()&&fluid().accepts(resource);}
        public int fill(FluidStack resource,FluidAction action){
            if(!fluid().accepts(resource))return 0;int filled=0;
            for(var tank:tanks()){
                var remaining=resource.copy();remaining.setAmount(resource.getAmount()-filled);
                filled+=tank.externalHandler().fill(remaining,action);if(filled==resource.getAmount())break;
            }
            return filled;
        }
        public FluidStack drain(FluidStack resource,FluidAction action){return fluid().accepts(resource)?drain(resource.getAmount(),action):FluidStack.EMPTY;}
        public FluidStack drain(int requested,FluidAction action){
            if(requested<=0)return FluidStack.EMPTY;int drained=0;
            for(var tank:tanks()){
                drained+=tank.externalHandler().drain(requested-drained,action).getAmount();if(drained==requested)break;
            }
            return fluid().stack(drained);
        }
    }
}
