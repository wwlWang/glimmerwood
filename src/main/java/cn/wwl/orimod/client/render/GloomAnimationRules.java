package cn.wwl.orimod.client.render;

import java.util.List;
import cn.wwl.orimod.entity.GloomHeart;
import cn.wwl.orimod.entity.GloomMatriarch;
import cn.wwl.orimod.entity.GloomHawk;
import cn.wwl.orimod.entity.GloomSpitter;

/** Pure clip selection. Timed warnings sample the replicated countdown, never a local playback clock. */
public final class GloomAnimationRules {
    public enum Kind { SPITTER, BEETLE, HAWK, HEART, MATRIARCH }
    public enum Timing { LOOP, SECONDS, NORMALIZED }
    public record State(Kind kind, int action, int remaining, int phase, int enrageRemaining,
                        int deathTicks, int hurtTicks, double speed) { }
    public record Selection(String clip, Timing timing, float time, float speed) { }
    public static final float BLEND_SECONDS = .15f, MOVE_THRESHOLD = .015f;
    private GloomAnimationRules() { }

    public static Selection select(State s, float partial, float releaseAge, float hurtAge) {
        if (s.deathTicks > 0) return progress("death", (s.deathTicks + partial) / 20f);
        if (s.enrageRemaining > 0) return countdown("enrage", s.enrageRemaining, 40, partial);
        Selection warning = warning(s, partial);
        if (warning != null) return warning;
        float releaseDuration = s.kind == Kind.SPITTER ? .5f : .4f;
        if (releaseAge >= 0 && releaseAge < releaseDuration) return new Selection("attack", Timing.SECONDS, releaseAge, 1);
        if (s.hurtTicks > 0 && hurtAge >= 0 && hurtAge < .4f) return progress("hurt", hurtAge / .4f);
        return switch (s.kind) {
            case SPITTER -> locomotion(s);
            // BeetleRules.State ordinal is stable in entityData: IDLE/WINDUP/CHARGING/STUNNED/FLIPPED.
            case BEETLE -> switch (s.action) {
                case 2 -> loop("charge", 1);
                case 3 -> loop("stunned", 1);
                case 4 -> loop("flipped", 1);
                default -> locomotion(s);
            };
            case HAWK -> switch (s.action) {
                case GloomHawk.DIVE -> loop("dive", 1);
                case GloomHawk.CLIMB -> loop("move", 1);
                default -> loop("idle", 1);
            };
            case MATRIARCH -> switch (s.action) {
                case GloomMatriarch.DIVE -> loop("dive", 1);
                case GloomMatriarch.STUNNED -> loop("stunned", 1);
                default -> locomotion(s);
            };
            case HEART -> loop(s.phase >= 2 ? "idle_phase2" : "idle", 1);
        };
    }

    private static Selection warning(State s, float partial) {
        return switch (s.kind) {
            case SPITTER -> s.remaining > 0 ? countdown("telegraph", s.remaining, GloomSpitter.CHARGE_TICKS, partial) : null;
            case BEETLE -> s.action == 1 ? countdown("telegraph", s.remaining, 15, partial) : null;
            case HAWK -> s.action == GloomHawk.TELEGRAPH ? countdown("telegraph", s.remaining, GloomHawk.TELEGRAPH_TICKS, partial) : null;
            case HEART -> switch (s.action) {
                case GloomHeart.VOLLEY -> countdown("telegraph", s.remaining, GloomHeart.VOLLEY_TELEGRAPH, partial);
                case GloomHeart.RING -> countdown("ring", s.remaining, GloomHeart.RING_TELEGRAPH, partial);
                case GloomHeart.SLAM -> countdown("slam", s.remaining, GloomHeart.SLAM_TELEGRAPH, partial);
                default -> null;
            };
            case MATRIARCH -> switch (s.action) {
                case GloomMatriarch.DIVE_TELEGRAPH -> countdown("telegraph", s.remaining, GloomMatriarch.DIVE_TELEGRAPH_TICKS, partial);
                case GloomMatriarch.FEATHERS -> countdown("feathers", s.remaining, GloomMatriarch.FEATHER_TELEGRAPH, partial);
                case GloomMatriarch.GUST -> countdown("gust", s.remaining, GloomMatriarch.GUST_TELEGRAPH, partial);
                default -> null;
            };
        };
    }

    public static boolean released(State before, State after) {
        if (before == null || before.kind != after.kind || after.deathTicks > 0 || after.enrageRemaining > 0) return false;
        return switch (after.kind) {
            case SPITTER -> before.remaining > 0 && after.remaining == 0;
            case HEART -> before.action != GloomHeart.IDLE && after.action == GloomHeart.IDLE;
            case MATRIARCH -> (before.action == GloomMatriarch.FEATHERS || before.action == GloomMatriarch.GUST)
                    && after.action == GloomMatriarch.IDLE;
            default -> false;
        };
    }

    public static float sample(Selection selection, float phase, float duration) {
        if (!(duration > 0) || !Float.isFinite(duration)) return 0;
        return switch (selection.timing) {
            case LOOP -> (phase % duration + duration) % duration;
            case NORMALIZED -> Math.clamp(selection.time, 0, 1) * duration;
            case SECONDS -> Math.clamp(selection.time, 0, duration);
        };
    }

    public static float flightPitch(double horizontalSpeed, double verticalSpeed) {
        return (float) Math.clamp(-Math.atan2(verticalSpeed, Math.max(.01, horizontalSpeed)), -1.25, 1.25);
    }

    public static List<String> required(Kind kind) {
        return switch (kind) {
            case SPITTER -> List.of("idle", "move", "telegraph", "attack", "hurt", "death");
            case BEETLE -> List.of("idle", "move", "telegraph", "charge", "stunned", "flipped", "hurt", "death");
            case HAWK -> List.of("idle", "move", "telegraph", "dive", "hurt", "death");
            case HEART -> List.of("idle", "idle_phase2", "telegraph", "ring", "slam", "attack", "enrage", "hurt", "death");
            case MATRIARCH -> List.of("idle", "move", "telegraph", "dive", "stunned", "feathers", "gust", "attack", "enrage", "hurt", "death");
        };
    }
    private static Selection countdown(String name, int left, int total, float partial) { return progress(name, 1 - (left - Math.clamp(partial, 0, 1)) / total); }
    private static Selection progress(String name, float value) { return new Selection(name, Timing.NORMALIZED, Math.clamp(value, 0, 1), 1); }
    private static Selection loop(String name, float speed) { return new Selection(name, Timing.LOOP, 0, speed); }
    private static Selection locomotion(State s) { return s.speed > MOVE_THRESHOLD ? loop("move", (float)Math.clamp(s.speed / .22, .4, 2.5)) : loop("idle", 1); }
}
