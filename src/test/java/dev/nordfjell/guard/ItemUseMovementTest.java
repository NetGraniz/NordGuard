package dev.nordfjell.guard;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemUseMovementTest {
    private static MovementModel.Frame frame(double x, boolean ground, double multiplier) {
        return new MovementModel.Frame(x, 64, 0, ground, false, false, .286, .42, .6, .08,
                false, false, false, multiplier);
    }
    @Test void ordinarySlowedItemUseIsAllowed() {
        var model = new MovementModel();
        for (int i = 0; i < 100; i++) assertTrue(model.accept(frame(i * .0572, true, .2), MovementModelTest.policy()).flags().isEmpty());
    }
    @Test void ignoringItemSlowdownIsDetected() {
        var model = new MovementModel(); var flags = EnumSet.noneOf(Check.class);
        for (int i = 0; i < 60; i++) flags.addAll(model.accept(frame(i * .25, true, .2), MovementModelTest.policy()).flags());
        assertTrue(flags.contains(Check.NOSLOW)); assertFalse(flags.contains(Check.SPEED));
    }
    @Test void customFullSpeedUseComponentIsRespected() {
        var model = new MovementModel();
        for (int i = 0; i < 100; i++) assertTrue(model.accept(frame(i * .286, true, 1), MovementModelTest.policy()).flags().isEmpty());
    }
    @Test void itemUseInAirDoesNotInvokeGroundSlowdownCheck() {
        var model = new MovementModel();
        for (int i = 0; i < 15; i++) assertFalse(model.accept(frame(i * .25, false, .2), MovementModelTest.policy()).flags().contains(Check.NOSLOW));
    }
}
