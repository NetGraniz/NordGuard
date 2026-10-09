package dev.nordfjell.guard;

import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PolicyTest {
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
