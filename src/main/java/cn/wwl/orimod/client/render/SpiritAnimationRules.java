package cn.wwl.orimod.client.render;

import cn.wwl.orimod.movement.FeelTuning;
import java.util.Set;

/** R112 visual policy, independent of Player, the render thread and the GLB loader. */
public final class SpiritAnimationRules {
    private SpiritAnimationRules() {}

    /** An observation snapshot; no field changes gameplay or the authoritative combat windows. */
    public static final class State {
        public boolean alive = true, spirit = true, grounded = true;
        public boolean sleeping, passenger, rowing, spin, elytra, crawling, water, submerged, climbing;
        public boolean mantle, burrow, crouching, moving, launchCharge, entering;
        public float speed, vertical, idleSeconds;
        public String mode = "idle", shot = "";
    }

    public static String family(State s) {
        if (!s.alive || !s.spirit || s.sleeping || s.passenger) return "";
        if (s.spin) return "spin";
        if (s.elytra) return "elytra";
        // MC's visuallySwimming also includes the dry one-block crawl. Test it first.
        if (s.crawling) return "crawl";
        if (s.burrow) return "burrow";
        if (s.water && !s.climbing) {
            if (s.speed >= 11.5f) return "swim_dash";
            if (s.speed < .65f) return "swim_idle";
            return s.submerged ? "swim" : "swim_surface";
        }
        return s.mode.equals("glide") ? "glide" : "";
    }

    public static String base(State s) {
        if (!s.alive) return "death";
        if (!s.spirit) return "transform_out";
        if (s.sleeping) return "sleep";
        if (s.passenger) return s.rowing ? "boat_row" : "sit";
        String family = family(s);
        if (family.equals("spin")) return "swim_dash";
        if (!family.isEmpty() && !family.equals("glide")) return family;
        if (s.mode.equals("bash_aim")) return "bash_aim";
        if (s.mode.equals("grapple")) return "grapple";
        if (s.mode.equals("stomp")) return s.shot.equals("stomp_start") ? "stomp_start" : "stomp";
        if (s.mode.equals("dash")) return "dash_start";
        if (s.mantle) return "mantle";
        if (s.climbing) return "climb";
        if (s.mode.equals("wall_slide")) return s.vertical > .03f ? "wall_climb" : Math.abs(s.vertical) < .03f ? "wall_hold" : "wall_slide";
        if (!s.shot.isEmpty()) return s.shot;
        if (family.equals("glide")) return "glide";
        if (!s.grounded && !s.water) return "apex";
        if (s.entering) return "transform_in";
        if (s.launchCharge && !s.moving) return "launch_charge";
        if (s.crouching) return s.moving ? "crouch_walk" : "crouch";
        if (s.moving) return "walk";
        return s.idleSeconds > 9 ? "alert" : "idle";
    }

    /** Same movement exclusions as SpiritCombat; a jump/climb/water pose must not swallow an accepted attack. */
    public static boolean hands(State s) {
        return s.alive && s.spirit && !s.sleeping && !s.passenger && switch (s.mode) {
            case "dash", "bash_aim", "grapple", "stomp" -> false;
            default -> true;
        };
    }

    /** base/run/rise/fall shares sum to one *before* GemRender's per-node normalization. */
    public static float[] weights(float speed, float locomotion, float vertical, float airborne) {
        var gait = FeelTuning.gaitWeights(speed);
        var air = FeelTuning.airWeights(vertical);
        float a = Math.clamp(airborne, 0, 1);
        float run = (gait[1] + gait[2]) * Math.clamp(locomotion, 0, 1) * (1 - a);
        float rise = air[0] * a, fall = air[2] * a;
        return new float[] {1 - run - rise - fall, run, rise, fall};
    }

    /** Metres per second -> cycles per second. Stationary ladder pauses; descending reverses. */
    public static float climbRate(float verticalBlocksPerTick) { return Math.clamp(verticalBlocksPerTick * 10, -2, 2); }
    public static float cycle(float phase) { return phase - (float)Math.floor(phase); }

    /** Post-tick delta includes gravity even when the feet are held against a wall or ladder. */
    public static float contactVertical(double displacement, float seconds, boolean continuous, float fallback) {
        return continuous && seconds > .00001f ? (float)(displacement / seconds / 20) : fallback;
    }

    /** Keep the semantic action through vanilla's swing tail, without replaying its short clip. */
    public static boolean actionVisible(float elapsed, float duration, boolean swinging) {
        return elapsed >= 0 && (elapsed < duration || swinging && elapsed < Math.max(duration, .5f));
    }

    /** Only body-coordinate assets receive the external pitch; crawl/surface/treading carry their own tilt. */
    public static float bodyPitch(String family, float horizontal, float vertical, float viewPitch) {
        return switch (family) {
            case "swim", "swim_dash", "burrow" -> (float)Math.PI / 2 + (float)Math.atan2(-vertical, Math.max(.01f, horizontal));
            case "spin", "elytra" -> (float)Math.PI / 2 + (float)Math.toRadians(Math.clamp(viewPitch, -80, 80));
            case "glide" -> .1f;
            default -> 0;
        };
    }

    /** Source coordinates in the victim's body frame: front +Z; player's right -X (glTF convention). */
    public static String hurt(float front, float right, float damage, float maxHealth, float impulse) {
        if (damage >= Math.max(4, maxHealth * .25f) || impulse >= .65f) return "hurt_heavy";
        if (Math.abs(right) > Math.abs(front)) return right > 0 ? "hurt_side" : "hurt_side_left";
        return front < 0 ? "hurt_back" : "hurt";
    }

    public static String use(String animation) {
        return switch (animation) {
            case "EAT" -> "eat";
            case "DRINK" -> "drink";
            case "TOOT_HORN" -> "horn";
            case "BRUSH" -> "brush";
            case "BLOCK" -> "block";
            case "SPEAR" -> "throw_ready";
            default -> "use_item";
        };
    }

    private static final Set<String> MIRRORED = Set.of("hold", "use_item", "block", "blade_1", "blade_2", "blade_3",
            "bow_charge", "bow_hold", "bow_release", "blade_air", "throw_ready", "spear_throw", "hammer_charge",
            "hammer_smash", "mine", "throw", "drink", "horn", "brush", "grapple", "place", "hurt_side");
    public static String handed(String name, boolean left) { return left && MIRRORED.contains(name) ? name + "_left" : name; }
    private static final Set<String> LOOPS = Set.of("idle", "alert", "walk", "run", "crouch", "rise", "apex", "fall",
            "wall_hold", "wall_slide", "bash_aim", "glide", "grapple", "stomp", "bow_hold", "hold", "use_item", "block",
            "swim", "climb", "sit", "sleep", "bend", "tail_sway", "crouch_walk", "crawl", "wall_climb", "swim_surface",
            "swim_idle", "swim_dash", "throw_ready", "hammer_charge", "eat", "drink", "horn", "brush", "boat_row", "elytra",
            "burrow", "launch_charge", "cast_charge", "flash");
    public static boolean loops(String name) { return LOOPS.contains(name.endsWith("_left") ? name.substring(0, name.length() - 5) : name); }
}
