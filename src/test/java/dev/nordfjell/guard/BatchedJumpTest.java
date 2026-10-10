package dev.nordfjell.guard;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BatchedJumpTest {
    private static MovementModel.Frame frame(double x, double y, boolean ground) {
        return new MovementModel.Frame(x, y, 0, ground, false, false, .286, .41999998688697815, .6, .08);
    }

    // Rounded owner samples from the isolated native 26.2 Folia fixture, not client-ground claims.
    private static final double[][] JUMP = {
        {.955433, .7531999805212}, {.955433, .7531999805212}, {1.302972, 1.00133597911214},
        {1.644713, 1.16610926093821}, {2.312839, 1.25220334025373}, {2.640131, 1.17675927506424},
        {2.640131, 1.17675927506424}, {3.283145, .79673560066871}, {3.283145, .79673560066871},
        {3.59955, .49520087700593}, {3.912959, .1212968405392}, {4.223641, 0}
    };

    @Test void repeatedNativeTwoStepTakeoffsDoNotLoseSprintImpulse() {
        assertTrue(replay(1).isEmpty());
    }

    @Test void excessiveHorizontalSpeedStillFlagsDespitePlausibleJumpHeights() {
        assertTrue(replay(2.5).contains(Check.SPEED));
    }

    @Test void missedLandingOnVerifiedFloorDoesNotBecomeContinuousFlight() {
        assertFalse(missedLandings(80, 1).contains(Check.FLIGHT));
    }

    @Test void unknownFloorCannotResetContinuousAirborneEvidence() {
        assertTrue(missedLandings(Double.NaN, 1).contains(Check.FLIGHT));
    }

    @Test void distantFloorCannotResetContinuousAirborneEvidence() {
        assertTrue(missedLandings(77, 1).contains(Check.FLIGHT));
    }

    @Test void missedLandingDoesNotExemptExcessiveHorizontalSpeed() {
        assertTrue(missedLandings(80, 3).contains(Check.SPEED));
    }

    @Test void threeStepTakeoffIsNotAnExcessiveHighJump() {
        assertFalse(repeatedTakeoff(1.00133597911214).contains(Check.HIGHJUMP));
    }

    @Test void implausibleTakeoffStillAccumulatesHighJumpEvidence() {
        assertTrue(repeatedTakeoff(1.8).contains(Check.HIGHJUMP));
    }

    @Test void microHopsDoNotBuySprintMomentum() {
        assertTrue(repeatedTakeoff(.1).contains(Check.SPEED));
    }

    @Test void transportPauseDoesNotTurnOneJumpIntoHoverEvidence() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(frame(0, 80, true), policy);
        model.accept(frame(.32, 80.42, false), policy);
        for (int i = 0; i < 100; i++) model.transportIdleTick();
        assertTrue(model.accept(frame(.53, 80.75319998, false), policy).flags().isEmpty());
    }

    @Test void receivedStationaryFramesStillDetectHover() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 60; i++) flags.addAll(model.accept(frame(0, 80.42, false), policy).flags());
        assertTrue(flags.contains(Check.FLIGHT));
    }

    @Test void transportPauseDoesNotBankFutureSpeedCredit() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(frame(0, 80.42, false), policy);
        for (int i = 0; i < 10000; i++) model.transportIdleTick();
        var flags = EnumSet.noneOf(Check.class);
        for (int i = 1; i < 30; i++) flags.addAll(model.accept(frame(i, 80.42, false), policy).flags());
        assertTrue(flags.contains(Check.SPEED));
    }

    @Test void inferredContactDoesNotEraseUnaccountedFallHeight() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(frame(0, 100, true), policy);
        model.accept(frame(0, 99, false), policy);
        model.accept(frame(0, 80.12129684, false), policy);
        model.accept(frame(.36, 80.42, false), policy, 80);
        assertEquals(20, model.accept(frame(.72, 80, true), policy).landingDistance(), 1E-9);
    }

    private static EnumSet<Check> repeatedTakeoff(double height) {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        var flags = EnumSet.noneOf(Check.class);
        model.accept(frame(0, 80, true), policy);
        for (int i = 1; i <= 60; i++) {
            flags.addAll(model.accept(frame(i * 1.2 - .6, 80 + height, false), policy).flags());
            flags.addAll(model.accept(frame(i * 1.2, 80, true), policy).flags());
        }
        return flags;
    }

    private static EnumSet<Check> missedLandings(double supportY, double multiplier) {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        var flags = EnumSet.noneOf(Check.class);
        model.accept(frame(0, 80, true), policy);
        double x = 0;
        // The actual native trace skipped y=80 between a descent to .1213 and a new takeoff to .42.
        double[] heights = {.42, .75319998, 1.00133598, 1.16610926, 1.24918708, 1.25220334,
                1.17675928, 1.02442409, .7967356, .49520088, .12129684};
        for (int cycle = 0; cycle < 60; cycle++) for (double height : heights) {
            x += .36 * multiplier;
            flags.addAll(model.accept(frame(x, 80 + height, false), policy, supportY).flags());
        }
        return flags;
    }

    private static EnumSet<Check> replay(double multiplier) {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        var flags = EnumSet.noneOf(Check.class);
        model.accept(frame(0, 80, true), policy);
        double base = 0;
        for (int cycle = 0; cycle < 60; cycle++) {
            for (var step : JUMP) flags.addAll(model.accept(frame(base + step[0] * multiplier, 80 + step[1], step[1] == 0), policy).flags());
            base += JUMP[JUMP.length - 1][0] * multiplier;
        }
        return flags;
    }
}
