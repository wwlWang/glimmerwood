package cn.wwl.orimod.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Animation/HUD timeline, synchronized only on transitions. Never used as a damage source. */
public record CombatView(int actionId, String mode, int stage, long startedAt, double originY) {
    public CombatView(int actionId, String mode, int stage, long startedAt) { this(actionId, mode, stage, startedAt, 0); }
    public static final CombatView IDLE = new CombatView(0, "idle", 0, 0);
    public static final Codec<CombatView> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("action").forGetter(CombatView::actionId),
            Codec.STRING.fieldOf("mode").forGetter(CombatView::mode),
            Codec.INT.fieldOf("stage").forGetter(CombatView::stage),
            Codec.LONG.fieldOf("started").forGetter(CombatView::startedAt),
            Codec.DOUBLE.optionalFieldOf("origin_y", 0d).forGetter(CombatView::originY)).apply(instance, CombatView::new));
}
