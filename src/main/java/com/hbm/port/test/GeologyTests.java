package com.hbm.port.test;

import com.hbm.port.material.RawMaterials;
import com.hbm.port.world.RawWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class GeologyTests {
    /** Independent source-parameter fixture; single stone sample isolates layer ordering. */
    @GameTest(template = "empty", batch = "raw_strata")
    public static void geologicalFeaturePlacesEverySourceStratum(GameTestHelper h) {
        long seed = h.getLevel().getSeed();
        var gneiss = noise(seed, 4);
        var asbestos = noise(seed + 31L * "hbm:stone_resource_asbestos".hashCode() + 25, 2);
        var layers = new SimplexNoise[3][3][];
        for (int i = 0; i < 3; i++) for (int axis = 0; axis < 3; axis++) layers[i][axis] = noise(seed + 101 + i + axis, 4);
        String[] names = {"stone_gneiss", "stone_resource_hematite", "stone_resource_bauxite", "stone_resource_malachite", "stone_resource_asbestos"};
        var feature = Objects.requireNonNull(h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.fromNamespaceAndPath("hbm", "geological_layers")));
        for (int kind = 0; kind < names.length; kind++) {
            BlockPos sample = null;
            for (int x = -8192; x <= 8192 && sample == null; x += 32) for (int z = -8192; z <= 8192; z += 32) {
                boolean gn = value(gneiss, x * .01, z * .01) > 5;
                if (gn && kind == 0) { sample = new BlockPos(x, 30, z); break; }
                if (gn) continue;
                boolean[] present = new boolean[3];
                for (int i = 0; i < 3; i++) {
                    double sh = new double[]{.04, .03, .10}[i], sv = new double[]{.25, .15, .15}[i];
                    present[i] = value(layers[i][0], 30 * sv, z * sh) * value(layers[i][1], x * sh, z * sh)
                            * value(layers[i][2], x * sh, 30 * sv) > new int[]{230,300,275}[i];
                }
                if (kind > 0 && kind < 4 && present[kind - 1]) {
                    boolean previous = false; for (int i = 0; i < kind - 1; i++) previous |= present[i];
                    if (!previous) { sample = new BlockPos(x, 30, z); break; }
                }
                if (kind == 4 && !present[0] && !present[1] && !present[2] && value(asbestos, x * .01, z * .01) > 2.05) {
                    sample = new BlockPos(x, 30, z); break;
                }
            }
            h.assertTrue(sample != null, "No fixture for source stratum " + names[kind]);
            // Avoid normal terrain at Y30: the GameTest world's terrain is below -59.
            h.getLevel().setBlock(sample, Blocks.STONE.defaultBlockState(), 2);
            h.getLevel().setBlock(sample.east(), Blocks.OBSIDIAN.defaultBlockState(), 2);
            h.getLevel().setBlock(sample.west(), Blocks.AIR.defaultBlockState(), 2);
            var origin = new BlockPos(sample.getX(), 0, sample.getZ());
            for (int repeat = 0; repeat < 2; repeat++) {
                h.getLevel().setBlock(sample, Blocks.STONE.defaultBlockState(), 2);
                feature.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), RandomSource.create(5687), origin);
                h.assertTrue(h.getLevel().getBlockState(sample).is(RawMaterials.block(names[kind])), "Wrong source layer ordering/yield: " + names[kind]);
                h.assertTrue(h.getLevel().getBlockState(sample.east()).is(Blocks.OBSIDIAN), "Geology replaced a non-host block");
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "raw_regional")
    public static void regionalDepositsGenerateAtTheirSourceLocations(GameTestHelper h) {
        var seeded = new Random(h.getLevel().getSeed() + 5);
        int colX = (int)(seeded.nextGaussian() * 1500), colZ = (int)(seeded.nextGaussian() * 1500);
        var centers = List.of(new BlockPos(Math.floorDiv(colX, 16) * 16, 0, Math.floorDiv(colZ, 16) * 16),
                new BlockPos(-416, 0, -416), new BlockPos(-1024, 0, -1024));
        var feature = Objects.requireNonNull(h.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE)
                .get(ResourceLocation.fromNamespaceAndPath("hbm", "regional_resources")));
        for (int region = 0; region < 3; region++) {
            var origin = centers.get(region);
            for (int x = -8; x < 24; x++) for (int z = -8; z < 24; z++) for (int y = 12; y <= 44; y++)
                h.getLevel().setBlock(origin.offset(x, y, z), (y == 13 ? Blocks.OBSIDIAN : Blocks.STONE).defaultBlockState(), 2);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                Heightmap.primeHeightmaps(h.getLevel().getChunk((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z), EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
            var random = RandomSource.create(5687);
            for (int run = 0; run < 12; run++) feature.place(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), random, origin);
            int coltan = 0, australium = 0;
            for (int x = -8; x < 24; x++) for (int z = -8; z < 24; z++) for (int y = 12; y <= 44; y++) {
                var state = h.getLevel().getBlockState(origin.offset(x, y, z));
                if (state.is(RawMaterials.block("ore_coltan"))) coltan++;
                if (state.is(RawMaterials.block("ore_australium"))) australium++;
                if (y == 13) h.assertTrue(state.is(Blocks.OBSIDIAN), "Regional ore changed non-host rock");
            }
            if (region == 0) h.assertTrue(coltan > 0, "No coltan in world-seed deposit center");
            if (region == 1) h.assertTrue(australium > 0, "No australium in original fixed region");
            if (region == 2) h.assertTrue(australium == 0, "Australium leaked outside its source region");
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "raw_floor")
    public static void depthDepositAdaptsToModernFloorWithoutOpeningVoid(GameTestHelper h) {
        int bottom = h.getLevel().getMinBuildHeight(); var origin = new BlockPos(2048, bottom, 2048);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int y = 0; y < 9; y++)
            h.getLevel().setBlock(origin.offset(x, y, z), (y == 0 ? Blocks.BEDROCK : Blocks.DEEPSLATE).defaultBlockState(), 2);
        h.assertTrue(RawWorldgen.deposit(h.getLevel(), RandomSource.create(5687), origin.offset(8, 1, 8), 5, .8, "ore_depth_zirconium", false) > 0, "No bottom-anchored depth deposit");
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
            h.assertTrue(h.getLevel().getBlockState(origin.offset(x,0,z)).is(Blocks.BEDROCK), "Depth deposit opened the bottom bedrock layer");
        h.succeed();
    }

    static SimplexNoise[] noise(long seed, int octaves) {
        var random = RandomSource.create(seed); var array = new SimplexNoise[octaves];
        for (int i = 0; i < octaves; i++) array[i] = new SimplexNoise(random); return array;
    }
    static double value(SimplexNoise[] octaves, double x, double z) {
        double result = 0, scale = 1;
        for (var octave : octaves) { result += octave.getValue(x * scale, z * scale) / scale; scale /= 2; }
        return result;
    }
}
