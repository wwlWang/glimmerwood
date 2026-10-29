package cn.wwl.orimod.gametest;

import java.util.Collection;
import java.util.stream.Stream;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Explicit opt-in reproduction; not part of the default full-suite namespaces.
 * Reuses the original tests verbatim and a byte-identical room template. */
@GameTestHolder("orimod_disk_focus")
public final class DiskUnloadFocusedGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        return Stream.concat(
                GreatTreeScaleInteriorGameTests.cases().stream().filter(f->f.batchName().equals("great_tree_fall")),
                GloomTideGameTests.cases().stream().filter(f->f.testName().equals("gloom_tide.chunk_unload_drops_section_cache")))
                .map(f->new TestFunction(f.batchName(),f.testName(),"orimod_disk_focus:room",f.rotation(),f.maxTicks(),
                        f.setupTicks(),f.required(),f.manualOnly(),f.maxAttempts(),f.requiredSuccesses(),f.skyAccess(),f.function()))
                .toList();
    }
}
