package dev.nordfjell.guard;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;

/** Reads only loaded, owned cells. Full cubes only; partial shapes are not guessed. */
final class SolidProbe {
    record Scan(boolean known, List<Geometry.Box> solids) {
        boolean blocked(Geometry.Point a, Geometry.Point b) {
            for(var box:solids) if(box.crosses(a,b)) return true;
            return false;
        }
        boolean obscured(Geometry.Point eye, Geometry.Box target) {
            if(!known) return false;
            for(var p:target.sightPoints(eye)) if(!blocked(eye,p)) return false;
            return true;
        }
    }
    static Scan scan(World world, Geometry.Box bounds, int limit) {
        return scan(world,bounds,limit,count->true);
    }
    static Scan scan(World world, Geometry.Box bounds, int limit,java.util.function.IntPredicate budget) {
        int x0=floor(bounds.minX()),x1=floor(bounds.maxX()),y0=floor(bounds.minY()),y1=floor(bounds.maxY());
        int z0=floor(bounds.minZ()),z1=floor(bounds.maxZ());
        long count=((long)x1-x0+1)*((long)y1-y0+1)*((long)z1-z0+1);
        if(count<=0 || count>limit || y0<world.getMinHeight() || y1>=world.getMaxHeight() || !budget.test((int)count)) return unknown();
        var boxes=new ArrayList<Geometry.Box>();
        for(int x=x0;x<=x1;x++) for(int z=z0;z<=z1;z++) {
            if(!world.isChunkLoaded(x>>4,z>>4) || !Bukkit.isOwnedByCurrentRegion(world,x>>4,z>>4)) return unknown();
            for(int y=y0;y<=y1;y++) {
                var block=world.getBlockAt(x,y,z); var type=block.getType();
                if(type.isAir() || block.isLiquid()) continue;
                String name=type.name();
                if(name.contains("PISTON") || name.endsWith("DOOR") || name.contains("SLIME") || name.contains("HONEY")) return unknown();
                var shapes=block.getCollisionShape().getBoundingBoxes();
                if(shapes.size()!=1) continue;
                var shape=shapes.iterator().next();
                if(shape.getMinX()<=1E-7 && shape.getMinY()<=1E-7 && shape.getMinZ()<=1E-7
                        && shape.getMaxX()>=1-1E-7 && shape.getMaxY()>=1-1E-7 && shape.getMaxZ()>=1-1E-7)
                    boxes.add(new Geometry.Box(x+.002,y+.002,z+.002,x+.998,y+.998,z+.998));
            }
        }
        return new Scan(true,List.copyOf(boxes));
    }
    private static Scan unknown() { return new Scan(false,List.of()); }
    private static int floor(double d) { return (int)Math.floor(d); }
}
