package dev.nordfjell.guard;

/**
 * Pure 26.2 free-space arithmetic, not a complete movement predictor.
 * Caller must establish ordinary player movement: no fluids, climbing, vehicles,
 * flight, special block speed factors, levitation, slow falling or bounciness.
 * Ground support, collisions, step-up, impulses and packet ordering belong to the caller.
 */
final class OrdinaryPhysics {
    private static final double ANGLE_INDEX = 10430.378350470453;
    private static final float[] SINES = createSines();

    private OrdinaryPhysics() {}

    record State(double vx, double vy, double vz, int jumpDelay) {
        State {
            finite(vx, "vx"); finite(vy, "vy"); finite(vz, "vz");
            if (jumpDelay < 0 || jumpDelay > 10) throw new IllegalArgumentException("jumpDelay outside 0..10");
        }
    }

    /** Digital input directions. Opposite held keys cancel before this representation. */
    record Input(int strafe, int forward, boolean jump) {
        Input {
            if (Math.abs((long) strafe) > 1 || Math.abs((long) forward) > 1)
                throw new IllegalArgumentException("Digital input outside -1..1");
        }
    }

    /** movementSpeed already includes the server's sprint modifier. Multipliers are native floats. */
    record Context(boolean grounded, boolean sprinting, float yaw, float movementSpeed,
                   float jumpPower, double gravity, float blockFriction,
                   float airDragModifier, float frictionModifier,
                   float useMultiplier, float sneakMultiplier, boolean movingSlowly) {
        Context {
            finite(yaw, "yaw"); nonnegative(movementSpeed, "movementSpeed");
            nonnegative(jumpPower, "jumpPower"); nonnegative(gravity, "gravity");
            unit(blockFriction, "blockFriction");
            nonnegative(airDragModifier, "airDragModifier");
            nonnegative(frictionModifier, "frictionModifier");
            unit(useMultiplier, "useMultiplier"); unit(sneakMultiplier, "sneakMultiplier");
        }
    }

    /** Displacement before collision clipping, and velocity for the following free-space tick. */
    record Step(double dx, double dy, double dz, State next) {}

    static Step step(State state, Input input, Context context) {
        java.util.Objects.requireNonNull(state);
        java.util.Objects.requireNonNull(input);
        java.util.Objects.requireNonNull(context);
        double vx = state.vx, vy = state.vy, vz = state.vz;
        if (vx * vx + vz * vz < 9E-6) { vx = 0; vz = 0; }
        if (Math.abs(vy) < .003) vy = 0;
        int jumpDelay = Math.max(0, state.jumpDelay - 1);

        float radians = context.yaw * .017453292f;
        float sin = sine(radians), cos = cosine(radians);
        if (input.jump && context.grounded && jumpDelay == 0) {
            if (context.jumpPower > 1E-5f) {
                vy = Math.max(vy, context.jumpPower);
                if (context.sprinting) { vx -= sin * .2; vz += cos * .2; }
            }
            jumpDelay = 10;
        } else if (!input.jump) jumpDelay = 0;

        float x = input.strafe, z = input.forward;
        float length = (float) Math.sqrt(x * x + z * z);
        if (length > 0) {
            x /= length; z /= length;
            x *= .98f; z *= .98f;
            x *= context.useMultiplier; z *= context.useMultiplier;
            if (context.movingSlowly) { x *= context.sneakMultiplier; z *= context.sneakMultiplier; }
            length = (float) Math.sqrt(x * x + z * z);
            if (length > 0) {
                // Native square adjustment scales by a rounded reciprocal, unlike KeyboardInput's division.
                float inverseLength = 1 / length;
                float nx = x * inverseLength, nz = z * inverseLength;
                float ratio = Math.min(Math.abs(nx), Math.abs(nz)) / Math.max(Math.abs(nx), Math.abs(nz));
                float magnitude = Math.min(length * (float) Math.sqrt(1 + ratio * ratio), 1);
                x = nx * magnitude; z = nz * magnitude;
            }
        }

        float friction = context.grounded ? modifiedFriction(context.blockFriction, context.frictionModifier) : 1;
        float acceleration = context.grounded
                ? (friction > .6 ? context.movementSpeed * (.21600002f / (friction * friction * friction)) : context.movementSpeed)
                : (context.sprinting ? .025999999f : .02f);
        double ix = x, iz = z, inputLengthSquared = ix * ix + iz * iz;
        if (inputLengthSquared >= 1E-7) {
            if (inputLengthSquared > 1) { double scale = 1 / Math.sqrt(inputLengthSquared); ix *= scale; iz *= scale; }
            ix *= acceleration; iz *= acceleration;
            vx += ix * cos - iz * sin;
            vz += iz * cos + ix * sin;
        }
        float horizontalDrag = friction * modifiedFriction(.91f, context.airDragModifier);
        float verticalDrag = modifiedFriction(.98f, context.airDragModifier);
        return new Step(vx, vy, vz, new State(vx * horizontalDrag,
                (vy - context.gravity) * verticalDrag, vz * horizontalDrag, jumpDelay));
    }

    static float modifiedFriction(float base, float modifier) {
        unit(base, "base friction"); nonnegative(modifier, "friction modifier");
        return Math.max(0, Math.min(1, 1 - (1 - base) * modifier));
    }

    // Table indexing follows the verified 26.2 trigonometric quantization; allocated once, not per player.
    private static float sine(double radians) { return SINES[(int) ((long) (radians * ANGLE_INDEX) & 65535L)]; }
    private static float cosine(double radians) { return SINES[(int) ((long) (radians * ANGLE_INDEX + 16384) & 65535L)]; }
    private static float[] createSines() {
        var table = new float[65536];
        for (int i = 0; i < table.length; i++) table[i] = (float) Math.sin(i / ANGLE_INDEX);
        return table;
    }
    private static void finite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + name);
    }
    private static void nonnegative(double value, String name) {
        finite(value, name);
        if (value < 0) throw new IllegalArgumentException("Negative " + name);
    }
    private static void unit(float value, String name) {
        finite(value, name);
        if (value < 0 || value > 1) throw new IllegalArgumentException(name + " outside 0..1");
    }
}
