package dev.nordfjell.guard;

import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

/** Synthetic loopback combat target and passive measurements; never part of the release. */
final class CombatProbe implements Listener {
    private final JavaPlugin plugin;
    private volatile Trial active;
    private static final class Trial {
        final UUID player;
        final Cow target;
        final String terrain;
        final double before;
        final double floorY;
        long preAttackCalls,preAttackCancelled,preAttackWillAttack,damageEvents,cancelledDamageEvents;
        long criticalDamageEvents,successfulDamageEvents,successfulCriticalEvents;
        double totalFinalDamage;
        double minY,maxY;
        boolean ready,sawPlayerMove;
        int impulses;
        String control="none";
        String weapon="plain";
        Trial(Player player,Cow target,String terrain,double floorY) {
            this.player=player.getUniqueId();this.target=target;this.terrain=terrain;before=target.getHealth();
            this.floorY=floorY;minY=maxY=floorY;
        }
    }
    CombatProbe(JavaPlugin plugin) {this.plugin=plugin;}
    boolean command(Player player,String[] args) {
        switch(args[0]) {
            case "combatprepare" -> {if(args.length!=3)throw new IllegalArgumentException("combatprepare <player> <open|wall|partial|slab|edge|ceiling|slab-floor>");prepare(player,args[2]);}
            case "combatresult" -> result(player);
            case "combatclear" -> clear(player);
            case "combatcontrol" -> {
                if(args.length!=3 || !Set.of("none","cancel","extra","late-pre-cancel").contains(args[2]))throw new IllegalArgumentException("combatcontrol <player> <none|cancel|extra|late-pre-cancel>");
                Trial trial=ownedTrial(player);trial.control=args[2];plugin.getLogger().info("GUARD_COMBAT_CONTROL "+trial.control);
            }
            case "combatweapon" -> {
                if(args.length!=3 || !Set.of("plain","sharpness").contains(args[2]))throw new IllegalArgumentException("combatweapon <player> <plain|sharpness>");
                Trial trial=ownedTrial(player);setWeapon(player,args[2]);trial.weapon=args[2];
                plugin.getLogger().info("GUARD_COMBAT_WEAPON {\"weapon\":\""+trial.weapon+"\",\"sharpnessLevel\":"+sharpnessLevel(player)+"}");
            }
            case "combatimpulse" -> {
                Trial trial=ownedTrial(player);player.setVelocity(new org.bukkit.util.Vector(0,.42,0));trial.impulses++;
                plugin.getLogger().info("GUARD_COMBAT_IMPULSE {\"impulses\":"+trial.impulses+",\"vx\":0,\"vy\":0.42,\"vz\":0}");
            }
            default -> {return false;}
        }
        return true;
    }
    private void prepare(Player player,String terrain) {
        if(!Set.of("open","wall","partial","slab","edge","ceiling","slab-floor").contains(terrain))throw new IllegalArgumentException("Unknown combat terrain");
        var at=player.getLocation();var world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ(),y=80;
        if(y-1<world.getMinHeight() || y+4>=world.getMaxHeight())throw new IllegalStateException("Combat height unavailable");
        for(int dx=-2;dx<=5;dx++)for(int dz=-2;dz<=3;dz++)
            if(!world.isChunkLoaded((x+dx)>>4,(z+dz)>>4) || !Bukkit.isOwnedByCurrentRegion(world,(x+dx)>>4,(z+dz)>>4))
                throw new IllegalStateException("Combat terrain must already be loaded and region-owned");
        // Validate every edited cell's bounds/ownership before clearing or changing the old trial.
        sharpness();clear(player);
        for(int dx=-2;dx<=5;dx++)for(int dz=-2;dz<=3;dz++) {
            world.getBlockAt(x+dx,y-1,z+dz).setType(Material.STONE,false);
            for(int dy=0;dy<=4;dy++)world.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);
        }
        if(terrain.equals("wall") || terrain.equals("edge")) {
            world.getBlockAt(x+1,y,z).setType(Material.STONE,false);
            world.getBlockAt(x+1,y+1,z).setType(Material.STONE,false);
        } else if(terrain.equals("partial"))world.getBlockAt(x+1,y,z).setType(Material.STONE,false);
        else if(terrain.equals("slab")) {
            var slab=(Slab)Bukkit.createBlockData(Material.STONE_SLAB);slab.setType(Slab.Type.BOTTOM);
            world.getBlockAt(x+1,y,z).setBlockData(slab,false);
        }
        if(terrain.equals("ceiling"))for(int dx=-2;dx<=5;dx++)for(int dz=-2;dz<=3;dz++)
            world.getBlockAt(x+dx,y+2,z+dz).setType(Material.STONE,false);
        if(terrain.equals("slab-floor")) {
            var slab=(Slab)Bukkit.createBlockData(Material.STONE_SLAB);slab.setType(Slab.Type.BOTTOM);
            for(int dx=-2;dx<=5;dx++)for(int dz=-2;dz<=3;dz++)world.getBlockAt(x+dx,y,z+dz).setBlockData(slab,false);
        }
        double floorY=y+(terrain.equals("slab-floor")?.5:0);
        player.clearActiveItem();player.getInventory().clear();setWeapon(player,"plain");
        for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
        player.setGameMode(GameMode.SURVIVAL);player.setSprinting(false);player.setInvulnerable(false);
        player.setFoodLevel(20);player.setSaturation(20);player.setHealth(20);player.setAbsorptionAmount(0);
        player.setVelocity(new org.bukkit.util.Vector());player.setFallDistance(0);
        var spawn=new Location(world,x+2.5,floorY,z+.5+(terrain.equals("edge")?.9:0));
        Cow target=world.spawn(spawn,Cow.class,cow->{
            cow.setAI(false);cow.setAware(false);cow.setGravity(false);cow.setSilent(true);cow.setPersistent(false);
            cow.setInvulnerable(false);cow.setCollidable(false);cow.setAbsorptionAmount(0);
            set(cow,Attribute.MAX_HEALTH,200);set(cow,Attribute.ARMOR,0);set(cow,Attribute.ARMOR_TOUGHNESS,0);
            set(cow,Attribute.KNOCKBACK_RESISTANCE,1);cow.setHealth(200);
            for(var effect:cow.getActivePotionEffects())cow.removePotionEffect(effect.getType());
        });
        var trial=new Trial(player,target,terrain,floorY);active=trial;
        player.teleportAsync(new Location(world,x+.5,floorY,z+.5,-90,0)).thenAccept(success->
            player.getScheduler().run(plugin,task->{
                if(active!=trial)return;
                if(!success || !Bukkit.isOwnedByCurrentRegion(target)) {
                    plugin.getLogger().severe("GUARD_PROBE_FAIL combat teleport/target ownership");return;
                }
                trial.minY=trial.maxY=floorY;trial.ready=true;
                plugin.getLogger().info("GUARD_COMBAT_READY {\"terrain\":\""+terrain+"\",\"targetId\":"+target.getEntityId()
                        +",\"targetUuid\":\""+target.getUniqueId()+"\",\"healthBefore\":"+trial.before
                        +",\"x\":"+spawn.getX()+",\"y\":"+spawn.getY()+",\"z\":"+spawn.getZ()
                        +",\"floorY\":"+floorY+",\"weapon\":\"plain\",\"sharpnessLevel\":"+sharpnessLevel(player)+"}");
            },null));
    }
    private static org.bukkit.enchantments.Enchantment sharpness() {
        var enchantment=Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness"));
        if(enchantment==null)throw new IllegalStateException("Sharpness registry entry unavailable");
        return enchantment;
    }
    private static int sharpnessLevel(Player player) {return player.getInventory().getItemInMainHand().getEnchantmentLevel(sharpness());}
    private static void setWeapon(Player player,String profile) {
        var item=new ItemStack(Material.IRON_SWORD);
        if(profile.equals("sharpness"))item.addEnchantment(sharpness(),5);
        player.getInventory().setItemInMainHand(item);
        if(player.getInventory().getItemInMainHand().getType()!=Material.IRON_SWORD
                ||sharpnessLevel(player)!=(profile.equals("sharpness")?5:0))throw new IllegalStateException("Combat weapon readback failed");
    }
    private static void set(Cow target,Attribute attribute,double value) {
        var instance=target.getAttribute(attribute);
        if(instance==null)throw new IllegalStateException("Missing target attribute: "+attribute);
        instance.setBaseValue(value);
    }
    private Trial ownedTrial(Player player) {
        Trial trial=active;
        if(trial==null || !trial.player.equals(player.getUniqueId()) || !Bukkit.isOwnedByCurrentRegion(player)
                || !Bukkit.isOwnedByCurrentRegion(trial.target))
            throw new IllegalStateException("Combat trial missing/not owned");
        return trial;
    }
    private Trial trial(Player player,org.bukkit.entity.Entity target) {
        Trial trial=active;
        return trial!=null && trial.player.equals(player.getUniqueId()) && trial.target.getUniqueId().equals(target.getUniqueId())?trial:null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void attack(PrePlayerAttackEntityEvent event) {
        Trial trial=trial(event.getPlayer(),event.getAttacked());if(trial==null)return;
        if(!Bukkit.isOwnedByCurrentRegion(trial.target))throw new IllegalStateException("Foreign-region combat target");
        trial.preAttackCalls++;if(event.isCancelled())trial.preAttackCancelled++;if(event.willAttack())trial.preAttackWillAttack++;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=false)
    public void latePreCancellation(PrePlayerAttackEntityEvent event) {
        Trial trial=trial(event.getPlayer(),event.getAttacked());
        if(trial!=null&&trial.control.equals("late-pre-cancel"))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
    public void priorCancellation(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Player player))return;
        Trial trial=trial(player,event.getEntity());if(trial==null)return;
        if(trial.control.equals("cancel"))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void extraDamage(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Player player))return;
        Trial trial=trial(player,event.getEntity());if(trial!=null && trial.control.equals("extra"))event.setDamage(event.getDamage()+3);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        Trial trial=active;Player player=event.getPlayer();
        if(trial==null || !trial.ready || !trial.player.equals(player.getUniqueId()))return;
        if(!Bukkit.isOwnedByCurrentRegion(player))throw new IllegalStateException("Foreign-owner combat movement");
        var to=event.getTo();if(to==null || to.getWorld()!=event.getFrom().getWorld())return;
        trial.sawPlayerMove|=event.hasChangedPosition();
        trial.minY=Math.min(trial.minY,to.getY());trial.maxY=Math.max(trial.maxY,to.getY());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void damage(EntityDamageByEntityEvent event) {
        if(!(event.getDamager() instanceof Player player))return;
        Trial trial=trial(player,event.getEntity());if(trial==null)return;
        if(!Bukkit.isOwnedByCurrentRegion(trial.target))throw new IllegalStateException("Foreign-region combat damage");
        trial.damageEvents++;if(event.isCritical())trial.criticalDamageEvents++;
        if(event.isCancelled())trial.cancelledDamageEvents++;
        else if(event.getFinalDamage()>0) {
            trial.successfulDamageEvents++;trial.totalFinalDamage+=event.getFinalDamage();
            if(event.isCritical())trial.successfulCriticalEvents++;
        }
    }
    private void result(Player player) {
        Trial trial=ownedTrial(player);double after=trial.target.isDead()?0:trial.target.getHealth();
        plugin.getLogger().info("GUARD_COMBAT_RESULT {\"terrain\":\""+trial.terrain+"\",\"control\":\""+trial.control
                +"\",\"targetId\":"+trial.target.getEntityId()+",\"healthBefore\":"+trial.before+",\"healthAfter\":"+after
                +",\"actualDamage\":"+(trial.before-after)+",\"preAttackCalls\":"+trial.preAttackCalls
                +",\"preAttackCancelled\":"+trial.preAttackCancelled+",\"preAttackWillAttack\":"+trial.preAttackWillAttack
                +",\"damageEvents\":"+trial.damageEvents+",\"cancelledDamageEvents\":"+trial.cancelledDamageEvents
                +",\"criticalDamageEvents\":"+trial.criticalDamageEvents+",\"successfulDamageEvents\":"+trial.successfulDamageEvents
                +",\"successfulCriticalEvents\":"+trial.successfulCriticalEvents+",\"totalFinalDamage\":"+trial.totalFinalDamage
                +",\"floorY\":"+trial.floorY+",\"playerMinY\":"+trial.minY+",\"playerMaxY\":"+trial.maxY
                +",\"playerMaxRise\":"+(trial.maxY-trial.floorY)+",\"sawPlayerMove\":"+trial.sawPlayerMove+",\"impulses\":"+trial.impulses
                +",\"weapon\":\""+trial.weapon+"\",\"sharpnessLevel\":"+sharpnessLevel(player)+"}");
    }
    private void clear(Player player) {
        Trial trial=active;if(trial==null)return;
        if(!trial.player.equals(player.getUniqueId()) || !Bukkit.isOwnedByCurrentRegion(trial.target))
            throw new IllegalStateException("Cannot clear foreign combat trial");
        active=null;trial.target.remove();plugin.getLogger().info("GUARD_COMBAT_CLEARED");
    }
}
