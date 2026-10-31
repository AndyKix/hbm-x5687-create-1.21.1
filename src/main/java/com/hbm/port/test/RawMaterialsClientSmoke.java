package com.hbm.port.test;

import com.google.gson.GsonBuilder;
import com.hbm.port.HbmPort;
import com.hbm.port.material.RawMaterials;
import com.hbm.port.storage.CrateContent;
import com.hbm.port.storage.StorageCrateBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.util.*;

/** Opt-in normal terrain generation in all three dimensions plus a separate-process reload.
 * Neither this class nor any GameTest is included in the player JAR.
 */
@EventBusSubscriber(modid = HbmPort.MOD_ID, value = Dist.CLIENT)
public final class RawMaterialsClientSmoke {
    private static int stage, ticks;
    private static long started;
    private static String world;
    private static BlockPos core;
    private static BlockPos lightTest;
    private static volatile boolean ready;
    private static volatile Throwable failure;
    private static final Map<String, Map<String, Integer>> COUNTS = new LinkedHashMap<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        boolean reload = Boolean.getBoolean("hbm.rawReloadSmoke");
        if (!reload && !Boolean.getBoolean("hbm.rawClientSmoke")) return;
        var mc = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        if (failure != null) throw new IllegalStateException("Raw material smoke failed", failure);
        if (System.nanoTime() - started > 360_000_000_000L) throw new IllegalStateException("Raw material client timeout at " + stage);
        var manifest = mc.gameDirectory.toPath().resolve("hbm-stage5-smoke.properties");
        if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            for (String name : RawMaterials.CATALOG.getAsJsonObject("blocks").keySet()) {
                var state = RawMaterials.block(name).defaultBlockState();
                var model = mc.getBlockRenderer().getBlockModel(state);
                if (model.getParticleIcon().contents().name().getPath().equals("missingno")) throw new IllegalStateException("Missing block texture: " + name);
                var definition = RawMaterials.CATALOG.getAsJsonObject("blocks").getAsJsonObject(name);
                if (model.getQuads(state, definition.has("decoration") ? null : net.minecraft.core.Direction.UP, RandomSource.create(1)).isEmpty()) throw new IllegalStateException("Missing block face: " + name);
            }
            for (String name : RawMaterials.CATALOG.getAsJsonObject("items").keySet()) {
                var stack = new ItemStack(RawMaterials.item(name));
                if (!Language.getInstance().has(stack.getDescriptionId())) throw new IllegalStateException("Missing translation: " + name);
                if (mc.getItemRenderer().getModel(stack, null, null, 0).getParticleIcon().contents().name().getPath().equals("missingno"))
                    throw new IllegalStateException("Missing item model: " + name);
            }
            stage = 1;
            if (reload) {
                var props = new Properties(); try (var in = Files.newInputStream(manifest)) { props.load(in); }
                world = props.getProperty("world"); core = new BlockPos(Integer.parseInt(props.getProperty("x")), Integer.parseInt(props.getProperty("y")), Integer.parseInt(props.getProperty("z")));
                mc.createWorldOpenFlows().openWorld(world, () -> { throw new IllegalStateException("Cannot reopen resource smoke world"); });
            } else {
                world = "hbm-stage5-smoke-" + System.currentTimeMillis();
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings("HBM raw materials / normal world", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(5687, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            }
        } else if (stage == 1 && mc.player != null && mc.level != null && mc.getSingleplayerServer() != null) {
            stage = 2; ticks = 0;
            var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var level = server.overworld(); var player = server.getPlayerList().getPlayer(uuid);
                    if (reload) {
                        checkSaved(level);
                        LogUtils.getLogger().info("HBM RAW RELOAD SMOKE PASSED: placed resources, all material stacks and generated ore snapshot persisted across processes");
                    } else {
                        core = player.blockPosition().above(16);
                        int cx = player.chunkPosition().x, cz = player.chunkPosition().z;
                        COUNTS.put("overworld", scan(level, cx, cz, 3, -63, 64));
                        COUNTS.put("nether", scan(Objects.requireNonNull(server.getLevel(Level.NETHER)), 0, 0, 2, 1, 126));
                        COUNTS.put("end", scan(Objects.requireNonNull(server.getLevel(Level.END)), 0, 0, 1, 1, 126));
                        for (var spec : RawMaterials.CATALOG.getAsJsonArray("veins")) {
                            var vein = spec.getAsJsonObject(); String name = vein.get("id").getAsString();
                            if (!vein.get("rarity").isJsonNull() || name.equals("stone_resource_limestone") || name.startsWith("ore_gneiss_")) continue;
                            if (COUNTS.get(vein.get("dimension").getAsString()).getOrDefault(name, 0) == 0)
                                throw new IllegalStateException("No naturally generated " + name + " in smoke region");
                        }
                        int index = 0;
                        for (String name : RawMaterials.CATALOG.getAsJsonObject("blocks").keySet()) {
                            int x = index % 10, z = index / 10; index++;
                            level.setBlockAndUpdate(core.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                            if (name.equals("stalactite_asbestos")) level.setBlockAndUpdate(core.offset(x, 1, z), Blocks.STONE.defaultBlockState());
                            level.setBlockAndUpdate(core.offset(x, 0, z), RawMaterials.block(name).defaultBlockState());
                        }
                        level.setBlockAndUpdate(core.offset(-2, -1, 0), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(core.offset(-2, 0, 0), CrateContent.BLOCKS.get("crate_steel").get().defaultBlockState());
                        var crate = (StorageCrateBlockEntity) level.getBlockEntity(core.offset(-2, 0, 0));
                        int slot = 0;
                        for (String name : RawMaterials.CATALOG.getAsJsonObject("items").keySet()) crate.setItem(slot++, new ItemStack(RawMaterials.item(name), 3));
                        var props = new Properties(); props.setProperty("world", world); props.setProperty("x", "" + core.getX()); props.setProperty("y", "" + core.getY()); props.setProperty("z", "" + core.getZ());
                        try (var out = Files.newOutputStream(manifest)) { props.store(out, "HBM stage5 test fixture"); }
                        var report = new LinkedHashMap<String, Object>(); report.put("seed", 5687); report.put("world", world); report.put("natural_resource_counts", COUNTS);
                        report.put("scanned_chunks", Map.of("overworld", 49, "nether", 25, "end", 9)); report.put("saved_material_stacks", slot);
                        // Store an immutable sample in an untouched generated chunk for the next process.
                        var sample = new BlockPos((cx - 3) * 16, 0, (cz - 3) * 16);
                        props.setProperty("sampleX", "" + sample.getX()); props.setProperty("sampleZ", "" + sample.getZ());
                        props.setProperty("sampleHash", "" + snapshotHash(level, sample));
                        try (var out = Files.newOutputStream(manifest)) { props.store(out, "HBM stage5 test fixture"); }
                        Files.writeString(mc.gameDirectory.toPath().resolve("hbm-stage5-world-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report));
                        LogUtils.getLogger().info("HBM RAW WORLD SMOKE PASSED: normal seed 5687, 49 Overworld + 25 Nether + 9 End chunks; counts {}", COUNTS);
                    }
                    level.setDayTime(6000);
                    player.connection.teleport(core.getX() + 4.5, core.getY() + 8, core.getZ() + 17, 180, 30);
                    player.getAbilities().flying = true; player.onUpdateAbilities();
                    ready = true;
                } catch (Throwable error) { failure = error; }
            });
        } else if (stage == 2 && ready) {
            ticks++;
            if (ticks == 20) {
                checkGemFrame(mc, mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition()));
                lightTest = mc.player.blockPosition();
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setBlockAndUpdate(lightTest, Blocks.LIGHT.defaultBlockState()));
            }
            if (ticks == 40) {
                if (mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition()) != 15) throw new IllegalStateException("Light fixture did not reach the client");
                checkGemFrame(mc, 15);
                LogUtils.getLogger().info("HBM ALEXANDRITE CLIENT SMOKE PASSED: original frame selection follows actual local block light up to 15");
                mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().overworld().setBlockAndUpdate(lightTest, Blocks.AIR.defaultBlockState()));
            }
            if (ticks == 100) {
                checkGemFrame(mc, mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition()));
                mc.getToasts().clear();
                Screenshot.grab(mc.gameDirectory, reload ? "hbm-stage5-reload.png" : "hbm-stage5-resources.png", mc.getMainRenderTarget(), c -> {});
                stage = 3; ticks = 0;
            }
        } else if (stage == 3 && ++ticks == 30) mc.stop();
    }

    static void checkGemFrame(Minecraft mc, int frame) {
        var model = mc.getItemRenderer().getModel(new ItemStack(RawMaterials.item("gem_alexandrite")), mc.level, mc.player, 0);
        if (!model.getParticleIcon().contents().name().getPath().equals("item/gem_alexandrite_" + frame))
            throw new IllegalStateException("Alexandrite item selected the wrong light frame " + frame);
    }

    static Map<String, Integer> scan(ServerLevel level, int cx, int cz, int radius, int low, int high) {
        var tracked = new IdentityHashMap<Block, String>();
        for (String name : RawMaterials.CATALOG.getAsJsonObject("blocks").keySet()) tracked.put(RawMaterials.block(name), name);
        var counts = new TreeMap<String, Integer>(); var pos = new BlockPos.MutableBlockPos();
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            var chunk = level.getChunk(x, z);
            for (int ox = 0; ox < 16; ox++) for (int oz = 0; oz < 16; oz++) for (int y = low; y <= high; y++) {
                pos.set(x * 16 + ox, y, z * 16 + oz); String name = tracked.get(chunk.getBlockState(pos).getBlock());
                if (name != null) counts.merge(name, 1, Integer::sum);
            }
        }
        return counts;
    }
    static int snapshotHash(ServerLevel level, BlockPos origin) {
        int hash = 1; var pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = -63; y <= 64; y++) {
            pos.set(origin.getX() + x, y, origin.getZ() + z);
            var id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            hash = 31 * hash + (id.getNamespace().equals("hbm") ? id.hashCode() : 0);
        }
        return hash;
    }
    static void checkSaved(ServerLevel level) throws Exception {
        var props = new Properties(); try (var in = Files.newInputStream(Minecraft.getInstance().gameDirectory.toPath().resolve("hbm-stage5-smoke.properties"))) { props.load(in); }
        int index = 0;
        for (String name : RawMaterials.CATALOG.getAsJsonObject("blocks").keySet()) {
            var pos = core.offset(index % 10, 0, index / 10); index++;
            if (!level.getBlockState(pos).is(RawMaterials.block(name))) throw new IllegalStateException("Placed resource lost on reload: " + name);
        }
        var crate = (StorageCrateBlockEntity) level.getBlockEntity(core.offset(-2, 0, 0)); int slot = 0;
        for (String name : RawMaterials.CATALOG.getAsJsonObject("items").keySet()) {
            var stack = crate.getItem(slot++);
            if (!stack.is(RawMaterials.item(name)) || stack.getCount() != 3) throw new IllegalStateException("Saved resource inventory differs: " + name);
        }
        var sample = new BlockPos(Integer.parseInt(props.getProperty("sampleX")), 0, Integer.parseInt(props.getProperty("sampleZ")));
        if (snapshotHash(level, sample) != Integer.parseInt(props.getProperty("sampleHash"))) throw new IllegalStateException("Generated chunk changed on reload");
    }
}
