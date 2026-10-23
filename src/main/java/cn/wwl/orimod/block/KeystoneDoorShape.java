package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.Comparator;
import java.util.List;

/** The visual is fitted to the original doorway, never to new collision cells. */
public record KeystoneDoorShape(BlockPos root, AABB bounds, boolean alongZ, int filled) {
    public static KeystoneDoorShape of(List<BlockPos> cells, int filled) {
        if (cells.isEmpty()) throw new IllegalArgumentException("Empty door");
        var root = cells.stream().min(Comparator.<BlockPos>comparingInt(p -> p.getY()).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).orElseThrow();
        int x0=root.getX(),x1=x0,y0=root.getY(),y1=y0,z0=root.getZ(),z1=z0;
        for (var p:cells) { x0=Math.min(x0,p.getX());x1=Math.max(x1,p.getX());y0=Math.min(y0,p.getY());y1=Math.max(y1,p.getY());z0=Math.min(z0,p.getZ());z1=Math.max(z1,p.getZ()); }
        return new KeystoneDoorShape(root.immutable(),new AABB(x0,y0,z0,x1+1,y1+1,z1+1),z1-z0>x1-x0,Math.clamp(filled,0,2));
    }
    public double width() { return alongZ ? bounds.getZsize() : bounds.getXsize(); }
    public double height() { return bounds.getYsize(); }
}
