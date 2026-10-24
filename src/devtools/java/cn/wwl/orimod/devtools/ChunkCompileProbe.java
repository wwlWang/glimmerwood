package cn.wwl.orimod.devtools;

import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Read-only F3 counters from the active renderer, never included in the release jar. */
final class ChunkCompileProbe {
    private static final Pattern BUILDER = Pattern.compile("Chunk Builder: Permits=(\\d+) \\(E (\\d+)\\) \\| Busy=(\\d+) \\| Total=(\\d+)");
    private static final Pattern QUEUES = Pattern.compile("Chunk Queues: U=(\\d+) \\(P0=(\\d+) \\| P1=(\\d+) \\| P2=(\\d+)\\)");

    static Map<String, Object> read(Minecraft mc) {
        var result = new LinkedHashMap<String, Object>();
        result.put("ready", false);
        if (mc.level == null) return result;
        var renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer == null) return result;
        var lines = renderer.getDebugStrings();
        result.put("renderer", "sodium");
        result.put("chunks", renderer.getChunksDebugString());
        result.put("visibleSections", renderer.getVisibleChunkCount());
        result.put("debug", lines);
        boolean builderIdle = false, queuesEmpty = false;
        for (String line : lines) {
            var b = BUILDER.matcher(line);
            if (b.matches()) {
                int scheduled = Integer.parseInt(b.group(1)), busy = Integer.parseInt(b.group(3));
                result.put("scheduled", scheduled);
                result.put("busy", busy);
                builderIdle = scheduled == 0 && busy == 0;
            }
            var q = QUEUES.matcher(line);
            if (q.matches()) {
                int total = 0;
                for (int i = 1; i <= 4; i++) total += Integer.parseInt(q.group(i));
                result.put("pendingUploads", Integer.parseInt(q.group(1)));
                result.put("pendingUpdates", total - Integer.parseInt(q.group(1)));
                queuesEmpty = total == 0;
            }
        }
        // isTerrainRenderComplete alone only checks the submitted queue, not busy workers or uploads.
        // Missing/changed F3 fields must fail closed rather than masquerading as zero.
        // A freshly teleported camera can temporarily have no received terrain and no jobs yet.
        result.put("ready", renderer.getVisibleChunkCount() > 0
                && builderIdle && queuesEmpty && renderer.isTerrainRenderComplete());
        return result;
    }
}
