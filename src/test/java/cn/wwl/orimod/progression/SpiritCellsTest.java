package cn.wwl.orimod.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** R36: cell bookkeeping keys and limits are stable (they are saved in progress rewards). */
class SpiritCellsTest {
    @Test void keysAndLimits() {
        assertEquals("cell/life", SpiritCells.Kind.LIFE.key());
        assertEquals("cell/energy", SpiritCells.Kind.ENERGY.key());
        assertEquals(10, SpiritCells.MAX);
        assertEquals(20, SpiritCells.MAX * SpiritCells.HEALTH_PER_CELL, 1e-9);   // ten cells double vanilla health
        assertEquals(100, SpiritCells.MAX * SpiritCells.ENERGY_PER_CELL, 1e-6);
    }
}
