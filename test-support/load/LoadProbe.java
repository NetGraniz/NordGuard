package dev.nordfjell.load;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper-only measurement fixture. Never installed on production. */
public final class LoadProbe extends JavaPlugin implements Listener {
    private final ArrayList<Double> durations = new ArrayList<>();
    private long moves, start, cpuStart, gcStart, gcCountStart, heapMax;
    private final com.sun.management.OperatingSystemMXBean os =
        (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        reset();
    }
    private long gcTime() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x -> Math.max(0,x.getCollectionTime())).sum(); }
    private long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(x -> Math.max(0,x.getCollectionCount())).sum(); }
    private void reset() {
        durations.clear(); moves=0; heapMax=0;
        start=System.nanoTime(); cpuStart=os.getProcessCpuTime(); gcStart=gcTime(); gcCountStart=gcCount();
    }
    @EventHandler public void tick(ServerTickEndEvent event) {
        if (durations.size()<12000) durations.add(event.getTickDuration());
        heapMax=Math.max(heapMax,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
    }
    @EventHandler public void move(PlayerMoveEvent event) { if(event.hasChangedPosition()) moves++; }
    private double percentile(double q) { return durations.isEmpty()?0:durations.get(Math.min(durations.size()-1,(int)Math.ceil(q*durations.size())-1)); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender)) return true;
        if(args.length==1 && args[0].equals("reset")) { reset(); getLogger().info("LOAD_RESET"); return true; }
        Collections.sort(durations);
        double seconds=(System.nanoTime()-start)/1e9;
        getLogger().info("LOAD_REPORT {\"players\":"+getServer().getOnlinePlayers().size()
            +",\"seconds\":"+seconds+",\"ticks\":"+durations.size()+",\"moves\":"+moves
            +",\"msptMedian\":"+percentile(.5)+",\"msptP95\":"+percentile(.95)+",\"msptMax\":"+percentile(1)
            +",\"processCpuCores\":"+((os.getProcessCpuTime()-cpuStart)/1e9/seconds)
            +",\"gcMillis\":"+(gcTime()-gcStart)+",\"gcCount\":"+(gcCount()-gcCountStart)
            +",\"sampledHeapMaxBytes\":"+heapMax+"}");
        return true;
    }
}
