package com.hbm.port.world;

import com.hbm.port.material.RawMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Random;

/** X5687 SchistStratum, OreLayer3D, DepthDeposit and regional HbmWorldGen deposits.
 * Native features run during chunk generation, with no runtime chunk-load rewriting.
 */
public final class RawWorldgen {
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, "hbm");
    static {
        FEATURES.register("geological_layers", Geology::new);
        FEATURES.register("regional_resources", Regional::new);
        FEATURES.register("depth_overworld", () -> new Depth(false));
        FEATURES.register("depth_nether", () -> new Depth(true));
    }

    /** Legacy Perlin's octave sum: frequency halves while amplitude doubles. */
    private static final class Noise {
        private final SimplexNoise[] octaves;
        Noise(long seed, int count) {
            var random = RandomSource.create(seed);
            octaves = new SimplexNoise[count];
            for (int i = 0; i < count; i++) octaves[i] = new SimplexNoise(random);
        }
        double value(double x, double z) {
            double sum = 0, frequency = 1;
            for (var octave : octaves) {
                sum += octave.getValue(x * frequency, z * frequency) / frequency;
                frequency /= 2;
            }
            return sum;
        }
    }

    private record Layer(Noise x, Noise y, Noise z, double scaleH, double scaleV, double threshold, String block) {
        Layer(long seed, int id, double scaleH, double scaleV, double threshold, String block) {
            this(new Noise(seed + 101 + id, 4), new Noise(seed + 102 + id, 4), new Noise(seed + 103 + id, 4),
                    scaleH, scaleV, threshold, block);
        }
    }
    private record GeologyNoise(long seed, Noise gneiss, Noise asbestos, Layer[] layers) {
        GeologyNoise(long seed) {
            this(seed, new Noise(seed, 4), new Noise(seed + 31L * "hbm:stone_resource_asbestos".hashCode() + 25, 2), new Layer[] {
                    new Layer(seed, 0, .04, .25, 230, "stone_resource_hematite"),
                    new Layer(seed, 1, .03, .15, 300, "stone_resource_bauxite"),
                    new Layer(seed, 2, .10, .15, 275, "stone_resource_malachite")});
        }
    }

    public static final class Geology extends Feature<NoneFeatureConfiguration> {
        private volatile GeologyNoise cached;
        Geology() { super(NoneFeatureConfiguration.CODEC); }
        private GeologyNoise noise(long seed) {
            var snapshot = cached;
            if (snapshot == null || snapshot.seed() != seed) {
                snapshot = new GeologyNoise(seed);
                cached = snapshot;
            }
            // All noise objects are immutable. Parallel worlds/chunks use their own snapshot.
            return snapshot;
        }
        @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            var level = context.level();
            var noise = noise(level.getSeed());
            var origin = context.origin();
            var pos = new BlockPos.MutableBlockPos();
            int changed = 0;
            for (int x = origin.getX(); x < origin.getX() + 16; x++) for (int z = origin.getZ(); z < origin.getZ() + 16; z++) {
                double n = noise.gneiss().value(x * .01, z * .01);
                if (n <= 5) continue;
                int range = (int) ((n - 5) * 3);
                if (range > 4) range = 8 - range;
                if (range < 0) continue;
                for (int y = 30 - range; y <= 30 + range; y++) {
                    pos.set(x, y, z);
                    changed += replace(level, pos, Blocks.STONE, "stone_gneiss");
                }
            }
            for (var layer : noise.layers()) {
                double[][] nx = new double[16][65], nz = new double[16][65];
                for (int o = 0; o < 16; o++) for (int y = 6; y <= 64; y++) {
                    nx[o][y] = layer.x().value(y * layer.scaleV(), (origin.getZ() + o) * layer.scaleH());
                    nz[o][y] = layer.z().value((origin.getX() + o) * layer.scaleH(), y * layer.scaleV());
                }
                for (int ox = 0; ox < 16; ox++) for (int oz = 0; oz < 16; oz++) {
                    int x = origin.getX() + ox, z = origin.getZ() + oz;
                    double ny = layer.y().value(x * layer.scaleH(), z * layer.scaleH());
                    for (int y = 64; y > 5; y--) if (nx[oz][y] * ny * nz[ox][y] > layer.threshold()) {
                        pos.set(x, y, z);
                        changed += replace(level, pos, Blocks.STONE, layer.block());
                    }
                }
            }
            changed += asbestosCave(context, noise.asbestos());
            return changed > 0;
        }
        private int asbestosCave(FeaturePlaceContext<NoneFeatureConfiguration> context, Noise noise) {
            var level = context.level(); var origin = context.origin(); var random = context.random();
            var pos = new BlockPos.MutableBlockPos(); int changed = 0;
            for (int x = origin.getX(); x < origin.getX() + 16; x++) for (int z = origin.getZ(); z < origin.getZ() + 16; z++) {
                double n = noise.value(x * .01, z * .01);
                if (n <= 1.75) continue;
                int range = (int) ((n - 1.75) * 20);
                if (range > 20) range = 40 - range;
                if (range < 0) continue;
                for (int y = 25 - range; y <= 25 + range; y++) {
                    pos.set(x, y, z); var state = level.getBlockState(pos);
                    if (state.is(Blocks.STONE)) {
                        for (var side : Direction.values()) {
                            var neighbor = level.getBlockState(pos.relative(side));
                            if (neighbor.isAir() || neighbor.is(RawMaterials.block("stalactite_asbestos")) || neighbor.is(RawMaterials.block("stalagmite_asbestos"))) {
                                changed += replace(level, pos, Blocks.STONE, "stone_resource_asbestos"); break;
                            }
                        }
                    } else if ((state.isAir() || !state.isSolidRender(level, pos)) && state.getFluidState().isEmpty() && random.nextInt(5) == 0) {
                        for (String name : new String[]{"stalactite_asbestos", "stalagmite_asbestos"}) {
                            var formation = RawMaterials.block(name).defaultBlockState();
                            if (formation.canSurvive(level, pos) && level.ensureCanWrite(pos)) {
                                level.setBlock(pos, formation, 2); changed++; break;
                            }
                        }
                    }
                }
            }
            return changed;
        }
    }

    public static final class Depth extends Feature<NoneFeatureConfiguration> {
        private final boolean nether;
        Depth(boolean nether) { super(NoneFeatureConfiguration.CODEC); this.nether = nether; }
        @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            var level = context.level();
            int changed = 0;
            if (nether) {
                changed += maybeDeposit(context, 0, 7, .6, "ore_depth_nether_neodymium", 16);
                changed += maybeDeposit(context, 125, 7, .6, "ore_depth_nether_neodymium", 16);
            } else {
                // In 1.21.1 the bedrock floor is -64. Move only depth deposits with it;
                // ordinary ore bands retain their original absolute Y ranges.
                int bottom = level.getMinBuildHeight();
                changed += maybeDeposit(context, bottom, 5, .6, "cluster_depth_iron", 24);
                changed += maybeDeposit(context, bottom, 5, .6, "cluster_depth_titanium", 32);
                changed += maybeDeposit(context, bottom, 5, .6, "cluster_depth_tungsten", 32);
                changed += maybeDeposit(context, bottom, 5, .8, "ore_depth_cinnebar", 16);
                changed += maybeDeposit(context, bottom, 5, .8, "ore_depth_zirconium", 16);
                changed += maybeDeposit(context, bottom, 5, .8, "ore_depth_borax", 16);
            }
            return changed > 0;
        }
        private int maybeDeposit(FeaturePlaceContext<NoneFeatureConfiguration> context, int base, int radius, double fill, String ore, int chance) {
            var random = context.random();
            if (random.nextInt(chance) != 0) return 0;
            var origin = context.origin();
            int x = origin.getX() + random.nextInt(16) + 8;
            int y = base + random.nextInt(3);
            int z = origin.getZ() + random.nextInt(16) + 8;
            return deposit(context.level(), random, new BlockPos(x, y, z), radius, fill, ore, nether);
        }
    }

    /** Kept public for the source-algorithm fixture, independent of rarity rolls. */
    public static int deposit(WorldGenLevel level, RandomSource random, BlockPos center, int radius, double fill, String ore, boolean nether) {
        int changed = 0;
        var pos = new BlockPos.MutableBlockPos();
        int minimum = nether ? 1 : level.getMinBuildHeight() + 1;
        int maximum = nether ? 126 : level.getMaxBuildHeight() - 1;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++)
            for (int y = center.getY() - radius; y <= center.getY() + radius; y++)
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    if (y < minimum || y > maximum) continue;
                    pos.set(x, y, z);
                    var state = level.getBlockState(pos);
                    if (!(state.is(Blocks.BEDROCK) || state.is(nether ? Blocks.NETHERRACK : Blocks.STONE)
                            || (!nether && state.is(Blocks.DEEPSLATE)))) continue;
                    double distance = Math.sqrt(center.distSqr(pos));
                    String result = null;
                    if (distance + random.nextInt(2) < radius * fill) result = ore;
                    else if (distance + random.nextInt(2) <= radius) result = nether ? "stone_depth_nether" : "stone_depth";
                    if (result != null && level.ensureCanWrite(pos)) {
                        level.setBlock(pos, RawMaterials.block(result).defaultBlockState(), 2);
                        changed++;
                    }
                }
        return changed;
    }

    public static final class Regional extends Feature<NoneFeatureConfiguration> {
        Regional() { super(NoneFeatureConfiguration.CODEC); }
        @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
            var seeded = new Random(context.level().getSeed() + 5);
            int colX = (int) (seeded.nextGaussian() * 1500), colZ = (int) (seeded.nextGaussian() * 1500);
            var random = context.random();
            var origin = context.origin();
            boolean generated = false;
            for (int k = 0; k < 2; k++) for (int r = 1; r <= 5; r++) {
                var pos = origin.offset(random.nextInt(16), 15 + random.nextInt(25) - origin.getY(), random.nextInt(16));
                int range = 750 / r;
                if (Math.abs((long) pos.getX() - colX) <= range && Math.abs((long) pos.getZ() - colZ) <= range)
                    generated |= ore(context, pos, "ore_coltan", 4);
            }
            // The original loop rolls its bound on every iteration, not once per chunk.
            for (int k = 0; k < random.nextInt(4); k++) {
                var pos = origin.offset(random.nextInt(16), 15 + random.nextInt(15) - origin.getY(), random.nextInt(16));
                if (pos.getX() >= -450 && pos.getX() <= -350 && pos.getZ() >= -450 && pos.getZ() <= -350)
                    generated |= ore(context, pos, "ore_australium", 50);
            }
            return generated;
        }
        private boolean ore(FeaturePlaceContext<NoneFeatureConfiguration> context, BlockPos pos, String ore, int size) {
            return Feature.ORE.place(new OreConfiguration(new BlockMatchTest(Blocks.STONE), RawMaterials.block(ore).defaultBlockState(), size),
                    context.level(), context.chunkGenerator(), context.random(), pos);
        }
    }

    private static int replace(WorldGenLevel level, BlockPos pos, Block target, String result) {
        if (!level.getBlockState(pos).is(target) || !level.ensureCanWrite(pos)) return 0;
        level.setBlock(pos, RawMaterials.block(result).defaultBlockState(), 2);
        return 1;
    }

    private RawWorldgen() {}
}
