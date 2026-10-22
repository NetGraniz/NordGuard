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
        if(kind.startsWith("diagonal-")) {
            String profile=kind.replace("diagonal-15-","diagonal-");
            double gradient=kind.startsWith("diagonal-15-")?Math.tan(Math.toRadians(15)):1;
            int drop=switch(profile) {case "diagonal-1" -> 1;case "diagonal-2" -> 2;case "diagonal-mixed" -> 0;
                default -> throw new IllegalArgumentException("Unknown diagonal route");};
            var at=player.getLocation();var world=at.getWorld();int x=at.getBlockX(),z=at.getBlockZ();
            for(int dx=-2;dx<=44;dx++)for(int lateral=-3;lateral<=3;lateral++) {
                int dz=(int)Math.round(dx*gradient)+lateral;
                if(!Bukkit.isOwnedByCurrentRegion(world,(x+dx)>>4,(z+dz)>>4)
                        ||!world.isChunkLoaded((x+dx)>>4,(z+dz)>>4))
                    throw new AssertionError("Diagonal terrain not loaded/owned");
            }
            for(int dx=-2;dx<=44;dx++)for(int lateral=-3;lateral<=3;lateral++) {
                int step=Math.max(0,(dx-4)/3),descent=drop==0?step+step/2:step*drop;
                int floor=80-descent,dz=(int)Math.round(dx*gradient)+lateral;
                for(int y=48;y<=86;y++)world.getBlockAt(x+dx,y,z+dz).setType(y<floor?Material.STONE:Material.AIR,false);
            }
            return;
        }
        if(kind.equals("speed-effect") || kind.equals("jump-effect")) {
            player.addPotionEffect(new PotionEffect(kind.equals("speed-effect") ? PotionEffectType.SPEED
                    : PotionEffectType.JUMP_BOOST,200,1));
            return;
        }
        if(kind.equals("knockback")) {
            player.setVelocity(new org.bukkit.util.Vector(.6,.42,0));return;
        }
        var material=switch(kind) {
            case "downstep","wall-run","mountain-wall" -> Material.STONE;
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
            if(kind.equals("mountain-wall")||kind.equals("wall-run")) {
                int floor=kind.equals("mountain-wall")?80-Math.max(0,(dx-3)/2):80;
                for(int y=71;y<=85;y++)at.getWorld().getBlockAt(x+dx,y,z+dz).setType(
                        y<floor||dz==1&&y<floor+4?Material.STONE:Material.AIR,false);
            } else if(kind.equals("downstep")) {
                if(dx>=6) {
                    at.getWorld().getBlockAt(x+dx,78,z+dz).setType(material,false);
                    at.getWorld().getBlockAt(x+dx,79,z+dz).setType(Material.AIR,false);
                }
            } else if(kind.equals("slabs")) {
                // Half-block transitions in both directions, surrounded by the ordinary floor.
                if((dx/3)%2==1)at.getWorld().getBlockAt(x+dx,80,z+dz).setType(material,false);
            } else at.getWorld().getBlockAt(x+dx,79,z+dz).setType(material,false);
        }
        if(kind.equals("mountain-wall")||kind.equals("wall-run"))
            player.teleportAsync(at.clone().set(at.getX(),at.getY(),z+.69));
    }
}
