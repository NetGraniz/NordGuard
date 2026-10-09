package dev.nordfjell.guard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Server-issued teleport sequence; client movement and ground flags cannot set it. */
final class NativeTeleport {
    private final Method getHandle;
    private final Field connection, sequence;
    NativeTeleport() throws ReflectiveOperationException {
        getHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
        connection = Class.forName("net.minecraft.server.level.ServerPlayer").getField("connection");
        sequence = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl").getDeclaredField("awaitingTeleport");
        sequence.setAccessible(true);
    }
    int sequence(Player player) {
        try { return sequence.getInt(connection.get(getHandle.invoke(player))); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot read server teleport sequence", error); }
    }
    static int next(int current) { return current == Integer.MAX_VALUE - 1 ? 0 : current + 1; }
}
