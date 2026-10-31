package com.hbm.port.infrastructure;

import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.*;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

public final class SteelBarrelBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int CAPACITY=16_000;
    private FluidKind fluid=FluidKind.NONE;
    private int amount,mode,comparator=-1;
    private NonNullList<ItemStack> items=NonNullList.withSize(6,ItemStack.EMPTY);
    private final IItemHandler inventory=new SidedInvWrapper(this,Direction.DOWN);
    private final IFluidHandler raw=new Handler(false),external=new Handler(true);
    public final ContainerData data=new ContainerData(){
        public int getCount(){return 3;}public int get(int index){return switch(index){case 0->amount;case 1->fluid.ordinal();case 2->mode;default->0;};}public void set(int index,int value){}
    };
    public SteelBarrelBlockEntity(BlockPos pos,BlockState state){super(InfrastructureContent.BARREL_ENTITY.get(),pos,state);}
    @Override public void onLoad(){super.onLoad();InfrastructureNetworks.register(this);setChanged();}
    @Override public void setRemoved(){InfrastructureNetworks.unregister(this);super.setRemoved();}
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> value){items=value;}
    @Override public int getContainerSize(){return 6;}
    @Override protected Component getDefaultName(){return Component.translatable("container.hbm.barrel_steel");}
    @Override protected AbstractContainerMenu createMenu(int id,Inventory player){return new SteelBarrelMenu(id,player,this,data);}
    public FluidKind fluid(){return fluid;} public int amount(){return amount;} public int mode(){return mode;}
    public boolean receives(){return mode==0||mode==1;} public boolean provides(){return mode==1||mode==2;}
    public int comparator(){return amount==0?0:Math.min(15,(int)(15D*amount/CAPACITY)+1);}
    public FluidStack contents(){return fluid.stack(amount);}
    public IFluidHandler externalHandler(){return external;} public IFluidHandler rawHandler(){return raw;} public IItemHandler itemHandler(){return inventory;}
    public void setMode(int mode){if(mode<0||mode>3)throw new IllegalArgumentException("Invalid barrel mode");if(this.mode!=mode){this.mode=mode;InfrastructureNetworks.dirty(level);setChanged();}}
    /** X5687 setTankType explicitly flushes old contents when selecting a different fluid. */
    public void configure(FluidKind kind){
        if(fluid==kind)return;fluid=kind;amount=0;InfrastructureNetworks.dirty(level);setChanged();
        if(level!=null&&!level.isClientSide){level.invalidateCapabilities(worldPosition);level.updateNeighborsAt(worldPosition,getBlockState().getBlock());}
    }
    void commitAmount(int amount){if(amount<0||amount>CAPACITY||fluid==FluidKind.NONE&&amount!=0)throw new IllegalArgumentException("Invalid fluid amount");this.amount=amount;}
    public BarrelState portable(){return new BarrelState(fluid,amount,mode);}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        return validInput(fluid,slot,stack);
    }
    public static boolean validInput(FluidKind fluid,int slot,ItemStack stack){
        if(slot==0)return stack.getItem() instanceof FluidIdentifierItem;
        var probe=new net.neoforged.neoforge.fluids.capability.templates.FluidTank(CAPACITY,fluid::accepts);
        if(slot==2)return FluidUtil.tryEmptyContainer(stack,probe,CAPACITY,null,false).isSuccess();
        if(slot==4){probe.setFluid(fluid.stack(CAPACITY));return FluidUtil.tryFillContainer(stack,probe,CAPACITY,null,false).isSuccess();}
        return false;
    }
    @Override public int[] getSlotsForFace(Direction side){return new int[]{2,3,4,5};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==3||slot==5;}
    public static void tick(Level level,BlockPos pos,BlockState state,SteelBarrelBlockEntity barrel){
        if(level.isClientSide)return;
        var selector=barrel.items.get(0);
        if(selector.getItem() instanceof FluidIdentifierItem&&barrel.items.get(1).isEmpty()){
            var selected=FluidIdentifierItem.primary(selector);
            if(selected!=barrel.fluid){barrel.configure(selected);barrel.items.set(1,selector.copy());barrel.items.set(0,ItemStack.EMPTY);barrel.setChanged();}
        }
        barrel.processContainer(2,3,false);barrel.processContainer(4,5,true);
    }
    private boolean fits(int slot,ItemStack stack){var existing=items.get(slot);return stack.isEmpty()||existing.isEmpty()||ItemStack.isSameItemSameComponents(existing,stack)&&existing.getCount()+stack.getCount()<=existing.getMaxStackSize();}
    private void processContainer(int input,int output,boolean filling){
        var source=items.get(input);if(source.isEmpty())return;
        var preview=filling?FluidUtil.tryFillContainer(source,raw,CAPACITY,null,false):FluidUtil.tryEmptyContainer(source,raw,CAPACITY,null,false);
        if(!preview.isSuccess()||!fits(output,preview.getResult()))return;
        var result=filling?FluidUtil.tryFillContainer(source,raw,CAPACITY,null,true):FluidUtil.tryEmptyContainer(source,raw,CAPACITY,null,true);
        if(!result.isSuccess())return;
        source.shrink(1);var container=result.getResult();
        if(!container.isEmpty()){if(items.get(output).isEmpty())items.set(output,container.copy());else items.get(output).grow(container.getCount());}
        setChanged();
    }
    @Override public void setChanged(){
        super.setChanged();if(level==null||level.isClientSide||isRemoved())return;
        int signal=comparator();if(signal!=comparator){comparator=signal;level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);tag.putString("fluid",fluid.getSerializedName());tag.putInt("amount",amount);tag.putInt("mode",mode);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);items=NonNullList.withSize(6,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);
        fluid=FluidKind.byName(tag.getString("fluid"));amount=fluid==FluidKind.NONE?0:Math.clamp(tag.getInt("amount"),0,CAPACITY);mode=Math.clamp(tag.getInt("mode"),0,3);
    }
    @Override protected void collectImplicitComponents(DataComponentMap.Builder builder){
        super.collectImplicitComponents(builder);var state=portable();
        if(!state.equals(BarrelState.EMPTY)){builder.set(InfrastructureContent.BARREL_STATE.get(),state);builder.set(DataComponents.MAX_STACK_SIZE,1);}
    }
    @Override protected void applyImplicitComponents(DataComponentInput input){
        super.applyImplicitComponents(input);var state=input.getOrDefault(InfrastructureContent.BARREL_STATE.get(),BarrelState.EMPTY);
        fluid=state.fluid();amount=state.amount();mode=state.mode();
    }
    private final class Handler implements IFluidHandler {
        private final boolean gated;Handler(boolean gated){this.gated=gated;}
        public int getTanks(){return 1;} public FluidStack getFluidInTank(int tank){return tank==0?contents():FluidStack.EMPTY;}
        public int getTankCapacity(int tank){return tank==0?CAPACITY:0;} public boolean isFluidValid(int tank,FluidStack resource){return tank==0&&fluid.accepts(resource);}
        public int fill(FluidStack resource,FluidAction action){
            if(isRemoved()||!fluid.accepts(resource)||gated&&!receives()||level!=null&&level.isClientSide)return 0;
            int accepted=Math.min(resource.getAmount(),CAPACITY-amount);if(action.execute()&&accepted>0){amount+=accepted;setChanged();}return accepted;
        }
        public FluidStack drain(FluidStack resource,FluidAction action){return fluid.accepts(resource)?drain(resource.getAmount(),action):FluidStack.EMPTY;}
        public FluidStack drain(int requested,FluidAction action){
            if(isRemoved()||requested<=0||gated&&!provides()||level!=null&&level.isClientSide)return FluidStack.EMPTY;
            int drained=Math.min(requested,amount);var result=fluid.stack(drained);if(action.execute()&&drained>0){amount-=drained;setChanged();}return result;
        }
    }
}
