package com.hbm.port;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.hbm.port.machine.IronFurnaceBlock;
import com.hbm.port.machine.IronFurnaceBlockEntity;
import com.hbm.port.machine.IronFurnaceMenu;
import com.hbm.port.storage.CrateContent;
import com.hbm.port.material.RawMaterials;
import com.hbm.port.material.RawOreYield;
import com.hbm.port.world.RawWorldgen;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;
import java.util.Map;

@Mod(HbmPort.MOD_ID)
public final class HbmPort {
    public static final String MOD_ID = "hbm";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final Map<String, DeferredHolder<Item, Item>> MATERIAL_ITEMS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Block, Block>> MATERIAL_BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Block, Block>> ORES = new LinkedHashMap<>();
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredHolder<Block, IronFurnaceBlock> IRON_FURNACE = BLOCKS.register("furnace_iron",
            () -> new IronFurnaceBlock(BlockBehaviour.Properties.of().strength(5, 10)
                    .sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredHolder<Item, BlockItem> IRON_FURNACE_ITEM = ITEMS.register("furnace_iron",
            () -> new BlockItem(IRON_FURNACE.get(), new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IronFurnaceBlockEntity>> IRON_FURNACE_ENTITY =
            BLOCK_ENTITIES.register("furnace_iron", () -> BlockEntityType.Builder.of(IronFurnaceBlockEntity::new, IRON_FURNACE.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<IronFurnaceMenu>> IRON_FURNACE_MENU = MENUS.register("furnace_iron",
            () -> new MenuType<>(IronFurnaceMenu::new, FeatureFlags.DEFAULT_FLAGS));

    static {
        for (String metal : new String[] {"steel", "titanium", "lead"}) {
            item("ingot_" + metal);
            item("plate_" + metal);
            String id = "block_" + metal;
            DeferredHolder<Block, Block> block = BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0F, 50.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));
            MATERIAL_BLOCKS.put(id, block);
            MATERIAL_ITEMS.put(id, ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties())));
        }
        for (String metal : new String[] {"titanium", "lead"}) {
            String id = "ore_" + metal;
            DeferredHolder<Block, Block> ore = BLOCKS.register(id, () -> new Block(BlockBehaviour.Properties.of()
                    .strength(5.0F, 10.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
            ORES.put(id, ore);
            MATERIAL_ITEMS.put(id, ITEMS.register(id, () -> new BlockItem(ore.get(), new Item.Properties())));
        }
        RawMaterials.register();
        TABS.register("materials", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hbm.materials"))
                .icon(() -> MATERIAL_ITEMS.get("ingot_steel").get().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    MATERIAL_ITEMS.values().forEach(item -> output.accept(item.get()));
                    output.accept(IRON_FURNACE_ITEM.get());
                    CrateContent.ITEMS.values().forEach(item -> output.accept(item.get()));
                })
                .build());
    }

    private static void item(String id) {
        MATERIAL_ITEMS.put(id, ITEMS.register(id, () -> new Item(new Item.Properties())));
    }

    public HbmPort(IEventBus bus) {
        RawOreYield.TYPES.register(bus);
        RawWorldgen.FEATURES.register(bus);
        CrateContent.register(bus);
        ITEMS.register(bus);
        BLOCKS.register(bus);
        TABS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        bus.addListener(HbmPort::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            IronFurnaceBlockEntity furnace = IronFurnaceBlock.controller(level, pos, state);
            return furnace == null ? null : furnace.itemHandler(side);
        }, IRON_FURNACE.get());
        CrateContent.registerCapabilities(event);
    }
}
