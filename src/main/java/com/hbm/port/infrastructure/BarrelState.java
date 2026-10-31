package com.hbm.port.infrastructure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Immutable item component, never a mutable FluidStack shared by two barrel items. */
public record BarrelState(FluidKind fluid, int amount, int mode) {
    public static final BarrelState EMPTY = new BarrelState(FluidKind.NONE, 0, 0);
    public static final Codec<BarrelState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FluidKind.CODEC.fieldOf("fluid").forGetter(BarrelState::fluid),
            Codec.intRange(0, 16_000).fieldOf("amount").forGetter(BarrelState::amount),
            Codec.intRange(0, 3).fieldOf("mode").forGetter(BarrelState::mode)).apply(instance, BarrelState::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, BarrelState> STREAM_CODEC = new StreamCodec<>() {
        @Override public BarrelState decode(RegistryFriendlyByteBuf buf) {
            return new BarrelState(buf.readEnum(FluidKind.class), buf.readVarInt(), buf.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf buf, BarrelState state) {
            buf.writeEnum(state.fluid()); buf.writeVarInt(state.amount()); buf.writeVarInt(state.mode());
        }
    };
    public BarrelState {
        if (amount < 0 || amount > 16_000 || mode < 0 || mode > 3 || fluid == null || fluid == FluidKind.NONE && amount != 0)
            throw new IllegalArgumentException("Invalid portable barrel state");
    }
}
