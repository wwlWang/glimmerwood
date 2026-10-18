package cn.wwl.orimod.fx;

import cn.wwl.orimod.config.VisualQuality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class FxBudgetTest {
    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void balancedKeepsVerifiedServerCounts(SpiritFxKind kind) {
        // R1–R13 runtime evidence used these server sendParticles counts.
        int verified = switch (kind) { case BLADE_HIT -> 4; case STOMP_IMPACT -> 20; case ARROW_TRAIL -> 1; case CHECKPOINT_REACHED -> 12; case ROUTE_MARKER -> 1; };
        assertEquals(verified, FxBudget.of(kind, VisualQuality.BALANCED, true).total());
    }

    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void advancedOffKeepsOnlyEssentialAndNeverZero(SpiritFxKind kind) {
        for (var quality : VisualQuality.values()) {
            var split = FxBudget.of(kind, quality, false);
            assertEquals(0, split.decorative(), kind + " " + quality);
            assertEquals(kind.essential(), split.essential());
            assertTrue(split.total() >= 1, "gameplay feedback must stay visible: " + kind);
        }
    }

    @ParameterizedTest @EnumSource(SpiritFxKind.class)
    void tiersAreMonotonicAndBounded(SpiritFxKind kind) {
        int fluent = FxBudget.of(kind, VisualQuality.FLUENT, true).total();
        int balanced = FxBudget.of(kind, VisualQuality.BALANCED, true).total();
        int detailed = FxBudget.of(kind, VisualQuality.DETAILED, true).total();
        assertTrue(fluent <= balanced && balanced <= detailed, kind + ": " + fluent + "/" + balanced + "/" + detailed);
        assertTrue(fluent >= kind.essential());
        assertTrue(detailed <= FxBudget.MAX_PER_BURST);
        assertTrue(FxBudget.of(kind, VisualQuality.FLUENT, true).essential() == kind.essential());
    }

    @Test void fluentHalvesDecorativeBursts() {
        assertEquals(10, FxBudget.of(SpiritFxKind.STOMP_IMPACT, VisualQuality.FLUENT, true).total());
        assertEquals(30, FxBudget.of(SpiritFxKind.STOMP_IMPACT, VisualQuality.DETAILED, true).total());
        assertEquals(1, FxBudget.of(SpiritFxKind.ARROW_TRAIL, VisualQuality.FLUENT, true).total());
    }

    @Test void wireIdsAreStableAndUnique() {
        int[] expected = {0, 1, 2, 3, 4};
        var kinds = SpiritFxKind.values();
        assertEquals(expected.length, kinds.length, "new kinds need a new id and test entry");
        for (int i = 0; i < kinds.length; i++) {
            assertEquals(expected[i], kinds[i].wireId());
            assertSame(kinds[i], SpiritFxKind.byWireId(expected[i]));
        }
        assertNull(SpiritFxKind.byWireId(-1));
        assertNull(SpiritFxKind.byWireId(5));
    }

    @Test void unknownQualityNamesFallBackToBalanced() {
        assertEquals(VisualQuality.BALANCED, VisualQuality.parse(null));
        assertEquals(VisualQuality.BALANCED, VisualQuality.parse("ULTRA"));
        assertEquals(VisualQuality.BALANCED, VisualQuality.parse("fluent"));
        assertEquals(VisualQuality.DETAILED, VisualQuality.parse("DETAILED"));
    }
}
