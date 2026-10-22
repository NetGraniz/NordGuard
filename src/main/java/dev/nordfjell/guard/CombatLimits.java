package dev.nordfjell.guard;

import org.bukkit.configuration.ConfigurationSection;

/** Immutable, validated limits; nanoseconds stay outside the editable schema. */
public record CombatLimits(int wallConfirmMillis,int wallRetentionMillis,int wallRescanMillis,
                           int attackEvidenceMillis,double criticalMinRise,double criticalMaxRise,
                           double criticalSupportEpsilon,double criticalHorizontalSpan) {
    public CombatLimits {
        if(wallConfirmMillis<100 || wallConfirmMillis>1000 || wallRetentionMillis<wallConfirmMillis+200
                || wallRetentionMillis>5000 || wallRescanMillis<50 || wallRescanMillis>wallConfirmMillis
                || attackEvidenceMillis<50 || attackEvidenceMillis>500
                || !range(criticalMinRise,.001,.04) || !range(criticalMaxRise,criticalMinRise,.1)
                || !range(criticalSupportEpsilon,.000001,.0001) || !range(criticalHorizontalSpan,.001,.1))
            throw new IllegalArgumentException("Combat limits outside supported bounds");
    }
    private static boolean range(double n,double low,double high) {return Double.isFinite(n)&&n>=low&&n<=high;}
    static CombatLimits defaults() {return new CombatLimits(200,1500,100,250,.01,.08,.00001,.02);}
    static CombatLimits read(ConfigurationSection c) {
        return new CombatLimits(integer(c,"combat.wall-confirm-ms",200),integer(c,"combat.wall-retention-ms",1500),
                integer(c,"combat.wall-rescan-ms",100),integer(c,"combat.attack-evidence-ms",250),
                number(c,"combat.criticals.min-packet-rise",.01),number(c,"combat.criticals.max-packet-rise",.08),
                number(c,"combat.criticals.support-epsilon",.00001),number(c,"combat.criticals.max-horizontal-span",.02));
    }
    private static int integer(ConfigurationSection c,String key,int fallback) {
        Object value=c.get(key,fallback);
        if(!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.intValue())
            throw new IllegalArgumentException(key+" must be an integer");
        return n.intValue();
    }
    private static double number(ConfigurationSection c,String key,double fallback) {
        Object value=c.get(key,fallback);
        if(!(value instanceof Number n))throw new IllegalArgumentException(key+" must be numeric");
        return n.doubleValue();
    }
}
