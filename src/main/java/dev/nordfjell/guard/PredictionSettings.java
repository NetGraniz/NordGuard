package dev.nordfjell.guard;

import org.bukkit.configuration.ConfigurationSection;

record PredictionSettings(boolean enabled,int framesPerSecond,int cellsPerSecond) {
    static PredictionSettings read(ConfigurationSection yaml,boolean packets,boolean replica) {
        Object enabled=yaml.get("prediction.enabled",Boolean.FALSE);
        if(!(enabled instanceof Boolean value))throw new IllegalArgumentException("prediction.enabled must be boolean");
        if(value && (!packets || !replica))throw new IllegalArgumentException("Prediction needs packets.enabled and packets.world-replica");
        return new PredictionSettings(value,limit(yaml,"frames-per-second",2000,20,20000),
                limit(yaml,"cells-per-second",250000,512,2000000));
    }
    private static int limit(ConfigurationSection yaml,String key,int fallback,int min,int max) {
        Object value=yaml.get("prediction."+key,fallback);
        if(!(value instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()!=n.intValue()
                || n.intValue()<min || n.intValue()>max)throw new IllegalArgumentException("prediction."+key+" must be an integer in "+min+".."+max);
        return n.intValue();
    }
}
