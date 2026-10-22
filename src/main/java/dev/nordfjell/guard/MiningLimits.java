package dev.nordfjell.guard;

import org.bukkit.configuration.ConfigurationSection;

/** Native progress compatibility plus a bounded, amortized non-instant mining budget. */
record MiningLimits(double minimumProgress,int tickSlack,double progressMargin,int pauseTicks,int burstTicks) {
    MiningLimits {
        if(!Double.isFinite(minimumProgress)||minimumProgress<.7||minimumProgress>1
                ||tickSlack<0||tickSlack>10||!Double.isFinite(progressMargin)||progressMargin<0||progressMargin>.1
                ||pauseTicks<0||pauseTicks>10||burstTicks<0||burstTicks>20)
            throw new IllegalArgumentException("Mining limits outside supported bounds");
    }
    static MiningLimits defaults() {return new MiningLimits(.7,2,.05,5,5);}
    static MiningLimits read(ConfigurationSection c) {
        return new MiningLimits(number(c,"mining.minimum-progress",.7),integer(c,"mining.tick-slack",2),
                number(c,"mining.progress-margin",.05),integer(c,"mining.inter-block-delay-ticks",5),integer(c,"mining.burst-ticks",5));
    }
    private static int integer(ConfigurationSection c,String key,int fallback) {
        double n=number(c,key,fallback);
        if(!Double.isFinite(n)||n!=(int)n)throw new IllegalArgumentException(key+" must be an integer");return (int)n;
    }
    private static double number(ConfigurationSection c,String key,double fallback) {
        Object value=c.get(key,fallback);
        if(!(value instanceof Number n))throw new IllegalArgumentException(key+" must be numeric");return n.doubleValue();
    }
}
