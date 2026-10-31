package com.hbm.port.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/** Non-vanilla distributions in BlockOre/BlockDepthOre/BlockResourceStone. */
public record RawOreYield(String ore) implements LootItemFunction {
    public static final MapCodec<RawOreYield> CODEC = Codec.STRING.fieldOf("ore").xmap(RawOreYield::new, RawOreYield::ore);
    public static final DeferredRegister<LootItemFunctionType<?>> TYPES = DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, "hbm");
    public static final java.util.function.Supplier<LootItemFunctionType<RawOreYield>> TYPE =
            TYPES.register("raw_ore_yield", () -> new LootItemFunctionType<>(CODEC));

    @Override public ItemStack apply(ItemStack stack, LootContext context) {
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        int fortune = tool == null ? 0 : tool.getEnchantmentLevel(context.getLevel().registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE));
        var random = context.getRandom();
        if (ore.equals("stone_resource_malachite")) {
            stack.setCount(3 + fortune + random.nextInt(fortune + 2));
        } else {
            int quantity = Math.max(random.nextInt(fortune + 2) - 1, 0) + 1;
            if (ore.equals("ore_alexandrite")) stack.setCount(Math.min(2, quantity));
            else if (ore.equals("ore_nether_fire")) {
                stack = new ItemStack(RawMaterials.item(random.nextInt(10) == 0 ? "ingot_phosphorus" : "powder_fire"), quantity);
            } else throw new IllegalArgumentException("Unsupported special ore yield: " + ore);
        }
        return stack;
    }
    @Override public LootItemFunctionType<? extends LootItemFunction> getType() { return TYPE.get(); }
    @Override public Set<LootContextParam<?>> getReferencedContextParams() { return Set.of(LootContextParams.TOOL); }
}
