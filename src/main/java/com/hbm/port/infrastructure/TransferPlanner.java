package com.hbm.port.infrastructure;

import java.math.BigInteger;
import java.util.*;

/** PowerNetMK2/FluidNetMK2 priority and proportional sharing, with exact conservative rounding.
 * Plans from one immutable snapshot; a buffer may receive and provide without spending new input twice.
 */
public final class TransferPlanner {
    public record Account<T>(T owner, long available, long demand, int priority) {
        public Account {
            if (available < 0 || demand < 0 || priority < 0 || priority > 4) throw new IllegalArgumentException("Invalid transfer account");
        }
    }
    public record Plan<T>(Map<T, Long> incoming, Map<T, Long> outgoing) {}

    public static <T> Plan<T> plan(List<Account<T>> accounts, long rotation) {
        var unique=new HashSet<T>();for(var account:accounts)if(account.owner()==null||!unique.add(account.owner()))throw new IllegalArgumentException("Duplicate transfer owner");
        var incoming = new LinkedHashMap<T, Long>(); var outgoing = new LinkedHashMap<T, Long>();
        BigInteger available = accounts.stream().map(a -> BigInteger.valueOf(a.available())).reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger remaining = available, transferred = BigInteger.ZERO;
        for (int priority = 4; priority >= 0; priority--) {
            List<Account<T>> group = new ArrayList<>();
            BigInteger demand = BigInteger.ZERO;
            for (var account : accounts) if (account.priority() == priority && account.demand() > 0) {
                group.add(account); demand = demand.add(BigInteger.valueOf(account.demand()));
            }
            BigInteger grant = remaining.min(demand);
            allocate(group, grant, true, rotation, incoming);
            remaining = remaining.subtract(grant); transferred = transferred.add(grant);
        }
        allocate(accounts, transferred, false, rotation, outgoing);
        return new Plan<>(Collections.unmodifiableMap(incoming), Collections.unmodifiableMap(outgoing));
    }
    private static <T> void allocate(List<Account<T>> accounts, BigInteger amount, boolean demand, long rotation, Map<T, Long> result) {
        if (amount.signum() == 0) return;
        BigInteger total = BigInteger.ZERO;
        for (var account : accounts) total = total.add(BigInteger.valueOf(demand ? account.demand() : account.available()));
        if (total.signum() == 0) throw new IllegalStateException("No accounts for a nonempty transfer");
        BigInteger assigned = BigInteger.ZERO;
        for (var account : accounts) {
            long weight = demand ? account.demand() : account.available();
            long share = amount.multiply(BigInteger.valueOf(weight)).divide(total).longValueExact();
            result.put(account.owner(), share); assigned = assigned.add(BigInteger.valueOf(share));
        }
        int remainder = amount.subtract(assigned).intValueExact();
        int start = Math.floorMod(rotation, accounts.size());
        for (int i = 0; i < accounts.size() && remainder > 0; i++) {
            var account = accounts.get((start + i) % accounts.size());
            long limit = demand ? account.demand() : account.available(), share = result.get(account.owner());
            if (share < limit) { result.put(account.owner(), share + 1); remainder--; }
        }
        if (remainder != 0) throw new IllegalStateException("Unassigned rounding remainder");
    }
    private TransferPlanner() {}
}
