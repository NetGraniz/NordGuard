package dev.nordfjell.guard;

import org.bukkit.configuration.ConfigurationSection;

/** Bounded arrival tolerance and local surface recovery; no per-packet world reads. */
record MovementLimits(int coalescedTicks,int spiderLaunchTicks,int snowSamples,double sinkDepth,int stationaryAirTicks) {
    MovementLimits(int coalescedTicks,int spiderLaunchTicks,int snowSamples,double sinkDepth){this(coalescedTicks,spiderLaunchTicks,snowSamples,sinkDepth,3);}
    MovementLimits {
        if(coalescedTicks<3||coalescedTicks>6||spiderLaunchTicks<0||spiderLaunchTicks>8
                ||snowSamples<10||snowSamples>100||!Double.isFinite(sinkDepth)||sinkDepth<.3||sinkDepth>.75
                ||stationaryAirTicks<1||stationaryAirTicks>3)
            throw new IllegalArgumentException("Movement limits outside supported bounds");
    }
    static MovementLimits defaults(){return new MovementLimits(6,6,12,.5);}
    static MovementLimits read(ConfigurationSection c){return new MovementLimits(
            integer(c,"movement.max-coalesced-ticks",6),integer(c,"movement.spider-launch-grace-ticks",6),
            integer(c,"movement.powder-snow-samples",12),number(c,"movement.surface-sink-depth",.5),
            integer(c,"movement.stationary-air-sample-ticks",3));}
    private static double number(ConfigurationSection c,String key,double fallback){
        if(!(c.get(key,fallback) instanceof Number n))throw new IllegalArgumentException(key+" must be numeric");
        return n.doubleValue();
    }
    private static int integer(ConfigurationSection c,String key,int fallback){double n=number(c,key,fallback);
        if(!Double.isFinite(n)||n!=(int)n)throw new IllegalArgumentException(key+" must be an integer");return (int)n;}
}
