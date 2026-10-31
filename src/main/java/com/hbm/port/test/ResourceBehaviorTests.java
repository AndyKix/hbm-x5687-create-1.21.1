package com.hbm.port.test;

import com.hbm.port.material.DepthRockTool;
import com.hbm.port.material.RawMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid = "hbm", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public final class ResourceBehaviorTests {
    @net.neoforged.bus.api.SubscribeEvent
    public static void registerTestTools(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (Boolean.getBoolean("hbm.resourceTestTools")) event.register(net.minecraft.core.registries.Registries.ITEM, helper -> {
            helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hbm", "proof_depth_tool_allowed"), new ProofTool(true));
            helper.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hbm", "proof_depth_tool_denied"), new ProofTool(false));
        });
    }
    private static final class ProofTool extends Item implements DepthRockTool {
        private final boolean allowed;
        ProofTool(boolean allowed) { super(new Properties()); this.allowed = allowed; }
        @Override public boolean canBreakRock(BlockGetter level, Player player, ItemStack tool, BlockState rock, BlockPos pos) { return allowed; }
    }
    @GameTest(template = "empty")
    public static void depthToolContractPreservesFiftyTickMining(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL); var pos = h.absolutePos(new BlockPos(2,2,2));
        var state = RawMaterials.block("ore_depth_zirconium").defaultBlockState();
        for (var tool : new Item[]{Items.NETHERITE_PICKAXE,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hbm", "proof_depth_tool_denied")),
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hbm", "proof_depth_tool_allowed"))}) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
            float progress = state.getDestroyProgress(player, h.getLevel(), pos);
            h.assertTrue(progress == (tool instanceof ProofTool proof && proof.allowed ? 1F / 50F : 0), "Depth-rock tool contract differs from X5687");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 30)
    public static void caveFormationsDropPowderAndRequireCorrectSupport(GameTestHelper h) {
        var floor = new BlockPos(1,2,2); var ceiling = new BlockPos(3,2,2);
        h.setBlock(floor.below(), Blocks.STONE); h.setBlock(ceiling.above(), Blocks.STONE);
        h.setBlock(floor, RawMaterials.block("stalagmite_asbestos")); h.setBlock(ceiling, RawMaterials.block("stalactite_asbestos"));
        for (var pos : new BlockPos[]{floor, ceiling}) {
            var state = h.getLevel().getBlockState(h.absolutePos(pos));
            h.assertTrue(state.canSurvive(h.getLevel(), h.absolutePos(pos)), "Cave formation rejected its support");
            var drops = Block.getDrops(state, h.getLevel(), h.absolutePos(pos), null, null, new ItemStack(Items.IRON_PICKAXE));
            h.assertTrue(drops.size() == 1 && drops.getFirst().is(RawMaterials.item("powder_asbestos")) && drops.getFirst().getCount() == 1, "Cave formation must drop one asbestos powder");
            h.assertTrue(state.getCollisionShape(h.getLevel(), h.absolutePos(pos)).isEmpty(), "Cave formation gained a solid collision box");
        }
        h.setBlock(floor.below(), Blocks.AIR); h.setBlock(ceiling.above(), Blocks.AIR);
        h.runAfterDelay(4, () -> { h.assertBlockPresent(Blocks.AIR, floor); h.assertBlockPresent(Blocks.AIR, ceiling); h.succeed(); });
    }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void redPhosphorusFallsAndInfernalCoalIgnitesContact(GameTestHelper h) {
        var pos = new BlockPos(2,4,2); h.setBlock(new BlockPos(2,0,2), Blocks.STONE); h.setBlock(pos, RawMaterials.block("block_red_phosphorus"));
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        RawMaterials.block("ore_nether_coal").stepOn(h.getLevel(), h.absolutePos(pos), RawMaterials.block("ore_nether_coal").defaultBlockState(), player);
        h.assertTrue(player.getRemainingFireTicks() == 60, "Infernal coal must ignite contact for three seconds");
        h.succeedWhen(() -> h.assertBlockPresent(RawMaterials.block("block_red_phosphorus"), new BlockPos(2,1,2)));
    }
    @GameTest(template = "ore_test", batch = "raw_lithium")
    public static void lithiumReactsToWaterOnPlacementAndNeighborChange(GameTestHelper h) {
        var pos = new BlockPos(16,5,8);
        for (int trial = 0; trial < 2; trial++) {
            for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++)
                h.setBlock(pos.offset(x,y,z), Blocks.STONE);
            if (trial == 0) {
                h.setBlock(pos.east(), Blocks.WATER); h.setBlock(pos, RawMaterials.block("block_lithium"));
            } else {
                h.setBlock(pos.east(), Blocks.AIR); h.setBlock(pos, RawMaterials.block("block_lithium"));
                h.assertBlockPresent(RawMaterials.block("block_lithium"), pos);
                h.setBlock(pos.east(), Blocks.WATER);
            }
            h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(pos)).is(RawMaterials.block("block_lithium")), "Lithium did not react to adjacent water");
            h.assertTrue(!h.getLevel().getBlockState(h.absolutePos(pos.west())).is(Blocks.STONE), "Lithium disappeared without its explosion");
        }
        h.succeed();
    }
}
