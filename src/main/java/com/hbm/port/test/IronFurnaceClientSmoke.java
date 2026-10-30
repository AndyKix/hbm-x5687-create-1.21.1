package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.hbm.port.client.IronFurnaceScreen;
import com.hbm.port.machine.IronFurnaceBlock;
import com.hbm.port.machine.IronFurnaceBlockEntity;
import com.hbm.port.machine.IronFurnaceMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
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

/** Opt-in development-only test. Never included in the distributable JAR. */
@EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class IronFurnaceClientSmoke {
    private static int stage, ticks;
    private static BlockPos core;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("hbm.clientSmoke")) return;
        Minecraft mc = Minecraft.getInstance();
        if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            for (Direction facing : Direction.Plane.HORIZONTAL) for (boolean lit : new boolean[]{false, true}) {
                var state = HbmPort.IRON_FURNACE.get().defaultBlockState().setValue(IronFurnaceBlock.FACING, facing).setValue(IronFurnaceBlock.LIT, lit);
                var model = mc.getBlockRenderer().getBlockModel(state);
                int quads = model.getQuads(state, null, RandomSource.create(1)).size();
                if (quads < 100) throw new IllegalStateException("Iron furnace OBJ failed to bake: " + facing + " / " + lit + " / " + quads);
            }
            LogUtils.getLogger().info("HBM CLIENT SMOKE: all 8 directional on/off OBJ models baked");
            stage = 1;
            mc.createWorldOpenFlows().createFreshLevel("hbm-stage3-smoke-" + System.currentTimeMillis(),
                    new LevelSettings("HBM stage 3 client smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(5687L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
        } else if (stage == 1 && mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
            stage = 2; ticks = 0;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel();
                core = player.blockPosition().above(8);
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 5; z++) {
                    level.setBlockAndUpdate(core.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 4; y++) level.setBlockAndUpdate(core.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
                var state = HbmPort.IRON_FURNACE.get().defaultBlockState();
                level.setBlockAndUpdate(core, state);
                HbmPort.IRON_FURNACE.get().setPlacedBy(level, core, state, null, ItemStack.EMPTY);
                var furnace = (IronFurnaceBlockEntity) level.getBlockEntity(core);
                furnace.setItem(0, new ItemStack(HbmPort.ORES.get("ore_titanium").get(), 8));
                furnace.setItem(1, new ItemStack(Items.COAL, 2));
                furnace.setItem(2, new ItemStack(Items.CHARCOAL, 3));
                player.connection.teleport(core.getX(), core.getY(), core.getZ() + 4.5, 180, 8);
                LogUtils.getLogger().info("HBM CLIENT SMOKE: placed server-side multiblock with input and both fuels");
            });
        } else if (stage == 2 && mc.player != null && ++ticks == 60) {
            Screenshot.grab(mc.gameDirectory, "hbm-stage3-world.png", mc.getMainRenderTarget(), c -> {});
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> server.getPlayerList().getPlayer(uuid).openMenu((IronFurnaceBlockEntity) server.overworld().getBlockEntity(core)));
            stage = 3; ticks = 0;
        } else if (stage == 3 && mc.screen instanceof IronFurnaceScreen && ++ticks == 20) {
            var menu = (IronFurnaceMenu) mc.player.containerMenu;
            if (!menu.getSlot(0).hasItem() || menu.burnTime() <= 0 || menu.progress() <= 0 || !menu.active()
                    || menu.getSlot(1).getItem().getCount() != 1 || menu.getSlot(2).getItem().getCount() != 3)
                throw new IllegalStateException("Iron furnace client menu failed to synchronize slots, fuel, progress or activity");
            Screenshot.grab(mc.gameDirectory, "hbm-stage3-menu.png", mc.getMainRenderTarget(), c -> {});
            LogUtils.getLogger().info("HBM CLIENT SMOKE PASSED: actual server-opened GUI received input, both fuels, activity and progress; screenshots saved");
            stage = 4; ticks = 0;
        } else if (stage == 4 && ++ticks == 20) mc.stop();
    }
}
