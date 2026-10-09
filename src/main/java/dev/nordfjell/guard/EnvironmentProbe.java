package dev.nordfjell.guard;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

public final class EnvironmentProbe {
    public record Environment(boolean known, boolean ground, boolean wall, boolean special, boolean clear,
                              boolean liquid, boolean liquidSurface, boolean web) {
        Environment(boolean known, boolean ground, boolean wall, boolean special, boolean clear) {
            this(known, ground, wall, special, clear, false, false, false);
        }
    }
    public static Environment inspect(Player player, Location at) {
        var world = at.getWorld();
        var actual = player.getLocation();
        var body = player.getBoundingBox().clone().shift(at.getX() - actual.getX(),
                at.getY() - actual.getY(), at.getZ() - actual.getZ());
        // Normal player box only. A scaled entity needs a separate model.
        if (body.getWidthX() > 0.8 || body.getHeight() > 2) return new Environment(false, false, false, true, false);
        var feet = new BoundingBox(body.getMinX() + .02, at.getY() - .06, body.getMinZ() + .02,
                body.getMaxX() - .02, at.getY() + .01, body.getMaxZ() - .02);
        var walls = body.clone().expand(.04, 0, .04);
        var inside = body.clone().expand(-.03);
        boolean ground = false, wall = false, special = false, clear = true;
        boolean liquid = false, liquidSurface = false, web = false;
        int count = 0;
        for (int x = floor(body.getMinX() - .05); x <= floor(body.getMaxX() + .05); x++)
            for (int z = floor(body.getMinZ() - .05); z <= floor(body.getMaxZ() + .05); z++) {
                if (!Bukkit.isOwnedByCurrentRegion(world, x >> 4, z >> 4) || !world.isChunkLoaded(x >> 4, z >> 4))
                    return new Environment(false, false, false, true, false);
                for (int y = floor(at.getY()) - 1; y <= floor(body.getMaxY()); y++) {
                    if (++count > 36 || y < world.getMinHeight() || y >= world.getMaxHeight())
                        return new Environment(false, false, false, true, false);
                    var block = world.getBlockAt(x, y, z);
                    Material material = block.getType();
                    String name = material.name();
                    if (material == Material.COBWEB)
                        web |= inside.overlaps(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));
                    if (block.isLiquid()) {
                        liquid |= body.overlaps(new BoundingBox(x, y, z, x + 1, y + 1, z + 1));
                        // Source surfaces only; flowing fluids remain outside this model.
                        liquidSurface |= block.getBlockData() instanceof Levelled level && level.getLevel() == 0
                                && Math.abs(at.getY() - (y + 1)) <= .12
                                && body.getMaxX() > x && body.getMinX() < x + 1
                                && body.getMaxZ() > z && body.getMinZ() < z + 1;
                    }
                    special |= name.contains("ICE") || name.contains("PISTON") || name.contains("SLIME")
                            || name.contains("HONEY") || name.endsWith("BED") || material == Material.HAY_BLOCK
                            || material == Material.POWDER_SNOW
                            || material == Material.SWEET_BERRY_BUSH || material == Material.SCAFFOLDING
                            || material == Material.SOUL_SAND || material == Material.BUBBLE_COLUMN;
                    if (material.isAir()) continue;
                    special |= block.getBlockData() instanceof Waterlogged water && water.isWaterlogged();
                    for (var local : block.getCollisionShape().getBoundingBoxes()) {
                        var shape = local.clone().shift(x, y, z);
                        ground |= feet.overlaps(shape);
                        wall |= walls.overlaps(shape) && shape.getMaxY() > at.getY() + .1
                                && shape.getMinY() < body.getMaxY() - .1;
                        clear &= !inside.overlaps(shape);
                    }
                }
            }
        return new Environment(true, ground, wall, special, clear, liquid, liquidSurface && !ground, web);
    }
    private static int floor(double value) { return (int) Math.floor(value); }
}
