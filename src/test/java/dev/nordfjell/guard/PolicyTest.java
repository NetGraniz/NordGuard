package dev.nordfjell.guard;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyTest {
    @Test void oldConfigurationPreservesModesAndNewChecksOnlyObserve() {
        var config = new MemoryConfiguration();
        config.set("schema-version", 1);
        config.set("checks.flight", "CORRECT");
        config.set("checks.speed", "CORRECT");
        var policy = Policy.read(config);
        assertEquals(Policy.Mode.CORRECT, policy.modes().get(Check.FLIGHT));
        assertEquals(Policy.Mode.CORRECT, policy.modes().get(Check.SPEED));
        for (Check check : new Check[]{Check.WATERWALK, Check.CLIMB, Check.NOWEB, Check.NOSLOW})
            assertEquals(Policy.Mode.OBSERVE, policy.modes().get(check));
    }
    @Test void rejectsTimeConversionOverflow() {
        var config = new MemoryConfiguration();
        config.set("schema-version", 1);
        config.set("movement.max-sample-gap-ms", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> Policy.read(config));
        config.set("movement.max-sample-gap-ms", 250);
        config.set("alerts.cooldown-seconds", Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> Policy.read(config));
    }
}
