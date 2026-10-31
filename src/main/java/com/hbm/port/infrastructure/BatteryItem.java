package com.hbm.port.infrastructure;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

public final class BatteryItem extends Item {
    public final BatteryKind kind;
    public BatteryItem(BatteryKind kind) { super(new Properties().stacksTo(1)); this.kind = kind; }
    public long charge(ItemStack stack) {
        if (kind.infinite) return Long.MAX_VALUE / 2;
        return Math.clamp(stack.getOrDefault(InfrastructureContent.CHARGE.get(), kind.initiallyFull ? kind.capacity : 0L), 0, kind.capacity);
    }
    public void setCharge(ItemStack stack, long value) {
        if (!kind.infinite) stack.set(InfrastructureContent.CHARGE.get(), Math.clamp(value, 0, kind.capacity));
    }
    public HeStorage storage(ItemStack stack) {
        return new HeStorage() {
            public long stored() { return charge(stack); }
            public long capacity() { return kind.capacity; }
            public long receive(long requested, boolean simulate) {
                long accepted = Math.min(Math.max(0, requested), Math.min(kind.chargeRate, kind.capacity - charge(stack)));
                if (!simulate && accepted > 0) setCharge(stack, charge(stack) + accepted);
                return accepted;
            }
            public long extract(long requested, boolean simulate) {
                long extracted = Math.min(Math.max(0, requested), Math.min(kind.dischargeRate, charge(stack)));
                if (!simulate && extracted > 0) setCharge(stack, charge(stack) - extracted);
                return extracted;
            }
        };
    }
    @Override public boolean isBarVisible(ItemStack stack) { return true; }
    @Override public int getBarWidth(ItemStack stack) { return (int) Math.round(13D * charge(stack) / kind.capacity); }
    @Override public int getBarColor(ItemStack stack) { return 0x50d050; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.hbm.energy_stored", charge(stack), kind.capacity).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.hbm.energy_rates", kind.chargeRate, kind.dischargeRate).withStyle(ChatFormatting.GRAY));
    }
}
