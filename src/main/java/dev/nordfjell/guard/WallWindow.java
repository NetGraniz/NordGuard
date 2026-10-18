package dev.nordfjell.guard;

import java.util.List;
import java.util.UUID;

/** Repeated observations only, not a claim of continuous terrain history. */
final class WallWindow {
    static final long WINDOW=200_000_000L;
    private UUID world,target;
    private List<Geometry.Box> solids;
    private long since,last;
    void reset() {world=target=null;solids=null;since=last=0;}
    void expire(long now) {if(solids!=null && (now<last || now-last>WINDOW))reset();}
    boolean blocked(UUID world,UUID target,List<Geometry.Box> solids,long now) {
        expire(now);
        if(this.solids==null || !world.equals(this.world) || !target.equals(this.target) || !solids.equals(this.solids)) {
            this.world=world;this.target=target;this.solids=solids;since=now;
        }
        last=now;
        return now>=since && now-since>=WINDOW;
    }
}
