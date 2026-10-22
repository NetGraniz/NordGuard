package dev.nordfjell.guard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatLimitsTest {
    private YamlConfiguration config() {var c=new YamlConfiguration();c.set("schema-version",1);return c;}
    @Test void missingNewCriticalModeInheritsReachButExplicitAlwaysWins() {
        var c=config();c.set("checks.reach","CORRECT");assertEquals(Policy.Mode.CORRECT,Policy.read(c).modes().get(Check.CRITICALS));
        c.set("checks.criticals","OFF");assertEquals(Policy.Mode.OFF,Policy.read(c).modes().get(Check.CRITICALS));
        c.set("checks.criticals","OBSERVE");assertEquals(Policy.Mode.OBSERVE,Policy.read(c).modes().get(Check.CRITICALS));
    }
    @Test void missingSettingsRemainBoundedDefaults() {assertEquals(CombatLimits.defaults(),Policy.read(config()).combat());}
    @Test void wrongTypeAndFractionalDurationAreRejected() {
        var c=config();c.set("combat.wall-retention-ms","long");assertThrows(IllegalArgumentException.class,()->Policy.read(c));
        c.set("combat.wall-retention-ms",1500.5);assertThrows(IllegalArgumentException.class,()->Policy.read(c));
        c.set("combat.wall-retention-ms",1500);c.set("combat.criticals.support-epsilon",Double.NaN);
        assertThrows(IllegalArgumentException.class,()->Policy.read(c));
    }
    @Test void unsafeAndInconsistentLimitsAreRejected() {
        var c=config();c.set("combat.wall-retention-ms",200);assertThrows(IllegalArgumentException.class,()->Policy.read(c));
        c.set("combat.wall-retention-ms",1500);c.set("combat.criticals.max-packet-rise",.42);
        assertThrows(IllegalArgumentException.class,()->Policy.read(c));
    }
}
