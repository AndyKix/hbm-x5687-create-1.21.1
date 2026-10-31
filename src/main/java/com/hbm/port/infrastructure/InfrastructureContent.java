package com.hbm.port.infrastructure;

import com.hbm.port.HbmPort;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.EnumMap;

public final class InfrastructureContent {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "hbm");
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> CHARGE = COMPONENTS.register("he_charge",
            () -> DataComponentType.<Long>builder().persistent(Codec.LONG.validate(value->value>=0?com.mojang.serialization.DataResult.success(value):com.mojang.serialization.DataResult.error(()->"Negative HE charge"))).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidKind>> PRIMARY = COMPONENTS.register("primary_fluid",
            () -> DataComponentType.<FluidKind>builder().persistent(FluidKind.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(FluidKind.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidKind>> SECONDARY = COMPONENTS.register("secondary_fluid",
            () -> DataComponentType.<FluidKind>builder().persistent(FluidKind.CODEC).networkSynchronized(ByteBufCodecs.fromCodec(FluidKind.CODEC)).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BarrelState>> BARREL_STATE = COMPONENTS.register("barrel_state",
            () -> DataComponentType.<BarrelState>builder().persistent(BarrelState.CODEC).networkSynchronized(BarrelState.STREAM_CODEC).build());
    public static final BlockCapability<HeStorage, net.minecraft.core.Direction> ENERGY = BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("hbm", "he_energy"), HeStorage.class);
    public static final ItemCapability<HeStorage, Void> ITEM_ENERGY = ItemCapability.createVoid(ResourceLocation.fromNamespaceAndPath("hbm", "he_energy"), HeStorage.class);
    public static final DeferredHolder<Item,Item> INSULATOR=HbmPort.ITEMS.register("plate_polymer",()->new Item(new Item.Properties()));
    public static final EnumMap<BatteryKind, DeferredHolder<Item, BatteryItem>> BATTERIES = new EnumMap<>(BatteryKind.class);
    public static final DeferredHolder<Block, EnergySocketBlock> SOCKET = HbmPort.BLOCKS.register("machine_battery_socket",
            () -> new EnergySocketBlock(BlockBehaviour.Properties.of().strength(5,10).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredHolder<Item, BlockItem> SOCKET_ITEM = HbmPort.ITEMS.register("machine_battery_socket", () -> new BlockItem(SOCKET.get(), new Item.Properties()));
    public static final DeferredHolder<Block, NetworkLinkBlock> CABLE = HbmPort.BLOCKS.register("red_cable",
            () -> new NetworkLinkBlock(BlockBehaviour.Properties.of().strength(5,10).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(), false));
    public static final DeferredHolder<Item, BlockItem> CABLE_ITEM = HbmPort.ITEMS.register("red_cable", () -> new BlockItem(CABLE.get(), new Item.Properties()));
    public static final DeferredHolder<Block, NetworkLinkBlock> PIPE = HbmPort.BLOCKS.register("fluid_duct_neo",
            () -> new NetworkLinkBlock(BlockBehaviour.Properties.of().strength(5,10).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion(), true));
    public static final DeferredHolder<Item, BlockItem> PIPE_ITEM = HbmPort.ITEMS.register("fluid_duct_neo", () -> new BlockItem(PIPE.get(), new Item.Properties()));
    public static final DeferredHolder<Block, SteelBarrelBlock> BARREL = HbmPort.BLOCKS.register("barrel_steel",
            () -> new SteelBarrelBlock(BlockBehaviour.Properties.of().strength(2,5).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion()));
    public static final DeferredHolder<Item, BlockItem> BARREL_ITEM = HbmPort.ITEMS.register("barrel_steel", () -> new BarrelItem(BARREL.get()));
    public static final DeferredHolder<Item, FluidIdentifierItem> IDENTIFIER = HbmPort.ITEMS.register("fluid_identifier_multi", FluidIdentifierItem::new);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EnergySocketBlockEntity>> SOCKET_ENTITY = HbmPort.BLOCK_ENTITIES.register("battery_socket",
            () -> BlockEntityType.Builder.of(EnergySocketBlockEntity::new, SOCKET.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NetworkLinkBlockEntity>> LINK_ENTITY = HbmPort.BLOCK_ENTITIES.register("network_link",
            () -> BlockEntityType.Builder.of(NetworkLinkBlockEntity::new, CABLE.get(), PIPE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SteelBarrelBlockEntity>> BARREL_ENTITY = HbmPort.BLOCK_ENTITIES.register("barrel_steel",
            () -> BlockEntityType.Builder.of(SteelBarrelBlockEntity::new, BARREL.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<EnergySocketMenu>> SOCKET_MENU = HbmPort.MENUS.register("battery_socket",
            () -> new MenuType<>(EnergySocketMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<SteelBarrelMenu>> BARREL_MENU = HbmPort.MENUS.register("barrel_steel",
            () -> new MenuType<>(SteelBarrelMenu::new, FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<FluidIdentifierMenu>> IDENTIFIER_MENU = HbmPort.MENUS.register("fluid_identifier",
            () -> new MenuType<>(FluidIdentifierMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus bus) {
        for (var kind : BatteryKind.values()) if (kind != BatteryKind.EMPTY)
            BATTERIES.put(kind, HbmPort.ITEMS.register(kind.id, () -> new BatteryItem(kind)));
        COMPONENTS.register(bus);
    }
    public static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(ENERGY, (level,pos,state,be,side) -> {
            var socket = EnergySocketBlock.controller(level,pos,state);
            return socket != null && EnergySocketBlock.isPort(state, side) ? socket : null;
        }, SOCKET.get());
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level,pos,state,be,side) -> {
            var socket = EnergySocketBlock.controller(level,pos,state);
            return socket == null ? null : socket.itemHandler();
        }, SOCKET.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BARREL_ENTITY.get(), (barrel,side) -> barrel.itemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, BARREL_ENTITY.get(), (barrel,side) -> barrel.externalHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, LINK_ENTITY.get(), (link,side) -> link.isFluid() ? link.fluidHandler() : null);
        for (var battery : BATTERIES.values()) event.registerItem(ITEM_ENERGY, (stack,ctx) -> ((BatteryItem)stack.getItem()).storage(stack), battery.get());
    }
    public static void creative(CreativeModeTab.Output output) {
        output.accept(INSULATOR.get());
        output.accept(SOCKET_ITEM.get()); output.accept(CABLE_ITEM.get()); output.accept(BARREL_ITEM.get()); output.accept(PIPE_ITEM.get());
        for (var entry : BATTERIES.entrySet()) {
            var item = entry.getValue().get(); var kind = entry.getKey();
            var empty = new ItemStack(item); item.setCharge(empty, 0); output.accept(empty);
            if (!kind.infinite) { var full = new ItemStack(item); item.setCharge(full, kind.capacity); output.accept(full); }
        }
        for (var kind : FluidKind.values()) {
            var stack = new ItemStack(IDENTIFIER.get()); stack.set(PRIMARY.get(),kind); output.accept(stack);
        }
    }
    private InfrastructureContent() {}
}
