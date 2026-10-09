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
    private org.bukkit.entity.Cow clientTarget;
    private org.bukkit.block.Block clientBlock;
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
                if(action.equals("replicaedit") || action.equals("replicarestore")) {
                    ReplicaNativeProbe.edit((JavaPlugin)Bukkit.getPluginManager().getPlugin("NordGuard"),player,action.equals("replicarestore"));
                    getLogger().info("GUARD_REPLICA_EDIT_SENT "+action);
                } else if(action.equals("replicaeditcheck")) {
                    ReplicaNativeProbe.verifyEdit((JavaPlugin)Bukkit.getPluginManager().getPlugin("NordGuard"),player,args[2]);
                    getLogger().info("GUARD_REPLICA_EDIT_PASS "+args[2]);
                } else if(action.equals("replicanative")) {
                    ReplicaNativeProbe.run((JavaPlugin)Bukkit.getPluginManager().getPlugin("NordGuard"),player,name->getLogger().info("GUARD_ACTION_PASS "+name));
                    getLogger().info("GUARD_REPLICA_NATIVE_DONE");
                } else if(action.equals("replicaresend")) {
                    ReplicaNativeProbe.resend((JavaPlugin)Bukkit.getPluginManager().getPlugin("NordGuard"),player);
                    getLogger().info("GUARD_REPLICA_RESENT");
                } else if(action.equals("replicacaptured")) {
                    ReplicaNativeProbe.verifyCaptured((JavaPlugin)Bukkit.getPluginManager().getPlugin("NordGuard"),player,name->getLogger().info("GUARD_ACTION_PASS "+name));
                    getLogger().info("GUARD_REPLICA_CAPTURED");
                } else if(action.equals("collisionnative")) {
                    CollisionNativeProbe.run((JavaPlugin)Bukkit.getPluginManager().getPlugin("NordGuard"),name->getLogger().info("GUARD_ACTION_PASS "+name));
                    getLogger().info("GUARD_COLLISION_NATIVE_DONE");
                } else if (action.equals("disableguard")) {
                    Object nativePlayer=player.getClass().getMethod("getHandle").invoke(player);
                    Object listener=nativePlayer.getClass().getField("connection").get(nativePlayer);
                    Object network=listener.getClass().getField("connection").get(listener);
                    var transport=(io.netty.channel.Channel)network.getClass().getField("channel").get(network);
                    var guard=Bukkit.getPluginManager().getPlugin("NordGuard");
                    Bukkit.getGlobalRegionScheduler().run(this,ignored->{
                        Bukkit.getPluginManager().disablePlugin(guard);
                        transport.eventLoop().schedule(()->{
                            if(transport.pipeline().get("nordguard_observer")!=null) getLogger().severe("GUARD_PROBE_FAIL observer remained after disable");
                            else getLogger().info("GUARD_PACKET_CLEANUP_PASS");
                        },500,java.util.concurrent.TimeUnit.MILLISECONDS);
                    });
                } else if (action.equals("networkvelocity")) {
                    player.setVelocity(new org.bukkit.util.Vector(.1,.2,0));
                    getLogger().info("GUARD_PACKET_IMPULSE_SENT");
                } else if (action.equals("stall")) {
                    // Isolated fixture only: exercise a long owner sampling gap without touching production.
                    java.util.concurrent.locks.LockSupport.parkNanos(350_000_000L);
                    getLogger().info("GUARD_STALL_DONE");
                } else if (action.equals("actions")) {
                    actionSuite(player);
                } else if(action.equals("phase")) {
                    phaseSuite(player);
                } else if(action.equals("perf")) {
                    benchmark(player);
                } else if(action.equals("clientfixture")) {
                    select(player,Check.FASTBREAK,Policy.Mode.CORRECT,Check.REACH,Check.BLOCKREACH,Check.WALLHIT);
                    var at=player.getLocation();
                    clientTarget=at.getWorld().spawn(at.clone().add(2,0,1),org.bukkit.entity.Cow.class,e->{e.setAI(false);e.setGravity(false);});
                    clientBlock=at.getWorld().getBlockAt(at.getBlockX()+1,at.getBlockY(),at.getBlockZ());clientBlock.setType(Material.STONE,false);
                    getLogger().info("GUARD_CLIENT_FIXTURE "+clientTarget.getEntityId()+" "+clientBlock.getX()+" "+clientBlock.getY()+" "+clientBlock.getZ());
                } else if(action.equals("clientresult")) {
                    actionPass("real_client_attack_delivered",clientTarget!=null&&clientTarget.getHealth()<20);
                    actionPass("real_client_ordinary_mining_allowed",clientBlock.getType().isAir());
                    clientTarget.remove();clientTarget=null;
                    getLogger().info("GUARD_CLIENT_DONE");
                } else if (action.equals("prepare")) {
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
    private void select(Player player,Check selected,Policy.Mode mode,Check... additional) throws Exception {
        var guard=(NordGuard)Bukkit.getPluginManager().getPlugin("NordGuard");
        Field f=NordGuard.class.getDeclaredField("policy");f.setAccessible(true);Policy old=(Policy)f.get(guard);
        var modes=new EnumMap<Check,Policy.Mode>(Check.class);
        for(Check c:Check.values()) modes.put(c,Policy.Mode.OFF);
        modes.put(selected,mode);
        for(Check check:additional) modes.put(check,mode);
        f.set(guard,new Policy(modes,old.buffer(),old.horizontalMargin(),old.verticalMargin(),old.burstTicks(),0,0,
                old.maxGapNanos(),old.alertNanos(),false,old.actions()));
        f=NordGuard.class.getDeclaredField("actions");f.setAccessible(true);var actions=f.get(guard);
        int sequence=(int)invoke(guard,"nativeSequence",new Class<?>[]{Player.class},player);
        invoke(actions,"tick",new Class<?>[]{Player.class,int.class,long.class},player,sequence,System.nanoTime());
    }
    private Object invoke(Object owner,String name,Class<?>[] types,Object... args) throws Exception {
        Class<?> type=owner instanceof Class<?> c?c:owner.getClass();
        var method=type.getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(owner instanceof Class<?>?null:owner,args);
    }
    private Class<?> guardType(String name) throws Exception {
        return Class.forName("dev.nordfjell.guard."+name,true,Bukkit.getPluginManager().getPlugin("NordGuard").getClass().getClassLoader());
    }
    private Object geometry(String name,double... values) throws Exception {
        var types=new Class<?>[values.length];java.util.Arrays.fill(types,double.class);
        var constructor=guardType("Geometry$"+name).getDeclaredConstructor(types);constructor.setAccessible(true);
        Object[] args=new Object[values.length];for(int i=0;i<values.length;i++) args[i]=values[i];return constructor.newInstance(args);
    }
    private Object scan(org.bukkit.World world,Object bounds) throws Exception {
        return invoke(guardType("SolidProbe"),"scan",new Class<?>[]{org.bukkit.World.class,guardType("Geometry$Box"),int.class},world,bounds,128);
    }
    private boolean known(Object scan) throws Exception { return (boolean)invoke(scan,"known",new Class<?>[]{}); }
    private void actionPass(String name,boolean condition) {
        if(!condition) throw new AssertionError(name);
        getLogger().info("GUARD_ACTION_PASS "+name);
    }
    private static io.papermc.paper.event.player.PrePlayerAttackEntityEvent attackEvent(Player p,org.bukkit.entity.Entity e) {
        var event=new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(p,e,true);Bukkit.getPluginManager().callEvent(event);return event;
    }
    private static org.bukkit.event.block.BlockBreakEvent breakEvent(Player p,org.bukkit.block.Block b) {
        var event=new org.bukkit.event.block.BlockBreakEvent(b,p);Bukkit.getPluginManager().callEvent(event);return event;
    }
    private static void startEvent(Player p,org.bukkit.block.Block b,boolean instant) {
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.block.BlockDamageEvent(p,b,org.bukkit.block.BlockFace.UP,p.getInventory().getItemInMainHand(),instant));
    }
    private void actionSuite(Player p) throws Exception {
        p.clearActiveItem();p.getInventory().clear();
        var at=p.getLocation();var world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ(),y=at.getBlockY();
        var near=world.getBlockAt(x+2,y,z);var far=world.getBlockAt(x+10,y,z);
        for(int dx=0;dx<=12;dx++) for(int dz=-2;dz<=2;dz++) for(int dy=0;dy<4;dy++)
            world.getBlockAt(x+dx,y+dy,z+dz).setType(Material.AIR,false);
        near.setType(Material.STONE,false);far.setType(Material.STONE,false);
        select(p,Check.BLOCKREACH,Policy.Mode.CORRECT);
        actionPass("legal_block_distance",!breakEvent(p,near).isCancelled());
        actionPass("far_block_cancelled",breakEvent(p,far).isCancelled());
        select(p,Check.FASTBREAK,Policy.Mode.CORRECT);startEvent(p,near,false);
        actionPass("premature_break_cancelled",breakEvent(p,near).isCancelled());
        startEvent(p,near,true);actionPass("plugin_instant_break_respected",!breakEvent(p,near).isCancelled());
        startEvent(p,near,false);startEvent(p,near,true);
        actionPass("repeated_start_instant_override_respected",!breakEvent(p,near).isCancelled());
        actionPass("unknown_mining_start_deferred",!breakEvent(p,near).isCancelled());
        select(p,Check.FASTBREAK,Policy.Mode.OBSERVE);startEvent(p,near,false);
        actionPass("observe_does_not_cancel",!breakEvent(p,near).isCancelled());
        select(p,Check.BREAKRATE,Policy.Mode.CORRECT);
        int denied=0;
        for(int i=0;i<100;i++) if(breakEvent(p,near).isCancelled()) denied++;
        actionPass("break_burst_exhausted",denied>=50);
        select(p,Check.PLACERATE,Policy.Mode.CORRECT);
        denied=0;
        for(int i=0;i<100;i++) {
            var event=new org.bukkit.event.block.BlockPlaceEvent(near,near.getState(),near,
                    new org.bukkit.inventory.ItemStack(Material.STONE),p,true,org.bukkit.inventory.EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()) denied++;
        }
        actionPass("place_burst_exhausted",denied>=50);
        near.setType(Material.AIR,false);
        var close=world.spawn(at.clone().add(2,0,0),org.bukkit.entity.ArmorStand.class,e->e.setGravity(false));
        var distant=world.spawn(at.clone().add(6,0,0),org.bukkit.entity.ArmorStand.class,e->e.setGravity(false));
        try {
            select(p,Check.REACH,Policy.Mode.CORRECT);
            actionPass("legal_attack_distance",!attackEvent(p,close).isCancelled());
            actionPass("far_attack_cancelled",attackEvent(p,distant).isCancelled());
            p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE).setBaseValue(8);
            actionPass("custom_reach_attribute_respected",!attackEvent(p,distant).isCancelled());
            p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE).setBaseValue(3);
            var item=new org.bukkit.inventory.ItemStack(Material.STICK);
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ATTACK_RANGE,
                    io.papermc.paper.datacomponent.item.AttackRange.attackRange().maxReach(8).build());
            p.getInventory().setItemInMainHand(item);
            actionPass("custom_weapon_range_respected",!attackEvent(p,distant).isCancelled());
            p.getInventory().clear();
            select(p,Check.ATTACKRATE,Policy.Mode.CORRECT);
            denied=0;for(int i=0;i<150;i++) if(attackEvent(p,close).isCancelled()) denied++;
            actionPass("attack_burst_exhausted",denied>=70);
            var attachment=p.addAttachment(this,"nordguard.bypass",true);
            actionPass("explicit_bypass_respected",!attackEvent(p,close).isCancelled());p.removeAttachment(attachment);
            select(p,Check.REACH,Policy.Mode.OFF);
            actionPass("disabled_reach_does_not_cancel",!attackEvent(p,distant).isCancelled());
            select(p,Check.REACH,Policy.Mode.CORRECT);
            var cancelled=new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(p,distant,true);cancelled.setCancelled(true);
            Bukkit.getPluginManager().callEvent(cancelled);actionPass("other_plugin_cancellation_kept",cancelled.isCancelled());
            for(int dz=-2;dz<=2;dz++) for(int dy=0;dy<4;dy++) world.getBlockAt(x+1,y+dy,z+dz).setType(Material.STONE,false);
            var eye=p.getEyeLocation();var target=geometry("Box",Math.min(x+2,eye.getX()),Math.min(y,eye.getY()),Math.min(z,eye.getZ()),
                    Math.max(x+3,eye.getX()),Math.max(y+2,eye.getY()),Math.max(z+1,eye.getZ()));
            var scan=scan(world,target);
            actionPass("solid_wall_geometry",known(scan)&&(boolean)invoke(scan,"obscured",new Class<?>[]{guardType("Geometry$Point"),guardType("Geometry$Box")},
                    geometry("Point",eye.getX(),eye.getY(),eye.getZ()),geometry("Box",x+2,y,z,x+3,y+2,z+1)));
            int chunks=world.getLoadedChunks().length;
            actionPass("oversized_scan_deferred",!known(scan(world,geometry("Box",x,y,z,x+100,y+100,z+100))));
            actionPass("scan_did_not_load_chunks",chunks==world.getLoadedChunks().length);
            int ux=(x>>4)+1000,uz=z>>4;
            if(world.isChunkLoaded(ux,uz)) throw new AssertionError("Expected unloaded test chunk");
            actionPass("unloaded_small_scan_deferred",!known(scan(world,geometry("Box",ux*16,y,z,ux*16+1,y+1,z+1))));
            actionPass("unloaded_scan_did_not_load_chunks",!world.isChunkLoaded(ux,uz));
            var door=world.getBlockAt(x+1,y+1,z);door.setType(Material.OAK_TRAPDOOR,false);
            actionPass("dynamic_shape_deferred",!known(scan(world,target)));door.setType(Material.STONE,false);
            select(p,Check.WALLHIT,Policy.Mode.CORRECT);
            actionPass("new_wall_settling_grace",!attackEvent(p,close).isCancelled());
            // Real delay on the owner's scheduler, never sleep or wait on the region thread.
            long wallDeadline=System.nanoTime()+300_000_000L;
            p.getScheduler().runAtFixedRate(this,task->{
                if(System.nanoTime()<wallDeadline) return;
                task.cancel();
                try {
                    actionPass("stable_wall_hit_cancelled",attackEvent(p,close).isCancelled());
                    getLogger().info("GUARD_ACTIONS_DONE");
                } catch(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"GUARD_PROBE_FAIL",error); }
                finally {close.remove();distant.remove();}
            },()->{},1,1);
        } catch(Throwable error) {close.remove();distant.remove();throw error;}
    }
    private void phaseSuite(Player p) throws Exception {
        select(p,Check.NOCLIP,Policy.Mode.CORRECT);
        Location origin=p.getLocation();int x=origin.getBlockX(),y=origin.getBlockY(),z=origin.getBlockZ();
        for(int dy=0;dy<3;dy++) origin.getWorld().getBlockAt(x+1,y+dy,z).setType(Material.STONE,false);
        p.teleportAsync(origin.clone().add(2,0,0)).thenRun(()->p.getScheduler().run(this,task->{
            try {
                var guard=(NordGuard)Bukkit.getPluginManager().getPlugin("NordGuard");
                Field map=NordGuard.class.getDeclaredField("sessions");map.setAccessible(true);
                Object session=((java.util.Map<?,?>)map.get(guard)).get(p.getUniqueId());
                // Test-only fault injection: give the sampled path a previous position across the wall.
                for(var entry:java.util.Map.<String,Object>of("last",origin,"safe",origin,"seenPolicy",invoke(guard,"policy",new Class<?>[]{}),
                        "grace",0,"teleportSequence",invoke(guard,"nativeSequence",new Class<?>[]{Player.class},p),"seenTeleportSequence",true,
                        "lastNanos",System.nanoTime()).entrySet()) {
                    Field f=session.getClass().getDeclaredField(entry.getKey());f.setAccessible(true);f.set(session,entry.getValue());
                }
                Field model=session.getClass().getDeclaredField("model");model.setAccessible(true);invoke(model.get(session),"reset",new Class<?>[]{});
                var tick=session.getClass().getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(session);
                p.getScheduler().runDelayed(this,ignored->{
                    try {
                        actionPass("noclip_fault_path_returned",p.getLocation().distanceSquared(origin)<.01);
                        Field flags=NordGuard.class.getDeclaredField("violations");flags.setAccessible(true);
                        var counts=(java.util.concurrent.atomic.LongAdder[])flags.get(guard);
                        actionPass("noclip_fault_path_reported",counts[Check.NOCLIP.ordinal()].sum()>0);
                        getLogger().info("GUARD_PHASE_DONE");
                    } catch(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"GUARD_PROBE_FAIL",e);}
                },null,8);
            } catch(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"GUARD_PROBE_FAIL",e);}
        },null));
    }
    private void benchmark(Player p) throws Exception {
        select(p,Check.REACH,Policy.Mode.OBSERVE);
        Location at=p.getLocation();
        var target=at.getWorld().spawn(at.clone().add(2,0,0),org.bukkit.entity.ArmorStand.class,e->e.setGravity(false));
        long[] probes=new long[800],events=new long[800];int[] tick={0};
        p.getScheduler().runAtFixedRate(this,task->{
            try {
                int n=tick[0]++;
                for(int i=0;i<4;i++) {
                    long start=System.nanoTime();
                    var env=EnvironmentProbe.inspect(p,at.clone().add((n%5)*.03,0,0));
                    long probe=System.nanoTime()-start;
                    if(!env.known() || !env.ground()) throw new AssertionError("Benchmark floor unknown");
                    start=System.nanoTime();attackEvent(p,target);long event=System.nanoTime()-start;
                    if(n>=100) {probes[(n-100)*4+i]=probe;events[(n-100)*4+i]=event;}
                }
                if(n==299) {
                    task.cancel();target.remove();java.util.Arrays.sort(probes);java.util.Arrays.sort(events);
                    getLogger().info("GUARD_PERF probe_ns median="+probes[400]+" p95="+probes[760]+" max="+probes[799]
                            +" event_ns median="+events[400]+" p95="+events[760]+" max="+events[799]+" samples=800");
                }
            } catch(Throwable e) {task.cancel();target.remove();getLogger().log(java.util.logging.Level.SEVERE,"GUARD_PROBE_FAIL",e);}
        },target::remove,1,1);
    }
}
