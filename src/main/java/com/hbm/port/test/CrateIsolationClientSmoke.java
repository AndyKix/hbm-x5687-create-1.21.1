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
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Repro for the user report: normal block interactions, ordinary pickup clicks and two placed crates of each type. */
@EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class CrateIsolationClientSmoke {
    private static int stage, ticks, metal;
    private static long started;
    private static volatile BlockPos core;
    private static volatile boolean ready;
    private static final int[] PATTERN = {0, 1, 8, 11, 12, 13, 23, 24, 25, 26, 30, 31, 32, 33, 34, 35};
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("hbm.crateIsolationSmoke")) return;
        var mc = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > 180_000_000_000L) throw new IllegalStateException("Crate isolation client timeout: " + stage);
        if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            stage = 1;
            mc.createWorldOpenFlows().createFreshLevel("hbm-crate-isolation-" + System.currentTimeMillis(),
                    new LevelSettings("HBM independent crate inventories", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(5687L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
        } else if (stage == 1 && mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
            stage = 2; ticks = 0;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel(); core = player.blockPosition().above(8);
                for (int x = -3; x <= 5; x++) for (int z = -3; z <= 6; z++) {
                    level.setBlockAndUpdate(core.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(core.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                for (int type = 0; type < 2; type++) for (int copy = 0; copy < 2; copy++)
                    level.setBlockAndUpdate(core.offset(copy * 2, 0, type * 2), CrateContent.BLOCKS.get(type == 0 ? "crate_iron" : "crate_steel").get().defaultBlockState());
                player.getInventory().setItem(9, new ItemStack(HbmPort.MATERIAL_ITEMS.get("plate_titanium").get(), 64));
                player.getInventory().setItem(10, new ItemStack(Items.GOLD_INGOT, 7)); player.getInventory().setItem(11, new ItemStack(Items.GOLD_INGOT, 9));
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
                player.connection.teleport(core.getX() + 1.5, core.getY(), core.getZ() + 3.5, 180, 10);
                ready = true;
            });
        } else if (stage == 2 && ready && ++ticks == 40) {
            open(mc, false); stage = 3; ticks = 0;
        } else if (stage == 3 && isMenu(mc)) {
            ticks++;
            int size = size();
            if (ticks == 2) {
                for (int i = 0; i < size; i++) if (mc.player.containerMenu.getSlot(i).hasItem()) throw new IllegalStateException("Fresh first crate is not empty");
                click(mc, size, 0);
            }
            if (ticks >= 4 && ticks <= 34 && ticks % 2 == 0) click(mc, PATTERN[(ticks - 4) / 2], 1);
            if (ticks == 36) click(mc, size, 0);
            if (ticks == 42) {
                for (int slot : PATTERN) if (mc.player.containerMenu.getSlot(slot).getItem().getCount() != 1) throw new IllegalStateException("Normal pickup input did not reach first crate");
                mc.player.closeContainer(); stage = 4; ticks = 0;
            }
        } else if (stage == 4 && ++ticks == 10) {
            open(mc, true); stage = 5; ticks = 0;
        } else if (stage == 5 && isMenu(mc) && ++ticks == 15) {
            for (int slot = 0; slot < size(); slot++) if (mc.player.containerMenu.getSlot(slot).hasItem()) throw new IllegalStateException("CLIENT REPORT REPRODUCED: second same-type crate mirrors first inventory");
            mc.getToasts().clear();
            Screenshot.grab(mc.gameDirectory, "hbm-isolation-" + metal + "-second-empty.png", mc.getMainRenderTarget(), c -> {});
            click(mc, size() + metal + 1, 0); stage = 6; ticks = 0;
        } else if (stage == 6 && isMenu(mc) && ++ticks == 4) {
            click(mc, 0, 0); stage = 7; ticks = 0;
        } else if (stage == 7 && isMenu(mc) && ++ticks == 10) {
            if (!mc.player.containerMenu.getSlot(0).getItem().is(Items.GOLD_INGOT)) throw new IllegalStateException("Second crate did not receive separate contents");
            mc.player.closeContainer(); stage = 8; ticks = 0;
        } else if (stage == 8 && ++ticks == 10) {
            open(mc, false); stage = 9; ticks = 0;
        } else if (stage == 9 && isMenu(mc) && ++ticks == 15) {
            if (!mc.player.containerMenu.getSlot(0).getItem().is(HbmPort.MATERIAL_ITEMS.get("plate_titanium").get()))
                throw new IllegalStateException("CLIENT REPORT REPRODUCED: editing second crate changed first");
            click(mc, 0, 0); stage = 10; ticks = 0;
        } else if (stage == 10 && isMenu(mc) && ++ticks == 4) {
            click(mc, size(), 0); stage = 11; ticks = 0;
        } else if (stage == 11 && isMenu(mc) && ++ticks == 10) {
            if (mc.player.containerMenu.getSlot(0).hasItem()) throw new IllegalStateException("First crate removal did not synchronize");
            Screenshot.grab(mc.gameDirectory, "hbm-isolation-" + metal + "-first-pattern.png", mc.getMainRenderTarget(), c -> {});
            mc.player.closeContainer();
            if (metal == 0) { metal = 1; stage = 2; ticks = 0; }
            else {
                stage = 12; ticks = 0; ready = false;
                var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var level = server.overworld();
                    for (int type = 0; type < 2; type++) {
                        var a = (StorageCrateBlockEntity) level.getBlockEntity(core.offset(0, 0, type * 2));
                        var b = (StorageCrateBlockEntity) level.getBlockEntity(core.offset(2, 0, type * 2));
                        if (!a.getItem(0).isEmpty() || !b.getItem(0).is(Items.GOLD_INGOT) || b.getItem(0).getCount() != (type == 0 ? 7 : 9))
                            throw new IllegalStateException("SERVER REPORT REPRODUCED: crate inventories are linked");
                        for (int i = 1; i < PATTERN.length; i++) if (a.getItem(PATTERN[i]).getCount() != 1)
                            throw new IllegalStateException("Server lost first crate pattern");
                    }
                    if (server.getPlayerList().getPlayer(uuid).getInventory().countItem(HbmPort.MATERIAL_ITEMS.get("plate_titanium").get()) != 34)
                        throw new IllegalStateException("Item totals changed across four independent crates");
                    ready = true;
                });
            }
        } else if (stage == 12 && ready && ++ticks == 15) {
            LogUtils.getLogger().info("HBM CRATE ISOLATION CLIENT PASSED: two iron and two steel crates, normal block use, ordinary pickup clicks, reopen, separate contents and exact server totals");
            mc.stop();
        }
    }
    private static int size() { return metal == 0 ? 36 : 54; }
    private static boolean isMenu(Minecraft mc) { return mc.screen instanceof StorageCrateScreen && mc.player.containerMenu instanceof StorageCrateMenu menu && menu.rows() * 9 == size(); }
    private static void click(Minecraft mc, int slot, int button) { mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, button, ClickType.PICKUP, mc.player); }
    private static void open(Minecraft mc, boolean second) {
        var pos = core.offset(second ? 2 : 0, 0, metal * 2);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5), Direction.UP, pos, false));
    }
}
