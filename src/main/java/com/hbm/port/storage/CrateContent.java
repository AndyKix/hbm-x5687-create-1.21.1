package com.hbm.port.storage;

import com.hbm.port.HbmPort;
import com.hbm.port.compat.CrateMountedStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CrateContent {
    public static final Map<String, DeferredHolder<Block, StorageCrateBlock>> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<Item, StorageCrateItem>> ITEMS = new LinkedHashMap<>();
    static {
        for (String metal : new String[]{"iron", "steel"}) {
            int rows = metal.equals("iron") ? 4 : 6;
            String id = "crate_" + metal;
            var block = HbmPort.BLOCKS.register(id, () -> new StorageCrateBlock(BlockBehaviour.Properties.of()
                    .strength(5, 10).sound(SoundType.METAL), rows));
            BLOCKS.put(id, block);
            ITEMS.put(id, HbmPort.ITEMS.register(id, () -> new StorageCrateItem(block.get(), new Item.Properties())));
        }
    }
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageCrateBlockEntity>> ENTITY =
            HbmPort.BLOCK_ENTITIES.register("storage_crate", () -> BlockEntityType.Builder.of(StorageCrateBlockEntity::new,
                    BLOCKS.get("crate_iron").get(), BLOCKS.get("crate_steel").get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<StorageCrateMenu>> IRON_MENU = HbmPort.MENUS.register("crate_iron",
            () -> new MenuType<>((id, inv) -> new StorageCrateMenu(id, inv, 4), FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<StorageCrateMenu>> STEEL_MENU = HbmPort.MENUS.register("crate_steel",
            () -> new MenuType<>((id, inv) -> new StorageCrateMenu(id, inv, 6), FeatureFlags.DEFAULT_FLAGS));
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HbmPort.MOD_ID);
    public static final DeferredHolder<SoundEvent, SoundEvent> OPEN = SOUNDS.register("block.crate_open",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("hbm", "block.crate_open")));
    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = SOUNDS.register("block.crate_close",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("hbm", "block.crate_close")));
    private static final DeferredRegister<MountedItemStorageType<?>> MOUNTED = DeferredRegister.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE, "hbm");
    public static final DeferredHolder<MountedItemStorageType<?>, CrateMountedStorage.Type> MOUNTED_CRATE =
            MOUNTED.register("storage_crate", CrateMountedStorage.Type::new);

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
        MOUNTED.register(bus);
        bus.addListener(CrateContent::setup);
    }
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BLOCKS.values().forEach(block -> MountedItemStorageType.REGISTRY.register(block.get(), MOUNTED_CRATE.get())));
    }
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var block : BLOCKS.values()) event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, be, side) -> be instanceof StorageCrateBlockEntity crate ? crate.itemHandler() : null, block.get());
    }
}
