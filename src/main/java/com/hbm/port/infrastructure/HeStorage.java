package com.hbm.port.infrastructure;

/** X5687 HE uses long values; it is not implicitly converted to Forge Energy. */
public interface HeStorage {
    long stored();
    long capacity();
    long receive(long requested, boolean simulate);
    long extract(long requested, boolean simulate);
}
