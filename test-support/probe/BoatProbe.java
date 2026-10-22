package dev.nordfjell.guard;

import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.entity.boat.OakBoat;
import org.bukkit.plugin.java.JavaPlugin;

/** Synthetic owner-only vehicle fixture; not included in the plugin release. */
final class BoatProbe {
    private final JavaPlugin plugin;
    private Boat boat;
    private Cow passenger;
    private double startY,maxY,minY;
    private String terrain;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask sample;
    BoatProbe(JavaPlugin plugin) {this.plugin=plugin;}
    boolean command(Player player,String[] args) {
        switch(args[0]) {
            case "boatprepare" -> {prepare(player,args[2]);return true;}
            case "boatresult" -> {
                requireOwned(player);
                plugin.getLogger().info("GUARD_BOAT_RESULT {\"terrain\":\""+terrain+"\",\"startY\":"+startY
                    +",\"maxY\":"+maxY+",\"minY\":"+minY+",\"currentY\":"+boat.getY()
                    +",\"boatId\":"+boat.getEntityId()+",\"sameBoat\":"+(player.getVehicle()==boat)
                    +",\"passengerCount\":"+boat.getPassengers().size()+",\"cowStillRiding\":"+(passenger.getVehicle()==boat)+"}");
                return true;
            }
            case "boatimpulse" -> {requireOwned(player);boat.setVelocity(new org.bukkit.util.Vector(0,1,0));plugin.getLogger().info("GUARD_BOAT_IMPULSE");return true;}
            case "boatclear" -> {clear(player);plugin.getLogger().info("GUARD_BOAT_CLEARED");return true;}
            default -> {return false;}
        }
    }
    private void requireOwned(Player player) {
        if(boat==null||passenger==null||!Bukkit.isOwnedByCurrentRegion(player)||!Bukkit.isOwnedByCurrentRegion(boat)
            ||!Bukkit.isOwnedByCurrentRegion(passenger))throw new IllegalStateException("Boat fixture missing/not owned");
    }
    private void clear(Player player) {
        if(boat==null)return;
        requireOwned(player);if(sample!=null)sample.cancel();sample=null;
        boat.eject();passenger.remove();boat.remove();boat=null;passenger=null;
    }
    private void prepare(Player player,String next) {
        if(!Set.of("land","water","bubble").contains(next))throw new IllegalArgumentException("Unknown boat terrain");
        var at=player.getLocation();var world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ(),y=80;
        for(int dx=-4;dx<=10;dx++)for(int dz=-4;dz<=4;dz++)
            if(!world.isChunkLoaded((x+dx)>>4,(z+dz)>>4)||!Bukkit.isOwnedByCurrentRegion(world,(x+dx)>>4,(z+dz)>>4))
                throw new IllegalStateException("Boat fixture cells must already be loaded and owned");
        clear(player);terrain=next;
        for(int dx=-4;dx<=10;dx++)for(int dz=-4;dz<=4;dz++) {
            world.getBlockAt(x+dx,y-3,z+dz).setType(Material.STONE,false);
            for(int dy=-2;dy<=5;dy++)world.getBlockAt(x+dx,y+dy,z+dz).setType(dy<0
                ?next.equals("land")?Material.STONE:Material.WATER:Material.AIR,false);
        }
        if(next.equals("bubble"))for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++) {
            world.getBlockAt(x+dx,y-3,z+dz).setType(Material.SOUL_SAND,false);
            for(int dy=-2;dy<0;dy++)world.getBlockAt(x+dx,y+dy,z+dz).setType(Material.BUBBLE_COLUMN,false);
        }
        player.setGameMode(GameMode.SURVIVAL);player.setFlying(false);player.setAllowFlight(false);
        player.setInvulnerable(false);player.setVelocity(new org.bukkit.util.Vector());player.setFallDistance(0);
        for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
        player.teleportAsync(new Location(world,x+.5,y+1,z+.5,-90,0)).thenAccept(ok->player.getScheduler().run(plugin,t->{
            if(!ok)throw new IllegalStateException("Boat fixture teleport failed");
            var spawn=new Location(world,x+.5,y,z+.5,-90,0);
            boat=world.spawn(spawn,OakBoat.class);
            passenger=world.spawn(spawn,Cow.class,cow->{cow.setAI(false);cow.setPersistent(false);cow.setSilent(true);});
            if(!boat.addPassenger(player)||!boat.addPassenger(passenger))throw new IllegalStateException("Boat fixture mount failed");
            startY=maxY=minY=boat.getY();
            sample=player.getScheduler().runAtFixedRate(plugin,ignored->{
                if(boat==null)return;
                requireOwned(player);double height=boat.getY();maxY=Math.max(maxY,height);minY=Math.min(minY,height);
            },()->{},1,1);
            plugin.getLogger().info("GUARD_BOAT_READY {\"terrain\":\""+next+"\",\"boatId\":"+boat.getEntityId()
                +",\"startY\":"+startY+",\"passengerCount\":"+boat.getPassengers().size()+"}");
        },null));
    }
}
