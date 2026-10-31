package com.hbm.port.test;

import com.hbm.port.compat.CrateMountedStorage;
import com.hbm.port.storage.*;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class CrateCreateTests {
    private static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", id)); }
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void ironCrateActuallyRotatesAndRestoresModifiedContents(GameTestHelper h) { rotate(h, "iron"); }
    @GameTest(template = "empty", timeoutTicks = 180)
    public static void steelCrateActuallyRotatesAndRestoresModifiedContents(GameTestHelper h) { rotate(h, "steel"); }
    private static void rotate(GameTestHelper h, String metal) {
        var crate = StorageCrateTests.place(h, metal); int last = crate.getContainerSize() - 1;
        crate.setItem(0, new ItemStack(Items.IRON_INGOT, 3)); crate.setItem(last, StorageCrateTests.namedDiamond());
        h.setBlock(StorageCrateTests.POS.below(), block("mechanical_bearing").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        h.setBlock(StorageCrateTests.POS.below(2), block("creative_motor").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        var bearing = (MechanicalBearingBlockEntity) h.getBlockEntity(StorageCrateTests.POS.below());
        h.startSequence().thenIdle(5).thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(StorageCrateTests.POS.below(2))).generatedSpeed.setValue(64))
            .thenWaitUntil(() -> h.assertTrue(bearing.isRunning() && bearing.getMovedContraption() != null, "Actual powered bearing did not assemble crate"))
            .thenIdle(25).thenExecute(() -> {
                var entity = bearing.getMovedContraption(); var contraption = entity.getContraption();
                h.assertTrue(bearing.getInterpolatedAngle(1) != 0 && Math.abs(bearing.getSpeed()) >= 32, "Bearing must actually rotate under power");
                h.assertTrue(h.getBlockState(StorageCrateTests.POS).isAir(), "Captured crate must leave world");
                var storage = contraption.getStorage().getAllItems();
                h.assertTrue(storage.getSlots() == last + 1 && storage.getStackInSlot(0).getCount() == 3, "Mounted crate capacity/contents lost");
                h.assertTrue(storage.extractItem(0, 1, false).getCount() == 1, "Mounted extraction failed");
                h.assertTrue(storage.insertItem(1, new ItemStack(Items.COAL, 7), false).isEmpty(), "Mounted insertion failed");
                h.assertTrue(storage.insertItem(2, new ItemStack(CrateContent.ITEMS.get("crate_iron").get()), false).getCount() == 1, "Mounted automation must reject nested portable crate");
                bearing.disassemble();
                var restored = (StorageCrateBlockEntity) h.getBlockEntity(StorageCrateTests.POS);
                h.assertTrue(restored != null && restored.getItem(0).getCount() == 2 && restored.getItem(1).getCount() == 7
                        && ItemStack.matches(restored.getItem(last), StorageCrateTests.namedDiamond()), "Disassembly must use modified mounted contents, not original NBT");
                h.assertItemEntityNotPresent(Items.IRON_INGOT, StorageCrateTests.POS, 3);
                h.assertItemEntityNotPresent(CrateContent.ITEMS.get("crate_" + metal).get(), StorageCrateTests.POS, 3);
            }).thenSucceed();
    }
    @GameTest(template = "empty")
    public static void crateContentsSurviveContraptionNbtAndRotation(GameTestHelper h) throws Exception {
        var bearingPos = h.absolutePos(StorageCrateTests.POS.below());
        h.getLevel().setBlockAndUpdate(bearingPos, block("mechanical_bearing").defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        var controller = (IControlContraption) h.getLevel().getBlockEntity(bearingPos);
        for (String metal : List.of("iron", "steel")) {
            var crate = StorageCrateTests.place(h, metal); int last = crate.getContainerSize() - 1;
            crate.setItem(0, new ItemStack(Items.IRON_INGOT, 11)); crate.setItem(last, StorageCrateTests.namedDiamond());
            StorageCrateTests.name(crate, "Moving X5687");
            var contraption = new BearingContraption(false, Direction.UP);
            h.assertTrue(contraption.assemble(h.getLevel(), bearingPos), "Create refused crate assembly");
            contraption.removeBlocksFromWorld(h.getLevel(), BlockPos.ZERO);
            var entity = ControlledContraptionEntity.create(h.getLevel(), controller, contraption);
            entity.setPos(h.absolutePos(StorageCrateTests.POS).getCenter()); h.getLevel().addFreshEntity(entity);
            var storage = contraption.getStorage().getAllItemStorages().get(BlockPos.ZERO);
            h.assertTrue(storage instanceof CrateMountedStorage, "Crate must register explicit mounted storage type");
            storage.extractItem(0, 2, false); storage.insertItem(1, new ItemStack(Items.GOLD_INGOT, 5), false);
            var saved = contraption.writeNBT(h.getLevel().registryAccess(), false);
            var loaded = new BearingContraption(); loaded.readNBT(h.getLevel(), saved, false);
            var readStorage = loaded.getStorage().getAllItems();
            h.assertTrue(readStorage.getStackInSlot(0).getCount() == 9 && readStorage.getStackInSlot(1).getCount() == 5
                    && ItemStack.matches(readStorage.getStackInSlot(last), StorageCrateTests.namedDiamond()), "Mounted inventory codec lost current contents");
            entity.discard();
            loaded.addBlocksToWorld(h.getLevel(), new StructureTransform(h.absolutePos(StorageCrateTests.POS), 0, 90, 0));
            var restored = (StorageCrateBlockEntity) h.getBlockEntity(StorageCrateTests.POS);
            h.assertTrue(restored.getName().getString().equals("Moving X5687") && restored.getItem(0).getCount() == 9
                    && restored.getItem(1).getCount() == 5 && ItemStack.matches(restored.getItem(last), StorageCrateTests.namedDiamond()), "Rotated NBT restoration lost name or inventory");
            StorageCrateTests.remove(h);
        }
        h.succeed();
    }
}
