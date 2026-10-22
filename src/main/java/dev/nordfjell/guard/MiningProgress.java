package dev.nordfjell.guard;

/** Native 26.2 STOP_DESTROY_BLOCK accepts 0.7 progress, not 1.0. */
final class MiningProgress {
    private double fastest;
    private int ticks;
    private final long started;
    private final boolean instant;
    private final MiningLimits limits;
    MiningProgress(double speed, boolean instant) { this(speed,instant,System.nanoTime(),MiningLimits.defaults()); }
    MiningProgress(double speed, boolean instant,MiningLimits limits) {this(speed,instant,System.nanoTime(),limits);}
    MiningProgress(double speed, boolean instant,long now) {this(speed,instant,now,MiningLimits.defaults());}
    MiningProgress(double speed, boolean instant,long now,MiningLimits limits) {this.limits=limits;this.instant=instant;started=now;advance(speed);}
    void advance(double speed) {
        if(!Double.isFinite(speed) || speed<0) return;
        fastest=Math.max(fastest,speed);ticks=Math.min(1202,ticks+1);
    }
    boolean premature(double currentSpeed) {
        return premature(currentSpeed,System.nanoTime());
    }
    boolean premature(double currentSpeed,long now) {
        if(Double.isFinite(currentSpeed) && currentSpeed>=0) fastest=Math.max(fastest,currentSpeed);
        // 26.2 game-mode mining ticks use wall-clock lag compensation, including on Folia.
        long elapsed=Math.min(1202,Math.max(0,(now-started)/50_000_000L)+1);
        return !instant && fastest*(Math.max(ticks,elapsed)+limits.tickSlack())+limits.progressMargin()<limits.minimumProgress();
    }
    // Native multiplies the CURRENT speed by elapsed ticks. Historical maximum is more permissive.
    boolean premature() { return !instant && fastest*(ticks+limits.tickSlack())+limits.progressMargin()<limits.minimumProgress(); }
    long fullWorkNanos() {
        if(instant||fastest<=0||fastest>=1)return 0;
        // START contributes the first progress step. Cap the bookkeeping cost;
        // one observed dig is already limited to 1,200 owner ticks.
        double ticks=Math.min(1200,Math.max(0,Math.ceil(1/fastest)-1));
        return (long)ticks*50_000_000L;
    }
}
