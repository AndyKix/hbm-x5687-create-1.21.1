package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.hbm.port.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class StorageCrateTests {
    static final BlockPos POS = new BlockPos(2, 2, 2);
    static StorageCrateBlockEntity place(GameTestHelper h, String metal) {
        h.setBlock(POS, CrateContent.BLOCKS.get("crate_" + metal).get());
        return (StorageCrateBlockEntity) h.getBlockEntity(POS);
    }
    static ItemStack namedDiamond() {
        var stack = new ItemStack(Items.DIAMOND, 3);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("X5687 сохранение")); return stack;
    }
    static void name(StorageCrateBlockEntity crate, String name) {
        var stack = crate.packedItem(); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        crate.applyComponentsFromItemStack(stack); crate.setChanged();
    }
    static void remove(GameTestHelper h) { h.getLevel().removeBlock(h.absolutePos(POS), false); }
    @GameTest(template = "empty")
    public static void capacitiesComponentsAndNbt(GameTestHelper h) {
        for (String metal : List.of("iron", "steel")) {
            var crate = place(h, metal); int size = metal.equals("iron") ? 36 : 54;
            h.assertTrue(crate.getContainerSize() == size, "Wrong X5687 capacity");
            h.assertTrue(!crate.getBlockState().requiresCorrectToolForDrops(), "Breaking a portable crate by hand must not destroy its inventory");
            var emptyDrops = net.minecraft.world.level.block.Block.getDrops(crate.getBlockState(), h.getLevel(), crate.getBlockPos(), crate);
            h.assertTrue(emptyDrops.size() == 1 && ItemStack.isSameItemSameComponents(emptyDrops.getFirst(),
                    new ItemStack(CrateContent.ITEMS.get("crate_" + metal).get())), "Empty mined crate must stack with a crafted crate");
            for (int slot = 0; slot < size; slot++) crate.setItem(slot, new ItemStack(Items.IRON_INGOT, slot + 1));
            crate.setItem(size - 1, namedDiamond()); name(crate, "Именной ящик");
            var tag = crate.saveWithFullMetadata(h.getLevel().registryAccess());
            var loaded = (StorageCrateBlockEntity) BlockEntity.loadStatic(crate.getBlockPos(), crate.getBlockState(), tag, h.getLevel().registryAccess());
            h.assertTrue(loaded.getContainerSize() == size && loaded.getName().getString().equals("Именной ящик"), "Name and capacity must persist");
            for (int slot = 0; slot < size; slot++) h.assertTrue(ItemStack.matches(crate.getItem(slot), loaded.getItem(slot)), "NBT lost a slot or item components");
            remove(h);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void originalCrateRecipes(GameTestHelper h) {
        for (String metal : List.of("iron", "steel")) {
            var ingot = metal.equals("iron") ? Items.IRON_INGOT : HbmPort.MATERIAL_ITEMS.get("ingot_steel").get();
            var plate = metal.equals("iron") ? BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "iron_sheet")) : HbmPort.MATERIAL_ITEMS.get("plate_steel").get();
            var input = CraftingInput.of(3, 3, List.of(new ItemStack(plate), new ItemStack(plate), new ItemStack(plate),
                    new ItemStack(ingot), ItemStack.EMPTY, new ItemStack(ingot), new ItemStack(ingot), new ItemStack(ingot), new ItemStack(ingot)));
            var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel()).orElseThrow();
            var output = recipe.value().assemble(input, h.getLevel().registryAccess());
            h.assertTrue(output.is(CrateContent.ITEMS.get("crate_" + metal).get()) && output.getCount() == 1, "3 plates and 5 ingots must craft one crate");
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void allSidesAndNestingRules(GameTestHelper h) {
        for (String metal : List.of("iron", "steel")) {
            var crate = place(h, metal);
            for (Direction side : Direction.values()) {
                var handler = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), side);
                h.assertTrue(handler != null && handler.getSlots() == crate.getContainerSize(), "Every face must expose all slots");
                h.assertTrue(handler.insertItem(handler.getSlots() - 1, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(), "Last slot must accept input");
                h.assertTrue(handler.extractItem(handler.getSlots() - 1, 2, false).getCount() == 2, "Every face must support extraction");
                for (ItemStack invalid : List.of(new ItemStack(CrateContent.ITEMS.get("crate_iron").get()), new ItemStack(CrateContent.ITEMS.get("crate_steel").get()), new ItemStack(Items.SHULKER_BOX)))
                    h.assertTrue(handler.insertItem(0, invalid, false).getCount() == 1, "Portable containers cannot be nested");
            }
            h.assertTrue(h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(POS), null).getSlots() == crate.getContainerSize(), "Unsided automation must work");
            remove(h);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void actualLootAndPlacementPreserveContents(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (String metal : List.of("iron", "steel")) {
            var crate = place(h, metal); int last = crate.getContainerSize() - 1;
            crate.setItem(0, new ItemStack(Items.IRON_INGOT, 17)); crate.setItem(last, namedDiamond()); name(crate, "Переносимый ящик");
            for (int cycle = 0; cycle < 3; cycle++) {
                h.assertTrue(h.getLevel().destroyBlock(h.absolutePos(POS), true), "Crate destruction failed");
                var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(2));
                h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(CrateContent.ITEMS.get("crate_" + metal).get()), "Must drop exactly one crate, no loose contents");
                var packed = drops.getFirst().getItem().copy(); drops.getFirst().discard();
                h.assertTrue(packed.getCount() == 1 && packed.getMaxStackSize() == 1, "Filled crates must be unstackable");
                var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(POS)), Direction.UP, h.absolutePos(POS), false);
                var item = CrateContent.ITEMS.get("crate_" + metal).get();
                var result = item.place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, packed, hit));
                h.assertTrue(result.consumesAction() && packed.isEmpty(), "Real block item must restore packed crate");
                crate = (StorageCrateBlockEntity) h.getBlockEntity(POS);
                h.assertTrue(crate.getItem(0).getCount() == 17 && ItemStack.matches(crate.getItem(last), namedDiamond())
                        && crate.getName().getString().equals("Переносимый ящик"), "Replaced crate lost inventory, components or name");
            }
            remove(h);
        }
        h.succeed();
    }
    @GameTest(template = "ore_test", batch = "storage_explosion")
    public static void explosionKeepsOnePortableInventory(GameTestHelper h) {
        BlockPos pos = new BlockPos(16, 4, 8);
        h.setBlock(pos, CrateContent.BLOCKS.get("crate_steel").get());
        var crate = (StorageCrateBlockEntity) h.getBlockEntity(pos); crate.setItem(0, new ItemStack(Items.IRON_INGOT, 13)); crate.setItem(53, namedDiamond());
        var center = Vec3.atCenterOf(h.absolutePos(pos));
        h.getLevel().explode(null, center.x, center.y, center.z, 6, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
        h.assertTrue(h.getBlockState(pos).isAir(), "Explosion must actually destroy crate");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(pos)).inflate(3));
        h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(CrateContent.ITEMS.get("crate_steel").get()), "Explosion must not void or duplicate packed contents");
        var packed = drops.getFirst().getItem();
        var items = net.minecraft.core.NonNullList.withSize(54, ItemStack.EMPTY);
        packed.get(DataComponents.CONTAINER).copyInto(items);
        h.assertTrue(items.getFirst().getCount() == 13 && ItemStack.matches(items.getLast(), namedDiamond()), "Explosion drop must contain both first and last slots");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void creativeBreakPreservesFilledCrates(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.CREATIVE);
        for (String metal : List.of("iron", "steel")) {
            var crate = place(h, metal); crate.setItem(crate.getContainerSize() - 1, namedDiamond());
            crate.getBlockState().getBlock().playerWillDestroy(h.getLevel(), h.absolutePos(POS), crate.getBlockState(), player); remove(h);
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(2));
            h.assertTrue(drops.size() == 1 && drops.getFirst().getItem().has(DataComponents.CONTAINER), "Creative destruction must preserve one filled crate");
            drops.getFirst().discard();
            crate = place(h, metal);
            crate.getBlockState().getBlock().playerWillDestroy(h.getLevel(), h.absolutePos(POS), crate.getBlockState(), player); remove(h);
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(POS)).inflate(2)).isEmpty(), "Empty creative crate must not drop");
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void menuShiftClickAndConcurrentViews(GameTestHelper h) {
        for (String metal : List.of("iron", "steel")) {
            var crate = place(h, metal); int size = crate.getContainerSize();
            var a = h.makeMockPlayer(GameType.SURVIVAL); var b = h.makeMockPlayer(GameType.SURVIVAL);
            a.setPos(Vec3.atCenterOf(h.absolutePos(POS))); b.setPos(a.position());
            var first = new StorageCrateMenu(0, a.getInventory(), crate, crate.rows());
            var second = new StorageCrateMenu(1, b.getInventory(), crate, crate.rows());
            h.assertTrue(first.stillValid(a) && second.stillValid(b), "Both nearby viewers must be allowed");
            a.getInventory().setItem(9, new ItemStack(Items.IRON_INGOT, 27));
            first.quickMoveStack(a, size);
            h.assertTrue(second.getSlot(0).getItem().getCount() == 27 && a.getInventory().getItem(9).isEmpty(), "Two menus must share one server inventory");
            second.quickMoveStack(b, 0);
            h.assertTrue(crate.isEmpty() && b.getInventory().countItem(Items.IRON_INGOT) == 27, "Second viewer must take items exactly once");
            first.quickMoveStack(a, 0);
            h.assertTrue(a.getInventory().countItem(Items.IRON_INGOT) == 0, "Stale menu must not duplicate taken items");
            a.getInventory().setItem(9, new ItemStack(CrateContent.ITEMS.get("crate_steel").get())); first.quickMoveStack(a, size);
            h.assertTrue(crate.isEmpty() && a.getInventory().getItem(9).getCount() == 1, "Shift click must reject nested crate");
            first.removed(a); second.removed(b); remove(h);
            h.assertTrue(!first.stillValid(a), "Broken crate must invalidate an open menu");
        }
        h.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 160)
    public static void realHoppersUseCrates(GameTestHelper h) {
        place(h, "iron"); h.setBlock(new BlockPos(4, 2, 2), CrateContent.BLOCKS.get("crate_steel").get());
        for (int x : new int[]{2, 4}) {
            h.setBlock(new BlockPos(x, 3, 2), Blocks.HOPPER); h.setBlock(new BlockPos(x, 1, 2), Blocks.HOPPER);
            ((HopperBlockEntity) h.getBlockEntity(new BlockPos(x, 3, 2))).setItem(0, new ItemStack(Items.IRON_INGOT, 4));
        }
        h.succeedWhen(() -> {
            for (int x : new int[]{2, 4}) {
                var output = (HopperBlockEntity) h.getBlockEntity(new BlockPos(x, 1, 2));
                int total = 0; for (int slot = 0; slot < output.getContainerSize(); slot++) if (output.getItem(slot).is(Items.IRON_INGOT)) total += output.getItem(slot).getCount();
                h.assertTrue(total == 4 && ((HopperBlockEntity) h.getBlockEntity(new BlockPos(x, 3, 2))).isEmpty(), "Real hoppers must transfer exactly four ingots through each crate");
            }
        });
    }
    @GameTest(template = "empty")
    public static void comparatorAndStackLimits(GameTestHelper h) {
        for (String metal : List.of("iron", "steel")) {
            var crate = place(h, metal);
            h.assertTrue(crate.getBlockState().getAnalogOutputSignal(h.getLevel(), h.absolutePos(POS)) == 0, "Empty crate comparator must be zero");
            for (int slot = 0; slot < crate.getContainerSize(); slot++) crate.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
            h.assertTrue(crate.getBlockState().getAnalogOutputSignal(h.getLevel(), h.absolutePos(POS)) == 15, "Full crate comparator must be fifteen");
            var remainder = crate.itemHandler().insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false);
            h.assertTrue(remainder.getCount() == 5 && crate.getItem(0).getCount() == 64, "Full crate must not swallow or overfill stacks");
            crate.clearContent();
            h.assertTrue(ItemStack.isSameItemSameComponents(crate.packedItem(), new ItemStack(CrateContent.ITEMS.get("crate_" + metal).get())),
                    "Emptied portable crate must recover normal empty-crate components");
            crate.itemHandler().insertItem(0, new ItemStack(Items.DIAMOND_SWORD), false);
            h.assertTrue(crate.getItem(0).getMaxStackSize() == 1, "Unstackable item components must survive");
            remove(h);
        }
        h.succeed();
    }
}
