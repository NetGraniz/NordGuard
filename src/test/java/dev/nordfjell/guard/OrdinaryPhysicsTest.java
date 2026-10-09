package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrdinaryPhysicsTest {
    private static final OrdinaryPhysics.State REST = new OrdinaryPhysics.State(0, 0, 0, 0);
    private static final OrdinaryPhysics.Input FORWARD = new OrdinaryPhysics.Input(0, 1, false);
    private static OrdinaryPhysics.Context context(boolean ground, boolean sprint, float friction,
                                                    float airModifier, float frictionModifier,
                                                    float use, float sneak, boolean slow) {
        return new OrdinaryPhysics.Context(ground, sprint, 0, sprint ? .13f : .1f, .42f, .08,
                friction, airModifier, frictionModifier, use, sneak, slow);
    }
    private static OrdinaryPhysics.Context ordinary(boolean ground, boolean sprint) {
        return context(ground, sprint, .6f, 1, 1, 1, .3f, false);
    }

    @Test void ordinaryGroundAccelerationAndDrag() {
        var step = OrdinaryPhysics.step(REST, FORWARD, ordinary(true, false));
        assertEquals((double) .98f * .1f, step.dz(), 1E-12);
        assertEquals(step.dz() * (.6f * .91f), step.next().vz(), 1E-12);
        assertEquals(0, step.dy());
        assertEquals(-.08 * .98f, step.next().vy(), 1E-12);
    }

    @Test void sprintAttributeIsNotMultipliedAgain() {
        var step = OrdinaryPhysics.step(REST, FORWARD, ordinary(true, true));
        assertEquals((double) .98f * .13f, step.dz(), 1E-12);
    }

    @Test void diagonalInputUsesSquareAdjustment() {
        var diagonal = OrdinaryPhysics.step(REST, new OrdinaryPhysics.Input(1, 1, false), ordinary(true, false));
        var forward = OrdinaryPhysics.step(REST, FORWARD, ordinary(true, false));
        assertEquals(.1, Math.hypot(diagonal.dx(), diagonal.dz()), 2E-8);
        assertTrue(Math.hypot(diagonal.dx(), diagonal.dz()) > forward.dz());
    }

    @Test void airAccelerationUsesSprintStateRatherThanGroundSpeed() {
        assertEquals((double) .98f * .02f, OrdinaryPhysics.step(REST, FORWARD, ordinary(false, false)).dz(), 1E-12);
        assertEquals((double) .98f * .025999999f, OrdinaryPhysics.step(REST, FORWARD, ordinary(false, true)).dz(), 1E-12);
    }

    @Test void lowFrictionDoesNotUseOldInverseCubeAcceleration() {
        var step = OrdinaryPhysics.step(REST, FORWARD, context(true, false, .2f, 1, 1, 1, 1, false));
        assertEquals((double) .98f * .1f, step.dz(), 1E-12);
        assertEquals(step.dz() * (OrdinaryPhysics.modifiedFriction(.2f, 1) * .91f), step.next().vz(), 1E-12);
    }

    @Test void highFrictionUsesInverseCubeAcceleration() {
        var step = OrdinaryPhysics.step(REST, FORWARD, context(true, false, .98f, 1, 1, 1, 1, false));
        float acceleration = .1f * (.21600002f / (.98f * .98f * .98f));
        assertEquals((double) .98f * acceleration, step.dz(), 1E-12);
    }

    @Test void modifierFormulaClampsAtBothEnds() {
        assertEquals(1, OrdinaryPhysics.modifiedFriction(.6f, 0));
        assertEquals(.6f, OrdinaryPhysics.modifiedFriction(.6f, 1));
        assertEquals(0, OrdinaryPhysics.modifiedFriction(.6f, 10));
    }

    @Test void zeroAirDragModifierRetainsMomentumButNotGravity() {
        var step = OrdinaryPhysics.step(new OrdinaryPhysics.State(.2, .1, .3, 0),
                new OrdinaryPhysics.Input(0, 0, false), context(false, false, .6f, 0, 1, 1, 1, false));
        assertEquals(.2, step.next().vx()); assertEquals(.3, step.next().vz());
        assertEquals(.02, step.next().vy(), 1E-12);
    }

    @Test void useAndSneakMultiplyInputBeforeSquareAdjustment() {
        var step = OrdinaryPhysics.step(REST, FORWARD, context(true, false, .6f, 1, 1, .2f, .3f, true));
        float magnitude = .98f; magnitude *= .2f; magnitude *= .3f;
        assertEquals((double) magnitude * .1f, step.dz(), 1E-12);
    }

    @Test void squareAdjustmentPreservesNativeRoundedReciprocal() {
        var step = OrdinaryPhysics.step(REST, FORWARD,
                context(true, false, .6f, 1, 1, .011f, 1, false));
        float beforeSquareAdjustment = .98f * .011f;
        float nativeDirection = beforeSquareAdjustment * (1 / beforeSquareAdjustment);
        float nativeMagnitude = nativeDirection * beforeSquareAdjustment;
        assertNotEquals(Float.floatToIntBits(beforeSquareAdjustment), Float.floatToIntBits(nativeMagnitude));
        assertEquals(Float.floatToIntBits(nativeMagnitude), Float.floatToIntBits((float) (step.dz() / .1f)));
    }

    @Test void sprintJumpAddsImpulseBeforeDrag() {
        var step = OrdinaryPhysics.step(REST, new OrdinaryPhysics.Input(0, 0, true), ordinary(true, true));
        assertEquals(.2, step.dz(), 1E-12); assertEquals(.42f, step.dy());
        assertEquals(.2 * (.6f * .91f), step.next().vz(), 1E-12);
        assertEquals(10, step.next().jumpDelay());
    }

    @Test void jumpPreservesLargerExistingVerticalImpulse() {
        var step = OrdinaryPhysics.step(new OrdinaryPhysics.State(0, .8, 0, 0),
                new OrdinaryPhysics.Input(0, 0, true), ordinary(true, false));
        assertEquals(.8, step.dy());
    }

    @Test void jumpCooldownCountsDownAndReleaseResetsIt() {
        var held = OrdinaryPhysics.step(new OrdinaryPhysics.State(0, 0, 0, 3),
                new OrdinaryPhysics.Input(0, 0, true), ordinary(true, false));
        assertEquals(0, held.dy()); assertEquals(2, held.next().jumpDelay());
        var released = OrdinaryPhysics.step(held.next(), new OrdinaryPhysics.Input(0, 0, false), ordinary(true, false));
        assertEquals(0, released.next().jumpDelay());
    }

    @Test void playerHorizontalCutoffUsesCombinedVectorLength() {
        var step = OrdinaryPhysics.step(new OrdinaryPhysics.State(.0025, .002, .0025, 0),
                new OrdinaryPhysics.Input(0, 0, false), ordinary(false, false));
        assertEquals(.0025, step.dx()); assertEquals(.0025, step.dz()); assertEquals(0, step.dy());
        var stopped = OrdinaryPhysics.step(new OrdinaryPhysics.State(.002, 0, .002, 0),
                new OrdinaryPhysics.Input(0, 0, false), ordinary(false, false));
        assertEquals(0, stopped.dx()); assertEquals(0, stopped.dz());
    }

    @Test void yawRotatesForwardIntoNegativeX() {
        var base = ordinary(true, false);
        var rotated = new OrdinaryPhysics.Context(true, false, 90, base.movementSpeed(), base.jumpPower(),
                base.gravity(), base.blockFriction(), 1, 1, 1, .3f, false);
        var step = OrdinaryPhysics.step(REST, FORWARD, rotated);
        assertEquals(-(double) .98f * .1f, step.dx(), 1E-12);
        assertEquals(0, step.dz(), 1E-7);
    }

    @Test void finiteInputsAndDigitalBoundsAreRequired() {
        assertThrows(IllegalArgumentException.class, () -> new OrdinaryPhysics.State(Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new OrdinaryPhysics.State(0, 0, 0, 11));
        assertThrows(IllegalArgumentException.class, () -> new OrdinaryPhysics.Input(Integer.MIN_VALUE, 0, false));
        assertThrows(IllegalArgumentException.class, () -> context(true, false, 1.1f, 1, 1, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> context(true, false, .6f, Float.NaN, 1, 1, 1, false));
        assertThrows(IllegalArgumentException.class, () -> context(true, false, .6f, 1, -1, 1, 1, false));
    }

    @Test void inputStateRemainsImmutable() {
        var state = new OrdinaryPhysics.State(.1, .2, .3, 4);
        OrdinaryPhysics.step(state, FORWARD, ordinary(false, true));
        assertEquals(new OrdinaryPhysics.State(.1, .2, .3, 4), state);
    }
}
