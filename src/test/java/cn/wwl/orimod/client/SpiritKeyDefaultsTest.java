package cn.wwl.orimod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SpiritKeyDefaultsTest {
    private static KeyMapping key(String name, int code) { return new KeyMapping(name, code, "key.categories.misc"); }
    @Test void defaultsAvoidAllVanillaAndIrisBindings() {
        var vanilla = new int[]{87,65,83,68,32,340,341,69,70,81,84,258,47,80,291,294,300,76,49,50,51,52,53,54,55,56,57,67,88,82,75,79};
        var all = new ArrayList<KeyMapping>();
        for (int code : vanilla) all.add(key("external."+code,code));
        Collections.addAll(all, SpiritControls.TOGGLE, SpiritControls.DASH, SpiritControls.BASH, SpiritControls.PANEL,
                SpiritControls.SOUL_LINK, SpiritControls.LIGHT_BURST, SpiritControls.CHARGE_FLAME, SpiritControls.FLASH);
        assertTrue(SpiritKeyDefaults.conflicts(all.toArray(KeyMapping[]::new)).isEmpty());
    }
    @Test void inspectionPreservesOldDefaultsCustomAndUnboundMappings() {
        var dash=key("key.orimod.fixture_dash",67); var custom=key("key.orimod.fixture_custom",90);
        custom.setKey(InputConstants.Type.KEYSYM.getOrCreate(85));
        var unbound=key("key.orimod.fixture_unbound",-1); var iris=key("iris.fixture.reload",82);
        var oldBash=key("key.orimod.fixture_bash",82); var hotbar=key("key.fixture.saveToolbar",67);
        var all=new KeyMapping[]{dash,custom,unbound,iris,oldBash,hotbar};
        var before=Arrays.stream(all).map(KeyMapping::saveString).toList();
        assertEquals(2,SpiritKeyDefaults.conflicts(all).size());
        assertEquals(before,Arrays.stream(all).map(KeyMapping::saveString).toList());
        assertEquals(2,SpiritKeyDefaults.conflicts(all).size());
    }
    @Test void unrelatedOrUnboundMappingsDoNotWarn() {
        assertTrue(SpiritKeyDefaults.conflicts(new KeyMapping[]{key("key.external.a",82),key("key.external.b",82),
                key("key.orimod.none",-1),key("key.orimod.none2",-1)}).isEmpty());
    }
}
