package cn.wwl.orimod.asset;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class AnimationContractTest {
    static Stream<String> clips() { return AnimationContract.REQUIRED.stream(); }

    private static JsonObject packagedGlb() throws Exception {
        try (var stream = AnimationContractTest.class.getResourceAsStream("/assets/orimod/models/entity/ori.glb")) {
            assertNotNull(stream, "Packaged player GLB missing");
            byte[] data = stream.readAllBytes(); var header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(0x46546c67, header.getInt()); assertEquals(2, header.getInt()); assertEquals(data.length, header.getInt());
            int length = header.getInt(); assertEquals(0x4e4f534a, header.getInt());
            assertTrue(length > 0 && length <= data.length - 20);
            return JsonParser.parseString(new String(data, 20, length, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static Set<String> names(JsonObject glb) {
        var names = new HashSet<String>();
        for (var clip : glb.getAsJsonArray("animations")) assertTrue(names.add(clip.getAsJsonObject().get("name").getAsString()), "Duplicate clip name");
        return names;
    }

    @Test void packagedPlayerSatisfiesRuntimeContract() throws Exception {
        var glb = packagedGlb(); var names = names(glb);
        assertTrue(AnimationContract.missing(names::contains).isEmpty(), () -> "Missing " + AnimationContract.missing(names::contains));
        for (var value : glb.getAsJsonArray("animations")) {
            var clip = value.getAsJsonObject(); float duration = 0;
            for (var sampler : clip.getAsJsonArray("samplers")) {
                var accessor = glb.getAsJsonArray("accessors").get(sampler.getAsJsonObject().get("input").getAsInt()).getAsJsonObject();
                float end = accessor.getAsJsonArray("max").get(0).getAsFloat();
                assertTrue(Float.isFinite(end)); duration = Math.max(duration, end);
            }
            assertTrue(duration > 0, "Unplayable clip " + clip.get("name"));
        }
    }

    @ParameterizedTest @MethodSource("clips")
    void deletingAnyRequiredClipIsDetectedAndHasAFallback(String removed) throws Exception {
        var names = names(packagedGlb()); assertTrue(names.remove(removed));
        assertEquals(java.util.List.of(removed), AnimationContract.missing(names::contains));
        assertEquals(removed.equals("idle") ? "" : "idle", AnimationContract.resolveName(removed, names::contains));
        for (String retained : names) assertEquals(retained, AnimationContract.resolveName(retained, names::contains));
    }

    @Test void missingIdleAndCombatOverlayUseRestPose() {
        var available = new HashSet<>(AnimationContract.REQUIRED); available.remove("idle"); available.remove("blade_2_left");
        assertEquals("", AnimationContract.resolveName("blade_2_left", available::contains));
        assertEquals("walk", AnimationContract.resolveName("walk", available::contains));
    }

    @Test void modelWithoutAnimationsRemainsResolvable() {
        for (String name : AnimationContract.REQUIRED) assertEquals("", AnimationContract.resolveName(name, ignored -> false));
        assertEquals(AnimationContract.REQUIRED, AnimationContract.missing(ignored -> false));
    }
}
