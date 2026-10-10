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
}
