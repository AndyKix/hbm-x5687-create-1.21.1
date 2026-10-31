package com.hbm.port.machine;

import com.hbm.port.infrastructure.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import java.util.*;
import javax.annotation.Nullable;

/** X5687 base electric furnace: upgrades and pollution remain with their systems. */
public final class ElectricFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer,HeNetworkNode {
    public static final int CAPACITY=100_000,PROCESSING_TIME=100,CONSUMPTION=50;
    private NonNullList<ItemStack> items=NonNullList.withSize(4,ItemStack.EMPTY);
    private final IItemHandler handler=new SidedInvWrapper(this,Direction.DOWN);
    private final RecipeManager.CachedCheck<SingleRecipeInput,SmeltingRecipe> recipes=RecipeManager.createCheck(RecipeType.SMELTING);
    private final Map<ResourceLocation,Integer> recipesUsed=new LinkedHashMap<>();
    private long power;
    private int progress,cooldown;
    public final ContainerData data=new ContainerData(){
        public int getCount(){return 4;}
        public int get(int index){return switch(index){case 0->(int)power&65535;case 1->(int)(power>>>16)&65535;case 2->progress;case 3->cooldown;default->0;};}
        public void set(int index,int value){}
    };
    public ElectricFurnaceBlockEntity(BlockPos pos,BlockState state){super(ElectricFurnaceContent.ENTITY.get(),pos,state);}
    @Override public void onLoad(){super.onLoad();InfrastructureNetworks.register(this);}
    @Override public void setRemoved(){InfrastructureNetworks.unregister(this);super.setRemoved();}
    @Override protected Component getDefaultName(){return Component.translatable("container.hbm.electric_furnace");}
    @Override protected NonNullList<ItemStack> getItems(){return items;}
    @Override protected void setItems(NonNullList<ItemStack> value){items=value;}
    @Override public int getContainerSize(){return 4;}
    @Override protected AbstractContainerMenu createMenu(int id,Inventory player){return new ElectricFurnaceMenu(id,player,this,data);}
    public IItemHandler itemHandler(){return handler;}
    public int progress(){return progress;}
    public int cooldown(){return cooldown;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==0?stack.getItem() instanceof BatteryItem:slot==1&&level!=null&&IronFurnaceBlockEntity.smeltable(level,stack);}
    @Override public int[] getSlotsForFace(Direction side){return new int[]{0,1,2};}
    @Override public boolean canPlaceItemThroughFace(int slot,ItemStack stack,Direction side){return canPlaceItem(slot,stack);}
    @Override public boolean canTakeItemThroughFace(int slot,ItemStack stack,Direction side){return slot==2||slot==0&&stack.getItem() instanceof BatteryItem item&&item.charge(stack)==0;}
    @Override public long stored(){return power;}
    @Override public long capacity(){return CAPACITY;}
    @Override public long available(){return 0;}
    @Override public long demand(){return isRemoved()?0:CAPACITY-power;}
    @Override public int networkPriority(){return 2;}
    @Override public void commit(long incoming,long outgoing){
        if(incoming<0||incoming>demand()||outgoing!=0)throw new IllegalArgumentException("Invalid electric furnace HE transfer");power+=incoming;
    }
    @Override public long receive(long request,boolean simulate){
        if(level==null||level.isClientSide||isRemoved())return 0;
        long accepted=Math.min(Math.max(0,request),demand());if(!simulate&&accepted>0){commit(accepted,0);setChanged();}return accepted;
    }
    @Override public long extract(long request,boolean simulate){return 0;}
    public static void tick(Level level,BlockPos pos,BlockState state,ElectricFurnaceBlockEntity furnace){
        if(level.isClientSide||furnace.isRemoved())return;
        long previousPower=furnace.power;int previousProgress=furnace.progress,previousCooldown=furnace.cooldown;boolean drainedBattery=false;
        if(furnace.cooldown>0)furnace.cooldown--;
        var battery=furnace.items.getFirst();
        if(battery.getItem() instanceof BatteryItem item&&furnace.power<CAPACITY){
            long extracted=item.storage(battery).extract(CAPACITY-furnace.power,false);furnace.power+=extracted;drainedBattery=extracted>0;
        }
        if(furnace.power<CONSUMPTION)furnace.cooldown=20;
        var input=new SingleRecipeInput(furnace.items.get(1));var recipe=furnace.recipes.getRecipeFor(input,level);
        var result=recipe.map(r->r.value().assemble(input,level.registryAccess())).orElse(ItemStack.EMPTY);var output=furnace.items.get(2);
        boolean work=furnace.power>=CONSUMPTION&&furnace.cooldown==0&&!result.isEmpty()
                &&(output.isEmpty()||ItemStack.isSameItemSameComponents(output,result))
                &&output.getCount()+result.getCount()<=Math.min(result.getMaxStackSize(),furnace.getMaxStackSize());
        if(work){
            furnace.power-=CONSUMPTION;
            if(++furnace.progress>=PROCESSING_TIME){
                if(output.isEmpty())furnace.items.set(2,result.copy());else output.grow(result.getCount());
                furnace.items.get(1).shrink(1);furnace.progress=0;furnace.recipesUsed.merge(recipe.orElseThrow().id(),1,Integer::sum);
            }
        }else furnace.progress=0;
        if(state.getValue(ElectricFurnaceBlock.LIT)!=work)level.setBlock(pos,state.setValue(ElectricFurnaceBlock.LIT,work),3);
        // BaseContainerBlockEntity saves only dirty chunks; charge, battery and cooldown all matter.
        if(drainedBattery||previousPower!=furnace.power||previousProgress!=furnace.progress||previousCooldown!=furnace.cooldown)furnace.setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);ContainerHelper.saveAllItems(tag,items,registries);tag.putLong("power",power);tag.putInt("progress",progress);tag.putInt("cooldown",cooldown);
        var used=new CompoundTag();recipesUsed.forEach((id,amount)->used.putInt(id.toString(),amount));tag.put("RecipesUsed",used);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);items=NonNullList.withSize(4,ItemStack.EMPTY);ContainerHelper.loadAllItems(tag,items,registries);
        power=Math.clamp(tag.getLong("power"),0,CAPACITY);progress=Math.clamp(tag.getInt("progress"),0,PROCESSING_TIME-1);cooldown=Math.clamp(tag.getInt("cooldown"),0,20);
        recipesUsed.clear();var used=tag.getCompound("RecipesUsed");for(String key:used.getAllKeys()){var id=ResourceLocation.tryParse(key);if(id!=null&&used.getInt(key)>0)recipesUsed.put(id,used.getInt(key));}
    }
    public void dropExperience(@Nullable ServerPlayer player){
        if(!(level instanceof ServerLevel server))return;
        for(var entry:recipesUsed.entrySet())server.getRecipeManager().byKey(entry.getKey()).ifPresent(recipe->{
            if(recipe.value() instanceof SmeltingRecipe smelting){float xp=smelting.getExperience()*entry.getValue();int amount=(int)xp;if(server.random.nextFloat()<xp-amount)amount++;ExperienceOrb.award(server,worldPosition.getCenter(),amount);if(player!=null)player.awardRecipes(List.of(recipe));}
        });recipesUsed.clear();setChanged();
    }
}
