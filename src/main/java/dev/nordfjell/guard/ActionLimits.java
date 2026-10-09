package dev.nordfjell.guard;

import org.bukkit.configuration.ConfigurationSection;

public record ActionLimits(double reachMargin, int historyMillis, int maxBlocks, int spatialPerTick,
                           int attacksPerSecond, int breaksPerSecond, int placesPerSecond, int spatialBlocksPerSecond) {
    public ActionLimits {
        if (!Double.isFinite(reachMargin) || reachMargin < .05 || reachMargin > 2
                || historyMillis < 0 || historyMillis > 400 || maxBlocks < 16 || maxBlocks > 256
                || spatialPerTick < 1 || spatialPerTick > 8 || !rate(attacksPerSecond)
                || !rate(breaksPerSecond) || !rate(placesPerSecond) || spatialBlocksPerSecond<1000 || spatialBlocksPerSecond>200000)
            throw new IllegalArgumentException("Action limits outside supported bounds");
    }
    private static boolean rate(int value) { return value >= 5 && value <= 200; }
    static ActionLimits defaults() { return new ActionLimits(.35, 200, 128, 2, 40, 25, 20, 20000); }
    static ActionLimits read(ConfigurationSection config) {
        return new ActionLimits(config.getDouble("actions.reach-margin", .35),
                config.getInt("actions.history-ms", 200), config.getInt("actions.max-blocks-per-scan", 128),
                config.getInt("actions.spatial-checks-per-tick", 2), config.getInt("actions.attacks-per-second", 40),
                config.getInt("actions.breaks-per-second", 25), config.getInt("actions.places-per-second", 20),
                config.getInt("actions.spatial-blocks-per-second", 20000));
    }
}
