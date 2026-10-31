package com.hbm.port.test;

import com.hbm.port.material.RawMaterials;
import com.hbm.port.world.RawWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.*;
import java.util.stream.Stream;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class RawMaterialTests {
    static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("hbm", name); }
    static ItemStack stack(String name) { return new ItemStack(RawMaterials.item(name)); }
    static List<ItemStack> drops(GameTestHelper h, String ore, ItemStack tool) {
        return Block.getDrops(RawMaterials.block(ore).defaultBlockState(), h.getLevel(), h.absolutePos(new BlockPos(2, 2, 2)), null, null, tool);
    }

    @GameTest(template = "empty")
    public static void catalogueAndBiomesLoadWithoutCrossDimensionLeaks(GameTestHelper h) {
        for (var entry : RawMaterials.CATALOG.getAsJsonObject("blocks").entrySet())
            h.assertTrue(RawMaterials.block(entry.getKey()) != Blocks.AIR, "Unregistered resource block " + entry.getKey());
        for (var entry : RawMaterials.CATALOG.getAsJsonObject("items").entrySet())
            h.assertTrue(!stack(entry.getKey()).isEmpty(), "Unregistered resource item " + entry.getKey());
        var biomes = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var keys = Map.of("overworld", Biomes.PLAINS, "nether", Biomes.NETHER_WASTES, "end", Biomes.END_HIGHLANDS);
        for (var value : RawMaterials.CATALOG.getAsJsonArray("veins")) {
            var vein = value.getAsJsonObject(); String name = vein.get("id").getAsString();
            for (var biome : keys.entrySet()) {
                boolean present = biomes.getHolderOrThrow(biome.getValue()).value().getGenerationSettings().features().stream()
                        .flatMap(x -> x.stream()).anyMatch(x -> x.unwrapKey().orElseThrow().location().equals(id(name)));
                h.assertTrue(present == biome.getKey().equals(vein.get("dimension").getAsString()), "Wrong dimension for " + name);
            }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void commonTagsFollowSourceAliasesWithoutGenericSpecialOreShortcuts(GameTestHelper h) {
        String[][] pairs = {{"lithium", "ingots/lithium"}, {"ingot_phosphorus", "ingots/white_phosphorus"},
                {"powder_fire", "dusts/red_phosphorus"}, {"fragment_cobalt", "nuggets/cobalt"},
                {"fragment_neodymium", "nuggets/neodymium"}, {"chunk_ore_rare", "ingots/rare_earth"},
                {"chunk_ore_cryolite", "crystals/cryolite"}, {"ore_depth_borax", "ores/borax"},
                {"stone_resource_sulfur", "ores/sulfur"}, {"stone_resource_bauxite", "gems/bauxite"}};
        for (var pair : pairs) h.assertTrue(stack(pair[0]).is(net.minecraft.tags.TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", pair[1]))), "Missing source material alias " + Arrays.toString(pair));
        for (var pair : new String[][]{{"ore_nether_coal", "ores/coal"}, {"ore_nether_fire", "ores/phosphorus"}, {"ore_tikite", "ores"}})
            h.assertTrue(!stack(pair[0]).is(net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", pair[1]))),
                    "Special ore accidentally acquired a generic foreign processing route");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void everyVeinHasItsSourceCountHeightAndHostRock(GameTestHelper h) {
        var placed = h.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (var value : RawMaterials.CATALOG.getAsJsonArray("veins")) {
            var spec = value.getAsJsonObject(); var feature = Objects.requireNonNull(placed.get(id(spec.get("id").getAsString())));
            var ore = (OreConfiguration) feature.feature().value().config();
            h.assertTrue(ore.size == spec.get("size").getAsInt(), "Wrong vein size");
            var host = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(spec.get("target").getAsString()));
            h.assertTrue(ore.targetStates.getFirst().target.test(host.defaultBlockState(), RandomSource.create(1)), "Wrong host rock");
            int occurrences = 0;
            var random = RandomSource.create(5687);
            for (int trial = 0; trial < 1000; trial++) {
                var context = new PlacementContext(h.getLevel(), h.getLevel().getChunkSource().getGenerator(), Optional.of(feature));
                Stream<BlockPos> positions = Stream.of(BlockPos.ZERO);
                for (var modifier : feature.placement()) if (!(modifier instanceof BiomeFilter))
                    positions = positions.flatMap(p -> modifier.getPositions(context, random, p));
                var samples = positions.toList(); occurrences += samples.size();
                if (spec.get("rarity").isJsonNull()) h.assertTrue(samples.size() == spec.get("count").getAsInt(), "Wrong per-chunk frequency");
                for (var pos : samples) h.assertTrue(pos.getY() >= spec.get("min_y").getAsInt() && pos.getY() <= spec.get("max_y").getAsInt(), "Wrong absolute Y range");
            }
            h.assertTrue(occurrences > 0, "Rarity filter never generated a candidate");
        }
        h.succeed();
    }

    @GameTest(template = "ore_test", batch = "raw_worldgen")
    public static void allNativeOreFeaturesActuallyReplaceTheirHost(GameTestHelper h) {
        var level = h.getLevel(); var features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        var anchor = h.absolutePos(new BlockPos(8, 8, 8));
        for (var value : RawMaterials.CATALOG.getAsJsonArray("veins")) {
            var spec = value.getAsJsonObject(); String name = spec.get("id").getAsString();
            var target = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(spec.get("target").getAsString()));
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++)
                level.setBlock(h.absolutePos(new BlockPos(x, y, z)), (y == 4 ? Blocks.OBSIDIAN : target).defaultBlockState(), 2);
            var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
            for (int x : new int[]{0, 15}) for (int z : new int[]{0, 15}) chunks.add(new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(x, 0, z))));
            for (var chunk : chunks) Heightmap.primeHeightmaps(level.getChunk(chunk.x, chunk.z), EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
            var feature = Objects.requireNonNull(features.get(id(name)));
            for (int attempt = 0; attempt < 12; attempt++) feature.place(level, level.getChunkSource().getGenerator(), RandomSource.create(5687 + attempt), anchor);
            int found = 0;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                var state = level.getBlockState(h.absolutePos(new BlockPos(x, y, z)));
                if (state.is(RawMaterials.block(name))) found++;
                if (y == 4) h.assertTrue(state.is(Blocks.OBSIDIAN), "Ore replaced a non-host block: " + name);
            }
            h.assertTrue(found > 0, "Actual ore feature produced no " + name);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void originalDropsSilkTouchAndFortune(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var silk = new ItemStack(Items.DIAMOND_PICKAXE); silk.enchant(registry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        var fortune = new ItemStack(Items.DIAMOND_PICKAXE); fortune.enchant(registry.getHolderOrThrow(Enchantments.FORTUNE), 3);
        Object[][] cases = {{"ore_sulfur", "sulfur", 2, 4}, {"ore_niter", "niter", 2, 4}, {"ore_fluorite", "fluorite", 2, 4},
                {"ore_nether_sulfur", "sulfur", 2, 4}, {"ore_rare", "chunk_ore_rare", 1, 1}, {"ore_gneiss_rare", "chunk_ore_rare", 1, 1},
                {"ore_lignite", "lignite", 1, 1}, {"ore_asbestos", "ingot_asbestos", 1, 1}, {"ore_cinnebar", "cinnebar", 1, 1},
                {"ore_cobalt", "fragment_cobalt", 4, 9}, {"ore_nether_cobalt", "fragment_cobalt", 5, 12},
                {"ore_coltan", "fragment_coltan", 1, 1}, {"cluster_iron", "crystal_iron", 1, 1}, {"cluster_aluminium", "crystal_aluminium", 1, 1},
                {"ore_nether_coal", "coal_infernal", 1, 1}, {"ore_depth_cinnebar", "cinnebar", 2, 4}, {"ore_depth_zirconium", "nugget_zirconium", 2, 3}};
        for (var spec : cases) for (int run = 0; run < 30; run++) {
            String ore = (String) spec[0], item = (String) spec[1]; int min = (int) spec[2], max = (int) spec[3];
            var plain = drops(h, ore, new ItemStack(Items.DIAMOND_PICKAXE));
            h.assertTrue(plain.size() == 1 && plain.getFirst().is(RawMaterials.item(item)) && plain.getFirst().getCount() >= min && plain.getFirst().getCount() <= max, "Wrong plain drop " + ore);
            var lucky = drops(h, ore, fortune).getFirst();
            boolean fixed = ore.startsWith("cluster_") || ore.equals("ore_nether_coal");
            h.assertTrue(lucky.getCount() >= min && lucky.getCount() <= max * (fixed ? 1 : 4), "Wrong Fortune bound " + ore);
            var silky = drops(h, ore, silk);
            h.assertTrue(silky.size() == 1 && silky.getFirst().is(RawMaterials.block(ore).asItem()) && silky.getFirst().getCount() == 1, "Silk Touch lost original ore " + ore);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void specialDistributionsAndDepthMiningRules(GameTestHelper h) {
        var registry = h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        var tool = new ItemStack(Items.DIAMOND_PICKAXE); tool.enchant(registry.getHolderOrThrow(Enchantments.FORTUNE), 3);
        int white = 0, red = 0;
        for (int i = 0; i < 500; i++) {
            var fire = drops(h, "ore_nether_fire", tool).getFirst();
            if (fire.is(RawMaterials.item("ingot_phosphorus"))) white++; else if (fire.is(RawMaterials.item("powder_fire"))) red++; else throw new AssertionError("Unknown phosphorus drop");
            h.assertTrue(fire.getCount() >= 1 && fire.getCount() <= 4, "Wrong phosphorus Fortune multiplier");
            int malachite = drops(h, "stone_resource_malachite", tool).getFirst().getCount();
            h.assertTrue(malachite >= 6 && malachite <= 10, "Malachite must use 3 + fortune + nextInt(fortune+2)");
            int alexandrite = drops(h, "ore_alexandrite", tool).getFirst().getCount();
            h.assertTrue(alexandrite >= 1 && alexandrite <= 2, "Alexandrite Fortune cap must be two");
        }
        h.assertTrue(white > 15 && white < 90 && white + red == 500, "Phosphorus 10% branch is absent or biased");
        var player = h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        for (String name : List.of("stone_depth", "stone_depth_nether", "ore_depth_zirconium", "ore_alexandrite")) {
            var state = RawMaterials.block(name).defaultBlockState();
            h.assertTrue(state.getDestroyProgress(player, h.getLevel(), h.absolutePos(new BlockPos(2,2,2))) == 0, "Ordinary pickaxe bypassed depth-rock restriction");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 280)
    public static void actualFurnacesPreserveAluminiumCryoliteAndCrystalYield(GameTestHelper h) {
        String[] sources = {"ore_aluminium", "crystal_aluminium", "ore_uranium"};
        String[] outputs = {"chunk_ore_cryolite", "ingot_aluminium", "ingot_uranium"};
        for (int i = 0; i < sources.length; i++) {
            var pos = new BlockPos(i + 1, 1, 2); h.setBlock(pos, Blocks.FURNACE);
            var furnace = (AbstractFurnaceBlockEntity) h.getBlockEntity(pos); furnace.setItem(0, stack(sources[i])); furnace.setItem(1, new ItemStack(Items.COAL));
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < sources.length; i++) {
                var furnace = (AbstractFurnaceBlockEntity) h.getBlockEntity(new BlockPos(i + 1, 1, 2));
                var result = furnace.getItem(2);
                h.assertTrue(result.is(RawMaterials.item(outputs[i])) && result.getCount() == (i == 1 ? 2 : 1), "Wrong actual furnace yield " + sources[i]);
                h.assertTrue(furnace.getItem(0).isEmpty(), "Furnace did not consume input");
            }
        });
    }

    @GameTest(template = "empty")
    public static void deferredChemistryDoesNotBecomeFreeSmelting(GameTestHelper h) {
        for (String name : List.of("fragment_coltan", "fragment_cobalt", "chunk_ore_rare", "chunk_ore_cryolite", "ore_tikite", "stone_resource_bauxite"))
            h.assertTrue(h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack(name)), h.getLevel()).isEmpty(), "Invented smelting shortcut for " + name);
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack("ore_thorium")), h.getLevel()).orElseThrow().value();
        h.assertTrue(recipe.getResultItem(h.getLevel().registryAccess()).is(RawMaterials.item("ingot_th232")) && recipe.getExperience() == 3, "Wrong thorium product/XP");
        h.succeed();
    }

    static ItemStack craft(GameTestHelper h, int width, int height, String input, int count) {
        var stacks = new ArrayList<ItemStack>(); for (int i = 0; i < count; i++) stacks.add(stack(input));
        var crafting = CraftingInput.of(width, height, stacks);
        return h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, crafting, h.getLevel()).orElseThrow().value().assemble(crafting, h.getLevel().registryAccess());
    }
    @GameTest(template = "empty")
    public static void mineralRoundTripsConserveUnits(GameTestHelper h) {
        for (String metal : List.of("uranium", "beryllium", "australium", "cobalt", "zirconium")) {
            var block = craft(h, 3, 3, "ingot_" + metal, 9);
            h.assertTrue(block.is(RawMaterials.block("block_" + metal).asItem()) && block.getCount() == 1, "9:1 compression failed");
            var back = craft(h, 1, 1, "block_" + metal, 1);
            h.assertTrue(back.is(RawMaterials.item("ingot_" + metal)) && back.getCount() == 9, "Compression duplicated/lost metal");
            var billets = craft(h, 2, 1, "ingot_" + metal, 2);
            h.assertTrue(billets.is(RawMaterials.item("billet_" + metal)) && billets.getCount() == 3, "Two ingots must produce three billets");
            var nuggets = craft(h, 1, 1, "billet_" + metal, 1);
            h.assertTrue(nuggets.is(RawMaterials.item("nugget_" + metal)) && nuggets.getCount() == 6, "One billet must produce six nuggets");
            var ingots = craft(h, 3, 1, "billet_" + metal, 3);
            h.assertTrue(ingots.is(RawMaterials.item("ingot_" + metal)) && ingots.getCount() == 2, "Billet round trip lost material");
        }
        for (String carbon : List.of("coal", "charcoal")) {
            var input = CraftingInput.of(3, 1, List.of(stack("sulfur"), stack("niter"), new ItemStack(carbon.equals("coal") ? Items.COAL : Items.CHARCOAL)));
            var powder = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow().value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(powder.is(Items.GUNPOWDER) && powder.getCount() == 3, "Wrong direct gunpowder recipe");
        }
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void naturalFuelsWorkInActualVanillaFurnaces(GameTestHelper h) {
        String[] fuels = {"lignite", "coal_infernal", "powder_fire"}; int[] ticks = {1200, 4800, 6400};
        for (int i = 0; i < fuels.length; i++) {
            h.assertTrue(stack(fuels[i]).getBurnTime(RecipeType.SMELTING) == ticks[i], "Wrong original fuel duration");
            var pos = new BlockPos(i + 1, 1, 2); h.setBlock(pos, Blocks.FURNACE);
            var furnace = (AbstractFurnaceBlockEntity) h.getBlockEntity(pos); furnace.setItem(0, new ItemStack(Items.RAW_IRON)); furnace.setItem(1, stack(fuels[i]));
        }
        h.succeedWhen(() -> {
            for (int i = 0; i < fuels.length; i++) {
                var furnace = (AbstractFurnaceBlockEntity) h.getBlockEntity(new BlockPos(i + 1, 1, 2));
                h.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT) && furnace.getItem(1).isEmpty(), "Natural fuel did not smelt ore");
            }
        });
    }

    @GameTest(template = "empty")
    public static void ironFurnaceAcceptsNewFuelAndProtectsDoubleCrystalOutput(GameTestHelper h) {
        var pos = new BlockPos(2,1,2);
        var block = com.hbm.port.HbmPort.IRON_FURNACE.get();
        var state = block.defaultBlockState().setValue(com.hbm.port.machine.IronFurnaceBlock.FACING, net.minecraft.core.Direction.SOUTH);
        h.setBlock(pos, state); block.setPlacedBy(h.getLevel(), h.absolutePos(pos), state, null, ItemStack.EMPTY);
        var furnace = (com.hbm.port.machine.IronFurnaceBlockEntity) h.getBlockEntity(pos);
        furnace.setItem(0, stack("crystal_aluminium")); furnace.setItem(1, stack("powder_fire"));
        furnace.setItem(3, new ItemStack(RawMaterials.item("ingot_aluminium"), 63));
        for (int tick = 0; tick < 180; tick++) com.hbm.port.machine.IronFurnaceBlockEntity.tick(h.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
        h.assertTrue(furnace.getItem(0).getCount() == 1 && furnace.getItem(3).getCount() == 63, "Insufficient output space consumed a crystal or overflowed its yield");
        furnace.setItem(3, new ItemStack(RawMaterials.item("ingot_aluminium"), 62));
        for (int tick = 0; tick < 160; tick++) com.hbm.port.machine.IronFurnaceBlockEntity.tick(h.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
        h.assertTrue(furnace.getItem(0).isEmpty() && furnace.getItem(3).getCount() == 64 && furnace.getItem(1).isEmpty(), "HBM furnace failed new fuel/double yield");
        h.succeed();
    }

    @GameTest(template = "ore_test", batch = "raw_depth")
    public static void depthDepositsKeepShellHostRestrictionAndDeterminism(GameTestHelper h) {
        var level = h.getLevel(); var center = h.absolutePos(new BlockPos(8, 8, 8));
        Set<BlockPos> previous = null;
        for (int trial = 0; trial < 2; trial++) {
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++)
                level.setBlock(h.absolutePos(new BlockPos(x,y,z)), (y == 4 ? Blocks.OBSIDIAN : Blocks.DEEPSLATE).defaultBlockState(), 2);
            h.assertTrue(RawWorldgen.deposit(level, RandomSource.create(5687), center, 5, .8, "ore_depth_borax", false) > 0, "Depth deposit did not generate");
            var found = new HashSet<BlockPos>(); int shell = 0;
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                var pos = new BlockPos(x,y,z); var state = level.getBlockState(h.absolutePos(pos));
                if (y == 4) h.assertTrue(state.is(Blocks.OBSIDIAN), "Depth generation changed non-host rock");
                if (state.is(RawMaterials.block("ore_depth_borax"))) found.add(pos);
                if (state.is(RawMaterials.block("stone_depth"))) shell++;
            }
            h.assertTrue(found.size() > 0 && shell > 0, "Missing ore core or depth-rock shell");
            if (previous != null) h.assertTrue(found.equals(previous), "Depth feature differs for same seed and host terrain");
            previous = found;
        }
        h.succeed();
    }
}
