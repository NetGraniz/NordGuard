package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdleSpeedBudgetTest {
    private static MovementModel.Frame ground(double x) {
        return new MovementModel.Frame(x, 80, 0, true, false, false, .286, .42, .6, .08);
    }

    @Test void ordinaryTwoTickBatchesRepayDebtOnSkippedStationaryTicks() {
        var model = new MovementModel();
        model.accept(ground(0), MovementModelTest.policy());
        for (int i = 1; i <= 200; i++) {
            assertFalse(model.accept(ground(i * .572), MovementModelTest.policy()).flags().contains(Check.SPEED));
            model.stationaryTick();
        }
    }

    @Test void excessiveAverageSpeedStillFlagsWithStationaryTicks() {
        var model = new MovementModel();
        model.accept(ground(0), MovementModelTest.policy());
        boolean flagged = false;
        for (int i = 1; i <= 100; i++) {
            flagged |= model.accept(ground(i * .9), MovementModelTest.policy()).flags().contains(Check.SPEED);
            model.stationaryTick();
        }
        assertTrue(flagged);
    }

    @Test void standingStillCannotAccumulateFutureMovementCredit() {
        var model = new MovementModel();
        model.accept(ground(0), MovementModelTest.policy());
        for (int i = 0; i < 10000; i++) model.stationaryTick();
        boolean flagged = false;
        for (int i = 1; i <= 30; i++)
            flagged |= model.accept(ground(i), MovementModelTest.policy()).flags().contains(Check.SPEED);
        assertTrue(flagged);
    }

    @Test void uninitializedAndResetModelsRemainUntrusted() {
        var model = new MovementModel();
        model.stationaryTick();
        assertFalse(model.accept(ground(0), MovementModelTest.policy()).clean());
        model.reset(); model.stationaryTick();
        assertFalse(model.accept(ground(20), MovementModelTest.policy()).clean());
    }

    @Test void pauseBeforeRepeatedTwoTickBatchesDoesNotDiscardTimeAlreadyElapsed() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(ground(0), policy);
        for (int i = 1; i <= 300; i++) {
            model.stationaryTick();
            assertFalse(model.accept(ground(i * .572), policy).flags().contains(Check.SPEED));
        }
    }

    @Test void fullIdleSamplesAndSkippedSamplesUseTheSameBudget() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        double x = 0;
        model.accept(ground(x), policy);
        for (int i = 0; i < 300; i++) {
            for (int idle = 0; idle < 4; idle++) model.stationaryTick();
            assertFalse(model.accept(ground(x), policy).flags().contains(Check.SPEED));
            x += .286 * 6;
            assertFalse(model.accept(ground(x), policy).flags().contains(Check.SPEED));
        }
    }

    @Test void idleAllowanceCannotHideSustainedExcessOrSurviveAReset() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(ground(0), policy);
        for (int idle = 0; idle < 10000; idle++) model.stationaryTick();
        boolean flagged = false;
        for (int i = 1; i <= 20; i++) flagged |= model.accept(ground(i), policy).flags().contains(Check.SPEED);
        assertTrue(flagged);
        model.reset();
        assertFalse(model.accept(ground(0), policy).clean());
        assertFalse(model.accept(ground(2), policy).clean());
    }

    @Test void arrivalBatchesAtExcessiveAverageSpeedStillFlag() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(ground(0), policy);
        boolean flagged = false;
        for (int i = 1; i <= 100; i++) {
            model.stationaryTick();
            flagged |= model.accept(ground(i * 1.43), policy).flags().contains(Check.SPEED);
        }
        assertTrue(flagged);
    }

    @Test void pauseThenCatchUpDoesNotCountTheSameTransportDelayAsExcessSpeed() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(ground(0), policy);
        double x = 0;
        for (int cycle = 0; cycle < 20; cycle++) {
            for (int idle = 0; idle < 12; idle++) model.stationaryTick();
            for (int batch = 0; batch < 12; batch++) {
                x += .572;
                assertFalse(model.accept(ground(x), policy).flags().contains(Check.SPEED));
            }
        }
    }

    @Test void longIdleDoesNotBuyMoreAllowanceThanTheConfiguredBurstLimit() {
        var shortIdle = new MovementModel(); var longIdle = new MovementModel();
        var policy = MovementModelTest.policy();
        shortIdle.accept(ground(0), policy); longIdle.accept(ground(0), policy);
        for (int i = 0; i < policy.burstTicks(); i++) shortIdle.stationaryTick();
        for (int i = 0; i < 10000; i++) longIdle.stationaryTick();
        boolean flagged = false;
        for (int i = 1; i <= 30; i++) {
            var a = shortIdle.accept(ground(i), policy); var b = longIdle.accept(ground(i), policy);
            assertEquals(a.flags(), b.flags()); assertEquals(a.clean(), b.clean());
            flagged |= a.flags().contains(Check.SPEED);
        }
        assertTrue(flagged);
    }

    @Test void reducedSpeedAttributeClampsPreviouslyEarnedIdleAllowance() {
        var model = new MovementModel(); var policy = MovementModelTest.policy();
        model.accept(new MovementModel.Frame(0, 80, 0, true, false, false, 10, .42, .6, .08), policy);
        for (int i = 0; i < 10000; i++) model.stationaryTick();
        boolean flagged = false;
        for (int i = 1; i <= 20; i++) flagged |= model.accept(ground(i), policy).flags().contains(Check.SPEED);
        assertTrue(flagged);
    }
}
