package cn.wwl.orimod.client.render;

import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/** Clips a billboard at the ground and fades into it while preserving the radial texture UVs. */
public final class SoftGlowClip {
    private SoftGlowClip() {}
    public record Vertex(float x, float y, float z, float u, float v, float alpha) {
        Vertex between(Vertex other, float t) {
            return new Vertex(x + (other.x - x) * t, y + (other.y - y) * t, z + (other.z - z) * t,
                    u + (other.u - u) * t, v + (other.v - v) * t, 1);
        }
    }

    /** QUADS vertices: each clipped triangle repeats its last vertex. Four bands approximate smoothstep. */
    public static List<Vertex> vertices(Vector3f c, Vector3f u, Vector3f v, float floor, float fadeHeight) {
        var quad = List.of(
                new Vertex(c.x - u.x - v.x, c.y - u.y - v.y, c.z - u.z - v.z, 0, 1, 1),
                new Vertex(c.x + u.x - v.x, c.y + u.y - v.y, c.z + u.z - v.z, 1, 1, 1),
                new Vertex(c.x + u.x + v.x, c.y + u.y + v.y, c.z + u.z + v.z, 1, 0, 1),
                new Vertex(c.x - u.x + v.x, c.y - u.y + v.y, c.z - u.z + v.z, 0, 0, 1));
        var result = new ArrayList<Vertex>();
        for (int band = 0; band <= 4; band++) {
            var polygon = clip(quad, floor + fadeHeight * band / 4, true);
            if (band < 4) polygon = clip(polygon, floor + fadeHeight * (band + 1) / 4, false);
            for (int i = 1; i + 1 < polygon.size(); i++) {
                for (var vertex : List.of(polygon.get(0), polygon.get(i), polygon.get(i + 1), polygon.get(i + 1))) {
                    float t = Math.clamp((vertex.y - floor) / fadeHeight, 0, 1);
                    result.add(new Vertex(vertex.x, vertex.y, vertex.z, vertex.u, vertex.v, t * t * (3 - 2 * t)));
                }
            }
        }
        return result;
    }

    private static List<Vertex> clip(List<Vertex> input, float y, boolean above) {
        var result = new ArrayList<Vertex>();
        if (input.isEmpty()) return result;
        var previous = input.getLast();
        boolean previousInside = above ? previous.y >= y : previous.y <= y;
        for (var next : input) {
            boolean inside = above ? next.y >= y : next.y <= y;
            if (inside != previousInside) {
                var crossing = previous.between(next, (y - previous.y) / (next.y - previous.y));
                // Pin the clipped coordinate: rounding must not leave a nonzero alpha below the floor.
                result.add(new Vertex(crossing.x, y, crossing.z, crossing.u, crossing.v, 1));
            }
            if (inside) result.add(next);
            previous = next;
            previousInside = inside;
        }
        return result;
    }
}
