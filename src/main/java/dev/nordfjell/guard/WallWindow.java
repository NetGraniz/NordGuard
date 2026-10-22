package dev.nordfjell.guard;

import java.util.List;
import java.util.UUID;

/** Repeated observations only, not a claim of continuous terrain history. */
final class WallWindow {
    static final long WINDOW=200_000_000L;
    private final CombatLimits limits;
    WallWindow() {this(CombatLimits.defaults());}
    WallWindow(CombatLimits limits) {this.limits=limits;}
    private UUID world,target;
    private List<Geometry.Box> solids;
    private long since,last,attackTime;
    void reset() {world=target=null;solids=null;since=last=attackTime=0;}
    boolean present() {return solids!=null;}
    void expire(long now) {if(solids!=null && (now<last || now-attackTime>limits.wallRetentionMillis()*1_000_000L))reset();}
    boolean pending(long now) {return solids!=null && now>=since && now-since<limits.wallConfirmMillis()*1_000_000L;}
    boolean confirmed(long now) {return solids!=null && now>=since && now-since>=limits.wallConfirmMillis()*1_000_000L;}
    boolean blocked(UUID world,UUID target,List<Geometry.Box> solids,long now) {
        return observe(world,target,solids,now,true);
    }
    void refresh(UUID world,UUID target,List<Geometry.Box> solids,long now) {observe(world,target,solids,now,false);}
    private boolean observe(UUID world,UUID target,List<Geometry.Box> solids,long now,boolean attack) {
        expire(now);
        if(!attack&&this.solids==null)return false;
        if(this.solids==null || !world.equals(this.world) || !target.equals(this.target) || !solids.equals(this.solids)) {
            this.world=world;this.target=target;this.solids=solids;since=now;
        }
        last=now;
        if(attack)attackTime=now;
        return confirmed(now);
    }
}
