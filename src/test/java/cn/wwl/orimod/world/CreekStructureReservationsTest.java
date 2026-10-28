package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CreekStructureReservationsTest {
    @Test void coversLongHalfFootprintRimAndOutpostHalfWidth(){
        assertEquals(62,CreekStructureReservations.radius(45,67));
        assertEquals(4,CreekStructureReservations.chunkRadius(45,67));
        assertEquals(CreekStructureReservations.chunkRadius(45,67),CreekStructureReservations.chunkRadius(67,45));
    }
    @Test void includesBothVanillaSetsAndTheTwoObservedGladesCollisions(){
        assertEquals(java.util.Set.of("minecraft:villages","minecraft:pillager_outposts","orimod:glades_outpost"),
                CreekStructureReservations.SETS.stream().map(Object::toString).collect(java.util.stream.Collectors.toSet()));
    }
}
