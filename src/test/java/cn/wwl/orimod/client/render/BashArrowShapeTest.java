package cn.wwl.orimod.client.render;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BashArrowShapeTest {
    @Test void endOnAndVerticalViewsHaveVisibleArea() {
        for (var along : new Vec3[]{new Vec3(0,0,1), new Vec3(0,1,0), new Vec3(.1,.8,.5).normalize()}) {
            var shape = BashArrowShape.quads(Vec3.ZERO, along);
            assertTrue(shape.stream().anyMatch(q -> Math.abs(q.b().subtract(q.a()).cross(q.c().subtract(q.a())).dot(along)) > .02));
            for (var q : shape) for (var p : new Vec3[]{q.a(), q.b(), q.c(), q.d()}) assertTrue(Double.isFinite(p.lengthSqr()));
        }
    }

    @Test void worldOffsetIsSubtractedBeforeFloatConversion() {
        var origin = new Vec3(20_000_000, 100, -20_000_000);
        var local = BashArrowShape.quads(Vec3.ZERO, new Vec3(0,0,1));
        var distant = BashArrowShape.quads(origin, new Vec3(0,0,1));
        for (int i = 0; i < local.size(); i++) assertTrue(distant.get(i).a().subtract(origin).distanceTo(local.get(i).a()) < 1e-7);
    }
}
