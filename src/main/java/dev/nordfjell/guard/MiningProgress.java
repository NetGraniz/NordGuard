package dev.nordfjell.guard;

/** Native 26.2 STOP_DESTROY_BLOCK accepts 0.7 progress, not 1.0. */
final class MiningProgress {
    private double fastest;
    private int ticks;
    private final long started;
    private final boolean instant;
    MiningProgress(double speed, boolean instant) { this(speed,instant,System.nanoTime()); }
    MiningProgress(double speed, boolean instant,long now) { this.instant=instant;started=now;advance(speed); }
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
        return !instant && fastest*(Math.max(ticks,elapsed)+2)+.05<.7;
    }
    // Native multiplies the CURRENT speed by elapsed ticks. Historical maximum is more permissive.
    boolean premature() { return !instant && fastest*(ticks+2)+.05<.7; }
}
