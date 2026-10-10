package dev.nordfjell.guard.fixture;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** Isolated test fixture, never a server dependency. Drives native input, not movement packets. */
public final class NativeClientProbe implements ClientModInitializer {
    private final Gson json = new Gson();
    private final Path root = Path.of(System.getProperty("nordguard.fixture"));
    private String mode = "idle", module = "", error = "";
    private long id = -1, ticks;
    private Object hack;
    @Override public void onInitializeClient() {
        ClientTickEvents.START_CLIENT_TICK.register(this::tick);
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
                    mode = next.get("mode").getAsString(); id = next.get("id").getAsLong();
                    if (!java.util.Set.of("idle","walk","sprint","jump","sneak","flight","speed","spider","water","stop").contains(mode))
                        throw new IllegalArgumentException("Unknown fixture mode");
                    if (java.util.Set.of("flight","speed","spider","water").contains(mode)) {
                        if (!local || mc.player == null) throw new IllegalStateException("Wurst requires connected loopback fixture");
                        module = switch(mode) { case "flight" -> "flightHack"; case "speed" -> "speedHackHack";
                            case "spider" -> "spiderHack"; default -> "jesusHack"; };
                        var type = Class.forName("net.wurstclient.WurstClient");
                        Object wurst = type.getField("INSTANCE").get(null);
                        if (!(boolean)type.getMethod("isEnabled").invoke(wurst)) type.getMethod("setEnabled",boolean.class).invoke(wurst,true);
                        Object hax = type.getMethod("getHax").invoke(wurst);
                        hack = hax.getClass().getField(module).get(hax);
                        hack.getClass().getMethod("setEnabled", boolean.class).invoke(hack, true);
                        if (!(boolean)hack.getClass().getMethod("isEnabled").invoke(hack)) throw new IllegalStateException("Wurst module did not enable");
                    }
                }
            }
            boolean forward = local && !mode.equals("idle") && !mode.equals("stop") && !mode.equals("flight");
            mc.options.keyUp.setDown(forward);
            mc.options.keySprint.setDown(local && (mode.equals("sprint") || mode.equals("jump")));
            mc.options.keyJump.setDown(local && (mode.equals("jump") || mode.equals("flight")));
            mc.options.keyShift.setDown(local && mode.equals("sneak"));
            if (mc.player != null && local) mc.player.setYRot(-90);
            if (ticks % 2 == 0 || mode.equals("stop")) {
                var state = new JsonObject();
                state.addProperty("id",id); state.addProperty("mode",mode); state.addProperty("ticks",ticks);
                state.addProperty("connected",mc.player != null && local && !mode.equals("stop")); state.addProperty("module",module);
                state.addProperty("error",error);
                state.addProperty("moduleEnabled",hack != null && (boolean)hack.getClass().getMethod("isEnabled").invoke(hack));
                state.addProperty("screen",mc.gui.screen()==null?"none":mc.gui.screen().getClass().getSimpleName());
                if (mc.player != null && local) {
                    state.addProperty("x",mc.player.getX()); state.addProperty("y",mc.player.getY()); state.addProperty("z",mc.player.getZ());
                }
                Files.writeString(root.resolve("state.json"),json.toJson(state));
            }
            if (mode.equals("stop")) { disableHack(); mc.stop(); }
        } catch (Exception failure) {
            error = failure.getClass().getSimpleName()+": "+failure.getMessage();
            mode = "idle";
            try { disableHack(); Files.writeString(root.resolve("failure.txt"),error); } catch(Exception ignored) {}
        }
    }
    private void disableHack() throws ReflectiveOperationException {
        if (hack != null) hack.getClass().getMethod("setEnabled",boolean.class).invoke(hack,false);
        hack = null; module = "";
    }
}
