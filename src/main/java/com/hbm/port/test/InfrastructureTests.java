package com.hbm.port.test;

import com.hbm.port.infrastructure.*;
import com.hbm.port.material.RawMaterials;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.gametest.*;
import java.math.BigInteger;
import java.util.*;

@GameTestHolder("hbm") @PrefixGameTestTemplate(false)
public final class InfrastructureTests {
    static EnergySocketBlockEntity socket(GameTestHelper h,BlockPos pos,BatteryKind kind,long charge,int mode){
        var block=InfrastructureContent.SOCKET.get();var state=block.defaultBlockState();h.setBlock(pos,state);
        block.setPlacedBy(h.getLevel(),h.absolutePos(pos),state,null,new ItemStack(block));
        var be=(EnergySocketBlockEntity)h.getBlockEntity(pos);be.lowMode=mode;
        var item=InfrastructureContent.BATTERIES.get(kind).get();var stack=new ItemStack(item);item.setCharge(stack,charge);be.setItem(0,stack);InfrastructureNetworks.register(be);return be;
    }
    static SteelBarrelBlockEntity barrel(GameTestHelper h,BlockPos pos,FluidKind kind,int amount,int mode){
        h.setBlock(pos,InfrastructureContent.BARREL.get());var be=(SteelBarrelBlockEntity)h.getBlockEntity(pos);
        be.configure(kind);be.rawHandler().fill(kind.stack(amount),FluidAction.EXECUTE);be.setMode(mode);InfrastructureNetworks.register(be);return be;
    }
    static NetworkLinkBlockEntity link(GameTestHelper h,BlockPos pos,boolean fluid,FluidKind kind){
        h.setBlock(pos,(fluid?InfrastructureContent.PIPE.get():InfrastructureContent.CABLE.get()).defaultBlockState().setValue(NetworkLinkBlock.FLUID,kind));
        var be=(NetworkLinkBlockEntity)h.getBlockEntity(pos);InfrastructureNetworks.register(be);return be;
    }
    @GameTest(template="empty") public static void plannerConservesLongTotalsAndPriority(GameTestHelper h){
        var accounts=List.of(new TransferPlanner.Account<>("a",Long.MAX_VALUE,0,1),new TransferPlanner.Account<>("b",Long.MAX_VALUE,0,1),new TransferPlanner.Account<>("c",0,Long.MAX_VALUE,3),new TransferPlanner.Account<>("d",0,Long.MAX_VALUE,1));
        var plan=TransferPlanner.plan(accounts,1);h.assertTrue(plan.incoming().get("c")==Long.MAX_VALUE&&plan.incoming().get("d")==Long.MAX_VALUE,"Aggregate overflow");
        var priority=TransferPlanner.plan(List.of(new TransferPlanner.Account<>("source",7,0,1),new TransferPlanner.Account<>("low",0,9,1),new TransferPlanner.Account<>("high",0,6,3)),0);
        h.assertTrue(priority.incoming().get("high")==6&&priority.incoming().get("low")==1,"Priority failed");h.succeed();
    }
    @GameTest(template="empty") public static void plannerRandomizedConservationAndFairRounding(GameTestHelper h){
        var random=new Random(5687);
        for(int trial=0;trial<1000;trial++){
            var accounts=new ArrayList<TransferPlanner.Account<Integer>>();
            for(int i=0;i<16;i++)accounts.add(new TransferPlanner.Account<>(i,random.nextLong(Long.MAX_VALUE),random.nextLong(Long.MAX_VALUE),random.nextInt(4)));
            var plan=TransferPlanner.plan(accounts,trial);BigInteger in=BigInteger.ZERO,out=BigInteger.ZERO;
            for(var a:accounts){long credit=plan.incoming().getOrDefault(a.owner(),0L),debit=plan.outgoing().getOrDefault(a.owner(),0L);
                h.assertTrue(credit>=0&&credit<=a.demand()&&debit>=0&&debit<=a.available(),"Allocation outside bounds");in=in.add(BigInteger.valueOf(credit));out=out.add(BigInteger.valueOf(debit));}
            h.assertTrue(in.equals(out),"HE created or destroyed by rounding");
        }
        for(int i=0;i<3;i++){
            var plan=TransferPlanner.plan(List.of(new TransferPlanner.Account<>(0,1,0,1),new TransferPlanner.Account<>(1,0,1,1),new TransferPlanner.Account<>(2,0,1,1)),i);
            h.assertTrue(plan.incoming().values().stream().mapToLong(Long::longValue).sum()==1,"Small transfer vanished");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void batteryItemSimulationAndLongCodec(GameTestHelper h){
        var item=InfrastructureContent.BATTERIES.get(BatteryKind.CUBE).get();var stack=new ItemStack(item);item.setCharge(stack,123456789012345678L);var cap=stack.getCapability(InfrastructureContent.ITEM_ENERGY);
        h.assertTrue(cap!=null&&cap.stored()==123456789012345678L,"Long storage capability missing");
        h.assertTrue(cap.extract(1000,true)==1000&&cap.stored()==123456789012345678L,"Simulation mutated charge");
        h.assertTrue(cap.receive(-1,false)==0&&cap.extract(-1,false)==0,"Negative request accepted");
        var loaded=ItemStack.parseOptional(h.getLevel().registryAccess(),(net.minecraft.nbt.CompoundTag)stack.save(h.getLevel().registryAccess()));h.assertTrue(ItemStack.matches(stack,loaded),"Long component did not serialize");
        var creative=InfrastructureContent.BATTERIES.get(BatteryKind.CREATIVE).get();var infinite=new ItemStack(creative);creative.storage(infinite).extract(Long.MAX_VALUE,false);h.assertTrue(creative.charge(infinite)==Long.MAX_VALUE/2,"Creative source consumed");h.succeed();
    }
    @GameTest(template="empty") public static void actualCableNetworkConservesAndSplits(GameTestHelper h){
        var source=socket(h,new BlockPos(1,2,2),BatteryKind.CUBE,123456789012345678L,2);
        var target=socket(h,new BlockPos(4,2,2),BatteryKind.CUBE,0,0);var pos=new BlockPos(2,2,2);link(h,pos,false,FluidKind.NONE);
        InfrastructureNetworks.tick(h.getLevel());h.assertTrue(source.stored()==122456789012345678L&&target.stored()==1000000000000000L,"Cable failed long transfer");
        long sum=source.stored()+target.stored();InfrastructureNetworks.tick(h.getLevel());h.assertTrue(source.stored()+target.stored()==sum&&target.stored()==1000000000000000L,"Repeated tick bypassed per-tick budget");
        h.setBlock(pos,Blocks.AIR);h.runAfterDelay(2,()->{h.assertTrue(target.stored()==1000000000000000L,"Detached network still transfers");h.succeed();});
    }
    @GameTest(template="empty") public static void socketAllFacesShareBudgetAndOffModeBlocksEnergy(GameTestHelper h){
        var be=socket(h,new BlockPos(2,2,2),BatteryKind.REDSTONE,0,0);long received=0;
        for(int part=0;part<8;part++)for(var side:Direction.values()){
            var pos=be.getBlockPos().offset(EnergySocketBlock.offset(part,Direction.SOUTH));var cap=h.getLevel().getCapability(InfrastructureContent.ENERGY,pos,side);
            h.assertTrue((cap!=null)==(part<4&&side.getAxis().isHorizontal()),"Incorrect socket power face");
            if(cap!=null){h.assertTrue(cap.receive(1000,true)==1000-received,"Simulation or shared budget differs");received+=cap.receive(1000,false);}
        }
        h.assertTrue(received==1000&&be.stored()==1000,"Face access bypassed rated charge");be.lowMode=3;
        h.assertTrue(be.receive(1,false)==0&&be.extract(1,false)==0,"Off socket transfers energy");h.assertTrue(h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,be.getBlockPos(),Direction.EAST)==null,"HE silently converted to FE");h.succeed();
    }
    @GameTest(template="empty") public static void socketRedstoneAndNbt(GameTestHelper h){
        var be=socket(h,new BlockPos(2,2,2),BatteryKind.CUBE,123456789012345678L,0);be.highMode=2;be.priority=3;
        h.setBlock(new BlockPos(3,2,2),Blocks.REDSTONE_BLOCK);h.assertTrue(be.mode()==2&&be.available()>0,"Redstone high mode failed");
        var loaded=(EnergySocketBlockEntity)BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.stored()==be.stored()&&loaded.lowMode==0&&loaded.highMode==2&&loaded.priority==3,"Socket NBT lost charge/modes");h.succeed();
    }
    @GameTest(template="empty") public static void socketGhostRemovalDropsBatteryOnce(GameTestHelper h){
        var be=socket(h,new BlockPos(2,2,2),BatteryKind.CUBE,123456789012345678L,0);var core=be.getBlockPos();
        h.getLevel().destroyBlock(core.offset(EnergySocketBlock.offset(7,Direction.SOUTH)),true);
        int batteries=0,sockets=0;
        for(var entity:h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(core).inflate(3))){if(entity.getItem().is(InfrastructureContent.BATTERIES.get(BatteryKind.CUBE).get())){batteries+=entity.getItem().getCount();h.assertTrue(entity.getItem().get(InfrastructureContent.CHARGE.get())==123456789012345678L,"Battery drop lost long HE");}if(entity.getItem().is(InfrastructureContent.SOCKET_ITEM.get()))sockets+=entity.getItem().getCount();}
        h.assertTrue(batteries==1&&sockets==1,"Multiblock duplicated or lost drops");for(int i=0;i<8;i++)h.assertTrue(!h.getLevel().getBlockState(core.offset(EnergySocketBlock.offset(i,Direction.SOUTH))).is(InfrastructureContent.SOCKET.get()),"Ghost part left behind");h.succeed();
    }
    @GameTest(template="empty") public static void barrelsRejectWrongFluidsAndHonorModes(GameTestHelper h){
        var be=barrel(h,new BlockPos(2,2,2),FluidKind.WATER,4000,0);var cap=be.externalHandler();
        h.assertTrue(cap.fill(FluidKind.LAVA.stack(1000),FluidAction.EXECUTE)==0&&cap.drain(1000,FluidAction.EXECUTE).isEmpty(),"Input mode/type checks failed");
        h.assertTrue(cap.fill(FluidKind.WATER.stack(20000),FluidAction.SIMULATE)==12000&&be.amount()==4000,"Fill simulation mutated barrel");
        var view=cap.getFluidInTank(0);view.setAmount(1);h.assertTrue(be.amount()==4000,"Read view mutated barrel");
        be.setMode(2);h.assertTrue(cap.fill(FluidKind.WATER.stack(1),FluidAction.EXECUTE)==0&&cap.drain(1000,FluidAction.EXECUTE).getAmount()==1000,"Output mode failed");
        be.setMode(3);h.assertTrue(cap.drain(1,FluidAction.EXECUTE).isEmpty()&&be.rawHandler().drain(1,FluidAction.EXECUTE).getAmount()==1,"Off must block network but allow buckets");
        be.configure(FluidKind.LAVA);h.assertTrue(be.amount()==0,"X5687 type change must flush old contents");h.succeed();
    }
    @GameTest(template="empty") public static void fluidComponentsAreNotCoerced(GameTestHelper h){
        var be=barrel(h,new BlockPos(2,2,2),FluidKind.WATER,0,0);var altered=FluidKind.WATER.stack(1000);altered.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("foreign components"));
        h.assertTrue(be.externalHandler().fill(altered,FluidAction.EXECUTE)==0,"Foreign fluid components silently discarded");
        var honey=new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","honey")),1000);h.assertTrue(be.externalHandler().fill(honey,FluidAction.EXECUTE)==0,"Unmapped fluid was coerced");h.succeed();
    }
    @GameTest(template="empty") public static void typedPipeTransfersAndDisconnects(GameTestHelper h){
        var source=barrel(h,new BlockPos(1,2,2),FluidKind.WATER,9000,2);var target=barrel(h,new BlockPos(3,2,2),FluidKind.WATER,0,0);var pipe=link(h,new BlockPos(2,2,2),true,FluidKind.WATER);
        InfrastructureNetworks.tick(h.getLevel());h.assertTrue(source.amount()==0&&target.amount()==9000,"Pipe did not transfer exact amount");
        pipe.configure(FluidKind.LAVA);source.rawHandler().fill(FluidKind.WATER.stack(4000),FluidAction.EXECUTE);InfrastructureNetworks.tick(h.getLevel());h.assertTrue(source.amount()==4000&&target.amount()==9000,"Mismatched pipe connected water barrels");
        pipe.configure(FluidKind.WATER);InfrastructureNetworks.tick(h.getLevel());h.assertTrue(source.amount()==0&&target.amount()==13000,"Pipe reconfiguration lost network");h.succeed();
    }
    @GameTest(template="empty") public static void outputBarrelDoesNotBridgeDisconnectedPipeNetworks(GameTestHelper h){
        var source=barrel(h,new BlockPos(2,2,2),FluidKind.WATER,5000,0);var left=link(h,new BlockPos(1,2,2),true,FluidKind.WATER);var right=link(h,new BlockPos(3,2,2),true,FluidKind.WATER);
        var target=barrel(h,new BlockPos(4,2,2),FluidKind.WATER,0,0);
        h.assertTrue(left.fluidHandler().getTanks()==1&&right.fluidHandler().getTanks()==2,"Input barrel merged disconnected pipe graphs");
        source.setMode(2);InfrastructureNetworks.tick(h.getLevel());h.assertTrue(source.amount()==0&&target.amount()==5000,"Endpoint shared balance lost fluid");h.succeed();
    }
    @GameTest(template="empty") public static void pipeCapabilitySimulationModesAndRemoval(GameTestHelper h){
        var tank=barrel(h,new BlockPos(1,2,2),FluidKind.LAVA,2000,1);var pos=new BlockPos(2,2,2);var pipe=link(h,pos,true,FluidKind.LAVA);var cap=h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,h.absolutePos(pos),Direction.EAST);
        h.assertTrue(cap!=null&&cap.getTanks()==1&&cap.fill(FluidKind.LAVA.stack(2000),FluidAction.SIMULATE)==2000&&tank.amount()==2000,"Pipe simulation failed");
        h.assertTrue(cap.drain(3000,FluidAction.SIMULATE).getAmount()==2000&&tank.amount()==2000,"Drain simulation mutated tank");
        tank.setMode(3);h.assertTrue(cap.fill(FluidKind.LAVA.stack(1),FluidAction.EXECUTE)==0&&cap.drain(1,FluidAction.EXECUTE).isEmpty(),"Pipe bypassed barrel mode");
        h.setBlock(pos,Blocks.AIR);h.assertTrue(cap.getTanks()==0&&cap.fill(FluidKind.LAVA.stack(1),FluidAction.EXECUTE)==0,"Stale removed pipe still provides capability");h.succeed();
    }
    @GameTest(template="empty") public static void barrelBucketsDoNotConsumeWhenOutputBlocked(GameTestHelper h){
        var be=barrel(h,new BlockPos(2,2,2),FluidKind.WATER,0,3);be.setItem(2,new ItemStack(Items.WATER_BUCKET));be.setItem(3,new ItemStack(Items.DIAMOND));
        SteelBarrelBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);h.assertTrue(be.amount()==0&&be.getItem(2).is(Items.WATER_BUCKET),"Blocked output consumed bucket");
        be.setItem(3,ItemStack.EMPTY);SteelBarrelBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);h.assertTrue(be.amount()==1000&&be.getItem(3).is(Items.BUCKET)&&be.getItem(2).isEmpty(),"Bucket emptying failed");
        be.setItem(4,new ItemStack(Items.BUCKET));SteelBarrelBlockEntity.tick(h.getLevel(),be.getBlockPos(),be.getBlockState(),be);h.assertTrue(be.amount()==0&&be.getItem(5).is(Items.WATER_BUCKET),"Bucket filling failed while network off");h.succeed();
    }
    @GameTest(template="empty") public static void barrelDropsPersistFluidAndModeWithoutCloningSlots(GameTestHelper h){
        var be=barrel(h,new BlockPos(2,2,2),FluidKind.LAVA,12345,3);be.setItem(2,new ItemStack(Items.LAVA_BUCKET));
        var drops=Block.getDrops(be.getBlockState(),h.getLevel(),be.getBlockPos(),be,null,new ItemStack(Items.IRON_PICKAXE));h.assertTrue(drops.size()==1,"Barrel loot absent");
        var drop=drops.getFirst();h.assertTrue(drop.get(InfrastructureContent.BARREL_STATE.get()).equals(new BarrelState(FluidKind.LAVA,12345,3))&&drop.getMaxStackSize()==1&&!drop.has(DataComponents.CONTAINER),"Packed state lost or cloned bucket slots");
        var copy=new SteelBarrelBlockEntity(be.getBlockPos(),be.getBlockState());copy.applyComponentsFromItemStack(drop);h.assertTrue(copy.portable().equals(be.portable())&&copy.isEmpty(),"Placement did not restore fluid/mode");
        var loaded=(SteelBarrelBlockEntity)BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(loaded.portable().equals(be.portable())&&loaded.getItem(2).is(Items.LAVA_BUCKET),"Barrel NBT lost state");h.succeed();
    }
    @GameTest(template="empty") public static void fluidIdentifierAndMenuValidation(GameTestHelper h){
        var player=h.makeMockPlayer(GameType.SURVIVAL);var item=new ItemStack(InfrastructureContent.IDENTIFIER.get());player.getInventory().setItem(0,item);player.getInventory().selected=0;
        var menu=new FluidIdentifierMenu(1,player.getInventory());h.assertTrue(menu.clickMenuButton(player,1)&&FluidIdentifierItem.primary(item)==FluidKind.WATER,"Selector primary failed");h.assertTrue(menu.clickMenuButton(player,5)&&FluidIdentifierItem.secondary(item)==FluidKind.LAVA,"Selector secondary failed");
        h.assertTrue(!menu.clickMenuButton(player,6)&&!menu.clickMenuButton(player,-1),"Invalid selector packet accepted");
        player.getInventory().setItem(0,new ItemStack(Items.DIAMOND));h.assertTrue(!menu.clickMenuButton(player,2),"Selector edited replacement item");
        h.assertTrue(ItemStack.isSameItemSameComponents(item,InfrastructureContent.IDENTIFIER.get().getCraftingRemainingItem(item)),"Identifier crafting remainder lost selection");h.succeed();
    }
    @GameTest(template="empty") public static void originalReachableRecipesAndMovementBoundary(GameTestHelper h){
        var steel=new ItemStack(RawMaterials.item("plate_steel"));var ingot=new ItemStack(RawMaterials.item("ingot_steel"));
        var input=CraftingInput.of(3,3,List.of(steel,ingot,steel,steel,ItemStack.EMPTY,steel,steel,ingot,steel));
        var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();h.assertTrue(recipe.value().assemble(input,h.getLevel().registryAccess()).is(InfrastructureContent.BARREL_ITEM.get()),"Original barrel recipe missing");
        var aluminium=new ItemStack(RawMaterials.item("plate_aluminium"));input=CraftingInput.of(3,3,List.of(steel,aluminium,steel,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,steel,aluminium,steel));
        recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();var result=recipe.value().assemble(input,h.getLevel().registryAccess());h.assertTrue(result.is(InfrastructureContent.PIPE_ITEM.get())&&result.getCount()==8,"Original pipe recipe missing");
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","non_movable"));
        for(var block:List.of(InfrastructureContent.SOCKET.get(),InfrastructureContent.CABLE.get(),InfrastructureContent.PIPE.get(),InfrastructureContent.BARREL.get()))h.assertTrue(block.defaultBlockState().is(tag),"Unimplemented mounted device may move");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400) public static void actualCreatePumpTransfersBetweenHbmPipeEndpoints(GameTestHelper h){
        var source=barrel(h,new BlockPos(0,2,2),FluidKind.WATER,7000,2);var target=barrel(h,new BlockPos(4,2,2),FluidKind.WATER,0,0);link(h,new BlockPos(1,2,2),true,FluidKind.WATER);link(h,new BlockPos(3,2,2),true,FluidKind.WATER);
        h.setBlock(new BlockPos(2,2,2),net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","mechanical_pump")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
        h.setBlock(new BlockPos(2,3,2),net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","cogwheel")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X));
        h.setBlock(new BlockPos(1,3,2),net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","creative_motor")).defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.EAST));
        h.runAfterDelay(5,()->((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)h.getBlockEntity(new BlockPos(1,3,2))).generatedSpeed.setValue(64));
        h.succeedWhen(()->{var pump=(com.simibubi.create.content.fluids.pump.PumpBlockEntity)h.getBlockEntity(new BlockPos(2,2,2));h.assertTrue(pump.getSpeed()!=0,"Actual Create pump not powered");h.assertTrue(source.amount()==0&&target.amount()==7000,"Actual Create pump has not moved exactly 7000mB through HBM pipe capabilities: source="+source.amount()+", target="+target.amount()+", RPM="+pump.getSpeed());});
    }
}
