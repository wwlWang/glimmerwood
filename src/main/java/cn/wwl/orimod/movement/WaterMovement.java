package cn.wwl.orimod.movement;

import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * R56 Water Dash (WotW) and clean breath (BF "Clean Water"): with the ability learned, spirit-form Ori swims
 * faster, never runs out of air, and the dash key bursts along the look direction under water. Displacement is
 * client-side like the rest of Ori's movement; the server keeps the cooldown, air and FX.
 */
public final class WaterMovement {
    public static final double DASH_SPEED = .95, SWIM_BOOST = .03, SWIM_MAX = .55;
    public static final int DASH_COOLDOWN = 14;

    public static boolean active(Player player) {
        return SpiritData.isSpirit(player) && Ability.WATER_DASH.unlocked(player) && cn.wwl.orimod.block.OriFluids.inWater(player) && !player.isSpectator();
    }

    public static Vec3 dashVelocity(Vec3 look) { return look.normalize().scale(DASH_SPEED); }

    /** Swim boost toward the look direction while moving forward, capped at {@link #SWIM_MAX}. */
    public static Vec3 swimVelocity(Vec3 velocity, Vec3 look, boolean forward) {
        if (!forward) return velocity;
        var boosted = velocity.add(look.normalize().scale(SWIM_BOOST));
        return boosted.length() > SWIM_MAX && boosted.length() > velocity.length() ? velocity : boosted;
    }

    /** Server: air never runs out. */
    public static void breathe(ServerPlayer player) {
        if (active(player) && player.getAirSupply() < player.getMaxAirSupply()) player.setAirSupply(player.getMaxAirSupply());
    }

    /** Server: accept a water dash (cooldown) and play its FX. @return accepted. */
    public static boolean dash(ServerPlayer player) {
        if (!active(player)) return false;
        var state = player.getData(SpiritData.ENERGY_STATE);
        long now = player.level().getGameTime();
        if (now < state.waterDashReadyAt) return false;
        state.waterDashReadyAt = now + DASH_COOLDOWN;
        cn.wwl.orimod.fx.SpiritFx.emit((net.minecraft.server.level.ServerLevel) player.level(), cn.wwl.orimod.fx.SpiritFxKind.DASH, player.getX(), player.getY() + .5, player.getZ(), null);
        player.level().playSound(null, player.blockPosition(), SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, SoundSource.PLAYERS, 1.2f, 1.3f);
        return true;
    }

    private WaterMovement() {}
}
