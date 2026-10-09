package dev.nordfjell.guard;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollisionPhysicsTest {
    private static final Geometry.Box BODY = box(.2, 1, .2, .8, 2.8, .8);
    private static final Geometry.Box FLOOR = box(-5, 0, -5, 5, 1, 5);
    private static Geometry.Box box(double x, double y, double z, double xx, double yy, double zz) {
        return new Geometry.Box(x, y, z, xx, yy, zz);
    }
    private static CollisionPhysics.Motion motion(double x, double y, double z) {
        return new CollisionPhysics.Motion(x, y, z);
    }
    private static CollisionPhysics.Result move(CollisionPhysics.Motion desired, boolean ground, float step,
                                                Geometry.Box... shapes) {
        return CollisionPhysics.move(BODY, desired, desired, ground, step,
                new CollisionPhysics.Scene(List.of(shapes)));
    }

    @Test void freeSpacePreservesDisplacementAndVelocity() {
        var velocity = motion(.4, .2, -.1);
        var result = move(velocity, false, .6f);
        assertEquals(velocity, result.displacement()); assertEquals(velocity, result.velocity());
        assertFalse(result.ground()); assertFalse(result.horizontalCollision()); assertFalse(result.collisionY());
    }

    @Test void floorClipsDownwardAndResetsVerticalVelocity() {
        var result = move(motion(.1, -.08, 0), false, 0, FLOOR);
        assertEquals(0, result.displacement().y()); assertTrue(result.ground());
        assertEquals(0, result.velocity().y()); assertEquals(.1, result.velocity().x());
    }

    @Test void fullWallClipsWithoutAllowingStepAboveLimit() {
        var result = move(motion(.5, -.08, 0), true, .6f, FLOOR, box(1, 1, 0, 2, 3, 1));
        assertEquals(.2, result.displacement().x(), 1E-12);
        assertTrue(result.collisionX()); assertEquals(0, result.velocity().x()); assertFalse(result.stepped());
    }

    @Test void halfSlabStepsUsingItsActualTop() {
        var result = move(motion(.5, -.08, 0), true, .6f, FLOOR, box(1, 1, 0, 2, 1.5, 1));
        assertEquals(.5, result.displacement().x(), 1E-12); assertEquals(.5, result.displacement().y(), 1E-12);
        assertTrue(result.stepped()); assertTrue(result.ground()); assertEquals(.5, result.velocity().x());
    }

    @Test void stairLowerTreadWinsBeforeHigherCandidate() {
        var result = move(motion(.5, -.08, 0), true, 1.1f, FLOOR,
                box(1, 1, 0, 2, 1.5, 1), box(1.5, 1.5, 0, 2, 2, 1));
        assertEquals(.5, result.displacement().y()); assertEquals(.5, result.displacement().x());
    }

    @Test void stepRequiresHeadClearance() {
        var result = move(motion(.5, -.08, 0), true, .6f, FLOOR,
                box(1, 1, 0, 2, 1.5, 1), box(0, 3, 0, 2, 4, 1));
        assertFalse(result.stepped()); assertEquals(.2, result.displacement().x(), 1E-12);
    }

    @Test void airborneSideCollisionDoesNotGrantStep() {
        var result = move(motion(.5, .1, 0), false, .6f, box(1, 1, 0, 2, 1.5, 1));
        assertFalse(result.stepped()); assertEquals(.2, result.displacement().x(), 1E-12);
    }

    @Test void downwardLandingCanStepEvenWithoutPreviousGroundFlag() {
        var fallingBody = box(.2, 1.2, .2, .8, 3, .8);
        var desired = motion(.5, -.4, 0);
        var result = CollisionPhysics.move(fallingBody, desired, desired, false, .6f,
                new CollisionPhysics.Scene(List.of(FLOOR, box(1, 1, 0, 2, 1.5, 1))));
        assertTrue(result.stepped()); assertEquals(.3, result.displacement().y(), 1E-7);
        assertEquals(.5, result.displacement().x());
    }

    @Test void verticalAxisClipsBeforeHorizontalMovement() {
        var result = move(motion(2, 1, 0), false, 0, box(0, 3, 0, 1, 4, 1));
        assertEquals(.2, result.displacement().y(), 1E-12);
        assertEquals(2, result.displacement().x()); assertEquals(0, result.velocity().y());
    }

    @Test void largerZAxisMovesBeforeX() {
        var result = move(motion(.5, 0, 1), false, 0, box(1, 1, 0, 2, 3, 1));
        assertEquals(1, result.displacement().z()); assertEquals(.5, result.displacement().x());
    }

    @Test void tiedHorizontalAxesMoveXBeforeZ() {
        var result = move(motion(.5, 0, .5), false, 0, box(1, 1, 0, 2, 3, 1));
        assertEquals(.2, result.displacement().x(), 1E-12); assertEquals(.5, result.displacement().z());
    }

    @Test void negativeCoordinatesAndNegativeMotionClipSymmetrically() {
        var body = box(-.8, 1, -.8, -.2, 2.8, -.2);
        var desired = motion(-.5, 0, 0);
        var result = CollisionPhysics.move(body, desired, desired, false, 0,
                new CollisionPhysics.Scene(List.of(box(-2, 1, -1, -1, 3, 0))));
        assertEquals(-.2, result.displacement().x(), 1E-12); assertTrue(result.collisionX());
    }

    @Test void grazingOtherAxisDoesNotCatchABoxEdge() {
        var result = move(motion(.5, 0, 0), false, 0, box(1, 1, .8, 2, 3, 1.8));
        assertEquals(.5, result.displacement().x()); assertFalse(result.collisionX());
    }

    @Test void snapshotIsImmutableAndObstaclesNeedNotBeOrdered() {
        var source = new ArrayList<>(List.of(box(2, 1, 0, 3, 3, 1), box(1, 1, 0, 2, 3, 1)));
        var scene = new CollisionPhysics.Scene(source); source.clear();
        var desired = motion(3, 0, 0);
        var result = CollisionPhysics.move(BODY, desired, desired, false, 0, scene);
        assertEquals(.2, result.displacement().x(), 1E-12);
        assertEquals(2, scene.shapes().size()); assertThrows(UnsupportedOperationException.class, () -> scene.shapes().clear());
    }

    @Test void initialPenetrationDefersInsteadOfGuessingEscapeDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> move(motion(.5, 0, 0), false, 0, box(.5, 1, 0, 1.5, 3, 1)));
    }

    @Test void horizontalFlagsUseNativeEqualityTolerance() {
        var result = move(motion(1E-8, 0, 0), false, 0, box(3, 1, 0, 4, 3, 1));
        assertEquals(0, result.displacement().x());
        assertFalse(result.collisionX()); assertEquals(1E-8, result.velocity().x());
    }

    @Test void tooManyDistinctStepHeightsDefersInsteadOfUnboundedSearch() {
        var shapes = new ArrayList<Geometry.Box>(); shapes.add(FLOOR);
        for (int i = 0; i < 40; i++) {
            double y = 1 + i * .015;
            shapes.add(box(1, y, 0, 2, y + .005, 1));
        }
        var desired = motion(.5, -.08, 0);
        var scene = new CollisionPhysics.Scene(shapes);
        assertThrows(IllegalArgumentException.class,
                () -> CollisionPhysics.move(BODY, desired, desired, true, .6f, scene));
    }

    @Test void unsupportedSizesAndNonfiniteMotionAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CollisionPhysics.Motion(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> move(motion(0, 0, 0), true, Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new CollisionPhysics.Scene(java.util.Collections.nCopies(257, FLOOR)));
        assertThrows(IllegalArgumentException.class,
                () -> new CollisionPhysics.Scene(List.of(box(0, 0, 0, 0, 1, 1))));
    }
}
