package dev.nordfjell.guard;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.BoundingBox;

/** One controller-owned state, sampled by the existing player task. Never searches world entities. */
final class BoatGuard implements Listener {
    private final NordGuard host;
    private final ConcurrentHashMap<UUID,State> states=new ConcurrentHashMap<>();
    BoatGuard(NordGuard host) {this.host=host;}
    private static final class State {
        final UUID boat,world;
        final Policy policy;
        final BoatModel model=new BoatModel();
        int sequence,grace,cooldown;
        Location anchor;
        long anchorAt,serial,pending,pendingAt;
        State(Boat boat,Policy policy,int sequence) {
            this.boat=boat.getUniqueId();world=boat.getWorld().getUID();this.policy=policy;this.sequence=sequence;
            grace=policy.joinGrace();
        }
    }
    private record Probe(boolean known,boolean supported,boolean water,boolean bubble,boolean special,boolean clear) {}
    void clear() {states.clear();}
    void remove(Player player) {states.remove(player.getUniqueId());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void enter(VehicleEnterEvent event) {if(event.getEntered() instanceof Player player)remove(player);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void exit(VehicleExitEvent event) {if(event.getExited() instanceof Player player)remove(player);}

    void tick(Player player,long now,int sequence,boolean freshMovement) {
        Policy policy=host.policy();
        if(policy.modes().get(Check.BOATFLIGHT)==Policy.Mode.OFF||player.isDead()
                ||player.getGameMode()==GameMode.CREATIVE||player.getGameMode()==GameMode.SPECTATOR
                ||player.hasPermission("nordguard.bypass")||player.getAllowFlight()||player.isFlying()
                ||!(player.getVehicle() instanceof Boat boat)||!Bukkit.isOwnedByCurrentRegion(boat)) {remove(player);return;}
        List<Entity> passengers=boat.getPassengers();
        if(passengers.isEmpty()||passengers.size()>2||passengers.getFirst()!=player||boat.isInsideVehicle()
                ||!owned(passengers)||boat.isDead()||!boat.hasGravity()||boat.isLeashed()
                ||boat.getWorld()!=player.getWorld()||boat.getStatus()==Boat.Status.NOT_IN_WORLD) {remove(player);return;}
        UUID playerId=player.getUniqueId();
        State state=states.get(playerId);
        if(state==null||state.policy!=policy||!state.boat.equals(boat.getUniqueId())||!state.world.equals(boat.getWorld().getUID())) {
            state=new State(boat,policy,sequence);states.put(playerId,state);
        }
        if(state.pending!=0) {
            if(now<state.pendingAt||now-state.pendingAt>=SetbackWindow.TIMEOUT_NANOS) {
                state.pending=0;state.anchor=null;state.model.reset();state.grace=policy.transitionGrace();
            }
            return;
        }
        if(state.sequence!=sequence) {state.sequence=sequence;state.anchor=null;state.model.reset();state.grace=policy.transitionGrace();}
        if(state.cooldown>0)state.cooldown--;
        if(state.grace>0) {state.grace--;state.model.reset();return;}
        BoatLimits limits=policy.boats();
        Location at=boat.getLocation();
        Probe probe=probe(boat,at,limits);
        if(!probe.known||probe.special) {
            state.model.reset();state.anchor=null;
            if(probe.special)state.grace=limits.bubbleGraceTicks();
            return;
        }
        var result=state.model.accept(new BoatModel.Frame(now,at.getX(),at.getY(),at.getZ(),probe.supported,
                probe.water,probe.bubble,false,freshMovement,.04),limits);
        if(result.clean()&&probe.clear&&(probe.supported||probe.water)&&!probe.bubble) {state.anchor=at.clone();state.anchorAt=now;}
        if(result.violation()&&host.actionViolation(player,Check.BOATFLIGHT,"controlled boat remains above a conservative dry-air fall envelope")
                &&state.cooldown==0&&state.anchor!=null&&probe.clear
                &&now>=state.anchorAt&&now-state.anchorAt<limits.anchorMaxAgeSeconds()*1_000_000_000L
                &&at.getWorld()==state.anchor.getWorld()&&at.distanceSquared(state.anchor)<=limits.maxSetbackDistance()*limits.maxSetbackDistance()
                &&state.anchor.getWorld().getWorldBorder().isInside(state.anchor)) {
            correct(player,boat,passengers,state,limits,now);
        }
    }
    private static boolean owned(List<Entity> passengers) {
        for(Entity passenger:passengers)if(!Bukkit.isOwnedByCurrentRegion(passenger))return false;
        return true;
    }
    private void correct(Player player,Boat boat,List<Entity> passengers,State state,BoatLimits limits,long now) {
        Location target=state.anchor.clone();
        Probe destination=probe(boat,target,limits);
        if(!destination.known||!destination.clear||destination.special||destination.bubble||!(destination.supported||destination.water))return;
        Location actual=boat.getLocation();
        for(Entity passenger:passengers) {
            BoundingBox shifted=passenger.getBoundingBox().clone().shift(target.getX()-actual.getX(),target.getY()-actual.getY(),target.getZ()-actual.getZ());
            if(!clear(passenger.getWorld(),shifted,limits))return;
        }
        UUID[] ids=passengers.stream().map(Entity::getUniqueId).toArray(UUID[]::new);
        long ticket=++state.serial;state.pending=ticket;state.pendingAt=now;state.cooldown=limits.correctionCooldownTicks();
        // The root vehicle is teleported once. 26.2 carries passengers by default, including on Folia.
        try {
            boat.teleportAsync(target,PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((success,error)->
                player.getScheduler().run(host,ignored->{
                    if(!host.isEnabled()||states.get(player.getUniqueId())!=state||state.pending!=ticket)return;
                    state.pending=0;state.model.reset();state.grace=2;
                    if(error!=null||!Boolean.TRUE.equals(success)||!player.isOnline()||!Bukkit.isOwnedByCurrentRegion(boat)
                            ||boat.isDead()||!boat.getUniqueId().equals(state.boat)||boat.getWorld()!=target.getWorld()
                            ||player.getVehicle()!=boat||now>System.nanoTime()||System.nanoTime()-now>=SetbackWindow.TIMEOUT_NANOS) {state.anchor=null;return;}
                    List<Entity> after=boat.getPassengers();
                    if(after.size()!=ids.length||after.isEmpty()||after.getFirst()!=player||!owned(after)) {state.anchor=null;return;}
                    for(int i=0;i<ids.length;i++)if(!ids[i].equals(after.get(i).getUniqueId())) {state.anchor=null;return;}
                    Location landed=boat.getLocation();
                    if(landed.distanceSquared(target)>4||!probe(boat,landed,limits).clear) {state.anchor=null;return;}
                    state.sequence=host.nativeSequence(player);state.anchor=target;state.anchorAt=System.nanoTime();
                    host.actionCancelled();
                },()->{}));
        } catch(RuntimeException failure) {state.pending=0;state.anchor=null;state.model.reset();state.grace=host.policy().transitionGrace();}
    }
    private Probe probe(Boat boat,Location at,BoatLimits limits) {
        Location actual=boat.getLocation();
        BoundingBox body=boat.getBoundingBox().clone().shift(at.getX()-actual.getX(),at.getY()-actual.getY(),at.getZ()-actual.getZ());
        if(body.getWidthX()>2||body.getWidthZ()>2||body.getHeight()>1)return new Probe(false,false,false,false,false,false);
        return scan(at.getWorld(),body,limits,true);
    }
    private boolean clear(org.bukkit.World world,BoundingBox body,BoatLimits limits) {return scan(world,body,limits,false).clear;}
    private Probe scan(org.bukkit.World world,BoundingBox body,BoatLimits limits,boolean feet) {
        int x0=(int)Math.floor(body.getMinX()-.02),x1=(int)Math.floor(body.getMaxX()+.02);
        int z0=(int)Math.floor(body.getMinZ()-.02),z1=(int)Math.floor(body.getMaxZ()+.02);
        int y0=(int)Math.floor(body.getMinY())-(feet?1:0),y1=(int)Math.floor(body.getMaxY());
        long count=((long)x1-x0+1)*((long)y1-y0+1)*((long)z1-z0+1);
        if(count<=0||count>limits.maxProbeBlocks()||y0<world.getMinHeight()||y1>=world.getMaxHeight()||!host.reserveSpatialCells((int)count))return new Probe(false,false,false,false,false,false);
        var foot=new BoundingBox(body.getMinX()+.02,body.getMinY()-.06,body.getMinZ()+.02,body.getMaxX()-.02,body.getMinY()+.01,body.getMaxZ()-.02);
        var inside=body.clone().expand(-.005);
        boolean support=false,water=false,bubble=false,special=false,clear=true;
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++) {
            if(!Bukkit.isOwnedByCurrentRegion(world,x>>4,z>>4)||!world.isChunkLoaded(x>>4,z>>4))return new Probe(false,false,false,false,false,false);
            for(int y=y0;y<=y1;y++) {
                var block=world.getBlockAt(x,y,z);Material material=block.getType();
                if(material.isAir())continue;
                var cube=new BoundingBox(x,y,z,x+1,y+1,z+1);
                bubble|=material==Material.BUBBLE_COLUMN&&(body.overlaps(cube)||foot.overlaps(cube));
                water|=(block.isLiquid()||material==Material.BUBBLE_COLUMN)&&(body.overlaps(cube)||foot.overlaps(cube));
                String name=material.name();
                special|=name.contains("PISTON")||name.contains("SLIME")||name.contains("HONEY")||name.contains("PORTAL")||material==Material.COBWEB||material==Material.POWDER_SNOW;
                var shapes=block.getCollisionShape().getBoundingBoxes();
                if(shapes.size()>16)return new Probe(false,false,false,false,false,false);
                for(var local:shapes) {var shape=local.clone().shift(x,y,z);support|=foot.overlaps(shape);clear&=!inside.overlaps(shape);}
            }
        }
        return new Probe(true,support,water,bubble,special,clear);
    }
}
