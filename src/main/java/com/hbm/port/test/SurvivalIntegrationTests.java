package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class SurvivalIntegrationTests {
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void oresSmeltInVanillaFurnaces(GameTestHelper helper) {
        List<BlockPos> positions = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1),
                new BlockPos(1, 1, 3), new BlockPos(3, 1, 3));
        String[] metals = {"titanium", "lead", "titanium", "lead"};
        for (int i = 0; i < positions.size(); i++) {
            helper.setBlock(positions.get(i), i < 2 ? Blocks.FURNACE : Blocks.BLAST_FURNACE);
            var furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(positions.get(i));
            furnace.setItem(0, new ItemStack(HbmPort.ORES.get("ore_" + metals[i]).get()));
            furnace.setItem(1, new ItemStack(Items.COAL));
        }
        helper.succeedWhen(() -> {
            for (int i = 0; i < positions.size(); i++) {
                var furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(positions.get(i));
                ItemStack result = furnace.getItem(2);
                helper.assertTrue(result.is(HbmPort.MATERIAL_ITEMS.get("ingot_" + metals[i]).get()) && result.getCount() == 1,
                        "Furnace must turn exactly one " + metals[i] + " ore into one ingot");
                helper.assertTrue(furnace.getItem(0).isEmpty(), "Furnace did not consume the ore");
            }
        });
    }

    @GameTest(template = "empty")
    public static void oresKeepLegacyBlockDrops(GameTestHelper helper) {
        for (var holder : HbmPort.ORES.values()) {
            var state = holder.get().defaultBlockState();
            helper.assertTrue(new ItemStack(Items.WOODEN_PICKAXE).isCorrectToolForDrops(state), "X5687 ore must accept a pickaxe");
            helper.assertTrue(!ItemStack.EMPTY.isCorrectToolForDrops(state), "Ore must require a tool");
            var drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 2)), null,
                    null, new ItemStack(Items.WOODEN_PICKAXE));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().is(holder.get().asItem()) && drops.getFirst().getCount() == 1,
                    "Ore must drop exactly its own block, not raw metal");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void heatedCreateMixerMakesSteel(GameTestHelper helper) {
        setupMixer(helper, true, true);
        helper.succeedWhen(() -> {
            var mixer = (KineticBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 2));
            helper.assertTrue(Math.abs(mixer.getSpeed()) >= 32, "Mixer is not receiving sufficient real rotation");
            IItemHandler inventory = basin(helper);
            helper.assertTrue(count(inventory, HbmPort.MATERIAL_ITEMS.get("ingot_steel").get()) == 1,
                    "Heated mixer has not produced one steel ingot");
            helper.assertTrue(count(inventory, Items.IRON_INGOT) == 0 && count(inventory, Items.COAL) == 0,
                    "Mixing must consume one iron ingot and one coal");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void unheatedMixerCannotMakeSteel(GameTestHelper helper) {
        setupMixer(helper, false, true);
        helper.runAfterDelay(180, () -> {
            var mixer = (KineticBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 2));
            helper.assertTrue(Math.abs(mixer.getSpeed()) >= 32, "Cold test must still have real rotation");
            assertIngredientsUnchanged(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 220)
    public static void stoppedMixerCannotMakeSteel(GameTestHelper helper) {
        setupMixer(helper, true, false);
        helper.runAfterDelay(180, () -> {
            var mixer = (KineticBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 2));
            helper.assertTrue(mixer.getSpeed() == 0, "Stopped mixer unexpectedly has rotation");
            assertIngredientsUnchanged(helper);
            helper.succeed();
        });
    }

    private static void assertIngredientsUnchanged(GameTestHelper helper) {
        IItemHandler inventory = basin(helper);
        helper.assertTrue(count(inventory, Items.IRON_INGOT) == 1 && count(inventory, Items.COAL) == 1
                        && count(inventory, HbmPort.MATERIAL_ITEMS.get("ingot_steel").get()) == 0,
                "Steel must not be produced or ingredients consumed without both heat and rotation");
    }

    private static Block createBlock(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id));
    }

    private static IItemHandler basin(GameTestHelper helper) {
        IItemHandler inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(new BlockPos(2, 1, 2)), Direction.UP);
        helper.assertTrue(inventory != null, "Create basin is missing its inventory");
        return inventory;
    }

    private static int count(IItemHandler inventory, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static void setupMixer(GameTestHelper helper, boolean heated, boolean rotating) {
        helper.setBlock(new BlockPos(2, 0, 2), createBlock("blaze_burner").defaultBlockState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        helper.setBlock(new BlockPos(2, 1, 2), createBlock("basin"));
        helper.setBlock(new BlockPos(2, 3, 2), createBlock("mechanical_mixer"));
        if (rotating) {
            helper.setBlock(new BlockPos(1, 3, 2), createBlock("cogwheel").defaultBlockState()
                    .setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
            helper.setBlock(new BlockPos(1, 2, 2), createBlock("creative_motor").defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.UP));
        }
        helper.runAfterDelay(5, () -> {
            if (rotating) {
                var motor = (CreativeMotorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 2));
                motor.generatedSpeed.setValue(64);
            }
            if (heated) {
                BlockPos burner = helper.absolutePos(new BlockPos(2, 0, 2));
                ItemStack fuel = new ItemStack(Items.COAL);
                var result = BlazeBurnerBlock.tryInsert(helper.getLevel().getBlockState(burner), helper.getLevel(), burner,
                        fuel, false, false, false);
                helper.assertTrue(result.getResult().consumesAction() && fuel.isEmpty(), "Burner did not accept coal fuel");
            }
            IItemHandler inventory = basin(helper);
            helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "Basin rejected iron");
            helper.assertTrue(inventory.insertItem(1, new ItemStack(Items.COAL), false).isEmpty(), "Basin rejected coal");
        });
    }
}
