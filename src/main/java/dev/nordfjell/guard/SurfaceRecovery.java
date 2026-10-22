package dev.nordfjell.guard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Only five feet points and the existing body scan. No search or chunk loads. */
final class SurfaceRecovery {
    private SurfaceRecovery(){}
    static boolean leatherBoots(Player p){var boots=p.getInventory().getBoots();return boots!=null&&boots.getType()==Material.LEATHER_BOOTS;}
    static boolean valid(Player p,Location at,Material medium) {
        if(!at.getWorld().getWorldBorder().isInside(at))return false;
        var environment=EnvironmentProbe.inspect(p,at);
        if(!environment.known()||!environment.clear()||environment.ground())return false;
        if(medium==Material.WATER && (environment.special()||!environment.liquid()))return false;
        if(medium==Material.POWDER_SNOW && leatherBoots(p))return false;
        // All feet must enter the same medium. Shore edges, lava and partial support defer.
        double radius=p.getBoundingBox().getWidthX()*.5-.02;
        for(int i=0;i<5;i++){
            int x=(int)Math.floor(at.getX()+(i==0?0:(i&1)==0?radius:-radius));
            int z=(int)Math.floor(at.getZ()+(i==0?0:i<=2?-radius:radius));
            int y=at.getBlockY();var world=at.getWorld();
            if(y<world.getMinHeight()||y>=world.getMaxHeight()||!world.isChunkLoaded(x>>4,z>>4)
                    ||!Bukkit.isOwnedByCurrentRegion(world,x>>4,z>>4)||world.getBlockAt(x,y,z).getType()!=medium)return false;
        }
        return true;
    }
}
