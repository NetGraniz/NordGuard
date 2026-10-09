package dev.nordfjell.guard;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediumMovementTest {
    @Test void zeroGravityAttributeDoesNotMakeHoverOrAscentIllegal() {
        var model = new MovementModel();
        for (int i = 0; i < 500; i++) assertTrue(model.accept(new MovementModel.Frame(
                0, 64 + i * .2, 0, false, true, false, .286, .42, .6, 0), MovementModelTest.policy()).flags().isEmpty());
    }
    private static MovementModel.Frame frame(double x, double y, boolean ground, boolean wall,
                                              boolean water, boolean climb, boolean web) {
        return new MovementModel.Frame(x, y, 0, ground, wall, false, .286, .42, .6, .08, water, climb, web);
    }
    @Test void miniHopSpeedDetectedBelowOldAllowance() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 60; i++) flags.addAll(model.accept(
                frame(i * .5, 64 + (i % 3 == 1 ? .1 : 0), i % 3 != 1, false, false, false, false), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.SPEED));
    }
    @Test void ordinaryGroundSprintDoesNotAccumulateDebt() {
        var model = new MovementModel();
        for (int i = 0; i < 400; i++) assertTrue(model.accept(
                frame(i * .286, 64, true, false, false, false, false), MovementModelTest.policy()).flags().isEmpty());
    }
    @Test void repeatedSprintJumpMomentumIsAllowed() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        double x = 0;
        model.accept(frame(x, 64, true, false, false, false, false), policy);
        for (int jump = 0; jump < 25; jump++) {
            double y = 64, dy = .42, momentum = .2;
            for (int tick = 0; tick < 20; tick++) {
                x += .286 + momentum; y += dy; dy = (dy - .08) * .98; momentum *= .91;
                boolean ground = y <= 64; if (ground) y = 64;
                assertTrue(model.accept(frame(x, y, ground, false, false, false, false), policy).flags().isEmpty());
                if (ground) break;
            }
        }
    }
    @Test void spiderEvidenceTriggersBeforeOneBlock() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(frame(0, 64, true, true, false, false, false), policy);
        int flagged = 0;
        for (int i = 1; i <= 5; i++) {
            if (model.accept(frame(0, 64 + i * .2, false, true, false, false, false), policy).flags().contains(Check.SPIDER)) {
                flagged = i; break;
            }
        }
        assertTrue(flagged > 0 && flagged * .2 < 1);
    }
    @Test void normalJumpAgainstWallIsNotSpider() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(frame(0, 64, true, true, false, false, false), policy);
        double y = 64, dy = .42;
        for (int i = 0; i < 12; i++) {
            y += dy; dy = (dy - .08) * .98;
            boolean ground = y <= 64; if (ground) y = 64;
            assertTrue(model.accept(frame(0, y, ground, true, false, false, false), policy).flags().isEmpty());
            if (ground) break;
        }
    }
    @Test void glideOutsideVanillaFallingCurveDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 60; i++) flags.addAll(model.accept(
                frame(0, 100 - i * .125, false, false, false, false, false), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.FLIGHT));
    }
    @Test void liquidSurfaceOscillationsDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 30; i++) flags.addAll(model.accept(
                frame(i * .1, 64 + (i % 4 == 0 ? -.05 : .05), false, false, true, false, false), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.WATERWALK));
        assertFalse(flags.contains(Check.FLIGHT));
    }
    @Test void briefSurfaceCrossingIsNotWaterwalk() {
        var model = new MovementModel();
        for (int i = 0; i < 8; i++) assertTrue(model.accept(
                frame(i * .2, 64, false, false, true, false, false), MovementModelTest.policy()).flags().isEmpty());
    }
    @Test void ordinaryClimbDoesNotTriggerAirChecks() {
        var model = new MovementModel();
        for (int i = 0; i < 100; i++) assertTrue(model.accept(
                frame(0, 64 + i * .2, false, true, false, true, false), MovementModelTest.policy()).flags().isEmpty());
    }
    @Test void fastLadderAscentDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 20; i++) flags.addAll(model.accept(
                frame(0, 64 + i * .2872, false, true, false, true, false), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.CLIMB));
        assertFalse(flags.contains(Check.SPIDER));
    }
    @Test void cobwebSlowMovementAllowed() {
        var model = new MovementModel();
        for (int i = 0; i < 80; i++) assertTrue(model.accept(
                frame(i * .03, 64 - i * .02, false, false, false, false, true), MovementModelTest.policy()).flags().isEmpty());
    }
    @Test void cobwebNormalRunningSpeedDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 30; i++) flags.addAll(model.accept(
                frame(i * .25, 64, true, false, false, false, true), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.NOWEB));
    }
    @Test void cobwebFastDescentDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 30; i++) flags.addAll(model.accept(
                frame(0, 90 - i * .3, false, false, false, false, true), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.NOWEB));
    }
    @Test void mediumEntryClearsUnrelatedFallEvidenceAndNeverTrustsAnchor() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(frame(0, 90, false, false, false, false, false), policy);
        var result = model.accept(frame(0, 64, false, false, true, false, false), policy);
        assertEquals(0, result.landingDistance()); assertFalse(result.clean());
        assertEquals(0, model.accept(frame(0, 63, true, false, false, false, false), policy).landingDistance());
    }
    @Test void stationarySupportedLadderBottomCanBeReturnAnchor() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        var bottom = frame(0, 64, true, true, false, true, false);
        assertFalse(model.accept(bottom, policy).clean());
        assertTrue(model.accept(bottom, policy).clean());
        assertFalse(model.accept(frame(0, 64.2872, false, true, false, true, false), policy).clean());
    }
}
