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
    private NativePackets packets;
    private volatile boolean packetsEnabled;
    private volatile boolean replicaEnabled;
    private volatile boolean shuttingDown;
    private final LongAdder packetEvents = new LongAdder(), packetDrainNanos = new LongAdder();
    private final SpatialBudget worldCopyBudget=new SpatialBudget(), worldDecodeBudget=new SpatialBudget(), worldMaterializeBudget=new SpatialBudget();
    private NativeBlocks blockGeometry;
    private ActionGuard actions;
    private final SpatialBudget spatialBudget=new SpatialBudget();
    private final SpatialBudget alertBudget=new SpatialBudget();
    private final LongAdder spatialDeferred=new LongAdder(), spatialCells=new LongAdder();
    private final LongAdder actionCount=new LongAdder(), actionNanos=new LongAdder();
    private final java.util.concurrent.atomic.AtomicLong slowestAction=new java.util.concurrent.atomic.AtomicLong();
    void actionTiming(long nanos) { actionCount.increment();actionNanos.add(nanos);slowestAction.accumulateAndGet(nanos,Math::max); }
    Policy policy() { return policy; }
    int nativeSequence(Player player) { return nativeTeleport.sequence(player); }
    boolean actionViolation(Player player,Check check,String detail) {
        Session session=sessions.get(player.getUniqueId());
        if(session==null || policy.modes().get(check)==Policy.Mode.OFF) return false;
        report(session,check,detail);return policy.modes().get(check)==Policy.Mode.CORRECT;
    }
    void actionCancelled() { corrections.increment(); }
    SolidProbe.Scan scan(org.bukkit.World world,Geometry.Box bounds,ActionLimits limits) {
        var result=SolidProbe.scan(world,bounds,limits.maxBlocks(),count->{
            if(!spatialBudget.acquire(System.nanoTime(),count,limits.spatialBlocksPerSecond())) return false;
            spatialCells.add(count);return true;
        });
        if(!result.known()) spatialDeferred.increment();
        return result;
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            policy = readPolicy();
            if (!Bukkit.getMinecraftVersion().equals("26.2"))
                throw new IllegalStateException("This test build supports Minecraft 26.2 only");
            nativeFall = new NativeFall();
            nativeTeleport = new NativeTeleport();
            packetsEnabled = readPacketsEnabled();
            replicaEnabled = readReplicaEnabled();
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "NordGuard cannot start safely", error);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try { packets=NativePackets.bind(); }
        catch (ReflectiveOperationException | LinkageError error) {
            getLogger().log(java.util.logging.Level.WARNING, "Packet diagnostics unavailable; existing checks remain active", error);
        }
        if(replicaEnabled) try { blockGeometry=NativeBlocks.bind(); }
        catch(ReflectiveOperationException error) {replicaEnabled=false;getLogger().log(java.util.logging.Level.WARNING,"Replica diagnostics unavailable; existing checks remain active",error);}
        Bukkit.getPluginManager().registerEvents(this, this);
        actions=new ActionGuard(this);
        Bukkit.getPluginManager().registerEvents(actions,this);
        for (Player player : Bukkit.getOnlinePlayers()) player.getScheduler().run(this, task -> attach(player), null);
        getLogger().info("NordGuard " + getDescription().getVersion() + " enabled; modes=" + policy.modes() + "; no bans or external services");
    }
    @Override public void onDisable() {
        shuttingDown = true;
        sessions.values().forEach(Session::stop);
        sessions.clear(); subscribers.clear();
        if(actions!=null) actions.clear();
    }
    private Policy readPolicy() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(new java.io.File(getDataFolder(), "config.yml"));
        return Policy.read(yaml);
    }
    private boolean readPacketsEnabled() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(new java.io.File(getDataFolder(), "config.yml"));
        Object enabled = yaml.get("packets.enabled", Boolean.TRUE);
        if (!(enabled instanceof Boolean value)) throw new IllegalArgumentException("packets.enabled must be boolean");
        return value;
    }
    private void attach(Player player) {
        if (shuttingDown) return;
        var session = new Session(player);
        Session previous = sessions.putIfAbsent(player.getUniqueId(), session);
        if (previous != null) return;
        if (shuttingDown) { session.stop(); sessions.remove(player.getUniqueId(),session); return; }
        // Install before the first scheduled sample so initial chunk packets can be observed.
        session.observePackets(System.nanoTime());
        session.task = player.getScheduler().runAtFixedRate(this, ignored -> session.tick(),
                () -> {session.stop();if(sessions.remove(player.getUniqueId(),session)&&actions!=null) actions.remove(player);}, 1, 1);
        if (session.stopped || shuttingDown) session.stop();
        if (session.task == null) { session.stop(); sessions.remove(player.getUniqueId(), session); }
    }
    private boolean readReplicaEnabled() throws Exception {
        var yaml=new YamlConfiguration();yaml.load(new java.io.File(getDataFolder(),"config.yml"));
        Object enabled=yaml.get("packets.world-replica",Boolean.FALSE);
        if(!(enabled instanceof Boolean value))throw new IllegalArgumentException("packets.world-replica must be boolean");
        return value;
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) session.stop();
        subscribers.remove(event.getPlayer().getUniqueId());
        if(actions!=null) actions.remove(event.getPlayer());
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
    public void inside(io.papermc.paper.event.entity.EntityInsideBlockEvent event) {
        // Respect plugins that deliberately disable cobweb slowdown.
        if (event.isCancelled() && event.getBlock().getType() == org.bukkit.Material.COBWEB
                && event.getEntity() instanceof Player player) {
            Session session = sessions.get(player.getUniqueId());
            if (session != null) session.suspend(3);
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
        if(actions!=null) actions.invalidate(player);
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
        if(!policy.console() && subscribers.isEmpty()) return;
        long now = System.nanoTime();
        if (now - session.lastAlert[check.ordinal()] < policy.alertNanos()) return;
        session.lastAlert[check.ordinal()] = now;
        // A wave of violations must not turn into an unbounded logging/broadcast workload.
        if(!alertBudget.acquire(now,1,20)) return;
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
        if (args.length == 2 && args[0].equalsIgnoreCase("inspect")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("Player not online."); return true; }
            target.getScheduler().run(this, task -> {
                Session session = sessions.get(target.getUniqueId());
                String[] detail = session == null || session.timeline == null
                        ? new String[]{"Packet observer is not attached."} : session.timeline.diagnostic();
                if(session!=null && session.timeline!=null) {
                    detail=Arrays.copyOf(detail,detail.length+1);
                    ClientWorld world=session.clientWorld;
                    detail[detail.length-1]=world==null?"Replica disabled (diagnostic only)."
                            :"Replica chunks="+world.size()+", bytes="+world.bytes()+", decoded="+world.decoded()
                            +", invalidated="+world.invalidated()+", evicted="+world.evicted()+" (diagnostic only).";
                }
                final String[] response=detail;
                if (sender instanceof Player receiver) receiver.getScheduler().run(this, ignored -> {
                    if (receiver.hasPermission("nordguard.admin")) receiver.sendMessage(response);
                }, null);
                else sender.sendMessage(response);
            }, null);
            return true;
        }
        if (args.length != 1) return false;
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> {
                long count = samples.sum();
                sender.sendMessage("NordGuard " + getDescription().getVersion() + " | sessions=" + sessions.size() + " | modes=" + policy.modes());
                sender.sendMessage("Samples=" + count + ", deferred=" + skipped.sum() + ", corrections=" + corrections.sum()
                        + ", mean sample us=" + (count == 0 ? 0 : sampleNanos.sum() / count / 1000));
                sender.sendMessage("Spatial cell budget used="+spatialCells.sum()+", spatial scans deferred="+spatialDeferred.sum());
                long packetCount = packetEvents.sum();
                sender.sendMessage("Packet diagnostics="+packetsEnabled+", native bridge="+(packets!=null)+", drained="+packetCount
                        +", mean drain ns/event="+(packetCount==0?0:packetDrainNanos.sum()/packetCount));
                long events=actionCount.sum();
                sender.sendMessage("Action events="+events+", mean action us="+(events==0?0:actionNanos.sum()/events/1000)
                        +", slowest action us="+slowestAction.get()/1000);
                for (Check check : Check.values()) sender.sendMessage(check + ": " + violations[check.ordinal()].sum());
            }
            case "reload" -> {
                try {
                    Policy next=readPolicy();boolean enabled=readPacketsEnabled(),replica=readReplicaEnabled();
                    if(replica && blockGeometry==null)blockGeometry=NativeBlocks.bind();
                    policy=next;replicaEnabled=replica;packetsEnabled=enabled;
                    sender.sendMessage("NordGuard configuration reloaded; histories reset on next sample.");
                }
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
        return List.of("status", "reload", "alerts", "inspect").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }

    private static double attribute(Player player, Attribute attribute, double fallback) {
        var value = player.getAttribute(attribute);
        return value == null ? fallback : value.getValue();
    }

    private final class Session {
        final Player player;
        final MovementModel model = new MovementModel();
        final long[] lastAlert = new long[Check.values().length];
        volatile ScheduledTask task;
        volatile NativePackets.Handle packetHandle;
        PacketInbox inbox;
        PacketTimeline timeline;
        ClientWorld clientWorld;
        boolean packetAttachFailed;
        boolean seenReplica;
        final java.util.function.IntPredicate materializeBudget=cells->worldMaterializeBudget.acquire(System.nanoTime(),cells,1_000_000);
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
        boolean airborne, teleporting;
        volatile boolean stopped;
        Session(Player player) { this.player = player; seenPolicy = policy; grace = policy.joinGrace(); }
        void stop() { stopped = true; if (task != null) task.cancel(); closePackets(); }
        void closePackets() {
            NativePackets.Handle handle = packetHandle;
            if (handle != null) handle.close();
        }
        void packetTick(long now) {
            if (!packetsEnabled || packets == null) {
                closePackets(); packetHandle = null; inbox = null; timeline = null; clientWorld=null;return;
            }
            if (packetAttachFailed) return;
            if(packetHandle!=null && seenReplica!=replicaEnabled) {
                closePackets();packetHandle=null;inbox=null;timeline=null;clientWorld=null;
                // Removal and installation use the same channel event loop, in order.
            }
            if (packetHandle == null) {
                seenReplica=replicaEnabled;
                clientWorld=seenReplica?new ClientWorld():null;
                inbox = seenReplica?new PacketInbox(bytes -> worldCopyBudget.acquire(System.nanoTime(),bytes,8*1024*1024)):new PacketInbox();
                timeline = new PacketTimeline(update -> {
                    if(clientWorld==null)return;
                    int min=player.getWorld().getMinHeight()>>4;
                    int sections=(player.getWorld().getMaxHeight()-player.getWorld().getMinHeight())>>4;
                    if(update instanceof WorldSnapshot.EncodedChunk encoded
                            && !worldDecodeBudget.acquire(System.nanoTime(),encoded.estimatedBytes(),4*1024*1024)) {
                        clientWorld.apply(new WorldSnapshot.Forget(encoded.x(),encoded.z()),sections,min);
                        timeline.invalidate("replica decode budget");return;
                    }
                    try {clientWorld.apply(update,sections,min,materializeBudget);}
                    catch(IllegalArgumentException error) {clientWorld.clear();timeline.invalidate("unsupported chunk data");}
                });
                try { packetHandle = packets.attach(player, inbox); }
                catch (ReflectiveOperationException error) {
                    packetAttachFailed = true;
                    getLogger().log(java.util.logging.Level.WARNING, "Packet observer could not attach; existing checks continue", error);
                    return;
                }
                if (stopped || shuttingDown) { closePackets(); return; }
            }
            long started = System.nanoTime();
            long droppedBefore=inbox.dropped();
            packetEvents.add(timeline.drain(inbox, now));
            if(inbox.dropped()!=droppedBefore && clientWorld!=null)clientWorld.clear();
            timeline.transportActive(packetHandle.active());
            if (timeline.shouldProbe(now)) {
                timeline.probeQueued(now);
                packets.probe(packetHandle, java.util.concurrent.ThreadLocalRandom.current().nextInt());
            }
            packetDrainNanos.add(System.nanoTime() - started);
        }
        void observePackets(long now) {
            try { packetTick(now); }
            catch(RuntimeException | LinkageError error) {
                packetAttachFailed=true;closePackets();
                getLogger().log(java.util.logging.Level.WARNING,"Packet diagnostics disabled for session; existing checks continue",error);
            }
        }
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
            observePackets(now);
            int sequence = nativeTeleport.sequence(player);
            if (seenTeleportSequence && sequence != teleportSequence && !teleporting) grace(player);
            teleportSequence = sequence; seenTeleportSequence = true;
            actions.tick(player,sequence,now);
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
                    || !player.hasGravity()
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
            // Use feet geometry, not a claimed swimming pose, to classify a liquid surface.
            boolean surface = environment.liquidSurface();
            if (!environment.known() || environment.special()
                    || (environment.liquid() || player.isInWater()) && !surface
                    || environment.web() && player.hasPotionEffect(PotionEffectType.WEAVING)) {
                suspend(3); skipped.increment(); return;
            }
            boolean climbing = player.isClimbing();
            if (surface || climbing || environment.web()) { pendingFall = 0; airborne = false; }
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
            double speed = Math.max(.1, attribute(player, Attribute.MOVEMENT_SPEED, .1)) * 2.2;
            speed *= Math.max(1, player.getWalkSpeed() / .2);
            speed += impulseSpeed;
            double jump = attribute(player, Attribute.JUMP_STRENGTH, .42);
            var jumpEffect = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
            if (jumpEffect != null) jump += .1 * (jumpEffect.getAmplifier() + 1);
            jump = Math.max(jump, impulseY);
            double useMultiplier = 1;
            if (player.hasActiveItem() && player.getActiveItemUsedTime() >= 10 && impulseSpeed < .03) {
                var effects = player.getActiveItem().getData(io.papermc.paper.datacomponent.DataComponentTypes.USE_EFFECTS);
                useMultiplier = effects == null ? .2 : effects.speedMultiplier();
            }
            var result = model.accept(new MovementModel.Frame(at.getX(), at.getY(), at.getZ(), environment.ground(),
                    environment.wall(), false, speed, jump, attribute(player, Attribute.STEP_HEIGHT, .6),
                    attribute(player, Attribute.GRAVITY, .08), surface, climbing, environment.web(), useMultiplier), current);
            boolean phased=false;
            if(current.modes().get(Check.NOCLIP)!=Policy.Mode.OFF && last!=null && environment.clear() && player.getBoundingBox().getHeight()>=1.5
                    && Math.abs(at.getY()-last.getY())<.05 && at.distanceSquared(last)>.64 && at.distanceSquared(last)<16) {
                var previous=EnvironmentProbe.inspect(player,last);
                if(previous.known() && previous.clear() && !previous.special()) {
                    var a=new Geometry.Point(last.getX(),last.getY()+.9,last.getZ());
                    var b=new Geometry.Point(at.getX(),at.getY()+.9,at.getZ());
                    var bounds=new Geometry.Box(Math.min(a.x(),b.x()),Math.min(a.y(),b.y()),Math.min(a.z(),b.z()),
                            Math.max(a.x(),b.x()),Math.max(a.y(),b.y()),Math.max(a.z(),b.z()));
                    var scan=scan(at.getWorld(),bounds,current.actions());
                    phased=scan.known() && scan.blocked(a,b);
                    if(phased) result.flags().add(Check.NOCLIP);
                }
            }
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
            if (environment.ground() && environment.clear() && result.clean() && !phased) safe = at.clone();
            last = at;
        }
    }
}
