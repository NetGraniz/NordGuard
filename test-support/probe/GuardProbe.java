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
                    player.clearActiveItem();
                    Location at = player.getLocation();
                    int x = at.getBlockX(), z = at.getBlockZ(), y = 80;
                    for (int dx = -2; dx <= 24; dx++) for (int dz = -2; dz <= 2; dz++) {
                        if (!Bukkit.isOwnedByCurrentRegion(at.getWorld(), (x + dx) >> 4, (z + dz) >> 4)
                                || !at.getWorld().isChunkLoaded((x + dx) >> 4, (z + dz) >> 4))
                            throw new AssertionError("Fixture terrain is not loaded and region-owned");
                        for (int dy = 0; dy <= 16; dy++) at.getWorld().getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
                        at.getWorld().getBlockAt(x + dx, y - 1, z + dz).setType(Material.STONE, false);
                    }
                    player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(false); player.setHealth(20);
                    player.teleportAsync(new Location(at.getWorld(), x + .5, y, z + .5)).thenRun(() ->
                        player.getScheduler().run(this, ignored -> getLogger().info("GUARD_PREPARED"), null));
                } else if (action.equals("terrain")) {
                    String kind = args[2];
                    Location at = player.getLocation();
                    int x = at.getBlockX(), z = at.getBlockZ();
                    for (int dx = 0; dx <= 22; dx++) for (int dz = -2; dz <= 2; dz++) {
                        if (!Bukkit.isOwnedByCurrentRegion(at.getWorld(), (x + dx) >> 4, (z + dz) >> 4)
                                || !at.getWorld().isChunkLoaded((x + dx) >> 4, (z + dz) >> 4))
                            throw new AssertionError("Medium fixture not loaded/owned");
                        for (int y = 80; y < 96; y++) at.getWorld().getBlockAt(x + dx, y, z + dz).setType(Material.AIR, false);
                        at.getWorld().getBlockAt(x + dx, 79, z + dz).setType(Material.STONE, false);
                        if (kind.equals("waterwalk") && dx >= 2) {
                            at.getWorld().getBlockAt(x + dx, 78, z + dz).setType(Material.STONE, false);
                            at.getWorld().getBlockAt(x + dx, 79, z + dz).setType(Material.WATER, false);
                        }
                        if (kind.equals("noweb") && dx >= 2)
                            for (int y = 80; y <= 81; y++) at.getWorld().getBlockAt(x + dx, y, z + dz).setType(Material.COBWEB, false);
                    }
                    if (kind.equals("spider") || kind.equals("climb")) {
                        for (int y = 80; y < 96; y++) {
                            at.getWorld().getBlockAt(x + 1, y, z).setType(Material.STONE, false);
                            if (kind.equals("climb")) {
                                var ladder = (org.bukkit.block.data.Directional) Bukkit.createBlockData(Material.LADDER);
                                ladder.setFacing(org.bukkit.block.BlockFace.WEST);
                                at.getWorld().getBlockAt(x, y, z).setBlockData(ladder, false);
                            }
                        }
                    }
                    player.teleportAsync(new Location(at.getWorld(), x + (kind.equals("climb") ? .5 : .69), 80, z + .5)).thenRun(() ->
                        player.getScheduler().run(this, ignored -> getLogger().info("GUARD_TERRAIN " + kind), null));
                } else if (action.equals("environment")) {
                    getLogger().info("GUARD_ENV " + EnvironmentProbe.inspect(player, player.getLocation())
                            + " climbing=" + player.isClimbing());
                } else if (action.equals("item") || action.equals("itemcustom")) {
                    player.clearActiveItem();
                    var item = new org.bukkit.inventory.ItemStack(Material.SHIELD);
                    if (action.equals("itemcustom")) item.setData(io.papermc.paper.datacomponent.DataComponentTypes.USE_EFFECTS,
                            io.papermc.paper.datacomponent.item.UseEffects.useEffects().speedMultiplier(1).build());
                    player.getInventory().setItemInMainHand(item);
                    player.startUsingItem(org.bukkit.inventory.EquipmentSlot.HAND);
                    if (!player.hasActiveItem()) throw new AssertionError("Item usage did not start");
                    getLogger().info("GUARD_ITEM " + action);
                } else if (action.equals("using")) {
                    if (!player.hasActiveItem() || player.getActiveItemUsedTime() < 10) throw new AssertionError("Stable item usage missing");
                    getLogger().info("GUARD_USING " + player.getActiveItemUsedTime());
                } else if (action.equals("spider") || action.equals("waterwalk") || action.equals("climb") || action.equals("noweb") || action.equals("noslow") || action.equals("observe")) {
                    var guard = (NordGuard) Bukkit.getPluginManager().getPlugin("NordGuard");
                    Field field = NordGuard.class.getDeclaredField("policy"); field.setAccessible(true);
                    Policy old = (Policy) field.get(guard);
                    var modes = new EnumMap<Check, Policy.Mode>(Check.class);
                    for (Check check : Check.values()) modes.put(check, Policy.Mode.OBSERVE);
                    if (!action.equals("observe")) modes.put(Check.valueOf(action.toUpperCase(java.util.Locale.ROOT)), Policy.Mode.CORRECT);
                    field.set(guard, new Policy(modes, old.buffer(), old.horizontalMargin(), old.verticalMargin(),
                            old.burstTicks(), old.joinGrace(), old.transitionGrace(), old.maxGapNanos(), old.alertNanos(), false));
                    getLogger().info("GUARD_MODE " + action);
                } else if (action.equals("probe")) {
                    var at = player.getLocation(); var env = EnvironmentProbe.inspect(player, at);
                    if (!env.known() || !env.ground() || !env.clear() || env.special()) throw new AssertionError("Geometry: " + env);
                    getLogger().info("GUARD_GEOMETRY_PASS");
                } else if (action.equals("permissions")) {
                    if (player.hasPermission("nordguard.admin") || player.hasPermission("nordguard.bypass")
                            || player.hasPermission("nordguard.alerts")) throw new AssertionError("Unexpected default permission");
                    getLogger().info("GUARD_PERMISSIONS_PASS");
                } else if (action.equals("origin")) {
                    var guard = Bukkit.getPluginManager().getPlugin("NordGuard");
                    Field map = NordGuard.class.getDeclaredField("sessions"); map.setAccessible(true);
                    Object session = ((java.util.Map<?, ?>) map.get(guard)).get(player.getUniqueId());
                    Field revision = session.getClass().getDeclaredField("originRevision"); revision.setAccessible(true);
                    long before = revision.getLong(session);
                    player.teleportAsync(player.getLocation().add(8, 0, 0)).thenRun(() ->
                        player.getScheduler().runDelayed(this, ignored -> {
                            try {
                                Field safe = session.getClass().getDeclaredField("safe"); safe.setAccessible(true);
                                Location anchor = (Location) safe.get(session);
                                if (revision.getLong(session) <= before) throw new AssertionError("External teleport did not change origin revision");
                                if (anchor != null && anchor.distanceSquared(player.getLocation()) > .0001)
                                    throw new AssertionError("External teleport kept old anchor: " + anchor + "; actual=" + player.getLocation());
                                getLogger().info("GUARD_ORIGIN_RESET");
                            } catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "GUARD_PROBE_FAIL", error); }
                        }, null, 3));
                } else if (action.equals("lift")) {
                    player.setHealth(20);
                    player.setNoDamageTicks(0);
                    player.teleportAsync(player.getLocation().add(0, 8, 0)).thenRun(() ->
                        player.getScheduler().run(this, ignored -> {
                            Location at = player.getLocation();
                            getLogger().info("GUARD_LIFT " + at.getX() + " " + at.getY() + " " + at.getZ());
                        }, null));
                } else if (action.equals("correct") || action.equals("setback") || action.equals("speed")) {
                    var guard = (NordGuard) Bukkit.getPluginManager().getPlugin("NordGuard");
                    Field field = NordGuard.class.getDeclaredField("policy"); field.setAccessible(true);
                    Policy old = (Policy) field.get(guard);
                    var modes = new EnumMap<Check, Policy.Mode>(old.modes());
                    if (action.equals("speed")) {
                        modes.put(Check.FLIGHT, Policy.Mode.OBSERVE);
                        modes.put(Check.SPEED, Policy.Mode.CORRECT);
                    } else modes.put(action.equals("correct") ? Check.NOFALL : Check.FLIGHT, Policy.Mode.CORRECT);
                    field.set(guard, new Policy(modes, old.buffer(), old.horizontalMargin(), old.verticalMargin(),
                            old.burstTicks(), old.joinGrace(), old.transitionGrace(), old.maxGapNanos(), old.alertNanos(), false));
                    getLogger().info(action.equals("correct") ? "GUARD_CORRECT_READY"
                            : action.equals("speed") ? "GUARD_SPEED_READY" : "GUARD_SETBACK_READY");
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
