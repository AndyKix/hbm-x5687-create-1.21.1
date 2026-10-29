package com.hbm.port;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
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
        TABS.register("materials", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.hbm.materials"))
                .icon(() -> MATERIAL_ITEMS.get("ingot_steel").get().getDefaultInstance())
                .displayItems((parameters, output) -> MATERIAL_ITEMS.values().forEach(item -> output.accept(item.get())))
                .build());
    }

    private static void item(String id) {
        MATERIAL_ITEMS.put(id, ITEMS.register(id, () -> new Item(new Item.Properties())));
    }

    public HbmPort(IEventBus bus) {
        ITEMS.register(bus);
        BLOCKS.register(bus);
        TABS.register(bus);
    }
}
