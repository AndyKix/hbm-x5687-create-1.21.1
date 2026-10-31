package com.hbm.port.test;

import com.hbm.port.HbmPort;
import com.hbm.port.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder("hbm")
@PrefixGameTestTemplate(false)
public final class CrateIsolationTests {
    private static StorageCrateBlockEntity place(GameTestHelper h, BlockPos pos, Player player, ItemStack stack, String metal) {
        var hit = new BlockHitResult(Vec3.atCenterOf(h.absolutePos(pos)), Direction.UP, h.absolutePos(pos), false);
        var result = CrateContent.ITEMS.get("crate_" + metal).get().place(new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
        h.assertTrue(result.consumesAction(), "Actual crate placement failed");
        return (StorageCrateBlockEntity) h.getBlockEntity(pos);
    }
    @GameTest(template = "empty")
    public static void freshSameTypeCratesStayIndependentAfterPickupClicks(GameTestHelper h) {
        var aPos = new BlockPos(1, 1, 1); var bPos = new BlockPos(3, 1, 1);
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        for (String metal : List.of("iron", "steel")) {
            ItemStack boxes = new ItemStack(CrateContent.ITEMS.get("crate_" + metal).get(), 2);
            var a = place(h, aPos, player, boxes, metal);
            var menuA = new StorageCrateMenu(0, player.getInventory(), a, a.rows());
            int[] slots = {0, 1, 8, 11, 12, 13, 23, 24, 25, 26, 30, 31, 32, 33, 34, 35};
            var plate = HbmPort.MATERIAL_ITEMS.get("plate_titanium").get();
            menuA.setCarried(new ItemStack(plate, slots.length));
            for (int slot : slots) menuA.clicked(slot, 1, ClickType.PICKUP, player);
            h.assertTrue(menuA.getCarried().isEmpty(), "Manual right-click placement must consume exactly sixteen carried plates");
            h.assertTrue(boxes.getCount() == 1 && boxes.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).equals(ItemContainerContents.EMPTY),
                    "Filling first placed crate must not modify remaining item in player's stack");
            var b = place(h, bPos, player, boxes, metal);
            h.assertTrue(a != b && a.itemHandler() != b.itemHandler() && b.isEmpty() && boxes.isEmpty(), "Fresh second same-type crate must be independent and empty");
            var menuB = new StorageCrateMenu(1, player.getInventory(), b, b.rows());
            menuB.setCarried(new ItemStack(Items.GOLD_INGOT, 7)); menuB.clicked(0, 0, ClickType.PICKUP, player);
            for (int slot : slots) h.assertTrue(a.getItem(slot).is(plate) && a.getItem(slot).getCount() == 1, "Second crate's GUI changed first crate");
            menuA.clicked(0, 0, ClickType.PICKUP, player);
            h.assertTrue(a.getItem(0).isEmpty() && b.getItem(0).is(Items.GOLD_INGOT) && b.getItem(0).getCount() == 7, "Removal from first crate must not affect second");
            h.getLevel().removeBlock(h.absolutePos(aPos), false); h.getLevel().removeBlock(h.absolutePos(bPos), false);
        }
        h.succeed();
    }
    @GameTest(template = "empty")
    public static void creativeCopiesHaveInitialContentsButDoNotShareUpdates(GameTestHelper h) {
        var first = new BlockPos(1, 1, 1); var second = new BlockPos(3, 1, 1);
        var player = h.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        for (String metal : List.of("iron", "steel")) {
            h.setBlock(first, CrateContent.BLOCKS.get("crate_" + metal).get());
            var original = (StorageCrateBlockEntity) h.getBlockEntity(first);
            original.setItem(0, new ItemStack(Items.IRON_INGOT, 17)); original.setItem(original.getContainerSize() - 1, StorageCrateTests.namedDiamond());
            var packed = original.packedItem(); h.getLevel().removeBlock(h.absolutePos(first), false);
            var a = place(h, first, player, packed, metal); var b = place(h, second, player, packed, metal);
            h.assertTrue(packed.getCount() == 1 && a.getItem(0).getCount() == 17 && b.getItem(0).getCount() == 17, "Creative mode intentionally copies an unconsumed filled item");
            h.assertTrue(a.getItem(0) != b.getItem(0), "Copied contents must have distinct stack objects");
            a.getItem(0).shrink(3); a.setChanged();
            h.assertTrue(a.getItem(0).getCount() == 14 && b.getItem(0).getCount() == 17 && packed.get(DataComponents.CONTAINER).copyOne().getCount() == 17,
                    "Mutating one creative copy must not change another or the held item snapshot");
            b.clearContent();
            h.assertTrue(a.getItem(0).getCount() == 14 && !a.getItem(a.getContainerSize() - 1).isEmpty(), "Clearing one copy must not clear another");
            h.getLevel().removeBlock(h.absolutePos(first), false); h.getLevel().removeBlock(h.absolutePos(second), false);
        }
        h.succeed();
    }
    @GameTest(template = "ore_test", batch = "crate_isolation")
    public static void independentInventoriesSurviveReloadAcrossChunks(GameTestHelper h) {
        var aPos = new BlockPos(2, 2, 2); var bPos = new BlockPos(18, 2, 2);
        for (String metal : List.of("iron", "steel")) {
            h.setBlock(aPos, CrateContent.BLOCKS.get("crate_" + metal).get()); h.setBlock(bPos, CrateContent.BLOCKS.get("crate_" + metal).get());
            var a = (StorageCrateBlockEntity) h.getBlockEntity(aPos); var b = (StorageCrateBlockEntity) h.getBlockEntity(bPos);
            a.setItem(0, new ItemStack(Items.IRON_INGOT, 11)); b.setItem(0, new ItemStack(Items.GOLD_INGOT, 9));
            for (var crate : List.of(a, b)) {
                var tag = crate.saveWithFullMetadata(h.getLevel().registryAccess());
                h.getLevel().removeBlockEntity(crate.getBlockPos());
                var loaded = BlockEntity.loadStatic(crate.getBlockPos(), crate.getBlockState(), tag, h.getLevel().registryAccess());
                h.getLevel().setBlockEntity(loaded);
            }
            a = (StorageCrateBlockEntity) h.getBlockEntity(aPos); b = (StorageCrateBlockEntity) h.getBlockEntity(bPos);
            a.itemHandler().extractItem(0, 4, false);
            h.assertTrue(a.getItem(0).is(Items.IRON_INGOT) && a.getItem(0).getCount() == 7 && b.getItem(0).is(Items.GOLD_INGOT)
                    && b.getItem(0).getCount() == 9, "Separate loaded crates must not share inventories or slot stacks");
            h.getLevel().removeBlock(h.absolutePos(aPos), false); h.getLevel().removeBlock(h.absolutePos(bPos), false);
        }
        h.succeed();
    }
}
