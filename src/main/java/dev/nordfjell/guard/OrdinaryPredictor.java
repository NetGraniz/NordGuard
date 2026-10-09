package dev.nordfjell.guard;

/**
 * Bounded, two-branch ordinary movement prototype. The caller owns this state
 * and supplies complete, client-visible collision geometry and ordinary attributes.
 * No observed position or displacement becomes predicted velocity. Unsupported
 * contexts and more than two ambiguous states clear the model, never produce a violation.
 * At most 36 candidate trials run per frame. A caller must cover both possible
 * positions with complete geometry and supply block friction independently of ground claims.
 */
final class OrdinaryPredictor {
    static final double POSITION_TOLERANCE = .003;
    static final double OMITTED_POSITION_TOLERANCE = .0002;
    static final int MAX_CANDIDATES = 36;
    private static final double STATE_EQUALITY = 1E-7;
    private static final OrdinaryPhysics.Input[] INPUTS = inputs();

    enum Status { ACCEPT, REJECT, DEFER }

    record Frame(double x, double y, double z, float yaw, boolean hasPosition) {
        Frame {
            if (!Float.isFinite(yaw) || hasPosition && (!Double.isFinite(x)
                    || !Double.isFinite(y) || !Double.isFinite(z)))
                throw new IllegalArgumentException("Non-finite observed frame");
        }
    }

    record Result(Status status, double error, int candidates, int consecutiveRejected,
                  Geometry.Point predicted, CollisionPhysics.Motion displacement, String reason) {}

    private boolean seeded, ground;
    private double x, y, z, reportedX, reportedY, reportedZ;
    private OrdinaryPhysics.State velocity;
    private Branch alternate;
    private int consecutiveRejected;

    /** Only call for a proven reset: for example stable support with established native resting velocity. */
    void seed(double x, double y, double z, OrdinaryPhysics.State velocity, boolean ground) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite seed");
        this.velocity = java.util.Objects.requireNonNull(velocity);
        this.x = reportedX = x; this.y = reportedY = y; this.z = reportedZ = z;
        this.ground = ground; seeded = true; consecutiveRejected = 0; alternate = null;
    }

    void clear() { seeded = false; velocity = null; consecutiveRejected = 0; alternate = null; }
    boolean seeded() { return seeded; }
    double x() { return x; }
    double y() { return y; }
    double z() { return z; }
    Geometry.Point position() { return seeded ? new Geometry.Point(x, y, z) : null; }
    OrdinaryPhysics.State velocity() { return velocity; }
    boolean ground() { return ground; }
    int branches() { return seeded ? alternate == null ? 1 : 2 : 0; }
    private record Branch(double x, double y, double z, OrdinaryPhysics.State velocity, boolean ground) {}

    Result accept(Frame frame, OrdinaryPhysics.Context supplied, CollisionPhysics.Scene scene, float stepHeight) {
        java.util.Objects.requireNonNull(frame);
        if (!seeded || supplied == null || scene == null) return defer(0, "context_unavailable");
        double observedX = frame.hasPosition ? frame.x : reportedX;
        double observedY = frame.hasPosition ? frame.y : reportedY;
        double observedZ = frame.hasPosition ? frame.z : reportedZ;
        double tolerance = frame.hasPosition ? POSITION_TOLERANCE : OMITTED_POSITION_TOLERANCE;
        double bestError = Double.POSITIVE_INFINITY;
        double bestX = x, bestY = y, bestZ = z;
        OrdinaryPhysics.State bestVelocity = null;
        CollisionPhysics.Motion bestDisplacement = null;
        boolean bestGround = ground;
        Branch plausibleA = null, plausibleB = null;
        int candidates = 0;
        Branch primary = new Branch(x, y, z, velocity, ground);
        Branch[] starts = alternate == null ? new Branch[]{primary} : new Branch[]{primary, alternate};
        try {
            for (var start : starts) {
                var context = supplied.grounded() == start.ground && supplied.yaw() == frame.yaw ? supplied
                        : new OrdinaryPhysics.Context(start.ground, supplied.sprinting(), frame.yaw,
                        supplied.movementSpeed(), supplied.jumpPower(), supplied.gravity(), supplied.blockFriction(),
                        supplied.airDragModifier(), supplied.frictionModifier(), supplied.useMultiplier(),
                        supplied.sneakMultiplier(), supplied.movingSlowly());
                var body = new Geometry.Box(start.x - .3, start.y, start.z - .3,
                        start.x + .3, start.y + 1.8, start.z + .3);
                float friction = start.ground ? OrdinaryPhysics.modifiedFriction(context.blockFriction(), context.frictionModifier()) : 1;
                float horizontalDrag = friction * OrdinaryPhysics.modifiedFriction(.91f, context.airDragModifier());
                float verticalDrag = OrdinaryPhysics.modifiedFriction(.98f, context.airDragModifier());
            for (var input : INPUTS) {
                candidates++;
                var free = OrdinaryPhysics.step(start.velocity, input, context);
                var raw = new CollisionPhysics.Motion(free.dx(), free.dy(), free.dz());
                var collision = CollisionPhysics.move(body, raw, raw, start.ground, stepHeight, scene);
                var displacement = collision.displacement();
                double nextX = start.x + displacement.x(), nextY = start.y + displacement.y(), nextZ = start.z + displacement.z();
                var next = new OrdinaryPhysics.State(collision.velocity().x() * horizontalDrag,
                        (collision.velocity().y() - context.gravity()) * verticalDrag,
                        collision.velocity().z() * horizontalDrag, free.next().jumpDelay());
                double error = distance(nextX, nextY, nextZ, observedX, observedY, observedZ);
                if (error <= tolerance) {
                    var branch = new Branch(nextX, nextY, nextZ, next, collision.ground());
                    if (plausibleA == null) plausibleA = branch;
                    else if (samePhysicalState(branch, plausibleA)) {
                        if (next.jumpDelay() < plausibleA.velocity.jumpDelay()) plausibleA = branch;
                    } else if (plausibleB == null) plausibleB = branch;
                    else if (samePhysicalState(branch, plausibleB)) {
                        if (next.jumpDelay() < plausibleB.velocity.jumpDelay()) plausibleB = branch;
                    } else return defer(candidates, "ambiguous_candidate_states");
                }
                if (bestVelocity == null || error < bestError || error == bestError
                        && next.jumpDelay() < bestVelocity.jumpDelay()) {
                    bestError = error; bestX = nextX; bestY = nextY; bestZ = nextZ;
                    bestVelocity = next; bestGround = collision.ground(); bestDisplacement = displacement;
                }
            }
            }
        } catch (IllegalArgumentException unsupported) {
            return defer(candidates, "unsupported_collision_or_arithmetic");
        }
        x = bestX; y = bestY; z = bestZ; velocity = bestVelocity; ground = bestGround;
        alternate = plausibleB == null ? null : samePhysicalState(new Branch(x, y, z, velocity, ground), plausibleA)
                ? plausibleB : plausibleA;
        // This is only the protocol's last reported anchor, not an internal simulation reset.
        if (frame.hasPosition) { reportedX = frame.x; reportedY = frame.y; reportedZ = frame.z; }
        boolean accepted = bestError <= tolerance;
        consecutiveRejected = accepted ? 0 : Math.min(Integer.MAX_VALUE - 1, consecutiveRejected) + 1;
        return new Result(accepted ? Status.ACCEPT : Status.REJECT, bestError, candidates, consecutiveRejected,
                new Geometry.Point(x, y, z), bestDisplacement, accepted ? "within_absolute_error" : "outside_absolute_error");
    }

    private Result defer(int candidates, String reason) {
        clear();
        return new Result(Status.DEFER, Double.NaN, candidates, 0, null, null, reason);
    }
    private static double distance(double x, double y, double z, double xx, double yy, double zz) {
        return Math.hypot(Math.hypot(x - xx, z - zz), y - yy);
    }
    private static boolean samePhysicalState(Branch a, Branch b) {
        return a.ground == b.ground
                && distance(a.x, a.y, a.z, b.x, b.y, b.z) <= STATE_EQUALITY
                && distance(a.velocity.vx(), a.velocity.vy(), a.velocity.vz(),
                b.velocity.vx(), b.velocity.vy(), b.velocity.vz()) <= STATE_EQUALITY;
    }
    private static OrdinaryPhysics.Input[] inputs() {
        var result = new OrdinaryPhysics.Input[18];
        int index = 0;
        // Prefer no input and released jump when multiple candidates have the same physical state.
        for (boolean jump : new boolean[]{false, true}) {
            result[index++] = new OrdinaryPhysics.Input(0, 0, jump);
            for (int strafe = -1; strafe <= 1; strafe++) for (int forward = -1; forward <= 1; forward++)
                if (strafe != 0 || forward != 0) result[index++] = new OrdinaryPhysics.Input(strafe, forward, jump);
        }
        return result;
    }
}
