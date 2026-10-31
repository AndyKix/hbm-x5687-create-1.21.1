package com.hbm.port.infrastructure;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Initial mapped X5687 fluid types, at ambient network pressure. No foreign-fluid coercion. */
public enum FluidKind implements StringRepresentable {
    NONE("none", 0x888888), WATER("water", 0x3333ff), LAVA("lava", 0xff3300);
    public static final Codec<FluidKind> CODEC = StringRepresentable.fromEnum(FluidKind::values);
    private final String name;
    public final int color;
    FluidKind(String name, int color) { this.name = name; this.color = color; }
    @Override public String getSerializedName() { return name; }
    public Fluid fluid() { return this == WATER ? Fluids.WATER : this == LAVA ? Fluids.LAVA : Fluids.EMPTY; }
    public FluidStack stack(int amount) { return this == NONE || amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid(), amount); }
    public boolean accepts(FluidStack stack) {
        return this != NONE && !stack.isEmpty() && FluidStack.isSameFluidSameComponents(stack, new FluidStack(fluid(), 1));
    }
    public Component label() { return Component.translatable(this == NONE ? "fluid.hbm.none" : "block.minecraft." + name); }
    public static FluidKind byName(String name) {
        for (var kind : values()) if (kind.name.equals(name)) return kind;
        return NONE;
    }
}
