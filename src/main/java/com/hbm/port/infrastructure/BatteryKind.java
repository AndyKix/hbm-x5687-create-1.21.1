package com.hbm.port.infrastructure;

import net.minecraft.util.StringRepresentable;

/** ItemBatteryPack capacities: batteries run 15min; capacitors 30s at rated discharge. */
public enum BatteryKind implements StringRepresentable {
    EMPTY("empty", 0, 0, 0, false, false),
    REDSTONE("battery_redstone", 1_800_000, 1_000, 100, false, false),
    LEAD("battery_lead", 18_000_000, 10_000, 1_000, false, false),
    LITHIUM("battery_lithium", 180_000_000, 100_000, 10_000, false, false),
    COPPER("capacitor_copper", 600_000, 1_000, 1_000, false, false),
    GOLD("capacitor_gold", 6_000_000, 10_000, 10_000, false, false),
    POTATO("battery_potato", 1_000, 0, 100, true, false),
    CREATIVE("battery_creative", Long.MAX_VALUE, Long.MAX_VALUE / 100, Long.MAX_VALUE / 100, false, true),
    CUBE("cube_power", 1_000_000_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000_000L, true, false);

    public final String id;
    public final long capacity, chargeRate, dischargeRate;
    public final boolean initiallyFull, infinite;
    BatteryKind(String id, long capacity, long chargeRate, long dischargeRate, boolean initiallyFull, boolean infinite) {
        this.id = id; this.capacity = capacity; this.chargeRate = chargeRate; this.dischargeRate = dischargeRate;
        this.initiallyFull = initiallyFull; this.infinite = infinite;
    }
    @Override public String getSerializedName() { return id; }
}
