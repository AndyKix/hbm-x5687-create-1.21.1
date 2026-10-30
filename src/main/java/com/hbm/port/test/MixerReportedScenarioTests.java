package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/** Reproduces a player dropping both recipes into the same real, rotating mixer. */
@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class MixerReportedScenarioTests {
    @GameTest(template = "empty", timeoutTicks = 850)
    public static void andesiteWorksColdSteelRequiresSeparatelyFueledBurner(GameTestHelper h) {
        BlockPos burner = new BlockPos(2, 0, 2), basin = new BlockPos(2, 1, 2), mixer = new BlockPos(2, 3, 2);
        h.setBlock(burner, block("blaze_burner").defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
        h.setBlock(basin, block("basin")); h.setBlock(mixer, block("mechanical_mixer"));
        h.setBlock(new BlockPos(1, 3, 2), block("cogwheel").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        h.setBlock(new BlockPos(1, 2, 2), block("creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        Item alloy = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "andesite_alloy"));
        Item steel = HbmPort.MATERIAL_ITEMS.get("ingot_steel").get();
        h.startSequence()
            .thenIdle(5)
            .thenExecute(() -> {
                ((CreativeMotorBlockEntity) h.getBlockEntity(new BlockPos(1, 2, 2))).generatedSpeed.setValue(64);
                drop(h, Items.ANDESITE); drop(h, Items.IRON_NUGGET);
            })
            .thenWaitUntil(() -> {
                h.assertTrue(count(inventory(h), alloy) == 1, "Cold mixer has not made andesite alloy from dropped inputs");
                h.assertTrue(h.getBlockState(burner).getValue(BlazeBurnerBlock.HEAT_LEVEL) == BlazeBurnerBlock.HeatLevel.SMOULDERING, "Andesite case must have an unfueled burner");
            })
            .thenExecute(() -> {
                LogUtils.getLogger().info("HBM MIXER DIAGNOSTIC: dropped andesite + iron nugget produced alloy without fueled heat");
                var inv = inventory(h);
                for (int i = 0; i < inv.getSlots(); i++) if (inv.getStackInSlot(i).is(alloy)) inv.extractItem(i, 64, false);
                drop(h, Items.IRON_INGOT); drop(h, Items.COAL);
            })
            .thenIdle(180)
            .thenExecute(() -> {
                var inv = inventory(h);
                h.assertTrue(Math.abs(((KineticBlockEntity) h.getBlockEntity(mixer)).getSpeed()) >= 32, "Same mixer must retain real rotation");
                h.assertTrue(count(inv, Items.IRON_INGOT) == 1 && count(inv, Items.COAL) == 1 && count(inv, steel) == 0,
                        "Iron + coal in basin must remain unchanged without fueled heat");
                LogUtils.getLogger().info("HBM MIXER DIAGNOSTIC: same rotating mixer left iron ingot + basin coal unchanged with unfueled burner");
                BlockPos absolute = h.absolutePos(burner); ItemStack fuel = new ItemStack(Items.COAL);
                var result = BlazeBurnerBlock.tryInsert(h.getLevel().getBlockState(absolute), h.getLevel(), absolute, fuel, false, false, false);
                h.assertTrue(result.getResult().consumesAction() && fuel.isEmpty(), "Separate burner coal must be accepted");
            })
            .thenWaitUntil(() -> {
                var inv = inventory(h);
                h.assertTrue(count(inv, steel) == 1 && count(inv, Items.COAL) == 0 && count(inv, Items.IRON_INGOT) == 0,
                        "Fueling the same burner must produce exactly one steel from the existing basin inputs");
            })
            .thenExecute(() -> LogUtils.getLogger().info("HBM MIXER DIAGNOSTIC PASSED: separately fueling burner enabled one steel without replacing the machine or recipe"))
            .thenSucceed();
    }
    private static void drop(GameTestHelper h, Item item) {
        var entity = h.spawnItem(item, 2.5f, 2.6f, 2.5f);
        entity.setDeltaMovement(0, 0, 0);
    }
    private static Block block(String path) { return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path)); }
    private static IItemHandler inventory(GameTestHelper h) {
        var inv = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(new BlockPos(2, 1, 2)), Direction.UP);
        h.assertTrue(inv != null, "Basin inventory is missing"); return inv;
    }
    private static int count(IItemHandler inv, Item item) {
        int result = 0;
        for (int slot = 0; slot < inv.getSlots(); slot++) if (inv.getStackInSlot(slot).is(item)) result += inv.getStackInSlot(slot).getCount();
        return result;
    }
}
