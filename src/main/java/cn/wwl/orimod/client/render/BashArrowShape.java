package cn.wwl.orimod.client.render;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/** Soft crossed arrow ribbons plus a tip cap: the arrow retains area when viewed along its axis. */
public final class BashArrowShape {
    public record Quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d) { }
    private BashArrowShape() { }

    public static List<Quad> quads(Vec3 start, Vec3 direction) {
        var along = direction.normalize();
        if (along.lengthSqr() < .5) return List.of();
        var side = along.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < .01) side = new Vec3(1, 0, 0);
        side = side.normalize();
        var up = along.cross(side).normalize();
        var end = start.add(along.scale(2.2));
        var quads = new ArrayList<Quad>();
        for (var width : List.of(side, up)) {
            quads.add(ribbon(start.add(along.scale(.6)), end.subtract(along.scale(.35)), width, .16));
            for (var wing : List.of(side, side.scale(-1), up, up.scale(-1)))
                quads.add(ribbon(end.subtract(along.scale(.5)).add(wing.scale(.32)), end, width, .12));
        }
        // Neither crossed shaft can be seen exactly end-on. This face closes that remaining blind angle.
        quads.add(new Quad(end.subtract(side.scale(.09)).subtract(up.scale(.09)),
                end.add(side.scale(.09)).subtract(up.scale(.09)), end.subtract(side.scale(.09)).add(up.scale(.09)),
                end.add(side.scale(.09)).add(up.scale(.09))));
        return List.copyOf(quads);
    }

    private static Quad ribbon(Vec3 a, Vec3 b, Vec3 side, double width) {
        var half = side.scale(width / 2);
        return new Quad(a.subtract(half), a.add(half), b.subtract(half), b.add(half));
    }
}
