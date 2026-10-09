package dev.nordfjell.guard;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;

public final class NordGuard extends JavaPlugin implements Listener {
    private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();
    private final LongAdder samples = new LongAdder(), sampleNanos = new LongAdder(), skipped = new LongAdder();
    private final LongAdder corrections = new LongAdder();
    private final LongAdder[] violations = Arrays.stream(Check.values()).map(c -> new LongAdder()).toArray(LongAdder[]::new);
    private volatile Policy policy;
    private NativeFall nativeFall;
    private NativeTeleport nativeTeleport;

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            policy = readPolicy();
            if (!Bukkit.getMinecraftVersion().equals("26.2"))
                throw new IllegalStateException("This test build supports Minecraft 26.2 only");
            nativeFall = new NativeFall();
            nativeTeleport = new NativeTeleport();
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "NordGuard cannot start safely", error);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player player : Bukkit.getOnlinePlayers()) player.getScheduler().run(this, task -> attach(player), null);
        getLogger().info("NordGuard " + getDescription().getVersion() + " enabled; modes=" + policy.modes() + "; no bans or external services");
    }
    @Override public void onDisable() {
        sessions.values().forEach(s -> { if (s.task != null) s.task.cancel(); });
        sessions.clear(); subscribers.clear();
    }
    private Policy readPolicy() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(new java.io.File(getDataFolder(), "config.yml"));
        return Policy.read(yaml);
    }
    private void attach(Player player) {
        var session = new Session(player);
        Session previous = sessions.putIfAbsent(player.getUniqueId(), session);
        if (previous != null) return;
        session.task = player.getScheduler().runAtFixedRate(this, ignored -> session.tick(),
                () -> sessions.remove(player.getUniqueId(), session), 1, 1);
        if (session.task == null) sessions.remove(player.getUniqueId(), session);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null && session.task != null) session.task.cancel();
        subscribers.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.teleporting && session.setbackTarget != null && event.getTo() != null
                && event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                && event.getTo().getWorld() == session.setbackTarget.getWorld()
                && event.getTo().distanceSquared(session.setbackTarget) < 1.0E-8) return;
        grace(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event) { grace(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void world(PlayerChangedWorldEvent event) { grace(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent event) { grace(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void velocity(PlayerVelocityEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            session.suspend(2);
            var velocity = event.getVelocity();
            session.impulseSpeed = Math.min(32, Math.hypot(velocity.getX(), velocity.getZ()));
            session.impulseY = Math.min(32, Math.max(0, velocity.getY()));
        }
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void damage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        // A cancelled FALL event counts too: respect deliberate protection from another plugin.
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            session.fallEvents++;
            double original = event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE);
            if (event.isCancelled() || Math.abs(event.getDamage() - original) > 1.0E-6) session.fallOverrides++;
            else session.fallRawDamage += Math.max(0, original);
        }
    }
    private void grace(Player player) {
        Session session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.originRevision++;
            session.safe = session.setbackTarget = null;
            session.teleporting = false;
            session.suspend(policy.transitionGrace());
        }
    }

    private void report(Session session, Check check, String detail) {
        violations[check.ordinal()].increment();
        long now = System.nanoTime();
        if (now - session.lastAlert[check.ordinal()] < policy.alertNanos()) return;
        session.lastAlert[check.ordinal()] = now;
        String message = "[NordGuard] " + session.player.getName() + " " + check + " " + detail;
        if (policy.console()) getLogger().info(message);
        for (UUID id : subscribers) {
            Player receiver = Bukkit.getPlayer(id);
            if (receiver != null) receiver.getScheduler().run(this, task -> {
                if (receiver.hasPermission("nordguard.alerts")) receiver.sendMessage(message);
                else subscribers.remove(id);
            }, null);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("nordguard.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length != 1) return false;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                long count = samples.sum();
                sender.sendMessage("NordGuard " + getDescription().getVersion() + " | sessions=" + sessions.size() + " | modes=" + policy.modes());
                sender.sendMessage("Samples=" + count + ", deferred=" + skipped.sum() + ", corrections=" + corrections.sum()
                        + ", mean sample us=" + (count == 0 ? 0 : sampleNanos.sum() / count / 1000));
                for (Check check : Check.values()) sender.sendMessage(check + ": " + violations[check.ordinal()].sum());
            }
            case "reload" -> {
                try { Policy next = readPolicy(); policy = next; sender.sendMessage("NordGuard configuration reloaded; histories reset on next sample."); }
                catch (Exception error) { sender.sendMessage("Reload rejected; previous policy kept: " + error.getMessage()); }
            }
            case "alerts" -> {
                if (!(sender instanceof Player player) || !sender.hasPermission("nordguard.alerts")) {
                    sender.sendMessage("Player with nordguard.alerts required."); return true;
                }
                if (!subscribers.add(player.getUniqueId())) { subscribers.remove(player.getUniqueId()); sender.sendMessage("Alerts OFF"); }
                else sender.sendMessage("Alerts ON");
            }
            default -> { return false; }
        }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("nordguard.admin") || args.length != 1) return List.of();
        return List.of("status", "reload", "alerts").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }

    private static double attribute(Player player, Attribute attribute, double fallback) {
        var value = player.getAttribute(attribute);
        return value == null ? fallback : value.getValue();
    }

    private final class Session {
        final Player player;
        final MovementModel model = new MovementModel();
        final long[] lastAlert = new long[Check.values().length];
        ScheduledTask task;
        Policy seenPolicy;
        Location last, safe, setbackTarget;
        long originRevision;
        int grace, idle;
        int teleportSequence;
        boolean seenTeleportSequence;
        long lastNanos, fallEvents, fallBaseline, pendingBaseline, fallOverrides, overrideBaseline, pendingOverrides;
        double fallRawDamage, rawBaseline, pendingRawBaseline;
        double pendingFall;
        double impulseSpeed, impulseY;
        int pendingTicks;
        boolean airborne, teleporting, stopped;
        Session(Player player) { this.player = player; seenPolicy = policy; grace = policy.joinGrace(); }
        void suspend(int ticks) {
            // A temporary evidence reset must not discard the last supported return position.
            model.reset(); last = null; grace = Math.max(grace, ticks);
            pendingFall = 0; pendingTicks = 0; airborne = false;
            impulseSpeed = impulseY = 0;
        }
        void tick() {
            if (stopped || !player.isOnline()) return;
            long start = System.nanoTime();
            try { sample(start); }
            catch (Exception error) {
                teleporting = false;
                setbackTarget = null;
                suspend(100);
                if (start - lastAlert[0] > 10_000_000_000L) {
                    lastAlert[0] = start;
                    getLogger().log(java.util.logging.Level.WARNING, "Deferred player check after internal error", error);
                }
            } finally { samples.increment(); sampleNanos.add(System.nanoTime() - start); }
        }
        void sample(long now) throws Exception {
            int sequence = nativeTeleport.sequence(player);
            if (seenTeleportSequence && sequence != teleportSequence && !teleporting) grace(player);
            teleportSequence = sequence; seenTeleportSequence = true;
            Policy current = policy;
            if (seenPolicy != current) { seenPolicy = current; suspend(current.joinGrace()); }
            long gap = now - lastNanos; lastNanos = now;
            impulseSpeed *= .91;
            impulseY = Math.max(0, (impulseY - .08) * .98);
            if (gap > current.maxGapNanos() && gap > 0) suspend(current.transitionGrace());
            if (teleporting || grace-- > 0) { skipped.increment(); return; }
            grace = 0;
            if (player.isDead() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                    || player.hasPermission("nordguard.bypass") || player.getAllowFlight() || player.isFlying()
                    || player.isInsideVehicle() || player.isGliding() || player.isRiptiding()
                    || player.isInWater() || player.isClimbing() || !player.hasGravity()
                    || player.hasPotionEffect(PotionEffectType.LEVITATION)
                    || player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
                suspend(3); skipped.increment(); return;
            }
            Location at = player.getLocation();
            if (!Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ())) { suspend(20); return; }
            if (last != null && at.getWorld() != last.getWorld()) { suspend(current.transitionGrace()); return; }
            boolean stationary = last != null && at.distanceSquared(last) < 1.0E-10;
            if (stationary && !airborne && pendingFall == 0 && ++idle % 5 != 0) return;
            var environment = EnvironmentProbe.inspect(player, at);
            if (!environment.known() || environment.special()) { suspend(3); skipped.increment(); return; }
            if (pendingFall > 0 && --pendingTicks <= 0) {
                double distance = pendingFall; pendingFall = 0;
                if (fallOverrides == pendingOverrides
                        && Boolean.TRUE.equals(at.getWorld().getGameRuleValue(GameRule.FALL_DAMAGE))
                        && current.modes().get(Check.NOFALL) != Policy.Mode.OFF) {
                    int expected = nativeFall.expected(player, distance);
                    double paid = Math.max(0, fallRawDamage - pendingRawBaseline);
                    int owed = FallAccounting.owed(expected, paid, false);
                    if (owed > 0) {
                        report(this, Check.NOFALL, "observed landing: distance=" + String.format(Locale.ROOT, "%.2f", distance)
                                + ", expected base=" + expected + ", observed base=" + paid);
                        if (current.modes().get(Check.NOFALL) == Policy.Mode.CORRECT && !player.isInvulnerable()) {
                            nativeFall.applyRemaining(player, distance, owed); corrections.increment();
                        }
                    }
                }
            }
            double speed = Math.max(.1, attribute(player, Attribute.MOVEMENT_SPEED, .1)) * 3.9;
            speed *= Math.max(1, player.getWalkSpeed() / .2);
            speed += impulseSpeed;
            double jump = attribute(player, Attribute.JUMP_STRENGTH, .42);
            var jumpEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
            if (jumpEffect != null) jump += .1 * (jumpEffect.getAmplifier() + 1);
            jump = Math.max(jump, impulseY);
            var result = model.accept(new MovementModel.Frame(at.getX(), at.getY(), at.getZ(), environment.ground(),
                    environment.wall(), false, speed, jump, attribute(player, Attribute.STEP_HEIGHT, .6),
                    attribute(player, Attribute.GRAVITY, .08)), current);
            if (!environment.ground() && !airborne) {
                fallBaseline = fallEvents; rawBaseline = fallRawDamage; overrideBaseline = fallOverrides;
            }
            airborne = !environment.ground();
            if (result.landingDistance() > attribute(player, Attribute.SAFE_FALL_DISTANCE, 3) + 1
                    && attribute(player, Attribute.FALL_DAMAGE_MULTIPLIER, 1) > 0 && !player.isInvulnerable()
                    && Boolean.TRUE.equals(at.getWorld().getGameRuleValue(GameRule.FALL_DAMAGE))
                    && current.modes().get(Check.NOFALL) != Policy.Mode.OFF) {
                pendingFall = result.landingDistance(); pendingTicks = 2; pendingBaseline = fallBaseline;
                pendingRawBaseline = rawBaseline; pendingOverrides = overrideBaseline;
            }
            boolean correct = false;
            for (Check check : result.flags()) {
                report(this, check, "movement outside conservative envelope");
                correct |= current.modes().get(check) == Policy.Mode.CORRECT;
            }
            if (correct && safe != null && !teleporting) {
                var targetEnvironment = EnvironmentProbe.inspect(player, safe);
                if (targetEnvironment.known() && targetEnvironment.ground() && targetEnvironment.clear() && !targetEnvironment.special()) {
                    Location target = safe.clone(); target.setYaw(at.getYaw()); target.setPitch(at.getPitch());
                    long revision = originRevision;
                    int expectedSequence = NativeTeleport.next(nativeTeleport.sequence(player));
                    setbackTarget = target;
                    teleporting = true;
                    player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).whenComplete((success, error) ->
                            player.getScheduler().run(NordGuard.this, ignored -> {
                                if (originRevision != revision) return;
                                teleportSequence = nativeTeleport.sequence(player);
                                seenTeleportSequence = true;
                                if (error == null && Boolean.TRUE.equals(success) && teleportSequence != expectedSequence) {
                                    grace(player);
                                    return;
                                }
                                teleporting = false; setbackTarget = null;
                                suspend(2);
                                if (error == null && Boolean.TRUE.equals(success)) {
                                    safe = target.clone();
                                    corrections.increment();
                                }
                            }, () -> {}));
                    return;
                }
            }
            if (environment.ground() && environment.clear() && result.clean()) safe = at.clone();
            last = at;
        }
    }
}
