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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/** All mutable state belongs to its player's region; only immutable Views cross regions. */
final class ActionGuard implements Listener {
    private final NordGuard host;
    private final ConcurrentHashMap<UUID, State> states=new ConcurrentHashMap<>();
    ActionGuard(NordGuard host) { this.host=host; }
    private static final class State {
        Policy policy;
        int grace, sequence, ticks, spatial;
        long lastTick;
        WallWindow wall=new WallWindow();
        Geometry.Box wallBounds;
        UUID wallWorld,wallTarget;
        NativePackets.Handle wallSource;
        long wallRevision,wallNext;
        final AttackEvidence.Cursor attack=new AttackEvidence.Cursor();
        NativePackets.Handle attackSource;
        int attackTarget=-1;
        long attackTime;
        boolean suspectCritical;
        volatile History history;
        Dig dig;
        final Dig[] rejectedDigs=new Dig[4];
        int rejectedCursor;
        MiningPace miningPace=new MiningPace();
        BlockBreakEvent pendingBreak;
        long pendingWork,pendingBreakTime;
        ActionBudget attacks=new ActionBudget(), breaks=new ActionBudget(), places=new ActionBudget();
        void reset(Policy next,int serial,int delay) {
            policy=next;sequence=serial;grace=delay;history=null;dig=null;
            miningPace=new MiningPace();pendingBreak=null;
            java.util.Arrays.fill(rejectedDigs,null);rejectedCursor=0;
            wall=new WallWindow(next.combat());wallBounds=null;wallSource=null;
            attack.valid=false;attackTarget=-1;attackSource=null;suspectCritical=false;
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
        s.wall.expire(now);
        if(!s.wall.present())s.wallBounds=null;
        if(s.wallBounds!=null) {
            var source=host.nativeHandle(p);
            if(source!=s.wallSource||source!=null&&source.terrainRevision()!=s.wallRevision)resetWall(s);
            else if((source==null||s.wall.pending(now))&&now>=s.wallNext
                    &&s.spatial<policy.actions().spatialPerTick()) {
                s.spatial++;
                var scan=host.scan(p.getWorld(),s.wallBounds,policy.actions());
                if(!scan.known()||source!=null&&source.terrainRevision()!=s.wallRevision)resetWall(s);
                else {
                    s.wall.refresh(s.wallWorld,s.wallTarget,scan.solids(),now);
                    s.wallNext=now+policy.combat().wallRescanMillis()*1_000_000L;
                }
            }
        }
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
    @EventHandler(priority=EventPriority.HIGHEST)
    public void attack(PrePlayerAttackEntityEvent event) {
        long start=System.nanoTime();
        try { checkAttack(event); } finally { host.actionTiming(System.nanoTime()-start); }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void attackFinished(PrePlayerAttackEntityEvent event) {
        if(!event.isCancelled())return;
        State s=states.get(event.getPlayer().getUniqueId());
        if(s!=null) {s.suspectCritical=false;s.attackTarget=-1;s.attackSource=null;}
    }
    private void checkAttack(PrePlayerAttackEntityEvent event) {
        Player p=event.getPlayer();
        var handle=host.nativeHandle(p);State existing=states.get(p.getUniqueId());
        if(existing!=null) {
            existing.suspectCritical=false;existing.attackTarget=-1;existing.attackSource=null;
            if(handle!=null)handle.attacks.poll(event.getAttacked().getEntityId(),System.nanoTime(),handle.combatRevision(),host.policy().combat(),existing.attack);
            else existing.attack.valid=false;
        }
        State s=ready(p);if(s==null||event.isCancelled()) return;
        if(!event.willAttack()) {resetWall(s);return;}
        boolean observedWall=false;
        long now=System.nanoTime();var limits=s.policy.actions();
        boolean cancel=rate(p,s,Check.ATTACKRATE,s.attacks,limits.attacksPerSecond(),now);
        if(!cancel&&handle!=null&&on(s.policy,Check.CRITICALS)&&s.attack.anomalous(s.policy.combat())
                &&s.spatial<limits.spatialPerTick()) {
            s.spatial++;
            // Two environment probes (at most 36 cells each) and the exact support block.
            if(host.reserveSpatialCells(73)&&criticalSupport(p,s.attack,s.policy.combat())) {
                s.suspectCritical=true;s.attackTarget=event.getAttacked().getEntityId();s.attackTime=now;s.attackSource=handle;
            }
        }
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
                    var source=host.nativeHandle(p);
                    long revision=source==null?-1:source.terrainRevision();
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
                        if(source==null||source.active()&&source.terrainRevision()==revision) {
                        if(s.wallSource!=source||s.wallRevision!=revision)resetWall(s);
                        observedWall=true;s.wallSource=source;s.wallRevision=revision;
                        s.wallWorld=world;s.wallTarget=target.getUniqueId();s.wallBounds=bounds;
                        s.wallNext=now+s.policy.combat().wallRescanMillis()*1_000_000L;
                        if(s.wall.blocked(world,target.getUniqueId(),scan.solids(),now))
                            cancel|=violation(p,Check.WALLHIT,"repeated sight-line observations cross matching full solid cubes");
                        }
                    }
                }
            }
        }
        if(!observedWall)resetWall(s);
        if(cancel) {s.suspectCritical=false;event.setCancelled(true);host.actionCancelled(); }
    }
    private static void resetWall(State s) {s.wall.reset();s.wallBounds=null;s.wallSource=null;}
    private static boolean criticalSupport(Player p,AttackEvidence.Cursor evidence,CombatLimits c) {
        if(p.isInsideVehicle()||p.isGliding()||p.isRiptiding()||p.isFlying()||p.getAllowFlight()||!p.hasGravity()
                ||p.isClimbing()||p.isInWater()||!p.getActivePotionEffects().isEmpty()
                ||Math.abs(attribute(p,Attribute.GRAVITY,.08)-.08)>1E-6||Math.abs(attribute(p,Attribute.JUMP_STRENGTH,.42)-.42)>.001)return false;
        var at=p.getLocation();var env=EnvironmentProbe.inspect(p,at);
        if(!env.known()||!env.clear()||env.special()||env.liquid()||env.web()||!Double.isFinite(env.supportY())
                ||Math.abs(at.getY()-env.supportY())>c.criticalSupportEpsilon()
                ||Math.abs(at.getY()-evidence.baseY)>c.criticalSupportEpsilon()
                ||Math.abs(env.supportY()-Math.rint(env.supportY()))>c.criticalSupportEpsilon())return false;
        var floor=p.getWorld().getBlockAt(at.getBlockX(),(int)Math.floor(at.getY()-c.criticalSupportEpsilon()),at.getBlockZ());
        var shapes=floor.getCollisionShape().getBoundingBoxes();if(shapes.size()!=1)return false;
        var b=shapes.iterator().next();
        if(b.getMinX()!=0||b.getMinY()!=0||b.getMinZ()!=0||b.getMaxX()!=1||b.getMaxY()!=1||b.getMaxZ()!=1)return false;
        var raised=EnvironmentProbe.inspect(p,at.clone().add(0,c.criticalMaxRise()+.05,0));
        return raised.known()&&raised.clear()&&!raised.special()&&!raised.liquid()&&!raised.web();
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void criticalDamage(EntityDamageByEntityEvent event) {
        if(event.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK||!(event.getDamager() instanceof Player p)
                ||!Bukkit.isOwnedByCurrentRegion(p))return;
        State s=states.get(p.getUniqueId());if(s==null)return;
        boolean suspect=s.suspectCritical;s.suspectCritical=false;
        if(!suspect||event.isCancelled()||!event.isCritical()||s.attackTarget!=event.getEntity().getEntityId()
                ||s.policy!=host.policy()||!Bukkit.isOwnedByCurrentRegion(event.getEntity()))return;
        var h=host.nativeHandle(p);long now=System.nanoTime();
        if(h==null||h!=s.attackSource||h.combatRevision()!=s.attack.epoch||now<s.attackTime
                ||now-s.attackTime>s.policy.combat().attackEvidenceMillis()*1_000_000L)return;
        if(violation(p,Check.CRITICALS,"melee critical follows contradictory micro-movement on exact solid support")) {
            event.setCancelled(true);host.actionCancelled();
        }
    }
    static Geometry.Box include(Geometry.Box a,Geometry.Box b) {
        return a.include(new Geometry.Point(b.minX(),b.minY(),b.minZ())).include(new Geometry.Point(b.maxX(),b.maxY(),b.maxZ()));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void start(BlockDamageEvent event) {
        var p=event.getPlayer();var s=ready(p);var b=event.getBlock();
        if(s==null || b.getWorld()!=p.getWorld() || !on(s.policy,Check.FASTBREAK) || !owned(b.getWorld(),b.getX(),b.getZ())) return;
        double speed=b.getBreakSpeed(p);
        s.dig=new Dig(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ(),b.getType(),s.ticks,new MiningProgress(speed,event.getInstaBreak()||speed>=1,s.policy.mining()));
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
        if(on(s.policy,Check.FASTBREAK)) {
            long now=System.nanoTime();
            Dig known=s.dig!=null&&s.dig.matches(b)?s.dig:null;
            if(known==null)for(int i=0;i<s.rejectedDigs.length;i++) {
                Dig retry=s.rejectedDigs[i];
                if(retry!=null&&s.ticks-retry.start>1200) {s.rejectedDigs[i]=null;continue;}
                if(retry!=null&&retry.matches(b)) {known=retry;break;}
            }
            boolean premature=known!=null&&known.progress.premature(b.getBreakSpeed(p));
            long work=known==null?0:known.progress.fullWorkNanos();
            boolean accelerated=!s.miningPace.allows(now,work,s.policy.mining());
            s.pendingBreak=event;s.pendingWork=work;s.pendingBreakTime=now;
            if(premature||accelerated) {
                boolean denied=violation(p,Check.FASTBREAK,premature
                        ?"break before observed mining progress permits it":"repeated non-instant mining exceeds its bounded work allowance");
                cancel|=denied;
                if(denied&&known!=null) {
                    int slot=-1;for(int i=0;i<s.rejectedDigs.length;i++)
                        if(s.rejectedDigs[i]!=null&&s.rejectedDigs[i].positionMatches(b)) {slot=i;break;}
                    s.rejectedDigs[slot<0?s.rejectedCursor++&3:slot]=known;
                }
            }
        }
        if(cancel) { event.setCancelled(true);host.actionCancelled(); }
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void miningFinished(BlockBreakEvent event) {
        var p=event.getPlayer();if(!Bukkit.isOwnedByCurrentRegion(p))return;
        State s=states.get(p.getUniqueId());if(s==null||s.pendingBreak!=event)return;
        s.pendingBreak=null;
        if(event.isCancelled()||s.policy!=host.policy())return;
        s.miningPace.commit(s.pendingBreakTime,s.pendingWork,s.policy.mining());
        if(s.dig!=null&&s.dig.positionMatches(event.getBlock()))s.dig=null;
        for(int i=0;i<s.rejectedDigs.length;i++)if(s.rejectedDigs[i]!=null&&s.rejectedDigs[i].positionMatches(event.getBlock()))s.rejectedDigs[i]=null;
        // A rejected native retry retains its original start/progress. Clearing
        // it would turn the next retry into an unchecked unknown-start break.
        // Four rejected-start references also cover delayed retries after a new
        // start or abort. They add no per-tick world queries or unbounded map.
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
