package dev.nordfjell.guard;

import java.util.EnumSet;

/** Pure bounded state. No world access, client-ground flags or shared mutable state. */
final class MovementModel {
    record Frame(double x, double y, double z, boolean ground, boolean wall, boolean exempt,
                 double speed, double jump, double step, double gravity,
                 boolean liquidSurface, boolean climbing, boolean web, double useMultiplier) {
        Frame(double x, double y, double z, boolean ground, boolean wall, boolean exempt,
              double speed, double jump, double step, double gravity, boolean water, boolean climb, boolean web) {
            this(x, y, z, ground, wall, exempt, speed, jump, step, gravity, water, climb, web, 1);
        }
        Frame(double x, double y, double z, boolean ground, boolean wall, boolean exempt,
              double speed, double jump, double step, double gravity) {
            this(x, y, z, ground, wall, exempt, speed, jump, step, gravity, false, false, false, 1);
        }
        boolean medium() { return liquidSurface || climbing || web; }
    }
    record Result(EnumSet<Check> flags, double landingDistance, boolean clean) {}
    private Frame last;
    private int airTicks, groundTicks, jumpAge, descentAge;
    private double lastDy, peakY, speedDebt, jumpMomentum, descentDy, idleSpeedCredit, idleSpeedLimit;
    private boolean falling;
    private final double[] scores = new double[Check.values().length];

    void reset() {
        last = null; airTicks = groundTicks = 0; jumpAge = descentAge = 100;
        lastDy = speedDebt = jumpMomentum = descentDy = idleSpeedCredit = idleSpeedLimit = 0; falling = false;
        java.util.Arrays.fill(scores, 0);
    }

    /** Account for elapsed owner time without repeating world queries. Credit is burst-bounded. */
    void stationaryTick() {
        if (last == null || !last.ground() || last.exempt() || last.medium()) return;
        jumpAge = Math.min(100, jumpAge + 1);
        jumpMomentum *= .91;
        idleSpeedTick(last.speed() + jumpMomentum + .02);
        int speed = Check.SPEED.ordinal();
        scores[speed] = Math.max(0, scores[speed] - .25);
    }

    /** No new native movement or TickEnd and no changed owner position: no hover evidence. */
    void transportIdleTick() {
        if (last == null || last.exempt() || last.medium()) return;
        idleSpeedTick(last.speed() + jumpMomentum + .02);
        int speed = Check.SPEED.ordinal();
        scores[speed] = Math.max(0, scores[speed] - .25);
        // Preserve vertical history until data arrives. Only owner ticks create bounded allowance.
    }

    private void idleSpeedTick(double allowance) {
        double repaid = Math.min(speedDebt, allowance);
        speedDebt -= repaid;
        idleSpeedCredit = Math.min(idleSpeedLimit, idleSpeedCredit + allowance - repaid);
    }

    Result accept(Frame next, Policy policy) {
        return accept(next, policy, Double.NaN);
    }

    Result accept(Frame next, Policy policy, double supportY) {
        var flags = EnumSet.noneOf(Check.class);
        if (last == null || next.exempt() || last.exempt()) {
            reset(); last = next; peakY = next.y();
            idleSpeedLimit = next.speed() * policy.burstTicks();
            // One position after a reset proves no displacement, so it cannot replace a return anchor.
            return new Result(flags, 0, false);
        }
        double dx = next.x() - last.x(), dy = next.y() - last.y(), dz = next.z() - last.z();
        double horizontal = Math.hypot(dx, dz);
        if (!Double.isFinite(horizontal) || !Double.isFinite(dy)) {
            reset(); last = next;
            return new Result(flags, 0, false);
        }
        groundTicks = next.ground() && last.ground() ? Math.min(100, groundTicks + 1) : 0;
        jumpAge = Math.min(100, jumpAge + 1);
        descentAge = Math.min(100, descentAge + 1);
        score(Check.NOSLOW, groundTicks >= 6 && !next.medium() && !last.medium()
                && next.useMultiplier() < .99 && last.useMultiplier() < .99
                && horizontal > next.speed() * next.useMultiplier() + .035 + jumpMomentum, policy, flags);
        score(Check.WATERWALK, next.liquidSurface() && last.liquidSurface()
                && !next.ground() && Math.abs(dy) <= .12, policy, flags, Math.max(10, policy.buffer()));
        score(Check.CLIMB, next.climbing() && last.climbing() && dy > .24,
                policy, flags, policy.buffer());
        score(Check.NOWEB, next.web() && last.web()
                && (horizontal > next.speed() * .4 + .03 || Math.abs(dy) > .15),
                policy, flags, policy.buffer());
        if (next.medium() || last.medium()) {
            score(Check.FLIGHT, false, policy, flags);
            score(Check.SPIDER, false, policy, flags);
            score(Check.SPEED, false, policy, flags);
            score(Check.HIGHJUMP, false, policy, flags);
            speedDebt = jumpMomentum = idleSpeedCredit = 0; airTicks = 0; falling = false;
            boolean supportedIdle = next.ground() && last.ground() && horizontal < .01 && Math.abs(dy) < .01;
            for (double evidence : scores) supportedIdle &= evidence == 0;
            peakY = next.y(); lastDy = dy; last = next;
            return new Result(flags, 0, supportedIdle);
        }
        // Owner snapshots can coalesce the first few native steps of a jump.
        // Match their cumulative height; do not grant momentum to arbitrary micro-hops.
        jumpMomentum *= .91;
        int jumpSteps = last.ground() && !next.ground() ? jumpSteps(dy, next.jump(), next.gravity()) : 0;
        // A batched landing/takeoff may have no grounded owner snapshot at all.
        // Require server collision support and descent near that floor, not the client ground bit.
        boolean missedLanding = !last.ground() && !next.ground() && Double.isFinite(supportY)
                && jumpAge >= 10 && descentAge <= 2 && lastDy <= 0 && dy > 0
                && last.y() >= supportY && last.y() - supportY <= .6
                && last.y() + (descentDy - next.gravity()) * .98 <= supportY + .01;
        if (jumpSteps == 0 && missedLanding) jumpSteps = jumpSteps(next.y() - supportY, next.jump(), next.gravity());
        if (jumpSteps != 0) {
            jumpMomentum = .2;
            for (int i = 1; i < jumpSteps; i++) jumpMomentum *= .91;
            airTicks = 0;
            jumpAge = 0;
            descentAge = 100;
        }
        double allowed = next.speed() + jumpMomentum + .02;
        idleSpeedLimit = next.speed() * policy.burstTicks();
        idleSpeedCredit = Math.min(idleSpeedCredit, idleSpeedLimit);
        if (horizontal < 1.0E-5) idleSpeedTick(allowed);
        else {
            // A zero-position owner tick may precede, rather than follow, the next TCP batch.
            // Consume its allowance once. Client MOVE/TickEnd counts never mint speed credit.
            double required = Math.max(0, speedDebt + horizontal - allowed);
            double spent = Math.min(idleSpeedCredit, required);
            speedDebt = Math.min(100, required - spent);
            idleSpeedCredit -= spent;
        }
        score(Check.SPEED, speedDebt > next.speed() * policy.burstTicks() + policy.horizontalMargin(), policy, flags);
        if (next.ground()) {
            score(Check.HIGHJUMP, dy > next.step() + policy.verticalMargin(), policy, flags);
        } else {
            score(Check.HIGHJUMP, jumpSteps == 0 && dy > next.jump() + policy.verticalMargin(), policy, flags);
        }
        if (!next.ground()) airTicks++; else airTicks = 0;
        double expectedDy = (lastDy - next.gravity()) * 0.98;
        int ascentAllowance = (int) Math.ceil(next.jump() / Math.max(.001, next.gravity())) + 6;
        score(Check.FLIGHT, next.gravity() > .001 && airTicks > Math.max(12, ascentAllowance) && (dy > expectedDy + Math.min(.06, policy.verticalMargin())
                || airTicks > Math.max(16, ascentAllowance) && dy > -0.03), policy, flags);
        score(Check.SPIDER, next.gravity() > .001 && !last.ground() && !next.ground() && next.wall() && dy > .08
                && dy > expectedDy + .04 + policy.verticalMargin() * .125,
                policy, flags, Math.min(3, policy.buffer()));
        if (!falling) { peakY = Math.max(last.y(), next.y()); falling = !next.ground(); }
        else peakY = Math.max(peakY, next.y());
        double landing = next.ground() && falling ? Math.max(0, peakY - next.y()) : 0;
        if (next.ground()) { falling = false; peakY = next.y(); }
        if (dy < -.001) { descentDy = dy; descentAge = 0; }
        else if (dy > .001) descentAge = 100;
        lastDy = dy; last = next;
        boolean clean = true;
        for (int i = 0; i < Check.NOFALL.ordinal(); i++) clean &= scores[i] == 0;
        return new Result(flags, landing, clean && speedDebt < 0.01);
    }

    private static int jumpSteps(double height, double jump, double gravity) {
        if (jump <= 0 || gravity <= 0 || height <= 0) return 0;
        double cumulative = 0, velocity = jump;
        for (int ticks = 1; ticks <= 3 && velocity > 0; ticks++) {
            cumulative += velocity;
            if (Math.abs(height - cumulative) <= .035) return ticks;
            velocity = (velocity - gravity) * .98;
        }
        return 0;
    }

    private void score(Check check, boolean suspicious, Policy policy, EnumSet<Check> flags) {
        score(check, suspicious, policy, flags, policy.buffer());
    }
    private void score(Check check, boolean suspicious, Policy policy, EnumSet<Check> flags, int threshold) {
        int i = check.ordinal();
        if (policy.modes().get(check) == Policy.Mode.OFF) { scores[i] = 0; return; }
        scores[i] = suspicious ? Math.min(threshold + 2, scores[i] + 1) : Math.max(0, scores[i] - 0.25);
        if (suspicious && scores[i] >= threshold) flags.add(check);
    }
}
