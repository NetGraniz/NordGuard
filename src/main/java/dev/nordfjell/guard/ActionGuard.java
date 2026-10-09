package dev.nordfjell.guard;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

/** All mutable state belongs to its player's region; only immutable Views cross regions. */
final class ActionGuard implements Listener {
    private final NordGuard host;
    private final ConcurrentHashMap<UUID, State> states=new ConcurrentHashMap<>();
    ActionGuard(NordGuard host) { this.host=host; }
    private static final class State {
        Policy policy;
        int grace, sequence, ticks, spatial;
        long lastTick;
        long wallSince;
        int wallHash;
        volatile History history;
        Dig dig;
        ActionBudget attacks=new ActionBudget(), breaks=new ActionBudget(), places=new ActionBudget();
        void reset(Policy next,int serial,int delay) {
            policy=next;sequence=serial;grace=delay;history=null;dig=null;
            wallSince=0;
            attacks=new ActionBudget();breaks=new ActionBudget();places=new ActionBudget();
        }
    }
    record View(UUID world, long time, Geometry.Point eye, Geometry.Box box) {}
    record History(View current,View previous,int sequence) {}
    private record Dig(UUID world,int x,int y,int z,Material type,int start,MiningProgress progress) {
        boolean positionMatches(Block b) { return world.equals(b.getWorld().getUID()) && x==b.getX() && y==b.getY() && z==b.getZ(); }
        boolean matches(Block b) { return positionMatches(b) && type==b.getType(); }
    }
    void clear() { states.clear(); }
    void remove(Player p) { states.remove(p.getUniqueId()); }
    void invalidate(Player p) {
        var s=states.get(p.getUniqueId());
        if(s!=null) s.reset(host.policy(),s.sequence,host.policy().transitionGrace());
    }
    void tick(Player p,int sequence,long now) {
        Policy policy=host.policy();
        var s=states.computeIfAbsent(p.getUniqueId(),id->new State());
        if(s.policy!=policy || s.sequence!=sequence || now-s.lastTick>policy.maxGapNanos())
            s.reset(policy,sequence,s.policy==null?policy.joinGrace():policy.transitionGrace());
        s.lastTick=now;s.ticks++;s.spatial=0;
        if(s.grace>0) s.grace--;
        // Two snapshots, at most ten publications per second. No entity search or per-player task.
        if((s.ticks&1)==0 && (on(policy,Check.REACH)||on(policy,Check.WALLHIT))) {
            History old=s.history;
            s.history=new History(view(p,now),old==null?null:old.current,sequence);
        }
        Dig d=s.dig;
        if(d==null) return;
        if(!on(policy,Check.FASTBREAK) || !d.world.equals(p.getWorld().getUID()) || s.ticks-d.start>1200
                || !owned(p.getWorld(),d.x,d.z)) { s.dig=null;return; }
        Block b=p.getWorld().getBlockAt(d.x,d.y,d.z);
        if(!d.matches(b) || blockBox(b).distance(point(p.getEyeLocation()))>attribute(p,Attribute.BLOCK_INTERACTION_RANGE,4.5)+1)
            s.dig=null;
        else d.progress.advance(b.getBreakSpeed(p));
    }
    private State ready(Player p) {
        if(!Bukkit.isOwnedByCurrentRegion(p) || p.isDead() || p.getGameMode()==GameMode.CREATIVE
                || p.getGameMode()==GameMode.SPECTATOR || p.hasPermission("nordguard.bypass")) return null;
        State s=states.get(p.getUniqueId());
        return s!=null && s.policy==host.policy() && s.grace==0 ? s:null;
    }
    private static boolean on(Policy p,Check c) { return p.modes().get(c)!=Policy.Mode.OFF; }
    private boolean violation(Player p,Check c,String detail) { return host.actionViolation(p,c,detail); }
    private boolean rate(Player p,State s,Check check,ActionBudget budget,int limit,long now) {
        return on(s.policy,check) && !budget.allow(now,limit) && violation(p,check,"action burst exceeds "+limit+"/s budget");
    }
    private static boolean owned(org.bukkit.World w,int x,int z) {
        return w.isChunkLoaded(x>>4,z>>4) && Bukkit.isOwnedByCurrentRegion(w,x>>4,z>>4);
    }
    private static Geometry.Point point(org.bukkit.Location at) { return new Geometry.Point(at.getX(),at.getY(),at.getZ()); }
    private static Geometry.Box box(Entity e) {
        var b=e.getBoundingBox();return new Geometry.Box(b.getMinX(),b.getMinY(),b.getMinZ(),b.getMaxX(),b.getMaxY(),b.getMaxZ());
    }
    private static View view(Player p,long now) { return new View(p.getWorld().getUID(),now,point(p.getEyeLocation()),box(p)); }
    private static Geometry.Box blockBox(Block b) { return new Geometry.Box(b.getX(),b.getY(),b.getZ(),b.getX()+1,b.getY()+1,b.getZ()+1); }
    private static double attribute(Player p,Attribute attr,double fallback) {
        var a=p.getAttribute(attr);return a==null?fallback:a.getValue();
    }
    private static boolean fresh(View v,UUID world,long now,long age) {
        return v!=null && v.world.equals(world) && now>=v.time && now-v.time<=age;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void attack(PrePlayerAttackEntityEvent event) {
        long start=System.nanoTime();
        try { checkAttack(event); } finally { host.actionTiming(System.nanoTime()-start); }
    }
    private void checkAttack(PrePlayerAttackEntityEvent event) {
        Player p=event.getPlayer();State s=ready(p);if(s==null || !event.willAttack()) return;
        long now=System.nanoTime();var limits=s.policy.actions();
        boolean cancel=rate(p,s,Check.ATTACKRATE,s.attacks,limits.attacksPerSecond(),now);
        if(on(s.policy,Check.REACH)||on(s.policy,Check.WALLHIT)) {
            Entity target=event.getAttacked();UUID world=p.getWorld().getUID();
            View latest=null,prior=null;
            if(target instanceof Player other) {
                State targetState=states.get(other.getUniqueId());
                History h=targetState==null?null:targetState.history;
                if(h!=null) {
                    latest=h.current;prior=h.previous;
                    if(Bukkit.isOwnedByCurrentRegion(other) && host.nativeSequence(other)!=h.sequence) prior=null;
                }
            }
            if(Bukkit.isOwnedByCurrentRegion(target)) {
                if(target.getWorld()==p.getWorld()) latest=new View(world,now,null,box(target));
                else latest=null;
            }
            // Never read a foreign region's live entity state. Stale/missing snapshots defer spatial checks.
            if(fresh(latest,world,now,250_000_000L)) {
                long age=Math.min(limits.historyMillis(),Math.max(100,p.getPing()+50))*1_000_000L;
                if(!fresh(prior,world,now,age)) prior=null;
                var eye=point(p.getEyeLocation());
                var component=p.getInventory().getItemInMainHand().getData(DataComponentTypes.ATTACK_RANGE);
                double reach=component==null?attribute(p,Attribute.ENTITY_INTERACTION_RANGE,3):component.maxReach()+component.hitboxMargin();
                double distance=latest.box.distance(eye);
                if(prior!=null) distance=Math.min(distance,prior.box.distance(eye));
                if(on(s.policy,Check.REACH) && distance>reach+limits.reachMargin())
                    cancel|=violation(p,Check.REACH,"eye-to-hitbox distance exceeds server range");
                if(on(s.policy,Check.WALLHIT) && distance<=8 && s.spatial<limits.spatialPerTick()) {
                    s.spatial++;
                    var bounds=latest.box.include(eye);
                    if(prior!=null) bounds=include(bounds,prior.box);
                    History own=s.history;
                    View ownPrior=own==null?null:own.previous;
                    if(!fresh(ownPrior,world,now,age)) ownPrior=null;
                    if(ownPrior!=null) bounds=bounds.include(ownPrior.eye);
                    var scan=host.scan(p.getWorld(),bounds,limits);
                    boolean blocked=scan.known() && scan.obscured(eye,latest.box)
                            && (prior==null || scan.obscured(eye,prior.box))
                            && (ownPrior==null || scan.obscured(ownPrior.eye,latest.box)
                            && (prior==null || scan.obscured(ownPrior.eye,prior.box)));
                    if(blocked) {
                        int hash=scan.solids().hashCode();
                        if(s.wallSince==0 || s.wallHash!=hash) { s.wallHash=hash;s.wallSince=now; }
                        // Allow a just-placed wall to settle before using current terrain against lagged attacks.
                        if(now-s.wallSince>=200_000_000L)
                            cancel|=violation(p,Check.WALLHIT,"all sampled sight lines cross stable full solid cubes");
                    } else s.wallSince=0;
                }
            }
        }
        if(cancel) { event.setCancelled(true);host.actionCancelled(); }
    }
    static Geometry.Box include(Geometry.Box a,Geometry.Box b) {
        return a.include(new Geometry.Point(b.minX(),b.minY(),b.minZ())).include(new Geometry.Point(b.maxX(),b.maxY(),b.maxZ()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void start(BlockDamageEvent event) {
        var p=event.getPlayer();var s=ready(p);var b=event.getBlock();
        if(s==null || b.getWorld()!=p.getWorld() || !on(s.policy,Check.FASTBREAK) || !owned(b.getWorld(),b.getX(),b.getZ())) return;
        double speed=b.getBreakSpeed(p);
        s.dig=new Dig(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),b.getType(),s.ticks,new MiningProgress(speed,event.getInstaBreak()||speed>=1));
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void abort(BlockDamageAbortEvent event) {
        var s=states.get(event.getPlayer().getUniqueId());
        if(s!=null && s.dig!=null && s.dig.positionMatches(event.getBlock())) s.dig=null;
    }
    private boolean blockReach(Player p,State s,Block b) {
        return on(s.policy,Check.BLOCKREACH)
                && blockBox(b).distance(point(p.getEyeLocation()))>attribute(p,Attribute.BLOCK_INTERACTION_RANGE,4.5)+s.policy.actions().reachMargin()
                && violation(p,Check.BLOCKREACH,"block beyond server interaction range");
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void destroy(BlockBreakEvent event) {
        long start=System.nanoTime();
        try { checkDestroy(event); } finally { host.actionTiming(System.nanoTime()-start); }
    }
    private void checkDestroy(BlockBreakEvent event) {
        var p=event.getPlayer();var s=ready(p);var b=event.getBlock();
        if(s==null || b.getWorld()!=p.getWorld() || !owned(b.getWorld(),b.getX(),b.getZ())) return;
        boolean cancel=blockReach(p,s,b);
        cancel|=rate(p,s,Check.BREAKRATE,s.breaks,s.policy.actions().breaksPerSecond(),System.nanoTime());
        if(on(s.policy,Check.FASTBREAK) && s.dig!=null && s.dig.matches(b) && s.dig.progress.premature(b.getBreakSpeed(p)))
            cancel|=violation(p,Check.FASTBREAK,"break before observed mining progress permits it");
        s.dig=null;
        if(cancel) { event.setCancelled(true);host.actionCancelled(); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent event) {
        long start=System.nanoTime();
        try { checkPlace(event); } finally { host.actionTiming(System.nanoTime()-start); }
    }
    private void checkPlace(BlockPlaceEvent event) {
        var p=event.getPlayer();var s=ready(p);var b=event.getBlockPlaced();
        if(s==null || b.getWorld()!=p.getWorld() || !owned(b.getWorld(),b.getX(),b.getZ())) return;
        boolean cancel=blockReach(p,s,b);
        cancel|=rate(p,s,Check.PLACERATE,s.places,s.policy.actions().placesPerSecond(),System.nanoTime());
        if(cancel) { event.setCancelled(true);host.actionCancelled(); }
    }
}
