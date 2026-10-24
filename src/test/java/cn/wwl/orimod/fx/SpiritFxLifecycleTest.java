package cn.wwl.orimod.fx;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpiritFxLifecycleTest {
    @Test void wireIdsUniqueAndAppendOnly() {
        var seen = new HashSet<Integer>();
        for (var kind : SpiritFxKind.values()) {
            assertTrue(seen.add(kind.wireId()), "duplicate wire id " + kind);
            assertEquals(kind, SpiritFxKind.byWireId(kind.wireId()));
        }
        // R15 ids must never move.
        assertEquals(0, SpiritFxKind.BLADE_HIT.wireId()); assertEquals(4, SpiritFxKind.ROUTE_MARKER.wireId());
        for (var phase : SpiritFxPhase.values()) assertEquals(phase, SpiritFxPhase.byWireId(phase.wireId()));
    }

    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void everyKindHasAFormalSoundEntry(SpiritFxKind kind) {
        assertTrue(SpiritSoundCue.listed(kind), "sound table misses " + kind);
        var cue = SpiritSoundCue.of(kind);
        if (cue == null) return;
        assertTrue(cue.sound().startsWith("minecraft:"), "cue must be a vanilla sound: " + cue);
        assertTrue(cue.volume() > 0 && cue.volume() <= 1, "volume " + cue);
        assertTrue(cue.pitch() >= .5f && cue.pitch() <= 2f, "pitch " + cue);
        assertEquals(kind.persistent(), cue.loop(), "only persistent effects loop: " + kind);
    }

    @Test void persistentKindsIncludeTrailsBashAimAndFlash() {
        var persistent = java.util.Arrays.stream(SpiritFxKind.values()).filter(SpiritFxKind::persistent).toList();
        assertEquals(List.of(SpiritFxKind.GLIDE, SpiritFxKind.GRAPPLE_TETHER, SpiritFxKind.BASH_AIM, SpiritFxKind.FLASH, SpiritFxKind.REGENERATE), persistent);
    }

    @Test void startStopAndHeartbeat() {
        var t = new SpiritFxTracker();
        assertTrue(t.start(7, SpiritFxKind.GLIDE, 0));
        assertFalse(t.start(7, SpiritFxKind.GLIDE, 20), "heartbeat is not a new start");
        assertTrue(t.tick(20 + SpiritFxTracker.EXPIRY_TICKS, id -> true).isEmpty(), "heartbeat did not refresh");
        assertTrue(t.stop(7, SpiritFxKind.GLIDE));
        assertFalse(t.stop(7, SpiritFxKind.GLIDE));
        assertTrue(t.active().isEmpty());
    }

    @Test void oneShotKindsNeverBecomeActive() {
        var t = new SpiritFxTracker();
        for (var kind : SpiritFxKind.values()) if (!kind.persistent()) assertFalse(t.start(1, kind, 0));
        assertTrue(t.active().isEmpty());
    }

    @Test void missedHeartbeatsExpire() {
        var t = new SpiritFxTracker();
        t.start(7, SpiritFxKind.GLIDE, 100);
        assertTrue(t.tick(100 + SpiritFxTracker.EXPIRY_TICKS, id -> true).isEmpty());
        assertEquals(List.of(new SpiritFxTracker.Key(7, SpiritFxKind.GLIDE)), t.tick(101 + SpiritFxTracker.EXPIRY_TICKS, id -> true));
    }

    @Test void entityGoneEndsItsEffects() {
        var t = new SpiritFxTracker();
        t.start(7, SpiritFxKind.GLIDE, 0); t.start(8, SpiritFxKind.GLIDE, 0);
        var removed = t.tick(1, id -> id != 7);
        assertEquals(List.of(new SpiritFxTracker.Key(7, SpiritFxKind.GLIDE)), removed);
        assertTrue(t.isActive(8, SpiritFxKind.GLIDE));
    }

    @Test void stopAllOnlyTouchesOneEntity() {
        var t = new SpiritFxTracker();
        t.start(7, SpiritFxKind.GLIDE, 0); t.start(7, SpiritFxKind.GRAPPLE_TETHER, 0); t.start(8, SpiritFxKind.GLIDE, 0);
        assertEquals(2, t.stopAll(7).size());
        assertEquals(1, t.active().size());
        assertEquals(1, t.clear().size());
        assertTrue(t.active().isEmpty());
    }

    @Test void expiryCoversThreeHeartbeats() { assertEquals(3 * SpiritFxTracker.HEARTBEAT_TICKS, SpiritFxTracker.EXPIRY_TICKS); }
}
