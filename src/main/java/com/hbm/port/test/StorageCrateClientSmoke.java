package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.hbm.port.client.StorageCrateScreen;
import com.hbm.port.storage.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Opt-in client/server interaction test plus a second process reopening its saved world. Not shipped in the mod JAR. */
@EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class StorageCrateClientSmoke {
    private static int stage, ticks;
    private static volatile BlockPos core;
    private static volatile boolean ready;
    private static com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity bearing;
    private static String world;
    private static long started;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        boolean reload = Boolean.getBoolean("hbm.crateReloadSmoke");
        if (!reload && !Boolean.getBoolean("hbm.crateClientSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > 180_000_000_000L) throw new IllegalStateException("Crate client smoke timed out at stage " + stage);
        Path manifest = mc.gameDirectory.toPath().resolve("hbm-stage4-smoke.properties");
        if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            for (var block : CrateContent.BLOCKS.values()) for (Direction side : Direction.values()) {
                var state = block.get().defaultBlockState();
                if (mc.getBlockRenderer().getBlockModel(state).getQuads(state, side, RandomSource.create(1)).size() != 1)
                    throw new IllegalStateException("Crate block model did not bake all six faces");
            }
            stage = 1;
            if (reload) {
                var props = new Properties(); try (var in = Files.newInputStream(manifest)) { props.load(in); }
                world = props.getProperty("world");
                core = new BlockPos(Integer.parseInt(props.getProperty("x")), Integer.parseInt(props.getProperty("y")), Integer.parseInt(props.getProperty("z")));
                mc.createWorldOpenFlows().openWorld(world, () -> { throw new IllegalStateException("Could not reopen crate smoke world"); });
            } else {
                world = "hbm-stage4-smoke-" + System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings("HBM stage 4 crates", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(5687L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            }
        } else if (stage == 1 && mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
            stage = reload ? 20 : 2; ticks = 0;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel();
                if (reload) {
                    for (int i = 0; i < 2; i++) {
                        var crate = (StorageCrateBlockEntity) level.getBlockEntity(core.offset(i * 2, 0, 0));
                        if (crate == null || crate.getItem(0).getCount() != 17 || !crate.getItem(crate.getContainerSize() - 1).isEmpty())
                            throw new IllegalStateException("Crate contents did not survive complete process restart");
                    }
                    var iron = (StorageCrateBlockEntity) level.getBlockEntity(core);
                    if (!iron.getItem(1).is(Items.COAL) || iron.getItem(1).getCount() != 7) throw new IllegalStateException("Mounted menu insertion did not persist after disassembly/restart");
                    if (player.getInventory().countItem(Items.DIAMOND) != 6) throw new IllegalStateException("Player inventory did not persist menu withdrawals");
                    player.openMenu((StorageCrateBlockEntity) level.getBlockEntity(core));
                    LogUtils.getLogger().info("HBM CRATE RELOAD: both crates and player inventory survived complete client/server restart");
                } else {
                    core = player.blockPosition().above(8);
                    for (int x = -3; x <= 5; x++) for (int z = -3; z <= 6; z++) {
                        level.setBlockAndUpdate(core.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                        for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(core.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                    int i = 0;
                    for (String metal : new String[]{"iron", "steel"}) {
                        BlockPos pos = core.offset(i++ * 2, 0, 0);
                        level.setBlockAndUpdate(pos, CrateContent.BLOCKS.get("crate_" + metal).get().defaultBlockState());
                        var crate = (StorageCrateBlockEntity) level.getBlockEntity(pos);
                        crate.setItem(0, new ItemStack(Items.IRON_INGOT, 17)); crate.setItem(crate.getContainerSize() - 1, StorageCrateTests.namedDiamond());
                        StorageCrateTests.name(crate, metal.equals("iron") ? "Железный тестовый ящик" : "Стальной тестовый ящик");
                    }
                    player.connection.teleport(core.getX() + 1.5, core.getY(), core.getZ() + 5.5, 180, 12);
                }
                ready = true;
            });
        } else if (stage == 2 && ready && ++ticks == 50) {
            var props = new Properties(); props.setProperty("world", world); props.setProperty("x", "" + core.getX()); props.setProperty("y", "" + core.getY()); props.setProperty("z", "" + core.getZ());
            try (var out = Files.newOutputStream(manifest)) { props.store(out, "Development-only crate smoke test world"); }
            Screenshot.grab(mc.gameDirectory, "hbm-stage4-crates.png", mc.getMainRenderTarget(), c -> {});
            open(mc, core); stage = 3; ticks = 0;
        } else if (stage == 3 && mc.screen instanceof StorageCrateScreen && ++ticks == 15) {
            checkMenu(mc, 4, true);
            Screenshot.grab(mc.gameDirectory, "hbm-stage4-iron-menu.png", mc.getMainRenderTarget(), c -> {});
            var menu = mc.player.containerMenu;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 35, 0, ClickType.QUICK_MOVE, mc.player);
            stage = 4;
        } else if (stage == 4 && mc.player.getInventory().countItem(Items.DIAMOND) == 3 && !mc.player.containerMenu.getSlot(35).hasItem()) {
            mc.player.closeContainer(); stage = 5; ticks = 0;
        } else if (stage == 5 && ++ticks == 10) {
            open(mc, core.offset(2, 0, 0)); stage = 6; ticks = 0;
        } else if (stage == 6 && mc.screen instanceof StorageCrateScreen && ++ticks == 15) {
            checkMenu(mc, 6, true);
            Screenshot.grab(mc.gameDirectory, "hbm-stage4-steel-menu.png", mc.getMainRenderTarget(), c -> {});
            var menu = mc.player.containerMenu;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 53, 0, ClickType.QUICK_MOVE, mc.player); stage = 7;
        } else if (stage == 7 && mc.player.getInventory().countItem(Items.DIAMOND) == 6 && !mc.player.containerMenu.getSlot(53).hasItem()) {
            mc.player.closeContainer();
            stage = 9; ticks = 0;
        } else if (stage == 9 && ++ticks == 10) {
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var level = server.overworld(); var player = server.getPlayerList().getPlayer(uuid);
                var bearingBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "mechanical_bearing"));
                var motorBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
                level.setBlockAndUpdate(core.below(), bearingBlock.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
                level.setBlockAndUpdate(core.below(2), motorBlock.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
                ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) level.getBlockEntity(core.below(2))).generatedSpeed.setValue(64);
                bearing = (com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity) level.getBlockEntity(core.below());
                bearing.assemble();
                var contraption = bearing.getMovedContraption().getContraption();
                var storage = contraption.getStorage().getAllItemStorages().get(BlockPos.ZERO);
                player.getInventory().setItem(10, new ItemStack(CrateContent.ITEMS.get("crate_steel").get()));
                player.getInventory().setItem(11, new ItemStack(Items.COAL, 7));
                if (!storage.handleInteraction(player, contraption, contraption.getBlocks().get(BlockPos.ZERO)))
                    throw new IllegalStateException("Moving crate GUI did not open");
            });
            stage = 10; ticks = 0;
        } else if (stage == 10 && mc.screen instanceof StorageCrateScreen && ++ticks == 20) {
            checkMenu(mc, 4, false);
            var menu = mc.player.containerMenu;
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 37, 0, ClickType.QUICK_MOVE, mc.player);
            mc.gameMode.handleInventoryMouseClick(menu.containerId, 38, 0, ClickType.QUICK_MOVE, mc.player);
            stage = 11; ticks = 0;
        } else if (stage == 11 && mc.player.containerMenu.getSlot(1).getItem().is(Items.COAL)
                && mc.player.containerMenu.getSlot(1).getItem().getCount() == 7 && ++ticks == 10) {
            if (!mc.player.getInventory().getItem(10).is(CrateContent.ITEMS.get("crate_steel").get()))
                throw new IllegalStateException("Moving crate menu allowed nested portable container");
            Screenshot.grab(mc.gameDirectory, "hbm-stage4-mounted-menu.png", mc.getMainRenderTarget(), c -> {});
            mc.player.closeContainer(); stage = 12; ticks = 0;
        } else if (stage == 12 && ++ticks == 10) {
            var server = mc.getSingleplayerServer(); ready = false;
            server.execute(() -> {
                bearing.disassemble(); var level = server.overworld();
                var crate = (StorageCrateBlockEntity) level.getBlockEntity(core);
                if (crate == null || crate.getItem(0).getCount() != 17 || crate.getItem(1).getCount() != 7)
                    throw new IllegalStateException("Moving GUI modifications lost on disassembly");
                level.setBlockAndUpdate(core.below(), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(core.below(2), Blocks.AIR.defaultBlockState());
                ready = true;
            });
            stage = 13; ticks = 0;
        } else if (stage == 13 && ready) {
            LogUtils.getLogger().info("HBM CRATE CLIENT SMOKE PASSED: original GUIs, 36/54 slots, real network shift-click, moving Create GUI, nesting rejection and modified contents after disassembly");
            stage = 8; ticks = 0;
        } else if (stage == 20 && ready && mc.screen instanceof StorageCrateScreen && ++ticks == 20) {
            checkMenu(mc, 4, false);
            if (!mc.screen.getTitle().getString().equals("Железный тестовый ящик")) throw new IllegalStateException("Crate name did not survive restart");
            Screenshot.grab(mc.gameDirectory, "hbm-stage4-reload.png", mc.getMainRenderTarget(), c -> {});
            LogUtils.getLogger().info("HBM CRATE RELOAD SMOKE PASSED: client GUI after process restart matches saved crate inventory and name");
            stage = 8; ticks = 0;
        } else if (stage == 8 && ++ticks == 20) mc.stop();
    }
    private static void open(Minecraft mc, BlockPos pos) {
        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
        server.execute(() -> server.getPlayerList().getPlayer(uuid).openMenu((StorageCrateBlockEntity) server.overworld().getBlockEntity(pos)));
    }
    private static void checkMenu(Minecraft mc, int rows, boolean full) {
        if (!(mc.player.containerMenu instanceof StorageCrateMenu menu) || menu.rows() != rows || menu.slots.size() != rows * 9 + 36
                || menu.getSlot(0).getItem().getCount() != 17) throw new IllegalStateException("Crate GUI capacity/input desynchronized");
        var last = menu.getSlot(rows * 9 - 1).getItem();
        if (full && (last.getCount() != 3 || !last.has(DataComponents.CUSTOM_NAME)) || !full && !last.isEmpty())
            throw new IllegalStateException("Crate last-slot components desynchronized");
    }
}
