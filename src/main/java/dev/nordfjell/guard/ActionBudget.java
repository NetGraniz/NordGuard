package dev.nordfjell.guard;

/** One second of burst credit; monotonic clock, no allocations or timers per action. */
final class ActionBudget {
    private long last;
    private double tokens;
    private int rate;
    private boolean initialized;
    boolean allow(long now, int limit) {
        if(limit<1) throw new IllegalArgumentException("Positive rate required");
        if(rate!=limit || !initialized) { initialized=true;rate=limit;tokens=limit;last=now; }
        double seconds=Math.max(0,Math.min(1,(now-last)/1_000_000_000.0));
        tokens=Math.min(limit,tokens+seconds*limit);last=Math.max(last,now);
        if(tokens<1) return false;
        tokens--;return true;
    }
}
