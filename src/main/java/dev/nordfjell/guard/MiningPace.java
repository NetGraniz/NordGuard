package dev.nordfjell.guard;

/** Owner-only scalar state. Time slack is a bounded reserve, not a per-block gift. */
final class MiningPace {
    private static final long TICK=50_000_000L,MAX_ELAPSED=120_000_000_000L,MAX_DEBT=60_000_000_000L;
    private boolean initialized;
    private long last,balance;
    private int pauseAfter;
    boolean allows(long now,long work,MiningLimits limits) {
        if(!initialized||now<last||now-last<0||work<=0)return true;
        long elapsed=Math.min(MAX_ELAPSED,now-last),cost=work+pauseAfter*TICK;
        // Repaying old OBSERVE-mode debt is not fresh accelerated mining evidence.
        return elapsed>=cost||balance+cost-elapsed<=limits.burstTicks()*TICK;
    }
    void commit(long now,long work,MiningLimits limits) {
        long reserve=limits.burstTicks()*TICK;
        if(!initialized||now<last||now-last<0)balance=0;
        else {
            long cost=work<=0?0:work+pauseAfter*TICK;
            balance=Math.max(-reserve,Math.min(MAX_DEBT,balance+cost-Math.min(MAX_ELAPSED,now-last)));
        }
        initialized=true;last=now;pauseAfter=work>0?limits.pauseTicks():0;
    }
}
