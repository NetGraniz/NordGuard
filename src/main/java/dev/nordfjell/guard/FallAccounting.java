package dev.nordfjell.guard;

final class FallAccounting {
    static int owed(int expectedBaseDamage, double observedBaseDamage, boolean pluginOverride) {
        if (pluginOverride || expectedBaseDamage <= 0 || !Double.isFinite(observedBaseDamage) || observedBaseDamage < 0) return 0;
        return (int) Math.max(0, Math.floor(expectedBaseDamage - observedBaseDamage + 1.0E-6));
    }
}
