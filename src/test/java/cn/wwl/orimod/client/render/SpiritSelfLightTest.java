package cn.wwl.orimod.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritSelfLightTest {
    @Test void pawsKeepTheWorldBodyLightFloorWithoutChangingSkyLight() {
        for (int sky = 0; sky <= 15; sky++) for (int block = 0; block <= 15; block++)
            assertEquals((sky << 20) | 240, SpiritSelfLight.packed((sky << 20) | (block << 4)));
    }
}
