package cn.wwl.orimod.asset;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;

/** Authored asset requirements shared by runtime resolution, JUnit and the export checker. */
public final class AnimationContract {
    public static final String RESOURCE = "/assets/orimod/models/entity/animation_contract.json";
    public static final List<String> REQUIRED = read();
    private AnimationContract() {}

    private static List<String> read() {
        try (var stream = AnimationContract.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) throw new IllegalStateException("Missing animation contract " + RESOURCE);
            var clips = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("required");
            var names = new java.util.ArrayList<String>();
            for (var clip : clips) names.add(clip.getAsString());
            if (!names.contains("idle") || names.stream().anyMatch(String::isBlank) || names.stream().distinct().count() != names.size())
                throw new IllegalStateException("Invalid animation contract " + RESOURCE);
            return List.copyOf(names);
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot read animation contract " + RESOURCE, e); }
    }

    public static List<String> missing(Predicate<String> available) {
        return REQUIRED.stream().filter(available.negate()).toList();
    }

    /** Empty names mean a generated rest pose; even a model with no clips remains renderable. */
    public static String resolveName(String requested, Predicate<String> available) {
        if (available.test(requested)) return requested;
        return available.test("idle") ? "idle" : "";
    }
}
