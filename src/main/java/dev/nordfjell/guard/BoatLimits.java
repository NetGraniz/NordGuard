package dev.nordfjell.guard;

import org.bukkit.configuration.ConfigurationSection;

/** Independent of check modes; explicit values are validated before policy publication. */
record BoatLimits(int buffer,int airGraceTicks,int bubbleGraceTicks,int maxGapMillis,
                  int maxProbeBlocks,int correctionCooldownTicks,int anchorMaxAgeSeconds,
                  double maxSetbackDistance,double riseMargin) {
    BoatLimits {
        if(buffer<2||buffer>100||airGraceTicks<4||airGraceTicks>100
                ||bubbleGraceTicks<120||bubbleGraceTicks>240||maxGapMillis<100||maxGapMillis>2000
                ||maxProbeBlocks<27||maxProbeBlocks>128||correctionCooldownTicks<2||correctionCooldownTicks>200
                ||anchorMaxAgeSeconds<1||anchorMaxAgeSeconds>30
                ||!Double.isFinite(maxSetbackDistance)||maxSetbackDistance<1||maxSetbackDistance>64
                ||!Double.isFinite(riseMargin)||riseMargin<.1||riseMargin>1)
            throw new IllegalArgumentException("Boat limits outside supported bounds");
    }
    static BoatLimits defaults() {return new BoatLimits(6,12,120,250,64,10,15,32,.35);}
    static BoatLimits read(ConfigurationSection config) {
        var defaults=defaults();
        return new BoatLimits(integer(config,"vehicle.violation-buffer",defaults.buffer),
                integer(config,"vehicle.air-grace-ticks",defaults.airGraceTicks),
                integer(config,"vehicle.bubble-grace-ticks",defaults.bubbleGraceTicks),
                integer(config,"vehicle.max-sample-gap-ms",defaults.maxGapMillis),
                integer(config,"vehicle.max-probe-blocks",defaults.maxProbeBlocks),
                integer(config,"vehicle.correction-cooldown-ticks",defaults.correctionCooldownTicks),
                integer(config,"vehicle.anchor-max-age-seconds",defaults.anchorMaxAgeSeconds),
                number(config,"vehicle.max-setback-distance",defaults.maxSetbackDistance),
                number(config,"vehicle.rise-margin",defaults.riseMargin));
    }
    private static int integer(ConfigurationSection c,String key,int fallback) {
        double value=number(c,key,fallback);
        if(!Double.isFinite(value)||value!=(int)value)throw new IllegalArgumentException(key+" must be an integer");
        return (int)value;
    }
    private static double number(ConfigurationSection c,String key,double fallback) {
        Object value=c.get(key,fallback);
        if(!(value instanceof Number n))throw new IllegalArgumentException(key+" must be numeric");
        return n.doubleValue();
    }
}
