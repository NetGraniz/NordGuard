package dev.nordfjell.guard;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record Policy(Map<Check, Mode> modes, int buffer, double horizontalMargin, double verticalMargin,
              int burstTicks, int joinGrace, int transitionGrace, long maxGapNanos,
              long alertNanos, boolean console, ActionLimits actions) {
    public Policy(Map<Check, Mode> modes, int buffer, double horizontalMargin, double verticalMargin,
                  int burstTicks, int joinGrace, int transitionGrace, long maxGapNanos, long alertNanos, boolean console) {
        this(modes,buffer,horizontalMargin,verticalMargin,burstTicks,joinGrace,transitionGrace,maxGapNanos,alertNanos,console,ActionLimits.defaults());
    }
    public enum Mode { OFF, OBSERVE, CORRECT }
    public Policy {
        java.util.Objects.requireNonNull(actions);
        modes = Map.copyOf(modes);
        if (buffer < 2 || buffer > 100 || !range(horizontalMargin, 0, 1)
                || !range(verticalMargin, 0.02, 1) || burstTicks < 1 || burstTicks > 20
                || joinGrace < 0 || joinGrace > 1200 || transitionGrace < 0 || transitionGrace > 200
                || maxGapNanos < 100_000_000L || maxGapNanos > 2_000_000_000L
                || alertNanos < 1_000_000_000L || alertNanos > 600_000_000_000L)
            throw new IllegalArgumentException("NordGuard configuration outside supported limits");
        if (modes.size() != Check.values().length) throw new IllegalArgumentException("Missing check mode");
    }
    private static boolean range(double value, double low, double high) {
        return Double.isFinite(value) && value >= low && value <= high;
    }
    static Policy read(ConfigurationSection config) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("Expected schema-version 1");
        var modes = new EnumMap<Check, Mode>(Check.class);
        for (Check check : Check.values()) modes.put(check, Mode.valueOf(config.getString(
                "checks." + check.name().toLowerCase(Locale.ROOT), "OBSERVE").toUpperCase(Locale.ROOT)));
        return new Policy(modes, config.getInt("movement.violation-buffer", 6),
                config.getDouble("movement.horizontal-margin", 0.12), config.getDouble("movement.vertical-margin", 0.16),
                config.getInt("movement.burst-ticks", 5), config.getInt("movement.join-grace-ticks", 60),
                config.getInt("movement.transition-grace-ticks", 20),
                Math.multiplyExact(config.getLong("movement.max-sample-gap-ms", 250), 1_000_000L),
                Math.multiplyExact(config.getLong("alerts.cooldown-seconds", 10), 1_000_000_000L),
                config.getBoolean("alerts.console", true), ActionLimits.read(config));
    }
}
