package dev.nordfjell.guard;

/** Local monotonic owner intervals, never global TPS or client packet counts. */
final class MovementTiming {
    static final long TICK_NANOS = 50_000_000L;
    private MovementTiming() {}
    static boolean uncertain(long gap, Policy policy) {
        return gap <= 0 || gap > Math.min(policy.maxGapNanos(),
                policy.movement().maxElapsedTicks() * TICK_NANOS);
    }
    static double ticks(long gap, Policy policy) {
        if (uncertain(gap, policy)) throw new IllegalArgumentException("Untrusted owner interval");
        return (double) gap / TICK_NANOS;
    }
}
