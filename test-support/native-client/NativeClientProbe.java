package dev.nordfjell.guard.fixture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.client.Minecraft;

/** Isolated test fixture, never a server dependency. Drives native input, not movement packets. */
public final class NativeClientProbe implements ClientModInitializer {
    private final Gson json = new Gson();
    private final Path root = Path.of(System.getProperty("nordguard.fixture"));
    private String mode = "idle", module = "", error = "";
    private long id = -1, ticks;
    private final Map<String,Object> hacks = new LinkedHashMap<>();
    private final JsonObject settings = new JsonObject();
    private boolean patrol, returning, face = true;
    private int targetId = -1;
    private long attackCalls, lastAttackTick;
    private net.minecraft.core.BlockPos[] miningTargets=new net.minecraft.core.BlockPos[0];
    private int miningIndex;
    private long miningCalls;
    private java.lang.reflect.Field mouseState;
    private Boolean previousMouseState;
    private double originX, travel, lastX, lastZ, minY;
    @Override public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
        AttackEntityCallback.EVENT.register((player,world,hand,target,hit)->{
            var mc=Minecraft.getInstance();
            if(player==mc.player && combat() && target.getId()==targetId && mc.getCurrentServer()!=null
                    && "127.0.0.1:25660".equals(mc.getCurrentServer().ip))attackCalls++;
            return net.minecraft.world.InteractionResult.PASS;
        });
        AttackBlockCallback.EVENT.register((player,world,hand,pos,direction)->{
            var mc=Minecraft.getInstance();
            if(player==mc.player&&mode.startsWith("mine")&&mc.getCurrentServer()!=null
                    &&"127.0.0.1:25660".equals(mc.getCurrentServer().ip))miningCalls++;
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
    private void tick(Minecraft mc) {
        try {
            ticks++;
            mc.options.pauseOnLostFocus = false;
            boolean local = mc.getCurrentServer() != null && "127.0.0.1:25660".equals(mc.getCurrentServer().ip);
            if (mc.player != null && !local) throw new IllegalStateException("Fixture refuses non-loopback server");
            Path control = root.resolve("control.json");
            if (Files.isRegularFile(control) && Files.size(control) <= 4096) {
                var next = json.fromJson(Files.readString(control), JsonObject.class);
                if (next.get("id").getAsLong() != id && (local && mc.player != null || next.get("mode").getAsString().equals("stop"))) {
                    disableHack();
                    if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();
                    mc.options.keyAttack.setDown(false);
                    mode = next.get("mode").getAsString(); id = next.get("id").getAsLong();
                    patrol = next.has("patrol") && next.get("patrol").getAsBoolean();
                    targetId = next.has("targetId") ? next.get("targetId").getAsInt() : -1;
                    face = !next.has("face") || next.get("face").getAsBoolean();
                    attackCalls = 0; lastAttackTick = ticks;
                    miningIndex=0;miningCalls=0;miningTargets=new net.minecraft.core.BlockPos[0];
                    if(next.has("targets")) {
                        var targets=next.getAsJsonArray("targets");
                        if(targets.size()>32)throw new IllegalArgumentException("Too many mining targets");
                        miningTargets=new net.minecraft.core.BlockPos[targets.size()];
                        for(int i=0;i<targets.size();i++) {var target=targets.get(i).getAsJsonObject();
                            miningTargets[i]=new net.minecraft.core.BlockPos(target.get("x").getAsInt(),target.get("y").getAsInt(),target.get("z").getAsInt());}
                    }
                    returning = false; originX = mc.player == null ? 0 : mc.player.getX();
                    travel = 0; lastX = originX; lastZ = mc.player == null ? 0 : mc.player.getZ();
                    minY = mc.player == null ? 0 : mc.player.getY();
                    if (!Set.of("idle","walk","sprint","jump","sneak","flight","speed","spider","water","snow","stop",
                            "attack","attack-away","critical","killaura","aura-critical","crit-jump","boat","boatfly","mine","mine-fast").contains(mode))
                        throw new IllegalArgumentException("Unknown fixture mode");
                    if (combat() && targetId < 0) throw new IllegalArgumentException("Combat requires targetId");
                    if(mode.equals("boatfly")) {
                        Object hack=getHack("boatFlyHack");slider(hack,"Upward Speed",.3);enableHack("boatFlyHack");
                    }
                    if(mode.startsWith("mine")&&miningTargets.length==0)throw new IllegalArgumentException("Mining targets required");
                    if(mode.equals("mine-fast")) {
                        Object fast=getHack("fastBreakHack");slider(fast,"Activation chance",1);
                        Object legit=setting(fast,"Legit mode");legit.getClass().getMethod("setChecked",boolean.class).invoke(legit,false);
                        boolean checked=(boolean)legit.getClass().getMethod("isChecked").invoke(legit);
                        if(checked)throw new IllegalStateException("FastBreak Legit mode remained enabled");
                        settings.addProperty("Legit mode",checked);enableHack("fastBreakHack");
                    }
                    if (Set.of("flight","speed","spider","water","snow").contains(mode)) {
                        String name = switch(mode) { case "flight" -> "flightHack"; case "speed" -> "speedHackHack";
                            case "spider" -> "spiderHack"; case "snow" -> "snowShoeHack"; default -> "jesusHack"; };
                        enableHack(name);
                    }
                    if (mode.equals("critical") || mode.equals("aura-critical")) {
                        Object criticals = getHack("criticalsHack");
                        Object selection = setting(criticals,"Mode");
                        if (!(boolean)selection.getClass().getMethod("setSelected",String.class).invoke(selection,"Packet"))
                            throw new IllegalStateException("Criticals Packet setting rejected");
                        String selected = ((Enum<?>)selection.getClass().getMethod("getSelected").invoke(selection)).name();
                        if (!selected.equals("PACKET")) throw new IllegalStateException("Criticals is not Packet");
                        settings.addProperty("criticalMode",selected);
                        enableHack("criticalsHack");
                    }
                    if (mode.equals("killaura") || mode.equals("aura-critical")) {
                        Object aura = getHack("killauraHack");
                        slider(aura,"Range",3); slider(aura,"Speed",1); slider(aura,"Speed randomization",0);
                        Object los = setting(aura,"Check line of sight");
                        los.getClass().getMethod("setChecked",boolean.class).invoke(los,false);
                        boolean checked = (boolean)los.getClass().getMethod("isChecked").invoke(los);
                        if (checked) throw new IllegalStateException("Killaura LOS remained enabled");
                        settings.addProperty("Check line of sight",checked);
                        enableHack("killauraHack");
                    }
                }
            }
            boolean forward = local && Set.of("walk","sprint","jump","sneak","speed","spider","water","snow").contains(mode);
            mc.options.keyUp.setDown(forward);
            mc.options.keySprint.setDown(local && (mode.equals("sprint") || mode.equals("jump")));
            mc.options.keyJump.setDown(local && (mode.equals("jump") || mode.equals("flight") || mode.equals("crit-jump")||mode.equals("boatfly")));
            mc.options.keyShift.setDown(local && mode.equals("sneak"));
            if(local&&mode.startsWith("mine")) {
                if(mouseState==null) {mouseState=mc.mouseHandler.getClass().getDeclaredField("mouseGrabbed");mouseState.setAccessible(true);}
                if(previousMouseState==null)previousMouseState=mc.mouseHandler.isMouseGrabbed();
                // Virtual input in this loopback fixture only. Do not call GLFW,
                // focus a window or capture the desktop cursor.
                mouseState.setBoolean(mc.mouseHandler,true);
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen)mc.gui.setScreen(null);
                if(mc.gui.screen()!=null)throw new IllegalStateException("Unexpected screen during fixture mining");
            } else restoreMouse(mc);
            mc.options.keyAttack.setDown(local&&mode.startsWith("mine")&&miningIndex<miningTargets.length);
            if (mc.player != null && local) {
                if(combat()||mode.startsWith("mine")) {
                    mc.player.getInventory().setSelectedSlot(0);
                    mc.options.keyDrop.setDown(false);while(mc.options.keyDrop.consumeClick()){}
                }
                travel += Math.hypot(mc.player.getX()-lastX,mc.player.getZ()-lastZ);
                minY = Math.min(minY,mc.player.getY());
                lastX = mc.player.getX(); lastZ = mc.player.getZ();
                if (patrol && forward) {
                    if (mc.player.getX() >= originX + 12) returning = true;
                    else if (mc.player.getX() <= originX + 1) returning = false;
                }
                if (combat()) {
                    var target = mc.level.getEntity(targetId);
                    if (target == null || !target.isAlive()) throw new IllegalStateException("Combat target missing/dead: " + targetId);
                    double dx = target.getX()-mc.player.getX(), dz = target.getZ()-mc.player.getZ();
                    double dy = target.getBoundingBox().getCenter().y()-mc.player.getEyeY();
                    float yaw = (float)Math.toDegrees(Math.atan2(dz,dx))-90;
                    float pitch = (float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
                    boolean away = !face || mode.equals("attack-away");
                    mc.player.setYRot(yaw+(away ? 180 : 0)); mc.player.setXRot(away ? 0 : pitch);
                    boolean manual = Set.of("attack","attack-away","critical","crit-jump").contains(mode);
                    boolean descending = !mc.player.onGround() && mc.player.getDeltaMovement().y()<-.01 && mc.player.fallDistance>0;
                    if (manual && ticks-lastAttackTick>=20 && mc.player.getAttackStrengthScale(0)>=.99f
                            && (!mode.equals("crit-jump") || descending)) {
                        mc.gameMode.attack(mc.player,target);
                        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        lastAttackTick=ticks;
                    }
                } else if(mode.startsWith("mine")) {
                    while(miningIndex<miningTargets.length&&mc.level.getBlockState(miningTargets[miningIndex]).isAir())miningIndex++;
                    if(miningIndex<miningTargets.length) {
                        var target=miningTargets[miningIndex];
                            var center=net.minecraft.world.phys.Vec3.atCenterOf(target);
                            double dx=center.x-mc.player.getX(),dz=center.z-mc.player.getZ(),dy=center.y-mc.player.getEyeY();
                            if(dx*dx+dy*dy+dz*dz>25)throw new IllegalStateException("Mining target outside fixture reach");
                            mc.player.setYRot((float)Math.toDegrees(Math.atan2(dz,dx))-90);
                            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
                    } else mc.options.keyAttack.setDown(false);
                } else mc.player.setYRot(returning ? 90 : -90);
            }
            if (ticks % 2 == 0 || mode.equals("stop")) {
                var state = new JsonObject();
                state.addProperty("id",id); state.addProperty("mode",mode); state.addProperty("ticks",ticks);
                state.addProperty("connected",mc.player != null && local && !mode.equals("stop")); state.addProperty("module",module);
                state.addProperty("error",error);
                var enabled = new JsonArray(); boolean allEnabled = !hacks.isEmpty();
                for (var entry:hacks.entrySet()) {
                    boolean active = (boolean)entry.getValue().getClass().getMethod("isEnabled").invoke(entry.getValue());
                    allEnabled &= active;if(active)enabled.add(entry.getKey());
                }
                state.addProperty("moduleEnabled",allEnabled);state.add("enabledModules",enabled);
                state.add("settings",settings.deepCopy());state.addProperty("targetId",targetId);
                state.addProperty("attackCalls",attackCalls);state.addProperty("face",face);
                state.addProperty("miningIndex",miningIndex);state.addProperty("miningTotal",miningTargets.length);state.addProperty("miningCalls",miningCalls);
                state.addProperty("screen",mc.gui.screen()==null?"none":mc.gui.screen().getClass().getSimpleName());
                state.addProperty("patrol",patrol);
                state.addProperty("travel",travel);
                state.addProperty("minY",minY);
                if (mc.player != null && local) {
                    state.addProperty("x",mc.player.getX()); state.addProperty("y",mc.player.getY()); state.addProperty("z",mc.player.getZ());
                    state.addProperty("onGround",mc.player.onGround());state.addProperty("fallDistance",mc.player.fallDistance);
                    state.addProperty("heldItem",mc.player.getMainHandItem().toString());state.addProperty("heldSlot",mc.player.getInventory().getSelectedSlot());
                    state.addProperty("mouseGrabbed",mc.mouseHandler.isMouseGrabbed());
                    state.addProperty("riding",mc.player.isPassenger());
                    if(mc.player.getVehicle()!=null) {state.addProperty("vehicleId",mc.player.getVehicle().getId());state.addProperty("vehicleY",mc.player.getVehicle().getY());}
                }
                Files.writeString(root.resolve("state.json"),json.toJson(state));
            }
            if (mode.equals("stop")) { disableHack(); mc.stop(); }
        } catch (Exception failure) {
            error = failure.getClass().getSimpleName()+": "+failure.getMessage();
            mode = "idle";
            try {restoreMouse(mc);}catch(ReflectiveOperationException ignored){}
            mc.options.keyAttack.setDown(false);if(mc.gameMode!=null)mc.gameMode.stopDestroyBlock();
            try { disableHack(); Files.writeString(root.resolve("failure.txt"),error); } catch(Exception ignored) {}
        }
    }
    private void restoreMouse(Minecraft mc) throws ReflectiveOperationException {
        if(previousMouseState!=null) {mouseState.setBoolean(mc.mouseHandler,previousMouseState);previousMouseState=null;}
    }
    private boolean combat() {return Set.of("attack","attack-away","critical","killaura","aura-critical","crit-jump").contains(mode);}
    private Object getHack(String name) throws ReflectiveOperationException {
        var type = Class.forName("net.wurstclient.WurstClient");Object wurst=type.getField("INSTANCE").get(null);
        if (!(boolean)type.getMethod("isEnabled").invoke(wurst)) type.getMethod("setEnabled",boolean.class).invoke(wurst,true);
        Object hax=type.getMethod("getHax").invoke(wurst);return hax.getClass().getField(name).get(hax);
    }
    private void enableHack(String name) throws ReflectiveOperationException {
        Object hack=getHack(name);hacks.put(name,hack);
        hack.getClass().getMethod("setEnabled",boolean.class).invoke(hack,true);
        if (!(boolean)hack.getClass().getMethod("isEnabled").invoke(hack)) throw new IllegalStateException("Wurst module did not enable: "+name);
        module=String.join(",",hacks.keySet());
    }
    private Object setting(Object hack,String name) throws ReflectiveOperationException {
        var values=(Map<?,?>)hack.getClass().getMethod("getSettings").invoke(hack);
        for(Object value:values.values()) if(name.equals(value.getClass().getMethod("getName").invoke(value)))return value;
        throw new IllegalStateException("Missing Wurst setting: "+name);
    }
    private void slider(Object hack,String name,double requested) throws ReflectiveOperationException {
        Object value=setting(hack,name);value.getClass().getMethod("setValue",double.class).invoke(value,requested);
        double actual=(double)value.getClass().getMethod("getValue").invoke(value);
        if(!Double.isFinite(actual)||Math.abs(actual-requested)>1E-9)throw new IllegalStateException("Wurst setting readback failed: "+name);
        settings.addProperty(name,actual);
    }
    private void disableHack() throws ReflectiveOperationException {
        ReflectiveOperationException failure=null;
        for(Object hack:hacks.values())try {hack.getClass().getMethod("setEnabled",boolean.class).invoke(hack,false);}
        catch(ReflectiveOperationException error) {if(failure==null)failure=error;}
        hacks.clear();module="";settings.keySet().clear();
        if(failure!=null)throw failure;
    }
}
