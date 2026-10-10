package dev.nordfjell.guard;

/** Owner-thread operation identity. A timeout invalidates bookkeeping, not the server teleport itself. */
final class SetbackWindow {
    static final long TIMEOUT_NANOS = 5_000_000_000L;
    static final long ANCHOR_NANOS = 30_000_000_000L;
    private long serial, pending, started, revision;

    long begin(long now, long originRevision) {
        pending = ++serial; started = now; revision = originRevision;
        return pending;
    }
    boolean owns(long ticket, long originRevision) {
        return pending != 0 && pending == ticket && revision == originRevision;
    }
    boolean expired(long now) {
        return pending != 0 && (now < started || now - started >= TIMEOUT_NANOS);
    }
    boolean finish(long ticket, long originRevision, long now) {
        if (!owns(ticket, originRevision)) return false;
        boolean valid = !expired(now);
        cancel();
        return valid;
    }
    void cancel() { pending = 0; }
    static boolean freshAnchor(long now, long created, double distanceSquared) {
        return created != 0 && now >= created && now - created < ANCHOR_NANOS
                && nearby(distanceSquared);
    }
    static boolean nearby(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= 4096;
    }
}
