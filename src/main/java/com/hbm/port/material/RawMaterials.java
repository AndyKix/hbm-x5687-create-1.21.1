package com.hbm.port.material;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hbm.port.HbmPort;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.List;

/** Registry definitions and source-derived data are generated together by import_raw_materials.py. */
public final class RawMaterials {
    public static final JsonObject CATALOG;
    static {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                RawMaterials.class.getResourceAsStream("/assets/hbm/raw-materials.json")), StandardCharsets.UTF_8)) {
            CATALOG = new Gson().fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot load the pinned X5687 material catalogue", e);
        }
    }

    public static void register() {
        for (var entry : CATALOG.getAsJsonObject("blocks").entrySet()) {
            String id = entry.getKey();
            if (HbmPort.MATERIAL_ITEMS.containsKey(id)) continue;
            JsonObject data = entry.getValue().getAsJsonObject();
            var holder = HbmPort.BLOCKS.register(id, () -> {
                var properties = BlockBehaviour.Properties.of()
                        .strength(data.get("hardness").getAsFloat(), data.get("resistance").getAsFloat())
                        .sound(flag(data, "storage") ? SoundType.METAL : SoundType.STONE);
                if (!id.equals("block_asbestos")) properties.requiresCorrectToolForDrops();
                else properties.sound(SoundType.WOOL);
                if (data.has("light")) properties.lightLevel(state -> data.get("light").getAsInt());
                // Depth rock is unbreakable with ordinary tools, as in BlockDepth.
                // Its loot table remains available for the future IDepthRockTool port.
                if (id.equals("block_red_phosphorus")) return new ResourceFallingBlock(properties.sound(SoundType.SAND));
                if (id.equals("block_lithium")) return new LithiumBlock(properties);
                if (flag(data, "decoration")) return new CaveFormation(properties.noCollission().noOcclusion(), id.startsWith("stalactite"));
                return new ResourceBlock(properties, flag(data, "burning"), flag(data, "depth"));
            });
            HbmPort.MATERIAL_ITEMS.put(id, HbmPort.ITEMS.register(id, () -> flag(data, "depth")
                    ? new DepthBlockItem(holder.get()) : new BlockItem(holder.get(), new Item.Properties())));
        }
        for (var entry : CATALOG.getAsJsonObject("items").entrySet()) {
            String id = entry.getKey();
            if (HbmPort.MATERIAL_ITEMS.containsKey(id)) continue;
            int fuel = CATALOG.getAsJsonObject("fuel_ticks").has(id) ? CATALOG.getAsJsonObject("fuel_ticks").get(id).getAsInt() : 0;
            HbmPort.MATERIAL_ITEMS.put(id, HbmPort.ITEMS.register(id, () -> new FuelMaterial(fuel)));
        }
    }

    private static boolean flag(JsonObject data, String key) { return data.has(key) && data.get(key).getAsBoolean(); }
    public static Block block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("hbm", id)); }
    public static Item item(String id) { return HbmPort.MATERIAL_ITEMS.get(id).get(); }

    private static final class FuelMaterial extends Item {
        private final int fuel;
        FuelMaterial(int fuel) { super(new Properties()); this.fuel = fuel; }
        @Override public int getBurnTime(ItemStack stack, RecipeType<?> type) { return fuel; }
    }

    private static final class DepthBlockItem extends BlockItem {
        DepthBlockItem(Block block) { super(block, new Item.Properties()); }
        @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flags) {
            super.appendHoverText(stack, context, lines, flags);
            lines.add(Component.translatable("tooltip.hbm.depth_rock").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static final class ResourceBlock extends Block {
        private final boolean burning;
        private final boolean depth;
        ResourceBlock(BlockBehaviour.Properties properties, boolean burning, boolean depth) { super(properties); this.burning = burning; this.depth = depth; }
        @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
            var held = player.getMainHandItem();
            if (depth && held.getItem() instanceof DepthRockTool tool && tool.canBreakRock(level, player, held, state, pos)) return 1F / 50F;
            return super.getDestroyProgress(state, player, level, pos);
        }
        @Override public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (burning) entity.igniteForSeconds(3);
            super.stepOn(level, pos, state, entity);
        }
    }

    private static final class LithiumBlock extends Block {
        LithiumBlock(BlockBehaviour.Properties properties) { super(properties); }
        private void react(Level level, BlockPos pos) {
            if (level.isClientSide || !level.getBlockState(pos).is(this)) return;
            for (var side : Direction.values()) if (level.getFluidState(pos.relative(side)).is(net.minecraft.tags.FluidTags.WATER)) {
                level.removeBlock(pos, false);
                level.explode(null, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 15, false, Level.ExplosionInteraction.BLOCK);
                return;
            }
        }
        @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moved) {
            super.onPlace(state, level, pos, previous, moved);
            if (!previous.is(this)) react(level, pos);
        }
        @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean moved) {
            react(level, pos);
        }
    }

    private static final class ResourceFallingBlock extends FallingBlock {
        private static final MapCodec<ResourceFallingBlock> CODEC = simpleCodec(ResourceFallingBlock::new);
        ResourceFallingBlock(BlockBehaviour.Properties properties) { super(properties); }
        @Override protected MapCodec<? extends FallingBlock> codec() { return CODEC; }
    }

    private static final class CaveFormation extends Block {
        private final Direction support;
        CaveFormation(BlockBehaviour.Properties properties, boolean hanging) { super(properties); support = hanging ? Direction.UP : Direction.DOWN; }
        @Override protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.relative(support)).isFaceSturdy(level, pos.relative(support), support.getOpposite());
        }
        @Override protected BlockState updateShape(BlockState state, Direction side, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
            return side == support && !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : super.updateShape(state, side, neighbor, level, pos, neighborPos);
        }
    }

    private RawMaterials() {}
}
