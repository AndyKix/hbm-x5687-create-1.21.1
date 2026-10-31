package com.hbm.port.machine;

import com.hbm.port.HbmPort;
import com.hbm.port.infrastructure.InfrastructureContent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ElectricFurnaceContent {
    public static final DeferredHolder<Block,ElectricFurnaceBlock> BLOCK=HbmPort.BLOCKS.register("machine_electric_furnace_off",()->new ElectricFurnaceBlock(BlockBehaviour.Properties.of().strength(5,10).sound(SoundType.METAL).requiresCorrectToolForDrops().lightLevel(state->state.getValue(ElectricFurnaceBlock.LIT)?15:0)));
    public static final DeferredHolder<Item,BlockItem> ITEM=HbmPort.ITEMS.register("machine_electric_furnace_off",()->new BlockItem(BLOCK.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ElectricFurnaceBlockEntity>> ENTITY=HbmPort.BLOCK_ENTITIES.register("electric_furnace",()->BlockEntityType.Builder.of(ElectricFurnaceBlockEntity::new,BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ElectricFurnaceMenu>> MENU=HbmPort.MENUS.register("electric_furnace",()->new MenuType<>(ElectricFurnaceMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static void init(){}
    public static void capabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(InfrastructureContent.ENERGY,ENTITY.get(),(furnace,side)->furnace);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,ENTITY.get(),(furnace,side)->furnace.itemHandler());
    }
    private ElectricFurnaceContent(){}
}
