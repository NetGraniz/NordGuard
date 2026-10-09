package dev.nordfjell.guard;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Ordinary, zero-bounciness AABB collision arithmetic. No world or Bukkit access.
 * A complete loaded snapshot must cover the requested sweep and the step-up sweep.
 * This does not collect shapes, resolve existing penetration, back away from sneak
 * edges, or simulate dynamic blocks, world borders, fluids and entity impulses.
 */
final class CollisionPhysics {
    static final int MAX_SHAPES = 256;
    static final int MAX_STEP_CANDIDATES = 32;
    private static final double EPSILON = 1E-7;
    private static final double HORIZONTAL_EQUALITY = 9.999999747378752E-6;

    private CollisionPhysics() {}

    /** Snapshot once, then reuse for bounded candidate simulation; callers cannot mutate the list. */
    record Scene(List<Geometry.Box> shapes) {
        Scene {
            Objects.requireNonNull(shapes);
            if (shapes.size() > MAX_SHAPES) throw new IllegalArgumentException("Collision snapshot too large");
            shapes = List.copyOf(shapes);
            for (var box : shapes)
                if (box.minX() == box.maxX() || box.minY() == box.maxY() || box.minZ() == box.maxZ())
                    throw new IllegalArgumentException("Empty collision box");
        }
    }

    record Motion(double x, double y, double z) {
        Motion {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
                throw new IllegalArgumentException("Non-finite motion");
        }
        double horizontalSquared() { return x * x + z * z; }
    }

    /** Velocity is collision-adjusted, before ordinary gravity and drag. */
    record Result(Motion displacement, Motion velocity, boolean ground,
                  boolean collisionX, boolean collisionY, boolean collisionZ, boolean stepped) {
        boolean horizontalCollision() { return collisionX || collisionZ; }
    }

    static Result move(Geometry.Box body, Motion requested, Motion velocity,
                       boolean oldGround, float stepHeight, Scene scene) {
        Objects.requireNonNull(body); Objects.requireNonNull(requested);
        Objects.requireNonNull(velocity); Objects.requireNonNull(scene);
        if (!Float.isFinite(stepHeight) || stepHeight < 0 || stepHeight > 16)
            throw new IllegalArgumentException("Step height outside supported 0..16");
        if (body.minX() == body.maxX() || body.minY() == body.maxY() || body.minZ() == body.maxZ())
            throw new IllegalArgumentException("Empty body");
        for (var shape : scene.shapes)
            if (overlaps(body.minX(), body.maxX(), shape.minX(), shape.maxX())
                    && overlaps(body.minY(), body.maxY(), shape.minY(), shape.maxY())
                    && overlaps(body.minZ(), body.maxZ(), shape.minZ(), shape.maxZ()))
                throw new IllegalArgumentException("Initially intersecting geometry is unsupported");

        Motion baseline = clip(body, requested, scene.shapes);
        Motion selected = baseline;
        boolean downCollision = requested.y < 0 && requested.y != baseline.y;
        boolean horizontalClipped = requested.x != baseline.x || requested.z != baseline.z;
        boolean stepped = false;
        if (stepHeight > 0 && horizontalClipped && (oldGround || downCollision)) {
            var stepBody = downCollision ? shift(body, 0, baseline.y, 0) : body;
            var sweep = expandTowards(stepBody, requested.x, stepHeight, requested.z);
            if (!downCollision) sweep = expandTowards(sweep, 0, -9.999999747378752E-6, 0);
            float[] candidates = new float[scene.shapes.size() * 2];
            int count = 0;
            float baselineY = (float) baseline.y;
            for (var shape : scene.shapes) {
                if (!intersectsStrict(sweep, shape)) continue;
                float low = (float) (shape.minY() - stepBody.minY());
                float high = (float) (shape.maxY() - stepBody.minY());
                if (low >= 0 && low <= stepHeight && low != baselineY) candidates[count++] = low;
                if (high >= 0 && high <= stepHeight && high != baselineY) candidates[count++] = high;
            }
            Arrays.sort(candidates, 0, count);
            int unique = 0;
            for (int i = 0; i < count; i++)
                if (i == 0 || candidates[i] != candidates[i - 1]) unique++;
            if (unique > MAX_STEP_CANDIDATES)
                throw new IllegalArgumentException("Too many distinct step candidates");
            for (int i = 0; i < count; i++) {
                if (i > 0 && candidates[i] == candidates[i - 1]) continue;
                var candidate = clip(stepBody, new Motion(requested.x, candidates[i], requested.z), scene.shapes);
                if (candidate.horizontalSquared() > baseline.horizontalSquared()) {
                    selected = new Motion(candidate.x, candidate.y - (body.minY() - stepBody.minY()), candidate.z);
                    stepped = true;
                    break;
                }
            }
        }
        boolean x = Math.abs(requested.x - selected.x) >= HORIZONTAL_EQUALITY;
        boolean z = Math.abs(requested.z - selected.z) >= HORIZONTAL_EQUALITY;
        boolean y = requested.y != selected.y;
        return new Result(selected, new Motion(x ? 0 : velocity.x, y ? 0 : velocity.y, z ? 0 : velocity.z),
                y && requested.y < 0, x, y, z, stepped);
    }

    private static Motion clip(Geometry.Box body, Motion desired, List<Geometry.Box> shapes) {
        if (shapes.isEmpty()) return desired;
        double y = clipAxis(1, body, 0, 0, 0, desired.y, shapes);
        double x, z;
        if (Math.abs(desired.x) < Math.abs(desired.z)) {
            z = clipAxis(2, body, 0, y, 0, desired.z, shapes);
            x = clipAxis(0, body, 0, y, z, desired.x, shapes);
        } else {
            x = clipAxis(0, body, 0, y, 0, desired.x, shapes);
            z = clipAxis(2, body, x, y, 0, desired.z, shapes);
        }
        return new Motion(x, y, z);
    }

    private static double clipAxis(int axis, Geometry.Box body, double ox, double oy, double oz,
                                   double distance, List<Geometry.Box> shapes) {
        if (distance == 0) return 0;
        double min = minimum(body, axis) + offset(axis, ox, oy, oz);
        double max = maximum(body, axis) + offset(axis, ox, oy, oz);
        int second = (axis + 1) % 3, third = (axis + 2) % 3;
        for (var shape : shapes) {
            if (Math.abs(distance) < EPSILON) return 0;
            if (!overlaps(minimum(body, second) + offset(second, ox, oy, oz),
                    maximum(body, second) + offset(second, ox, oy, oz), minimum(shape, second), maximum(shape, second))
                    || !overlaps(minimum(body, third) + offset(third, ox, oy, oz),
                    maximum(body, third) + offset(third, ox, oy, oz), minimum(shape, third), maximum(shape, third))) continue;
            if (distance > 0) {
                double gap = minimum(shape, axis) - max;
                if (gap >= -EPSILON) distance = Math.min(distance, gap);
            } else {
                double gap = maximum(shape, axis) - min;
                if (gap <= EPSILON) distance = Math.max(distance, gap);
            }
        }
        return distance;
    }

    private static boolean overlaps(double min, double max, double otherMin, double otherMax) {
        return max - EPSILON > otherMin && min + EPSILON < otherMax;
    }
    private static boolean intersectsStrict(Geometry.Box a, Geometry.Box b) {
        return a.maxX() > b.minX() && a.minX() < b.maxX()
                && a.maxY() > b.minY() && a.minY() < b.maxY()
                && a.maxZ() > b.minZ() && a.minZ() < b.maxZ();
    }
    private static double minimum(Geometry.Box b, int axis) { return axis == 0 ? b.minX() : axis == 1 ? b.minY() : b.minZ(); }
    private static double maximum(Geometry.Box b, int axis) { return axis == 0 ? b.maxX() : axis == 1 ? b.maxY() : b.maxZ(); }
    private static double offset(int axis, double x, double y, double z) { return axis == 0 ? x : axis == 1 ? y : z; }
    private static Geometry.Box shift(Geometry.Box b, double x, double y, double z) {
        return new Geometry.Box(b.minX() + x, b.minY() + y, b.minZ() + z,
                b.maxX() + x, b.maxY() + y, b.maxZ() + z);
    }
    private static Geometry.Box expandTowards(Geometry.Box b, double x, double y, double z) {
        return new Geometry.Box(b.minX() + Math.min(0, x), b.minY() + Math.min(0, y), b.minZ() + Math.min(0, z),
                b.maxX() + Math.max(0, x), b.maxY() + Math.max(0, y), b.maxZ() + Math.max(0, z));
    }
}
