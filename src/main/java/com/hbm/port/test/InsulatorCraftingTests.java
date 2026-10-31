package com.hbm.port.test;

import com.hbm.port.infrastructure.*;
import com.hbm.port.material.RawMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("hbm") @PrefixGameTestTemplate(false)
public final class InsulatorCraftingTests {
    static RecipeHolder<CraftingRecipe> recipe(GameTestHelper h,CraftingInput input){return h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();}
    static ItemStack craft(GameTestHelper h,CraftingInput input){
        var recipe=recipe(h,input);h.assertTrue(recipe.value().getRemainingItems(input).stream().allMatch(ItemStack::isEmpty),"Unexpected ingredient remainders duplicate materials");
        return recipe.value().assemble(input,h.getLevel().registryAccess());
    }
    @GameTest(template="empty") public static void originalInsulatorAmountsAndIngredients(GameTestHelper h){
        var bricks=CraftingInput.of(2,1,List.of(new ItemStack(Items.BRICK),new ItemStack(Items.BRICK)));var result=craft(h,bricks);
        h.assertTrue(result.is(InfrastructureContent.INSULATOR.get())&&result.getCount()==4,"Two bricks must give four insulators");
        var asbestos=CraftingInput.of(2,1,List.of(new ItemStack(RawMaterials.item("ingot_asbestos")),new ItemStack(RawMaterials.item("ingot_asbestos"))));result=craft(h,asbestos);
        h.assertTrue(result.is(InfrastructureContent.INSULATOR.get())&&result.getCount()==16,"Two asbestos sheets must give sixteen insulators");
        for(var wool:List.of(Items.WHITE_WOOL,Items.BLUE_WOOL,Items.BLACK_WOOL)){
            result=craft(h,CraftingInput.of(3,1,List.of(new ItemStack(Items.STRING),new ItemStack(wool),new ItemStack(Items.STRING))));
            h.assertTrue(result.is(InfrastructureContent.INSULATOR.get())&&result.getCount()==4,"Wool colors must preserve source wildcard behavior");
        }h.succeed();
    }
    @GameTest(template="empty") public static void invalidInsulatorSubstitutesHaveNoBypassRecipe(GameTestHelper h){
        for(var wrong:List.of(Items.BRICKS,Items.NETHER_BRICK,Items.NETHER_BRICKS)){
            var input=CraftingInput.of(2,1,List.of(new ItemStack(wrong),new ItemStack(wrong)));var recipe=h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel());
            h.assertTrue(recipe.isEmpty()||!recipe.get().value().assemble(input,h.getLevel().registryAccess()).is(InfrastructureContent.INSULATOR.get()),"Unmapped brick alias silently added");
        }
        var wrong=CraftingInput.of(1,2,List.of(new ItemStack(Items.BRICK),new ItemStack(Items.BRICK)));
        h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,wrong,h.getLevel()).isEmpty(),"Horizontal recipe accepted vertical pattern");h.succeed();
    }
    static ItemStack battery(GameTestHelper h,Item plate,Item middle){
        var p=new ItemStack(plate);var r=new ItemStack(middle);var ins=new ItemStack(InfrastructureContent.INSULATOR.get());
        return craft(h,CraftingInput.of(3,3,List.of(p,r,p,ins,r,ins,p,r,p)));
    }
    @GameTest(template="empty") public static void batteryRecipesUseOriginalCountsAndStartEmpty(GameTestHelper h){
        var iron=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create","iron_sheet"));var redstone=battery(h,iron,Items.REDSTONE_BLOCK);
        h.assertTrue(redstone.is(InfrastructureContent.BATTERIES.get(BatteryKind.REDSTONE).get())&&redstone.getCount()==1,"Redstone battery recipe differs");
        for(var copper:List.of(Items.COPPER_BLOCK,RawMaterials.item("block_copper"))){
            var capacitor=battery(h,RawMaterials.item("plate_steel"),copper);var item=(BatteryItem)capacitor.getItem();
            h.assertTrue(item.kind==BatteryKind.COPPER&&capacitor.getCount()==1&&item.charge(capacitor)==0,"Copper capacitor recipe/type/initial charge differs");
        }
        h.assertTrue(((BatteryItem)redstone.getItem()).charge(redstone)==0,"Crafting created free HE");h.succeed();
    }
    @GameTest(template="empty") public static void craftedBatteryActuallyChargesFromExistingHeNetwork(GameTestHelper h){
        var iron=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create","iron_sheet"));var crafted=battery(h,iron,Items.REDSTONE_BLOCK);
        var source=InfrastructureTests.socket(h,new BlockPos(1,2,2),BatteryKind.REDSTONE,1000,2);
        var target=InfrastructureTests.socket(h,new BlockPos(4,2,2),BatteryKind.REDSTONE,0,0);target.setItem(0,crafted);
        InfrastructureTests.link(h,new BlockPos(2,2,2),false,FluidKind.NONE);InfrastructureNetworks.tick(h.getLevel());
        h.assertTrue(target.stored()==100&&source.stored()==900&&((BatteryItem)crafted.getItem()).charge(crafted)==100,"Crafted battery did not behave like existing HE battery");h.succeed();
    }
}
