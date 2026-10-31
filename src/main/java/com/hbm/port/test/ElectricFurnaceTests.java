package com.hbm.port.test;

import com.hbm.port.infrastructure.*;
import com.hbm.port.machine.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("hbm") @PrefixGameTestTemplate(false)
public final class ElectricFurnaceTests {
    static final BlockPos POS=new BlockPos(3,2,2);
    static ElectricFurnaceBlockEntity place(GameTestHelper h){h.setBlock(POS,ElectricFurnaceContent.BLOCK.get());var f=(ElectricFurnaceBlockEntity)h.getBlockEntity(POS);InfrastructureNetworks.register(f);return f;}
    static void tick(GameTestHelper h,ElectricFurnaceBlockEntity f,int count){for(int i=0;i<count;i++)ElectricFurnaceBlockEntity.tick(h.getLevel(),f.getBlockPos(),f.getBlockState(),f);}
    @GameTest(template="empty") public static void exactlyFiveThousandHeForOneHundredTickSmelt(GameTestHelper h){
        var f=place(h);f.setItem(1,new ItemStack(Items.IRON_ORE));h.assertTrue(f.receive(5000,true)==5000&&f.stored()==0,"Simulation charged furnace");f.receive(5000,false);tick(h,f,99);
        h.assertTrue(f.progress()==99&&f.stored()==50&&f.getItem(2).isEmpty(),"Wrong base time/HE consumption");tick(h,f,1);
        h.assertTrue(f.progress()==0&&f.stored()==0&&f.getItem(1).isEmpty()&&f.getItem(2).is(Items.IRON_INGOT)&&f.getItem(2).getCount()==1,"100 ticks must consume 5000HE and one ore");h.succeed();
    }
    @GameTest(template="empty") public static void starvationAndBlockedOutputDoNotConsumeInputs(GameTestHelper h){
        var f=place(h);f.setItem(1,new ItemStack(Items.IRON_ORE));f.receive(1000,false);tick(h,f,10);h.assertTrue(f.progress()==10,"Initial progress missing");
        h.assertTrue(f.extract(1000,false)==0,"Consumer exported HE");f.setItem(2,new ItemStack(Items.IRON_INGOT,64));long stored=f.stored();tick(h,f,10);
        h.assertTrue(f.progress()==0&&f.stored()==stored&&f.getItem(1).getCount()==1,"Blocked output consumed HE/input or kept progress");
        f.setItem(2,ItemStack.EMPTY);tick(h,f,10);tick(h,f,1);h.assertTrue(f.stored()==0&&f.cooldown()==20&&f.getItem(1).getCount()==1,"Power loss must reset progress and start retry cooldown");
        f.receive(5000,false);tick(h,f,19);h.assertTrue(f.progress()==0&&f.stored()==5000,"Cooldown bypassed");tick(h,f,1);h.assertTrue(f.progress()==1&&f.stored()==4950,"Cooldown did not expire");h.succeed();
    }
    @GameTest(template="empty") public static void batteryInputAndCapacityAreConservative(GameTestHelper h){
        var f=place(h);var battery=InfrastructureContent.BATTERIES.get(BatteryKind.REDSTONE).get();var stack=new ItemStack(battery);battery.setCharge(stack,10000);f.setItem(0,stack);f.setItem(1,new ItemStack(Items.IRON_ORE));tick(h,f,100);
        h.assertTrue(f.getItem(2).is(Items.IRON_INGOT)&&f.stored()+battery.charge(stack)==5000,"Battery-powered smelting duplicated or lost HE");
        long accepted=f.receive(Long.MAX_VALUE,false);h.assertTrue(f.stored()==100000&&accepted>=0&&f.receive(1,false)==0&&f.receive(-1,false)==0,"Buffer overfilled");long charge=battery.charge(stack);tick(h,f,1);h.assertTrue(battery.charge(stack)==charge,"Full furnace drained battery");h.succeed();
    }
    @GameTest(template="empty") public static void saveAndReloadContinuesProgressAndKeepsComponents(GameTestHelper h){
        var f=place(h);f.receive(10000,false);f.setItem(1,new ItemStack(Items.IRON_ORE));tick(h,f,37);
        var loaded=(ElectricFurnaceBlockEntity)BlockEntity.loadStatic(f.getBlockPos(),f.getBlockState(),f.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(loaded.progress()==37&&loaded.stored()==8150&&loaded.getItem(1).is(Items.IRON_ORE),"NBT lost energy/progress/input");
        h.getLevel().removeBlockEntity(f.getBlockPos());loaded.setLevel(h.getLevel());h.getLevel().setBlockEntity(loaded);tick(h,loaded,63);
        h.assertTrue(loaded.getItem(2).is(Items.IRON_INGOT)&&loaded.stored()==5000,"Reloaded progress did not finish once");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=200) public static void realSocketCableAndFurnaceTickTogether(GameTestHelper h){
        var f=place(h);f.setItem(1,new ItemStack(Items.IRON_ORE));var source=InfrastructureTests.socket(h,new BlockPos(1,2,2),BatteryKind.REDSTONE,10000,2);InfrastructureTests.link(h,new BlockPos(2,2,2),false,FluidKind.NONE);
        h.succeedWhen(()->{h.assertTrue(f.getItem(2).is(Items.IRON_INGOT),"Cable-powered electric furnace has not smelted");h.assertTrue(source.stored()+f.stored()==5000,"Network plus production did not conserve remaining HE");});
    }
    @GameTest(template="empty") public static void furnaceDoesNotBridgeDisconnectedCables(GameTestHelper h){
        var f=place(h);var source=InfrastructureTests.socket(h,new BlockPos(1,2,2),BatteryKind.CUBE,1000000,2);InfrastructureTests.link(h,new BlockPos(2,2,2),false,FluidKind.NONE);
        InfrastructureTests.link(h,new BlockPos(4,2,2),false,FluidKind.NONE);var isolated=InfrastructureTests.socket(h,new BlockPos(6,2,2),BatteryKind.REDSTONE,0,0);
        InfrastructureNetworks.tick(h.getLevel());h.assertTrue(f.stored()==100000&&isolated.stored()==0&&source.stored()==900000,"Consumer bridged independent HE networks");h.succeed();
    }
    @GameTest(template="empty") public static void automationAndMovementRules(GameTestHelper h){
        var f=place(h);
        for(var side:Direction.values()){
            var items=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,f.getBlockPos(),side);var he=h.getLevel().getCapability(InfrastructureContent.ENERGY,f.getBlockPos(),side);
            h.assertTrue(items!=null&&items.getSlots()==3&&he!=null,"Native port missing");
            h.assertTrue(items.insertItem(1,new ItemStack(Items.IRON_ORE),false).isEmpty()&&items.extractItem(1,1,false).isEmpty(),"Automation must insert but not extract input");
            h.assertTrue(items.insertItem(2,new ItemStack(Items.DIAMOND),false).getCount()==1,"Output accepted insertion");
        }
        h.assertTrue(!f.canPlaceItem(3,new ItemStack(Items.DIAMOND)),"Upgrade placeholder accepted item");
        var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create","non_movable"));h.assertTrue(f.getBlockState().is(tag),"Unimplemented moving electric furnace may assemble");
        h.getLevel().destroyBlock(f.getBlockPos(),true);h.assertItemEntityCountIs(Items.IRON_ORE,POS,2,6);h.assertItemEntityCountIs(ElectricFurnaceContent.ITEM.get(),POS,2,1);h.succeed();
    }
}
