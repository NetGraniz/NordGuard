package dev.nordfjell.guard;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import java.lang.reflect.Field;
import java.util.EnumMap;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GuardProbe extends JavaPlugin implements Listener {
    private int falls;
    private boolean cancel;
    private boolean suppressFallDistance;
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this, this); }
    @EventHandler public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            falls++; if (cancel) event.setCancelled(true);
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void move(org.bukkit.event.player.PlayerMoveEvent event) {
        if (suppressFallDistance && event.getPlayer().getName().equals("GuardFixture")) event.getPlayer().setFallDistance(0);
    }
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) || args.length < 2) return true;
        Player player = Bukkit.getPlayerExact(args[1]); if (player == null) return true;
        String action = args[0];
        player.getScheduler().run(this, task -> {
            try {
                if (action.equals("prepare")) {
                    Location at = player.getLocation();
                    int x = at.getBlockX(), z = at.getBlockZ(), y = 80;
                    for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = 0; dy <= 16; dy++) at.getWorld().getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                        at.getWorld().getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE, false);
                    }
                    player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(false); player.setHealth(20);
                    player.teleportAsync(new Location(at.getWorld(), x + .5, y, z + .5)).thenRun(() ->
                        player.getScheduler().run(this, ignored -> getLogger().info("GUARD_PREPARED"), null));
                } else if (action.equals("probe")) {
                    var at = player.getLocation(); var env = EnvironmentProbe.inspect(player, at);
                    if (!env.known() || !env.ground() || !env.clear() || env.special()) throw new AssertionError("Geometry: " + env);
                    getLogger().info("GUARD_GEOMETRY_PASS");
                } else if (action.equals("permissions")) {
                    if (player.hasPermission("nordguard.admin") || player.hasPermission("nordguard.bypass")
                            || player.hasPermission("nordguard.alerts")) throw new AssertionError("Unexpected default permission");
                    getLogger().info("GUARD_PERMISSIONS_PASS");
                } else if (action.equals("lift")) {
                    player.setHealth(20);
                    player.setNoDamageTicks(0);
                    player.teleportAsync(player.getLocation().add(0, 8, 0)).thenRun(() ->
                        player.getScheduler().run(this, ignored -> {
                            Location at = player.getLocation();
                            getLogger().info("GUARD_LIFT " + at.getX() + " " + at.getY() + " " + at.getZ());
                        }, null));
                } else if (action.equals("correct") || action.equals("setback")) {
                    var guard = (NordGuard) Bukkit.getPluginManager().getPlugin("NordGuard");
                    Field field = NordGuard.class.getDeclaredField("policy"); field.setAccessible(true);
                    Policy old = (Policy) field.get(guard);
                    var modes = new EnumMap<Check, Policy.Mode>(old.modes());
                    modes.put(action.equals("correct") ? Check.NOFALL : Check.FLIGHT, Policy.Mode.CORRECT);
                    field.set(guard, new Policy(modes, old.buffer(), old.horizontalMargin(), old.verticalMargin(),
                            old.burstTicks(), old.joinGrace(), old.transitionGrace(), old.maxGapNanos(), old.alertNanos(), false));
                    getLogger().info(action.equals("correct") ? "GUARD_CORRECT_READY" : "GUARD_SETBACK_READY");
                } else if (action.equals("suppress")) {
                    suppressFallDistance = true;
                    getLogger().info("GUARD_SUPPRESSION_READY");
                } else if (action.equals("health")) {
                    getLogger().info("GUARD_HEALTH " + player.getHealth() + " FALL_EVENTS=" + falls + " Y=" + player.getY());
                    getLogger().info("GUARD_RULE " + player.getWorld().getGameRuleValue(org.bukkit.GameRule.FALL_DAMAGE)
                            + " MULT=" + player.getAttribute(org.bukkit.attribute.Attribute.FALL_DAMAGE_MULTIPLIER).getValue());
                    var guard = (NordGuard) Bukkit.getPluginManager().getPlugin("NordGuard");
                    Field sessionsField = NordGuard.class.getDeclaredField("sessions"); sessionsField.setAccessible(true);
                    Object session = ((java.util.Map<?, ?>) sessionsField.get(guard)).get(player.getUniqueId());
                    for (String name : new String[]{"fallEvents", "fallBaseline", "pendingBaseline", "pendingFall", "airborne", "grace"}) {
                        Field value = session.getClass().getDeclaredField(name); value.setAccessible(true);
                        getLogger().info("GUARD_DEBUG " + name + "=" + value.get(session));
                    }
                } else if (action.equals("partial")) {
                    player.setHealth(20); player.setNoDamageTicks(0);
                    int before = falls;
                    var bridge = new NativeFall();
                    if (bridge.expected(player, 8) != 5) throw new AssertionError("Unexpected fixture base damage");
                    bridge.applyRemaining(player, 8, 4);
                    if (falls != before + 1 || player.getHealth() != 16) throw new AssertionError("Partial native recovery mismatch");
                    getLogger().info("GUARD_NATIVE_PASS partial");
                } else if (action.equals("fall") || action.equals("cancel")) {
                    cancel = action.equals("cancel"); int before = falls; double health = player.getHealth();
                    player.setNoDamageTicks(0);
                    new NativeFall().apply(player, 8);
                    if (falls != before + 1) throw new AssertionError("Native FALL event missing");
                    if (cancel && player.getHealth() != health) throw new AssertionError("Cancelled damage changed health");
                    if (!cancel && player.getHealth() >= health) throw new AssertionError("Native damage did not apply");
                    cancel = false;
                    getLogger().info("GUARD_NATIVE_PASS " + action);
                }
            } catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "GUARD_PROBE_FAIL", error); }
        }, null);
        return true;
    }
}
