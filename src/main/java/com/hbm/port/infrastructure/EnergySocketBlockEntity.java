package com.hbm.port.infrastructure;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import java.util.*;

public final class EnergySocketBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer,HeNetworkNode {
    private NonNullList<ItemStack> items=NonNullList.withSize(1,ItemStack.EMPTY);
    private final IItemHandler itemHandler=new SidedInvWrapper(this,Direction.DOWN);
    public int lowMode=0,highMode=2,priority=1;
    private long budgetTick=Long.MIN_VALUE,received,provided;
    private final long[] history=new long[20]; private long previous,delta;
    private int comparator=-1;
    public final ContainerData data=new ContainerData() {
        public int getCount(){return 24;}
        public int get(int index){
            if(index<20){long value=switch(index/4){case 0->stored();case 1->capacity();case 2->kind().chargeRate;case 3->kind().dischargeRate;default->delta;};return (int)(value >>> ((index%4)*16)) & 65535;}
            return switch(index){case 20->lowMode;case 21->highMode;case 22->priority;case 23->mode();default->0;};
        }
        public void set(int index,int value){ }
    };
    public EnergySocketBlockEntity(BlockPos pos,BlockState state){super(InfrastructureContent.SOCKET_ENTITY.get(),pos,state);}
    @Override public void onLoad(){super.onLoad();InfrastructureNetworks.register(this);setChanged();}
    @Override public void setRemoved(){InfrastructureNetworks.unregister(this);super.setRemoved();}
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> value){items=value;}
    @Override public int getContainerSize(){return 1;}
    @Override public int getMaxStackSize(){return 1;}
    @Override protected Component getDefaultName(){return Component.translatable("container.hbm.battery_socket");}
    @Override protected AbstractContainerMenu createMenu(int id,Inventory inventory){return new EnergySocketMenu(id,inventory,this,data);}
    public IItemHandler itemHandler(){return itemHandler;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0 && stack.getItem() instanceof BatteryItem;}
    @Override public int[] getSlotsForFace(Direction side){return new int[]{0};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==0 && stack.getItem() instanceof BatteryItem item && item.charge(stack)==0;}
    public BatteryKind kind(){return items.getFirst().getItem() instanceof BatteryItem item ? item.kind : BatteryKind.EMPTY;}
    @Override public long stored(){return items.getFirst().getItem() instanceof BatteryItem item ? item.charge(items.getFirst()) : 0;}
    @Override public long capacity(){return kind().capacity;}
    public List<BlockPos> cells(){
        var facing=getBlockState().getValue(EnergySocketBlock.FACING);var cells=new ArrayList<BlockPos>();
        for(int part=0;part<4;part++)cells.add(worldPosition.offset(EnergySocketBlock.offset(part,facing)));return cells;
    }
    public int mode(){
        if(level!=null)for(var pos:cells())for(var side:Direction.values()){
            var neighbor=pos.relative(side);if(!level.hasChunkAt(neighbor))continue;var state=level.getBlockState(neighbor);
            if(state.getSignal(level,neighbor,side)>0)return highMode;
            if(state.isRedstoneConductor(level,neighbor))for(var around:Direction.values()){
                var indirect=neighbor.relative(around);if(level.hasChunkAt(indirect)&&level.getBlockState(indirect).getDirectSignal(level,indirect,around)>0)return highMode;
            }
        }
        return lowMode;
    }
    private void resetBudget(){
        long tick=level==null ? 0 : level.getGameTime();
        if(tick!=budgetTick){budgetTick=tick;received=provided=0;}
    }
    public long available(){resetBudget();int mode=mode();return mode==1||mode==2 ? Math.min(stored(),Math.max(0,kind().dischargeRate-provided)) : 0;}
    public long demand(){resetBudget();int mode=mode();return mode==0||mode==1 ? Math.min(capacity()-stored(),Math.max(0,kind().chargeRate-received)) : 0;}
    @Override public long receive(long request,boolean simulate){
        if(isRemoved() || level==null || level.isClientSide)return 0;
        long accepted=Math.min(Math.max(0,request),demand());
        if(!simulate && accepted>0){commit(accepted,0);setChanged();}return accepted;
    }
    @Override public long extract(long request,boolean simulate){
        if(isRemoved() || level==null || level.isClientSide)return 0;
        long accepted=Math.min(Math.max(0,request),available());
        if(!simulate && accepted>0){commit(0,accepted);setChanged();}return accepted;
    }
    /** Network commits all accounts before any comparator/neighbour callbacks. */
    public void commit(long incoming,long outgoing){
        resetBudget();var stack=items.getFirst();
        if(stack.getItem() instanceof BatteryItem item){item.setCharge(stack,stored()+incoming-outgoing);received+=incoming;provided+=outgoing;}
    }
    public int networkPriority(){return priority;}
    public int comparator(){return (int)Math.round(15D*stored()/Math.max(1,capacity()));}
    void observeTick(){
        long power=stored();long average=previous/2+power/2+((previous&1)+(power&1))/2;
        delta=average-history[0];System.arraycopy(history,1,history,0,19);history[19]=average;previous=power;
    }
    @Override public void setChanged(){
        super.setChanged();
        if(level==null||level.isClientSide||isRemoved()||!(getBlockState().getBlock() instanceof EnergySocketBlock))return;
        var state=level.getBlockState(worldPosition);
        if(!state.is(InfrastructureContent.SOCKET.get()))return;
        if(state.getValue(EnergySocketBlock.PACK)!=kind())level.setBlock(worldPosition,state.setValue(EnergySocketBlock.PACK,kind()),2);
        int current=comparator();
        if(current!=comparator){comparator=current;for(int part=0;part<8;part++)level.updateNeighbourForOutputSignal(worldPosition.offset(EnergySocketBlock.offset(part,state.getValue(EnergySocketBlock.FACING))),state.getBlock());}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);
        tag.putInt("lowMode",lowMode);tag.putInt("highMode",highMode);tag.putInt("priority",priority);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);items=NonNullList.withSize(1,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);
        lowMode=Math.clamp(tag.getInt("lowMode"),0,3);highMode=tag.contains("highMode")?Math.clamp(tag.getInt("highMode"),0,3):2;
        priority=tag.contains("priority")?Math.clamp(tag.getInt("priority"),1,3):1;
    }
}
