package dev.nordfjell.guard;

import java.util.EnumMap;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MovementModelTest {
    static Policy policy() {
        var modes = new EnumMap<Check, Policy.Mode>(Check.class);
        for (Check check : Check.values()) modes.put(check, Policy.Mode.OBSERVE);
        return new Policy(modes, 6, .12, .16, 5, 60, 20, 250_000_000, 10_000_000_000L, false);
    }
    static MovementModel.Frame frame(double x, double y, boolean ground, boolean wall, boolean exempt) {
        return new MovementModel.Frame(x, y, 0, ground, wall, exempt, .39, .42, .6, .08);
    }
    @Test void sprintJumpTrajectoryIsNotFlagged() {
        var model = new MovementModel(); var policy = policy();
        model.accept(frame(0, 64, true, false, false), policy);
        double y = 64, velocity = .42, x = 0;
        for (int i = 0; i < 12; i++) {
            x += .35; y += velocity; velocity = (velocity - .08) * .98;
            boolean ground = y <= 64; if (ground) y = 64;
            assertTrue(model.accept(frame(x, y, ground, false, false), policy).flags().isEmpty());
            if (ground) break;
        }
    }
    @Test void hoverDetectedWithoutTrustingClientGround() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 40; i++) flags.addAll(model.accept(frame(0, 80, false, false, false), policy()).flags());
        assertTrue(flags.contains(Check.FLIGHT));
    }
    @Test void spiderDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 40; i++) flags.addAll(model.accept(frame(0, 64 + i * .2, false, true, false), policy()).flags());
        assertTrue(flags.contains(Check.SPIDER));
    }
    @Test void sustainedSuperSpeedDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 40; i++) flags.addAll(model.accept(frame(i * 2, 64, true, false, false), policy()).flags());
        assertTrue(flags.contains(Check.SPEED));
    }
    @Test void oneNetworkBurstDoesNotFlag() {
        var model = new MovementModel();
        model.accept(frame(0, 64, true, false, false), policy());
        assertTrue(model.accept(frame(2, 64, true, false, false), policy()).flags().isEmpty());
        for (int i = 0; i < 15; i++) assertTrue(model.accept(frame(2, 64, true, false, false), policy()).flags().isEmpty());
    }
    @Test void repeatedHighJumpsDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 25; i++) flags.addAll(model.accept(frame(0, 64 + i, false, false, false), policy()).flags());
        assertTrue(flags.contains(Check.HIGHJUMP));
    }
    @Test void nofallDistanceUsesPeakAndRealSupport() {
        var model = new MovementModel();
        model.accept(frame(0, 80, true, false, false), policy());
        model.accept(frame(0, 78, false, false, false), policy());
        model.accept(frame(0, 74, false, false, false), policy());
        assertEquals(16, model.accept(frame(0, 64, true, false, false), policy()).landingDistance());
        assertEquals(0, model.accept(frame(0, 64, true, false, false), policy()).landingDistance());
    }
    @Test void exemptionClearsFallAndScores() {
        var model = new MovementModel();
        model.accept(frame(0, 100, false, false, false), policy());
        model.accept(frame(0, 90, false, false, true), policy());
        var result = model.accept(frame(0, 64, true, false, false), policy());
        assertEquals(0, result.landingDistance()); assertTrue(result.flags().isEmpty());
    }
    @Test void resetDoesNotTreatTeleportAsFalling() {
        var model = new MovementModel();
        model.accept(frame(0, 100, false, false, false), policy()); model.reset();
        assertEquals(0, model.accept(frame(0, 64, true, false, false), policy()).landingDistance());
    }
    @Test void increasedSpeedAttributeIsRespected() {
        var model = new MovementModel();
        for (int i = 0; i < 40; i++) assertTrue(model.accept(new MovementModel.Frame(i, 64, 0,
                true, false, false, 1.2, .42, .6, .08), policy()).flags().isEmpty());
    }
    @Test void disabledChecksDoNotFlag() {
        var base = policy(); var modes = new EnumMap<Check, Policy.Mode>(Check.class);
        for (Check check : Check.values()) modes.put(check, Policy.Mode.OFF);
        var off = new Policy(modes, base.buffer(), base.horizontalMargin(), base.verticalMargin(), base.burstTicks(),
                base.joinGrace(), base.transitionGrace(), base.maxGapNanos(), base.alertNanos(), false);
        var model = new MovementModel();
        for (int i = 0; i < 100; i++) assertTrue(model.accept(frame(i * 10, 64 + i, false, true, false), off).flags().isEmpty());
    }
    @Test void invalidPolicyRejected() {
        var base = policy();
        assertThrows(IllegalArgumentException.class, () -> new Policy(base.modes(), 0, .12, .16, 5, 60, 20,
                250_000_000, 10_000_000_000L, false));
        assertThrows(IllegalArgumentException.class, () -> new Policy(base.modes(), 6, Double.NaN, .16, 5, 60, 20,
                250_000_000, 10_000_000_000L, false));
    }
}
