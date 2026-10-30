package com.hbm.port.test;

import com.hbm.port.HbmPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Stream;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class OreGenerationTests {
    @GameTest(template = "empty")
    public static void oresAreAddedToOverworldBiomes(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var plains = registry.getHolderOrThrow(Biomes.PLAINS).value();
        var nether = registry.getHolderOrThrow(Biomes.NETHER_WASTES).value();
        int step = GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
        for (String metal : List.of("titanium", "lead")) {
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                    ResourceLocation.fromNamespaceAndPath("hbm", "ore_" + metal));
            helper.assertTrue(plains.getGenerationSettings().features().get(step).stream().anyMatch(h -> h.is(key)),
                    "Overworld biome is missing " + key.location());
            helper.assertTrue(nether.getGenerationSettings().features().stream().flatMap(h -> h.stream()).noneMatch(h -> h.is(key)),
                    "Overworld ore leaked into Nether biome generation");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void placementKeepsX5687FrequencyAndHeight(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (String metal : List.of("titanium", "lead")) {
            var placed = registry.get(ResourceLocation.fromNamespaceAndPath("hbm", "ore_" + metal));
            helper.assertTrue(placed != null, "Placed ore feature is missing");
            var context = new PlacementContext(level, level.getChunkSource().getGenerator(), Optional.of(placed));
            RandomSource random = RandomSource.create(5687);
            // Biome eligibility is exercised separately against the actual modified biome registry.
            Stream<BlockPos> positions = Stream.of(new BlockPos(0, 0, 0));
            for (var modifier : placed.placement()) {
                if (!(modifier instanceof BiomeFilter))
                    positions = positions.flatMap(pos -> modifier.getPositions(context, random, pos));
            }
            List<BlockPos> samples = positions.toList();
            helper.assertTrue(samples.size() == (metal.equals("titanium") ? 8 : 6), "Ore frequency differs from X5687 defaults");
            for (BlockPos pos : samples) {
                helper.assertTrue(pos.getY() >= 5 && pos.getY() <= 34, "Ore origin is outside X5687 minHeight + variance");
                helper.assertTrue(pos.getX() >= 0 && pos.getX() < 16 && pos.getZ() >= 0 && pos.getZ() < 16,
                        "Ore origin is outside its source chunk");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "ore_test", batch = "worldgen")
    public static void oreVeinsReplaceStoneReproducibly(GameTestHelper helper) {
        var level = helper.getLevel();
        var features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (String metal : List.of("titanium", "lead")) {
            var ore = HbmPort.ORES.get("ore_" + metal).get();
            var feature = features.get(ResourceLocation.fromNamespaceAndPath("hbm", "ore_" + metal));
            helper.assertTrue(feature != null, "Configured ore feature did not load");
            Set<BlockPos> previous = null;
            for (int offset : new int[] {0, 16}) {
                for (int x = 0; x < 16; x++) for (int y = 0; y < 14; y++) for (int z = 0; z < 16; z++) {
                    var state = y == 5 ? Blocks.BASALT.defaultBlockState() : Blocks.STONE.defaultBlockState();
                    level.setBlock(helper.absolutePos(new BlockPos(x + offset, y, z)), state, 2);
                }
                // WG heightmaps are normally prepared by the terrain generator. This fixture is
                // deliberately constructed after generation in a live GameTest ServerLevel.
                Set<ChunkPos> chunks = new HashSet<>();
                for (int x : new int[] {offset, offset + 15}) for (int z : new int[] {0, 15})
                    chunks.add(new ChunkPos(helper.absolutePos(new BlockPos(x, 0, z))));
                for (ChunkPos chunk : chunks)
                    Heightmap.primeHeightmaps(level.getChunk(chunk.x, chunk.z), EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                BlockPos origin = helper.absolutePos(new BlockPos(7 + offset, 7, 7));
                helper.assertTrue(feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(5687), origin),
                        "Ore placement produced no vein");
                Set<BlockPos> found = new HashSet<>();
                for (int x = 0; x < 16; x++) for (int y = 0; y < 14; y++) for (int z = 0; z < 16; z++) {
                    var state = level.getBlockState(helper.absolutePos(new BlockPos(x + offset, y, z)));
                    if (y == 5) helper.assertTrue(state.is(Blocks.BASALT), "Ore replaced a non-stone block");
                    if (state.is(ore)) found.add(new BlockPos(x, y, z));
                }
                helper.assertTrue(!found.isEmpty(), "Registered feature placed no HBM ore");
                if (previous != null) helper.assertTrue(found.equals(previous), "Equal seeds and stone volumes produced different ore patterns");
                previous = found;
            }
        }
        helper.succeed();
    }
}
