package cn.wwl.orimod.client.render;

import cn.wwl.orimod.network.AnimationActionPayload;
import cn.wwl.orimod.network.AnimationActionPayload.Action;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/** Short-lived visual observations keyed by the actual client entity (never retained across respawn/world changes). */
public final class SpiritAnimationEvents {
    private SpiritAnimationEvents() {}
    public static final class Observed {
        public Action action;
        public boolean offhand;
        public float actionAt = -100, castUntil = -100, castStarted = -100, castRelease = -100;
        public int hurtSequence;
        public float hurtAt = -100, healthBefore;
        public Vec3 hurtFrom;
    }
    private static final java.util.Map<Player, Observed> STATES = new java.util.WeakHashMap<>();
    public static Observed of(Player player) { return STATES.computeIfAbsent(player, p -> new Observed()); }
    public static void local(Action action, InteractionHand hand) {
        var player = Minecraft.getInstance().player;
        if (player == null || !cn.wwl.orimod.spirit.SpiritData.isSpirit(player)) return;
        var state = of(player);
        float now = player.tickCount / 20f;
        // Continuous mining invokes its input path every tick; an individual swing remains a full clip.
        if (state.action == action && now - state.actionAt < (action == Action.MINE ? .28f : .05f)) return;
        accept(player, action, hand == InteractionHand.OFF_HAND);
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new AnimationActionPayload(-1, action, hand == InteractionHand.OFF_HAND));
    }
    public static void receive(AnimationActionPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(payload.entity()) instanceof Player p) accept(p, payload.action(), payload.offhand());
    }
    private static void accept(Player player, Action action, boolean offhand) {
        var s = of(player); float now = player.tickCount / 20f;
        switch (action) {
            case CAST_HOLD -> { if (now >= s.castUntil) s.castStarted = now; s.castUntil = now + 1; }
            case CAST_STOP -> s.castUntil = -100;
            case CAST_RELEASE -> { s.castUntil = -100; s.castRelease = now; }
            default -> { s.action = action; s.actionAt = now; s.offhand = offhand; }
        }
    }
    /** Vanilla damage packets run on the client thread, including while hurtTime is already at its maximum. */
    public static void hurt(Player player, DamageSource source) {
        var s = of(player); s.hurtSequence++; s.hurtAt = player.tickCount / 20f;
        s.healthBefore = player.getHealth(); s.hurtFrom = source.getSourcePosition();
    }
}
