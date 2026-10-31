package cn.wwl.orimod.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DimensionNamesTest {
    @Test void customDimensionFallbackHasNoRegistrySyntax() {
        assertEquals("Crystal Caverns Upper",DimensionNames.fallback("crystal_caverns/upper"));
        assertEquals("Moon Realm",DimensionNames.fallback("moon-realm"));
    }
}
