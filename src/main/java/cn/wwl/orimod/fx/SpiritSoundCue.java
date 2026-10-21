package cn.wwl.orimod.fx;

import java.util.EnumMap;
import java.util.Map;

/**
 * The formal sound table for spirit effects. Every kind is listed: {@code null} means the effect is
 * deliberately silent (trails, markers). Bursts play once on the server so every nearby client hears them
 * through the vanilla sound path and volume sliders; loops play on each client while a persistent effect lives.
 * Ids are vanilla sound events so no audio asset ships with the mod.
 */
public record SpiritSoundCue(String sound, float volume, float pitch, boolean loop) {
    private static final Map<SpiritFxKind, SpiritSoundCue> TABLE = new EnumMap<>(SpiritFxKind.class);
    static {
        TABLE.put(SpiritFxKind.BLADE_HIT, new SpiritSoundCue("minecraft:entity.player.attack.strong", .5f, 1f, false));
        TABLE.put(SpiritFxKind.STOMP_IMPACT, new SpiritSoundCue("minecraft:item.mace.smash_ground", .7f, 1.3f, false));
        TABLE.put(SpiritFxKind.ARROW_TRAIL, null);
        TABLE.put(SpiritFxKind.CHECKPOINT_REACHED, new SpiritSoundCue("minecraft:block.amethyst_block.chime", 1f, 1.2f, false));
        TABLE.put(SpiritFxKind.ROUTE_MARKER, null);
        TABLE.put(SpiritFxKind.DASH, new SpiritSoundCue("minecraft:entity.player.attack.sweep", .45f, 1.6f, false));
        TABLE.put(SpiritFxKind.DOUBLE_JUMP, new SpiritSoundCue("minecraft:entity.ender_dragon.flap", .25f, 1.8f, false));
        TABLE.put(SpiritFxKind.WALL_JUMP, new SpiritSoundCue("minecraft:block.amethyst_block.step", .6f, 1.2f, false));
        TABLE.put(SpiritFxKind.BASH, new SpiritSoundCue("minecraft:item.trident.riptide_1", .45f, 1.4f, false));
        TABLE.put(SpiritFxKind.GRAPPLE_HOOK, new SpiritSoundCue("minecraft:entity.fishing_bobber.throw", .6f, 1.2f, false));
        TABLE.put(SpiritFxKind.FORM_SWITCH, new SpiritSoundCue("minecraft:block.beacon.activate", .4f, 1.8f, false));
        TABLE.put(SpiritFxKind.GLIDE, new SpiritSoundCue("minecraft:item.elytra.flying", .2f, 1.3f, true));
        TABLE.put(SpiritFxKind.GRAPPLE_TETHER, null);
        TABLE.put(SpiritFxKind.SPIRIT_FLAME, new SpiritSoundCue("minecraft:entity.blaze.shoot", .3f, 1.9f, false));
        TABLE.put(SpiritFxKind.RACE_GHOST, null);
        TABLE.put(SpiritFxKind.LANTERN_IGNITE, new SpiritSoundCue("minecraft:block.amethyst_block.chime", .6f, 1.5f, false));
        TABLE.put(SpiritFxKind.BASH_AIM, new SpiritSoundCue("minecraft:block.beacon.ambient", .25f, 1.6f, true));
        TABLE.put(SpiritFxKind.FLASH, new SpiritSoundCue("minecraft:block.beacon.ambient", .3f, 1.6f, true));
        TABLE.put(SpiritFxKind.TREE_BLESSING, new SpiritSoundCue("minecraft:block.amethyst_block.resonate", 1f, 1.4f, false));
    }

    /** @return the cue, or {@code null} for a silent effect. */
    public static SpiritSoundCue of(SpiritFxKind kind) { return TABLE.get(kind); }
    /** @return whether the table has an explicit entry (silent or not) for the kind. */
    public static boolean listed(SpiritFxKind kind) { return TABLE.containsKey(kind); }
}
