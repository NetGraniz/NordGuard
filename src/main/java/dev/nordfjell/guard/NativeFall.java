package dev.nordfjell.guard;

import java.lang.reflect.Method;
import org.bukkit.entity.Player;

/** Small version-gated bridge: use the engine's damage calculation and Bukkit damage events. */
public final class NativeFall {
    private final Method getHandle, damageSources, fallSource, causeFall, calculateFall;
    public NativeFall() throws ReflectiveOperationException {
        Class<?> craft = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer");
        Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
        Class<?> sources = Class.forName("net.minecraft.world.damagesource.DamageSources");
        Class<?> source = Class.forName("net.minecraft.world.damagesource.DamageSource");
        getHandle = craft.getMethod("getHandle");
        damageSources = entity.getMethod("damageSources");
        fallSource = sources.getMethod("fall");
        causeFall = Class.forName("net.minecraft.world.entity.LivingEntity").getMethod(
                "causeFallDamage", double.class, float.class, source);
        calculateFall = Class.forName("net.minecraft.world.entity.LivingEntity").getDeclaredMethod(
                "calculateFallDamage", double.class, float.class);
        calculateFall.setAccessible(true);
    }
    public void apply(Player player, double distance) throws ReflectiveOperationException {
        Object handle = getHandle.invoke(player);
        Object source = fallSource.invoke(damageSources.invoke(handle));
        causeFall.invoke(handle, distance, 1F, source);
    }
    public int expected(Player player, double distance) throws ReflectiveOperationException {
        return (int) calculateFall.invoke(getHandle.invoke(player), distance, 1F);
    }
    public void applyRemaining(Player player, double distance, int remaining) throws ReflectiveOperationException {
        Object handle = getHandle.invoke(player);
        double low = 0, high = distance;
        // Find a distance producing only the unpaid base damage, then use normal damage processing.
        for (int i = 0; i < 32; i++) {
            double mid = (low + high) / 2;
            if ((int) calculateFall.invoke(handle, mid, 1F) >= remaining) high = mid;
            else low = mid;
        }
        Object source = fallSource.invoke(damageSources.invoke(handle));
        causeFall.invoke(handle, high, 1F, source);
    }
}
