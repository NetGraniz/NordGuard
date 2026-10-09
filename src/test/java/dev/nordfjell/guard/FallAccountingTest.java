package dev.nordfjell.guard;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallAccountingTest {
    @Test void restoresMissingDamage() { assertEquals(5, FallAccounting.owed(5, 0, false)); }
    @Test void partialBaseDamageRetainsDebt() { assertEquals(4, FallAccounting.owed(5, 1, false)); }
    @Test void neverDuplicatesFullDamage() { assertEquals(0, FallAccounting.owed(5, 5, false)); }
    @Test void respectsPluginOverrides() { assertEquals(0, FallAccounting.owed(5, 0, true)); }
    @Test void invalidEvidenceDoesNotPunish() {
        assertEquals(0, FallAccounting.owed(5, Double.NaN, false));
        assertEquals(0, FallAccounting.owed(0, 0, false));
    }
}
