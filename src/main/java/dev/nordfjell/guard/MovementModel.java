package dev.nordfjell.guard;

import java.util.EnumSet;

/** Pure bounded state. No world access, client-ground flags or shared mutable state. */
final class MovementModel {
    record Frame(double x, double y, double z, boolean ground, boolean wall, boolean exempt,
                 double speed, double jump, double step, double gravity) {}
    record Result(EnumSet<Check> flags, double landingDistance, boolean clean) {}
    private Frame last;
    private int airTicks;
    private double lastDy, peakY, speedDebt;
    private boolean falling;
    private final double[] scores = new double[Check.values().length];

    void reset() {
        last = null; airTicks = 0; lastDy = speedDebt = 0; falling = false;
        java.util.Arrays.fill(scores, 0);
    }

    Result accept(Frame next, Policy policy) {
        var flags = EnumSet.noneOf(Check.class);
        if (last == null || next.exempt() || last.exempt()) {
            reset(); last = next; peakY = next.y();
            // One position after a reset proves no displacement, so it cannot replace a return anchor.
            return new Result(flags, 0, false);
        }
        double dx = next.x() - last.x(), dy = next.y() - last.y(), dz = next.z() - last.z();
        double horizontal = Math.hypot(dx, dz);
        if (!Double.isFinite(horizontal) || !Double.isFinite(dy)) {
            reset(); last = next;
            return new Result(flags, 0, false);
        }
        double allowed = next.speed() + policy.horizontalMargin();
        speedDebt = Math.min(100, Math.max(0, speedDebt + horizontal - allowed));
        score(Check.SPEED, speedDebt > allowed * policy.burstTicks(), policy, flags);
        if (next.ground()) {
            score(Check.HIGHJUMP, dy > next.step() + policy.verticalMargin(), policy, flags);
        } else {
            score(Check.HIGHJUMP, dy > next.jump() + policy.verticalMargin(), policy, flags);
        }
        if (!next.ground()) airTicks++; else airTicks = 0;
        double expectedDy = (lastDy - next.gravity()) * 0.98;
        int ascentAllowance = (int) Math.ceil(next.jump() / Math.max(.001, next.gravity())) + 6;
        score(Check.FLIGHT, airTicks > Math.max(12, ascentAllowance) && (dy > expectedDy + policy.verticalMargin()
                || airTicks > Math.max(16, ascentAllowance) && dy > -0.03), policy, flags);
        score(Check.SPIDER, airTicks > Math.max(7, ascentAllowance) && next.wall() && dy > 0.08, policy, flags);
        if (!falling) { peakY = Math.max(last.y(), next.y()); falling = !next.ground(); }
        else peakY = Math.max(peakY, next.y());
        double landing = next.ground() && falling ? Math.max(0, peakY - next.y()) : 0;
        if (next.ground()) { falling = false; peakY = next.y(); }
        lastDy = dy; last = next;
        boolean clean = true;
        for (int i = 0; i < Check.NOFALL.ordinal(); i++) clean &= scores[i] == 0;
        return new Result(flags, landing, clean && speedDebt < 0.01);
    }

    private void score(Check check, boolean suspicious, Policy policy, EnumSet<Check> flags) {
        int i = check.ordinal();
        if (policy.modes().get(check) == Policy.Mode.OFF) { scores[i] = 0; return; }
        scores[i] = suspicious ? Math.min(policy.buffer() + 2, scores[i] + 1) : Math.max(0, scores[i] - 0.25);
        if (suspicious && scores[i] >= policy.buffer()) flags.add(check);
    }
}
