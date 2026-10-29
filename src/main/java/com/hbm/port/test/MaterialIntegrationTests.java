package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Collections;
import java.util.List;

/** Server-side tests with the real Create mod loaded, not mock implementations. */
@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class MaterialIntegrationTests {
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void steelPressing(GameTestHelper helper) { press(helper, "steel"); }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void titaniumPressing(GameTestHelper helper) { press(helper, "titanium"); }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void leadPressing(GameTestHelper helper) { press(helper, "lead"); }

    private static Block createBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
    }

    private static void press(GameTestHelper helper, String metal) {
        BlockPos depot = new BlockPos(2, 1, 2);
        helper.setBlock(depot, createBlock("depot"));
        helper.setBlock(new BlockPos(2, 3, 2), createBlock("mechanical_press").defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        helper.setBlock(new BlockPos(1, 3, 2), createBlock("creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.runAfterDelay(5, () -> {
            IItemHandler inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(depot), Direction.UP);
            helper.assertTrue(inventory != null, "Create depot has no item capability");
            ItemStack remainder = inventory.insertItem(0, new ItemStack(HbmPort.MATERIAL_ITEMS.get("ingot_" + metal).get()), false);
            helper.assertTrue(remainder.isEmpty(), "Depot rejected the HBM ingot");
        });
        helper.succeedWhen(() -> {
            IItemHandler inventory = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(depot), Direction.UP);
            helper.assertTrue(inventory != null, "Create depot is missing");
            ItemStack result = inventory.getStackInSlot(0);
            helper.assertTrue(result.is(HbmPort.MATERIAL_ITEMS.get("plate_" + metal).get()) && result.getCount() == 1,
                    "Create press has not produced exactly one HBM " + metal + " plate");
        });
    }

    @GameTest(template = "empty")
    public static void storageRecipesConserveMaterials(GameTestHelper helper) {
        for (String metal : List.of("steel", "titanium", "lead")) {
            var ingot = HbmPort.MATERIAL_ITEMS.get("ingot_" + metal).get();
            var block = HbmPort.MATERIAL_ITEMS.get("block_" + metal).get();
            var manager = helper.getLevel().getRecipeManager();
            var input = CraftingInput.of(3, 3, Collections.nCopies(9, new ItemStack(ingot)));
            var packing = manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).orElseThrow();
            ItemStack packed = packing.value().assemble(input, helper.getLevel().registryAccess());
            helper.assertTrue(packed.is(block) && packed.getCount() == 1, "Packing must use nine ingots");
            var unpackInput = CraftingInput.of(1, 1, List.of(packed));
            var unpacking = manager.getRecipeFor(RecipeType.CRAFTING, unpackInput, helper.getLevel()).orElseThrow();
            ItemStack unpacked = unpacking.value().assemble(unpackInput, helper.getLevel().registryAccess());
            helper.assertTrue(unpacked.is(ingot) && unpacked.getCount() == 9, "Unpacking must return exactly nine ingots");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialBlocksSurviveContraptionCapture(GameTestHelper helper) throws Exception {
        BlockPos bearing = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos materialPos = bearing.above();
        helper.getLevel().setBlockAndUpdate(bearing, createBlock("mechanical_bearing").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        IControlContraption controller = (IControlContraption) helper.getLevel().getBlockEntity(bearing);
        for (String metal : List.of("steel", "titanium", "lead")) {
            Block block = HbmPort.MATERIAL_BLOCKS.get("block_" + metal).get();
            helper.getLevel().setBlockAndUpdate(materialPos, block.defaultBlockState());
            BearingContraption contraption = new BearingContraption(false, Direction.UP);
            helper.assertTrue(contraption.assemble(helper.getLevel(), bearing), "Create could not assemble the HBM block");
            helper.assertTrue(contraption.getBlocks().get(BlockPos.ZERO).state().is(block), "Assembly lost the HBM block state");
            contraption.removeBlocksFromWorld(helper.getLevel(), BlockPos.ZERO);
            helper.assertTrue(helper.getLevel().getBlockState(materialPos).isAir(), "Create did not remove the captured block");
            // Create initializes mounted storage when the assembled structure becomes an entity.
            ControlledContraptionEntity entity = ControlledContraptionEntity.create(helper.getLevel(), controller, contraption);
            entity.setPos(materialPos.getX() + 0.5, materialPos.getY(), materialPos.getZ() + 0.5);
            helper.getLevel().addFreshEntity(entity);
            entity.setAngle(90);
            // Exercise the same NBT path used when a moving contraption is saved and loaded.
            var saved = contraption.writeNBT(helper.getLevel().registryAccess(), false);
            BearingContraption restored = new BearingContraption();
            restored.readNBT(helper.getLevel(), saved, false);
            helper.assertTrue(restored.getBlocks().get(BlockPos.ZERO).state().is(block), "Contraption NBT lost the HBM block");
            entity.discard();
            restored.addBlocksToWorld(helper.getLevel(), new StructureTransform(materialPos, 0, 90, 0));
            helper.assertTrue(helper.getLevel().getBlockState(materialPos).is(block), "Disassembly lost or changed the HBM block");
            helper.getLevel().setBlockAndUpdate(materialPos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }
}
