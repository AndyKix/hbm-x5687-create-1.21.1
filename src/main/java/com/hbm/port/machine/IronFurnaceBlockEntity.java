package com.hbm.port.machine;

import com.hbm.port.HbmPort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Independent 1.21.1 adaptation of X5687 TileEntityFurnaceIron (base operation, no upgrades/pollution yet). */
public final class IronFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int PROCESSING_TIME = 160;
    private static final int[] AUTOMATION_SLOTS = {0, 1, 2, 3};
    private NonNullList<ItemStack> items = NonNullList.withSize(5, ItemStack.EMPTY);
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> recipes = RecipeManager.createCheck(RecipeType.SMELTING);
    private final Map<ResourceLocation, Integer> recipesUsed = new LinkedHashMap<>();
    private final IItemHandler[] handlers = new IItemHandler[6];
    private int burnTime, maxBurnTime, progress;
    private boolean active;
    boolean dismantling;
    // Native menu packets carry signed shorts; split fuel counters to preserve long modded burn times.
    public final ContainerData data = new ContainerData() {
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime & 0xffff; case 1 -> burnTime >>> 16;
                case 2 -> maxBurnTime & 0xffff; case 3 -> maxBurnTime >>> 16;
                case 4 -> progress; case 5 -> active ? 1 : 0; default -> 0;
            };
        }
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = (burnTime & 0xffff0000) | (value & 0xffff);
                case 1 -> burnTime = (burnTime & 0xffff) | ((value & 0xffff) << 16);
                case 2 -> maxBurnTime = (maxBurnTime & 0xffff0000) | (value & 0xffff);
                case 3 -> maxBurnTime = (maxBurnTime & 0xffff) | ((value & 0xffff) << 16);
                case 4 -> progress = value; case 5 -> active = value != 0;
            }
        }
        public int getCount() { return 6; }
    };

    public IronFurnaceBlockEntity(BlockPos pos, BlockState state) { super(HbmPort.IRON_FURNACE_ENTITY.get(), pos, state); }
    @Override protected Component getDefaultName() { return Component.translatable("container.hbm.furnace_iron"); }
    @Override protected NonNullList<ItemStack> getItems() { return items; }
    @Override protected void setItems(NonNullList<ItemStack> items) { this.items = items; }
    @Override public int getContainerSize() { return 5; }
    @Override protected AbstractContainerMenu createMenu(int id, Inventory inventory) { return new IronFurnaceMenu(id, inventory, this, data); }
    public int burnTime() { return burnTime; }
    public int maxBurnTime() { return maxBurnTime; }
    public int progress() { return progress; }
    public boolean active() { return active; }
    public IItemHandler itemHandler(@Nullable Direction side) {
        // An unspecified side has exactly the same restricted four slots as a physical side.
        int index = side == null ? 0 : side.ordinal();
        if (handlers[index] == null) handlers[index] = new SidedInvWrapper(this, side == null ? Direction.DOWN : side);
        return handlers[index];
    }
    public static boolean smeltable(Level level, ItemStack stack) {
        return !stack.isEmpty() && level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
    }
    public static int fuelTime(ItemStack stack) {
        int vanilla = stack.getBurnTime(RecipeType.SMELTING);
        // Legacy gemCoal/blockCoal bonus; charcoal has its own material and no coal bonus.
        double factor = stack.is(Items.COAL) || stack.is(Items.COAL_BLOCK) ? 1.25 : 1;
        return (int) Math.min(Integer.MAX_VALUE, vanilla * factor);
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == 0 ? level != null && smeltable(level, stack) : (slot == 1 || slot == 2) && fuelTime(stack) > 0;
    }
    @Override public int[] getSlotsForFace(Direction side) { return AUTOMATION_SLOTS.clone(); }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return canPlaceItem(slot, stack); }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return slot == 3; }
    @Override public boolean stillValid(Player player) { return !dismantling && super.stillValid(player); }

    public static void tick(Level level, BlockPos pos, BlockState state, IronFurnaceBlockEntity furnace) {
        if (furnace.dismantling) return;
        // Do not manufacture while part of the structure is unloaded or missing.
        for (int part = 0; part < 8; part++) {
            BlockPos other = pos.offset(IronFurnaceBlock.offset(part, state.getValue(IronFurnaceBlock.FACING)));
            if (!level.hasChunkAt(other)) return;
            BlockState expected = level.getBlockState(other);
            if (!expected.is(state.getBlock()) || expected.getValue(IronFurnaceBlock.PART) != part
                    || expected.getValue(IronFurnaceBlock.FACING) != state.getValue(IronFurnaceBlock.FACING)) return;
        }
        boolean changed = false;
        if (furnace.burnTime <= 0) {
            for (int slot = 1; slot <= 2; slot++) {
                ItemStack fuel = furnace.items.get(slot);
                int duration = fuelTime(fuel);
                if (duration <= 0) continue;
                furnace.maxBurnTime = furnace.burnTime = duration;
                ItemStack remainder = fuel.getCraftingRemainingItem();
                fuel.shrink(1);
                if (fuel.isEmpty()) furnace.items.set(slot, remainder);
                changed = true;
                break;
            }
        }
        SingleRecipeInput input = new SingleRecipeInput(furnace.items.getFirst());
        var recipe = furnace.recipes.getRecipeFor(input, level);
        ItemStack result = recipe.map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
        ItemStack output = furnace.items.get(3);
        boolean canWork = furnace.burnTime > 0 && !result.isEmpty()
                && (output.isEmpty() || ItemStack.isSameItemSameComponents(output, result))
                && output.getCount() + result.getCount() <= Math.min(result.getMaxStackSize(), furnace.getMaxStackSize());
        if (canWork) {
            furnace.progress++;
            furnace.burnTime--;
            changed = true;
            if (furnace.progress >= PROCESSING_TIME) {
                if (output.isEmpty()) furnace.items.set(3, result.copy()); else output.grow(result.getCount());
                furnace.items.getFirst().shrink(1);
                furnace.recipesUsed.merge(recipe.orElseThrow().id(), 1, Integer::sum);
                furnace.progress = 0;
            }
        } else if (furnace.progress != 0) {
            furnace.progress = 0;
            changed = true;
        }
        furnace.active = canWork;
        if (state.getValue(IronFurnaceBlock.LIT) != canWork)
            level.setBlock(pos, state.setValue(IronFurnaceBlock.LIT, canWork), 3);
        if (changed) furnace.setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putInt("burnTime", burnTime);
        tag.putInt("maxBurnTime", maxBurnTime);
        tag.putInt("progress", progress);
        CompoundTag used = new CompoundTag();
        recipesUsed.forEach((id, amount) -> used.putInt(id.toString(), amount));
        tag.put("RecipesUsed", used);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items = NonNullList.withSize(5, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
        burnTime = Math.max(0, tag.getInt("burnTime"));
        maxBurnTime = Math.max(burnTime, tag.getInt("maxBurnTime"));
        progress = Math.clamp(tag.getInt("progress"), 0, PROCESSING_TIME - 1);
        recipesUsed.clear();
        CompoundTag used = tag.getCompound("RecipesUsed");
        for (String key : used.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && used.getInt(key) > 0) recipesUsed.put(id, used.getInt(key));
        }
    }
    public void dropExperience(@Nullable ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return;
        for (var entry : recipesUsed.entrySet()) {
            server.getRecipeManager().byKey(entry.getKey()).ifPresent(recipe -> {
                if (!(recipe.value() instanceof SmeltingRecipe smelting)) return;
                float experience = smelting.getExperience() * entry.getValue();
                int amount = (int) experience;
                if (server.random.nextFloat() < experience - amount) amount++;
                ExperienceOrb.award(server, worldPosition.getCenter(), amount);
                if (player != null) player.awardRecipes(java.util.List.of(recipe));
            });
        }
        recipesUsed.clear();
        setChanged();
    }
}
