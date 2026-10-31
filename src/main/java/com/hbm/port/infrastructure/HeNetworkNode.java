package com.hbm.port.infrastructure;

/** A loaded HE account. The network commits all balances before callbacks. */
public interface HeNetworkNode extends HeStorage {
    long available();
    long demand();
    int networkPriority();
    void commit(long incoming, long outgoing);
}
