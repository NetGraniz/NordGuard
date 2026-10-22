package dev.nordfjell.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Owner-only synthetic mining measurements. Never breaks blocks on the client's behalf. */
final class MiningProbe implements Listener {
    private static final int MAX_RECORDS=128;
    private final JavaPlugin plugin;
    private volatile Trial active;
    private static final class Cell {
        final int x,y,z;
        final Material initial;
        final boolean primary;
        long start=-1,startCalls,breakCalls,accepted,cancelled;
        double speed,lastBreakSinceStartMillis=-1;
        Cell(int x,int y,int z,Material initial,boolean primary) {this.x=x;this.y=y;this.z=z;this.initial=initial;this.primary=primary;}
    }
    private static final class Trial {
        final UUID player;
        final World world;
        final String profile,tool;
        final List<Cell> cells;
        final List<String> records=new ArrayList<>();
        long readyNanos,firstMiningNanos=-1,firstAcceptedNanos=-1,lastAcceptedNanos=-1;
        long starts,cancelledStarts,instantStarts,aborts,breakCalls,accepted,cancelled,dropEvents,cancelledDropEvents,droppedStacks,droppedItems;
        boolean ready;
        Trial(Player player,String profile,String tool,List<Cell> cells) {
            this.player=player.getUniqueId();world=player.getWorld();this.profile=profile;this.tool=tool;this.cells=List.copyOf(cells);
        }
    }
    MiningProbe(JavaPlugin plugin) {this.plugin=plugin;}
    boolean command(Player player,String[] args) {
        switch(args[0]) {
            case "miningprepare" -> {if(args.length!=3)throw new IllegalArgumentException("miningprepare <player> <dirt|stone|instant|creative>");prepare(player,args[2]);}
            case "miningresult" -> result(player);
            case "miningclear" -> {
                Trial trial=owned(player);active=null;plugin.getLogger().info("GUARD_MINING_CLEARED "+trial.profile);
            }
            default -> {return false;}
        }
        return true;
    }
    private void prepare(Player player,String profile) {
        if(!Set.of("dirt","stone","instant","creative").contains(profile))throw new IllegalArgumentException("Unknown mining profile");
        if(!Bukkit.isOwnedByCurrentRegion(player))throw new IllegalStateException("Mining prepare must own player");
        var at=player.getLocation();var world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ(),y=80;
        if(y-2<world.getMinHeight()||y+4>=world.getMaxHeight())throw new IllegalStateException("Mining height unavailable");
        for(int dx=-2;dx<=6;dx++)for(int dz=-2;dz<=3;dz++)check(world,x+dx,z+dz);
        var efficiency=Registry.ENCHANTMENT.get(NamespacedKey.minecraft("efficiency"));
        if(efficiency==null)throw new IllegalStateException("Efficiency registry entry missing");
        if(active!=null)owned(player); // Validate the old trial before any mutation.
        active=null;
        for(int dx=-2;dx<=6;dx++)for(int dz=-2;dz<=3;dz++) {
            world.getBlockAt(x+dx,y-2,z+dz).setType(Material.STONE,false);
            world.getBlockAt(x+dx,y-1,z+dz).setType(Material.STONE,false);
            for(int dy=0;dy<=4;dy++)world.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);
        }
        var cells=new ArrayList<Cell>();Material type=profile.equals("dirt")?Material.DIRT:Material.STONE;
        for(int dz=0;dz<=1;dz++)for(int dx=1;dx<=4;dx++) {
            world.getBlockAt(x+dx,y,z+dz).setType(type,false);
            cells.add(new Cell(x+dx,y,z+dz,type,true));
        }
        for(int dz=0;dz<=1;dz++)for(int dx=1;dx<=4;dx++) {
            world.getBlockAt(x+dx,y-1,z+dz).setType(Material.DIRT,false);
            cells.add(new Cell(x+dx,y-1,z+dz,Material.DIRT,false));
        }
        player.clearActiveItem();player.getInventory().clear();
        for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
        player.setGameMode(profile.equals("creative")?GameMode.CREATIVE:GameMode.SURVIVAL);
        player.setFlying(false);player.setSprinting(false);player.setFoodLevel(20);player.setSaturation(20);
        player.setVelocity(new org.bukkit.util.Vector());player.setFallDistance(0);
        String tool="empty-hand";
        if(profile.equals("stone")) {player.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_PICKAXE));tool="wooden-pickaxe";}
        else if(profile.equals("instant")) {
            var pickaxe=new ItemStack(Material.DIAMOND_PICKAXE);pickaxe.addEnchantment(efficiency,5);
            player.getInventory().setItemInMainHand(pickaxe);
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,2400,1));tool="diamond-pickaxe-efficiency5-haste2";
        }
        var trial=new Trial(player,profile,tool,cells);active=trial;
        player.teleportAsync(new Location(world,x+.5,y,z+.5,-90,35)).thenAccept(success->
                player.getScheduler().run(plugin,task->{
                    if(active!=trial)return;
                    if(!success) {plugin.getLogger().severe("GUARD_PROBE_FAIL mining teleport");return;}
                    owned(player);trial.readyNanos=System.nanoTime();trial.ready=true;
                    for(Cell cell:cells)cell.speed=block(trial,cell).getBreakSpeed(player);
                    plugin.getLogger().info("GUARD_MINING_READY {\"profile\":\""+profile+"\",\"tool\":\""+trial.tool
                            +"\",\"creative\":"+profile.equals("creative")+",\"playerX\":"+(x+.5)+",\"playerY\":"+y+",\"playerZ\":"+(z+.5)
                            +",\"targets\":"+positions(trial,true)+",\"below\":"+positions(trial,false)+"}");
                },null));
    }
    private static void check(World world,int x,int z) {
        if(!world.isChunkLoaded(x>>4,z>>4)||!Bukkit.isOwnedByCurrentRegion(world,x>>4,z>>4))
            throw new IllegalStateException("Mining cells must already be loaded and owned");
    }
    private static Block block(Trial trial,Cell cell) {check(trial.world,cell.x,cell.z);return trial.world.getBlockAt(cell.x,cell.y,cell.z);}
    private Trial owned(Player player) {
        Trial trial=active;
        if(trial==null||!trial.player.equals(player.getUniqueId())||trial.world!=player.getWorld()||!Bukkit.isOwnedByCurrentRegion(player))
            throw new IllegalStateException("Mining trial missing/not owned");
        for(Cell cell:trial.cells)check(trial.world,cell.x,cell.z);
        return trial;
    }
    private Trial trial(Player player,Block block) {
        Trial trial=active;
        return trial!=null&&trial.ready&&trial.player.equals(player.getUniqueId())&&trial.world==block.getWorld()?trial:null;
    }
    private static Cell cell(Trial trial,Block block) {
        for(Cell cell:trial.cells)if(cell.x==block.getX()&&cell.y==block.getY()&&cell.z==block.getZ())return cell;
        return null;
    }
    private static void started(Trial trial,long now) {if(trial.firstMiningNanos<0)trial.firstMiningNanos=now;}
    private static void record(Trial trial,Cell cell,String kind,long now,boolean cancelled) {
        if(trial.records.size()>=MAX_RECORDS)return;
        trial.records.add("{\"kind\":\""+kind+"\",\"x\":"+cell.x+",\"y\":"+cell.y+",\"z\":"+cell.z
                +",\"elapsedMillis\":"+((now-trial.readyNanos)/1_000_000.0)+",\"cancelled\":"+cancelled+"}");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void start(BlockDamageEvent event) {
        var trial=trial(event.getPlayer(),event.getBlock());if(trial==null)return;
        var cell=cell(trial,event.getBlock());if(cell==null)return;owned(event.getPlayer());
        long now=System.nanoTime();started(trial,now);trial.starts++;cell.startCalls++;
        if(event.isCancelled())trial.cancelledStarts++;
        else {
            cell.start=now;cell.speed=event.getBlock().getBreakSpeed(event.getPlayer());
            if(event.getInstaBreak()||cell.speed>=1)trial.instantStarts++;
        }
        record(trial,cell,"start",now,event.isCancelled());
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void abort(BlockDamageAbortEvent event) {
        var trial=trial(event.getPlayer(),event.getBlock());if(trial==null)return;
        var cell=cell(trial,event.getBlock());if(cell==null)return;owned(event.getPlayer());
        trial.aborts++;record(trial,cell,"abort",System.nanoTime(),false);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void destroy(BlockBreakEvent event) {
        var trial=trial(event.getPlayer(),event.getBlock());if(trial==null)return;
        var cell=cell(trial,event.getBlock());if(cell==null)return;owned(event.getPlayer());
        long now=System.nanoTime();started(trial,now);trial.breakCalls++;cell.breakCalls++;
        cell.lastBreakSinceStartMillis=cell.start<0?-1:(now-cell.start)/1_000_000.0;
        if(event.isCancelled()) {trial.cancelled++;cell.cancelled++;}
        else {
            trial.accepted++;cell.accepted++;if(trial.firstAcceptedNanos<0)trial.firstAcceptedNanos=now;trial.lastAcceptedNanos=now;
        }
        record(trial,cell,"break",now,event.isCancelled());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=false)
    public void drop(BlockDropItemEvent event) {
        var trial=trial(event.getPlayer(),event.getBlock());if(trial==null)return;
        var cell=cell(trial,event.getBlock());if(cell==null)return;owned(event.getPlayer());
        trial.dropEvents++;
        if(event.isCancelled())trial.cancelledDropEvents++;
        else {trial.droppedStacks+=event.getItems().size();for(var item:event.getItems())trial.droppedItems+=item.getItemStack().getAmount();}
        record(trial,cell,"drop",System.nanoTime(),event.isCancelled());
    }
    private static String number(double value) {return Double.isFinite(value)?Double.toString(value):"null";}
    private static String positions(Trial trial,boolean primary) {
        var result=new ArrayList<String>();
        for(Cell cell:trial.cells)if(cell.primary==primary)result.add("{\"x\":"+cell.x+",\"y\":"+cell.y+",\"z\":"+cell.z
                +",\"type\":\""+cell.initial+"\",\"breakSpeed\":"+number(cell.speed)+"}");
        return "["+String.join(",",result)+"]";
    }
    private void result(Player player) {
        Trial trial=owned(player);int primaryChanged=0,primaryAir=0,belowChanged=0,belowAir=0;long now=System.nanoTime();
        var positions=new ArrayList<String>();
        for(Cell cell:trial.cells) {
            Material current=block(trial,cell).getType();boolean changed=current!=cell.initial,air=current.isAir();
            if(cell.primary) {if(changed)primaryChanged++;if(air)primaryAir++;}
            else {if(changed)belowChanged++;if(air)belowAir++;}
            positions.add("{\"x\":"+cell.x+",\"y\":"+cell.y+",\"z\":"+cell.z+",\"primary\":"+cell.primary
                    +",\"initialType\":\""+cell.initial+"\",\"currentType\":\""+current+"\",\"changed\":"+changed
                    +",\"startCalls\":"+cell.startCalls+",\"breakCalls\":"+cell.breakCalls+",\"acceptedBreaks\":"+cell.accepted
                    +",\"cancelledBreaks\":"+cell.cancelled+",\"nativeBreakSpeed\":"+number(cell.speed)
                    +",\"lastBreakSinceStartMillis\":"+cell.lastBreakSinceStartMillis+"}");
        }
        plugin.getLogger().info("GUARD_MINING_RESULT {\"profile\":\""+trial.profile+"\",\"tool\":\""+trial.tool
                +"\",\"creative\":"+(player.getGameMode()==GameMode.CREATIVE)+",\"primaryTotal\":8,\"belowTotal\":8"
                +",\"primaryChanged\":"+primaryChanged+",\"primaryAir\":"+primaryAir+",\"belowChanged\":"+belowChanged+",\"belowAir\":"+belowAir
                +",\"starts\":"+trial.starts+",\"cancelledStarts\":"+trial.cancelledStarts+",\"instantStarts\":"+trial.instantStarts+",\"aborts\":"+trial.aborts
                +",\"breakCalls\":"+trial.breakCalls+",\"acceptedBreaks\":"+trial.accepted+",\"cancelledBreaks\":"+trial.cancelled
                +",\"dropEvents\":"+trial.dropEvents+",\"cancelledDropEvents\":"+trial.cancelledDropEvents+",\"droppedStacks\":"+trial.droppedStacks+",\"droppedItems\":"+trial.droppedItems
                +",\"sinceReadyMillis\":"+((now-trial.readyNanos)/1_000_000.0)
                +",\"elapsedMillis\":"+(trial.firstMiningNanos<0?0:(now-trial.firstMiningNanos)/1_000_000.0)
                +",\"firstAcceptedMillis\":"+(trial.firstAcceptedNanos<0?-1:(trial.firstAcceptedNanos-trial.readyNanos)/1_000_000.0)
                +",\"acceptedSpanMillis\":"+(trial.firstAcceptedNanos<0?0:(trial.lastAcceptedNanos-trial.firstAcceptedNanos)/1_000_000.0)
                +",\"positions\":["+String.join(",",positions)+"],\"records\":["+String.join(",",trial.records)+"]}");
    }
}
