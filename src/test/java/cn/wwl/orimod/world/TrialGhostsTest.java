package cn.wwl.orimod.world;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TrialGhostsTest {
    @Test void roundTripsWithinFixedPointPrecision() {
        var trace = List.of(new Vec3(1.5, 6, 4.25), new Vec3(-2.123, 13.9, 30.01));
        var back = TrialGhosts.decode(TrialGhosts.encode(trace));
        assertEquals(2, back.size());
        for (int i = 0; i < 2; i++) assertTrue(back.get(i).distanceTo(trace.get(i)) < 1 / 128.0);
    }

    @Test void capsAndToleratesTruncatedData() {
        var trace = new ArrayList<Vec3>();
        for (int i = 0; i < ChallengeGhost.MAX_SAMPLES + 50; i++) trace.add(new Vec3(i, 0, 0));
        assertEquals(ChallengeGhost.MAX_SAMPLES * 3, TrialGhosts.encode(trace).length);
        assertEquals(1, TrialGhosts.decode(new int[]{1, 2, 3, 4}).size());
        assertTrue(TrialGhosts.decode(new int[0]).isEmpty());
    }

    @Test void saveAndLoadKeepEveryGhost() {
        var data = new TrialGhosts();
        data.put("a|x", List.of(new Vec3(1, 2, 3)));
        data.put("b|y", List.of(new Vec3(4, 5, 6), new Vec3(7, 8, 9)));
        assertTrue(data.isDirty());
        var loaded = TrialGhosts.load(data.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertEquals(2, loaded.size());
        assertEquals(new Vec3(7, 8, 9), loaded.ghost("b|y").get(1));
    }
}
