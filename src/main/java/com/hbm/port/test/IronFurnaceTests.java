package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.hbm.port.machine.IronFurnaceBlock;
import com.hbm.port.machine.IronFurnaceBlockEntity;
import com.hbm.port.machine.IronFurnaceMenu;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class IronFurnaceTests {
    private static final BlockPos CORE = new BlockPos(2, 1, 2);
    private static IronFurnaceBlockEntity assemble(GameTestHelper h, Direction facing) {
        var state = HbmPort.IRON_FURNACE.get().defaultBlockState().setValue(IronFurnaceBlock.FACING, facing);
        h.setBlock(CORE, state);
        HbmPort.IRON_FURNACE.get().setPlacedBy(h.getLevel(), h.absolutePos(CORE), state, null, ItemStack.EMPTY);
        return (IronFurnaceBlockEntity) h.getBlockEntity(CORE);
    }
    private static void tick(GameTestHelper h, IronFurnaceBlockEntity furnace, int count) {
        for (int i = 0; i < count; i++) IronFurnaceBlockEntity.tick(h.getLevel(), furnace.getBlockPos(), furnace.getBlockState(), furnace);
    }
    private static ItemStack ore(int amount) { return new ItemStack(HbmPort.ORES.get("ore_titanium").get(), amount); }
    private static ItemStack ingot(int amount) { return new ItemStack(HbmPort.MATERIAL_ITEMS.get("ingot_titanium").get(), amount); }

    @GameTest(template = "empty")
    public static void placementAndBreakPreserveExactFootprint(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            player.setYRot(facing.getOpposite().toYRot());
            ItemStack stack = new ItemStack(HbmPort.IRON_FURNACE_ITEM.get());
            var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(CORE)), Direction.UP, h.absolutePos(CORE), false);
            var result = HbmPort.IRON_FURNACE_ITEM.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
            h.assertTrue(result.consumesAction() && stack.isEmpty(), "Actual block item must place one machine");
            for (int part = 0; part < 8; part++) {
                BlockPos pos = CORE.offset(IronFurnaceBlock.offset(part, facing));
                var state = h.getBlockState(pos);
                h.assertTrue(state.is(HbmPort.IRON_FURNACE.get()) && state.getValue(IronFurnaceBlock.PART) == part
                        && state.getValue(IronFurnaceBlock.FACING) == facing, "Wrong rotated 2x2x2 footprint");
                h.assertTrue(IronFurnaceBlock.controller(h.getLevel(), h.absolutePos(pos), state) == h.getBlockEntity(CORE), "Part must resolve controller");
            }
            h.getLevel().destroyBlock(h.absolutePos(CORE.offset(IronFurnaceBlock.offset(7, facing))), true);
            for (int part = 0; part < 8; part++) h.assertTrue(h.getBlockState(CORE.offset(IronFurnaceBlock.offset(part, facing))).isAir(), "Breaking a part must dismantle all eight");
        }
        h.assertItemEntityCountIs(HbmPort.IRON_FURNACE_ITEM.get(), CORE, 4, 4);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void blockedPlacementConsumesNothing(GameTestHelper h) {
        h.setBlock(new BlockPos(1, 2, 1), Blocks.OBSIDIAN);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(Direction.NORTH.toYRot());
        ItemStack stack = new ItemStack(HbmPort.IRON_FURNACE_ITEM.get());
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(CORE)), Direction.UP, h.absolutePos(CORE), false);
        var result = HbmPort.IRON_FURNACE_ITEM.get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
        h.assertTrue(!result.consumesAction() && stack.getCount() == 1, "Blocked footprint must not consume machine");
        h.assertTrue(h.getBlockState(CORE).isAir() && h.getBlockState(new BlockPos(1, 2, 1)).is(Blocks.OBSIDIAN), "Placement must not overwrite blocks");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyTimingIdleFuelAndFullOutput(GameTestHelper h) {
        var furnace = assemble(h, Direction.SOUTH);
        furnace.setItem(1, new ItemStack(Items.COAL));
        tick(h, furnace, 20);
        h.assertTrue(furnace.burnTime() == 2000 && furnace.getItem(1).isEmpty() && furnace.progress() == 0, "Legacy coal bonus and idle preload must be retained");
        furnace.setItem(0, ore(2));
        tick(h, furnace, 159);
        h.assertTrue(furnace.getItem(3).isEmpty() && furnace.progress() == 159, "Must not finish before 160 ticks");
        tick(h, furnace, 1);
        h.assertTrue(furnace.getItem(3).getCount() == 1 && furnace.getItem(0).getCount() == 1 && furnace.burnTime() == 1840, "160 ticks must produce exactly one ingot");
        furnace.setItem(3, ingot(64));
        tick(h, furnace, 30);
        h.assertTrue(furnace.burnTime() == 1840 && furnace.progress() == 0 && furnace.getItem(0).getCount() == 1, "Full output must preserve fuel and input");
        furnace.setItem(3, new ItemStack(Items.DIAMOND));
        tick(h, furnace, 30);
        h.assertTrue(furnace.burnTime() == 1840 && furnace.getItem(3).is(Items.DIAMOND), "Incompatible output must not be overwritten");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void twoFuelSlotsAndContainerRemainder(GameTestHelper h) {
        var furnace = assemble(h, Direction.SOUTH);
        furnace.setItem(0, ore(10));
        furnace.setItem(1, new ItemStack(Items.CHARCOAL));
        furnace.setItem(2, new ItemStack(Items.LAVA_BUCKET));
        tick(h, furnace, 1600);
        h.assertTrue(furnace.getItem(3).getCount() == 10 && furnace.burnTime() == 0 && furnace.getItem(2).is(Items.LAVA_BUCKET), "First fuel slot must finish before consuming second");
        tick(h, furnace, 1);
        h.assertTrue(furnace.burnTime() == 20000 && furnace.getItem(2).is(Items.BUCKET), "Second fuel must preload and retain its empty container");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 260)
    public static void activeFurnaceResumesAfterReload(GameTestHelper h) {
        var furnace = assemble(h, Direction.EAST);
        furnace.setItem(0, ore(1));
        furnace.setItem(1, new ItemStack(Items.COAL, 2));
        furnace.setItem(2, new ItemStack(Items.CHARCOAL, 3));
        h.runAfterDelay(60, () -> {
            int progress = furnace.progress(), fuel = furnace.burnTime();
            h.assertTrue(progress > 0 && progress < 160, "Real server must advance furnace before save");
            var saved = furnace.saveWithFullMetadata(h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(furnace.getBlockPos());
            var loaded = (IronFurnaceBlockEntity) BlockEntity.loadStatic(furnace.getBlockPos(), furnace.getBlockState(), saved, h.getLevel().registryAccess());
            h.getLevel().setBlockEntity(loaded);
            IronFurnaceBlock.invalidateParts(h.getLevel(), loaded.getBlockPos(), Direction.EAST);
            h.assertTrue(loaded.progress() == progress && loaded.burnTime() == fuel && loaded.getItem(1).getCount() == 1
                    && loaded.getItem(2).getCount() == 3, "Reload must preserve progress, fuel and both inventories");
            h.succeedWhen(() -> {
                h.assertTrue(loaded.getItem(3).is(ingot(1).getItem()) && loaded.getItem(3).getCount() == 1, "Restored furnace has not produced output");
                h.assertTrue(loaded.getItem(0).isEmpty() && loaded.burnTime() == 1840, "Reload duplicated or restarted processing");
            });
        });
    }

    @GameTest(template = "empty")
    public static void automationUsesAllPartsAndRejectsInvalidSlots(GameTestHelper h) {
        var furnace = assemble(h, Direction.SOUTH);
        for (int part = 0; part < 8; part++) for (Direction side : Direction.values()) {
            var handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(CORE.offset(IronFurnaceBlock.offset(part, Direction.SOUTH))), side);
            h.assertTrue(handler != null && handler.getSlots() == 4, "Every part and side must expose four automation slots");
            h.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1, "Reject non-smeltable input");
            h.assertTrue(handler.insertItem(1, ore(1), false).getCount() == 1, "Reject non-fuel");
            h.assertTrue(handler.insertItem(3, ingot(1), false).getCount() == 1, "Reject output insertion");
        }
        furnace.setItem(0, ore(1)); furnace.setItem(1, new ItemStack(Items.COAL)); furnace.setItem(3, ingot(1));
        var handler = furnace.itemHandler(null);
        h.assertTrue(handler.getSlots() == 4 && handler.extractItem(0, 1, false).isEmpty() && handler.extractItem(1, 1, false).isEmpty(), "Cannot extract input or fuel");
        h.assertTrue(handler.extractItem(3, 1, false).getCount() == 1, "Output must be extractable");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void actualHoppersFeedAndExtractThroughProxy(GameTestHelper h) {
        assemble(h, Direction.SOUTH);
        h.setBlock(new BlockPos(1, 3, 1), Blocks.HOPPER);
        h.setBlock(new BlockPos(1, 0, 1), Blocks.HOPPER);
        var input = (HopperBlockEntity) h.getBlockEntity(new BlockPos(1, 3, 1));
        input.setItem(0, ore(2)); input.setItem(1, new ItemStack(Items.COAL));
        var output = (HopperBlockEntity) h.getBlockEntity(new BlockPos(1, 0, 1));
        h.succeedWhen(() -> {
            int total = 0;
            for (int slot = 0; slot < output.getContainerSize(); slot++) if (output.getItem(slot).is(ingot(1).getItem())) total += output.getItem(slot).getCount();
            h.assertTrue(total == 2 && input.isEmpty(), "Real hoppers must insert ore/fuel and extract exactly two ingots through proxy parts");
        });
    }

    @GameTest(template = "empty")
    public static void breakDropsMachineAndInventoryExactlyOnce(GameTestHelper h) {
        for (int broken = 0; broken < 8; broken++) {
            var furnace = assemble(h, Direction.SOUTH);
            furnace.setItem(0, ore(7)); furnace.setItem(1, new ItemStack(Items.COAL, 3)); furnace.setItem(3, ingot(4));
            h.getLevel().destroyBlock(h.absolutePos(CORE.offset(IronFurnaceBlock.offset(broken, Direction.SOUTH))), true);
        }
        h.assertItemEntityCountIs(HbmPort.IRON_FURNACE_ITEM.get(), CORE, 4, 8);
        h.assertItemEntityCountIs(ore(1).getItem(), CORE, 4, 56);
        h.assertItemEntityCountIs(Items.COAL, CORE, 4, 24);
        h.assertItemEntityCountIs(ingot(1).getItem(), CORE, 4, 32);
        for (int part = 0; part < 8; part++) {
            BlockPos pos = CORE.offset(IronFurnaceBlock.offset(part, Direction.SOUTH));
            h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(pos), Direction.UP) == null, "Removed parts must not expose inventory");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void machineRecipeAndMenuShiftClick(GameTestHelper h) {
        var input = CraftingInput.of(3, 3, List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.FURNACE), new ItemStack(Items.IRON_INGOT),
                new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.STONE_BRICKS), new ItemStack(Items.STONE_BRICKS)));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
        h.assertTrue(recipe.value().assemble(input, h.getLevel().registryAccess()).is(HbmPort.IRON_FURNACE_ITEM.get()), "Original X5687 crafting recipe must be present");
        var furnace = assemble(h, Direction.SOUTH);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var inventory = player.getInventory(); inventory.setItem(9, ore(3)); inventory.setItem(10, new ItemStack(Items.COAL, 2));
        var menu = new IronFurnaceMenu(0, inventory, furnace, furnace.data);
        menu.quickMoveStack(player, 5); menu.quickMoveStack(player, 6);
        h.assertTrue(furnace.getItem(0).getCount() == 3 && furnace.getItem(1).getCount() == 2 && inventory.getItem(9).isEmpty(), "Shift click must route input and fuel");
        h.assertTrue(!menu.getSlot(3).mayPlace(ore(1)) && !menu.getSlot(4).isActive(), "Output must reject input; upgrade slot disabled");
        furnace.setItem(3, ingot(4)); menu.quickMoveStack(player, 3);
        h.assertTrue(furnace.getItem(3).isEmpty() && inventory.countItem(ingot(1).getItem()) == 4, "Shift click output must conserve ingots");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void largeFuelCountersAndExperienceSurviveSave(GameTestHelper h) {
        var furnace = assemble(h, Direction.SOUTH);
        furnace.setItem(0, ore(1)); furnace.setItem(1, new ItemStack(Items.COAL));
        tick(h, furnace, 160);
        var tag = furnace.saveWithFullMetadata(h.getLevel().registryAccess());
        tag.putInt("burnTime", 199840); tag.putInt("maxBurnTime", 200000);
        furnace.loadWithComponents(tag, h.getLevel().registryAccess());
        var clientData = new net.minecraft.world.inventory.SimpleContainerData(6);
        for (int i = 0; i < 6; i++) clientData.set(i, (short) furnace.data.get(i));
        var menu = new IronFurnaceMenu(0, h.makeMockPlayer(GameType.SURVIVAL).getInventory(), new net.minecraft.world.SimpleContainer(5), clientData);
        h.assertTrue(menu.burnTime() == 199840 && menu.maxBurnTime() == 200000, "Menu signed-short packets must preserve large fuel counters");
        h.assertTrue(tag.getCompound("RecipesUsed").getAllKeys().size() == 1, "Save must retain smelting experience ledger");
        furnace.dropExperience(null);
        int xp = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(CORE)).inflate(3)).stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
        furnace.dropExperience(null);
        int after = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(CORE)).inflate(3)).stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
        h.assertTrue(xp == 3 && after == 3 && furnace.saveWithFullMetadata(h.getLevel().registryAccess()).getCompound("RecipesUsed").isEmpty(), "Experience must match modern smelting recipe and be awarded once");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void createCannotCapturePartialMachine(GameTestHelper h) throws Exception {
        var furnace = assemble(h, Direction.SOUTH); furnace.setItem(0, ore(3));
        BlockPos bearing = h.absolutePos(CORE.below());
        h.getLevel().setBlockAndUpdate(bearing, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "mechanical_bearing"))
                .defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
        boolean blocked = false;
        try { blocked = !new BearingContraption(false, Direction.UP).assemble(h.getLevel(), bearing); }
        catch (com.simibubi.create.content.contraptions.AssemblyException expected) { blocked = true; }
        h.assertTrue(blocked && furnace.getItem(0).getCount() == 3 && h.getBlockEntity(CORE) == furnace, "Create must reject the multiblock and preserve its inventory");
        h.succeed();
    }
}
