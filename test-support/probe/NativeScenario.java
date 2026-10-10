package dev.nordfjell.guard;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Test-only, console-selected terrain and legitimate server-side state changes. */
final class NativeScenario {
    private NativeScenario() {}
    static void prepare(Player player,String kind) {
        if(kind.equals("speed-effect") || kind.equals("jump-effect")) {
            player.addPotionEffect(new PotionEffect(kind.equals("speed-effect") ? PotionEffectType.SPEED
                    : PotionEffectType.JUMP_BOOST,200,1));
            return;
        }
        if(kind.equals("knockback")) {
            player.setVelocity(new org.bukkit.util.Vector(.6,.42,0));return;
        }
        var material=switch(kind) {
            case "ice" -> Material.ICE;
            case "honey" -> Material.HONEY_BLOCK;
            case "slime" -> Material.SLIME_BLOCK;
            case "soul-sand" -> Material.SOUL_SAND;
            case "slabs" -> Material.SMOOTH_STONE_SLAB;
            default -> throw new IllegalArgumentException("Unknown ordinary scenario: "+kind);
        };
        var at=player.getLocation();int x=at.getBlockX(),z=at.getBlockZ();
        // Validate the whole edit before writing anything. Never load another region.
        for(int dx=0;dx<=22;dx++)for(int dz=-2;dz<=2;dz++)
            if(!Bukkit.isOwnedByCurrentRegion(at.getWorld(),(x+dx)>>4,(z+dz)>>4)
                    || !at.getWorld().isChunkLoaded((x+dx)>>4,(z+dz)>>4))
                throw new AssertionError("Native scenario terrain not loaded/owned");
        for(int dx=2;dx<=22;dx++)for(int dz=-2;dz<=2;dz++) {
            if(kind.equals("slabs")) {
                // Half-block transitions in both directions, surrounded by the ordinary floor.
                if((dx/3)%2==1)at.getWorld().getBlockAt(x+dx,80,z+dz).setType(material,false);
            } else at.getWorld().getBlockAt(x+dx,79,z+dz).setType(material,false);
        }
    }
}
