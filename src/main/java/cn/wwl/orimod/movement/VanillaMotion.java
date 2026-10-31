package cn.wwl.orimod.movement;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Per-life ownership of a vanilla impulse, including its flight after the spin flag ends. */
public final class VanillaMotion {
    private VanillaMotion() {}
    public static final class State {
        private boolean riptide, airborne;
        private int started;
        private Level level;
        public void clear() { riptide = airborne = false; level = null; }
    }
    /** Called on BOTH sides by Player.startAutoSpinAttack, before the client receives shared flag 4. */
    public static void startRiptide(Player player) {
        if (!SpiritData.isSpirit(player)) return;
        SlimeBounce.clear(player);
        var movement = player.getData(SpiritData.MOVEMENT);
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            var impulse = player.getDeltaMovement();
            cn.wwl.orimod.combat.SpiritCombat.cancel(server, "riptide");
            SpiritMovement.cancel(server, movement);
            player.setDeltaMovement(impulse);
        }
        var state = movement.vanillaMotion;
        state.riptide = true; state.airborne = false;
        state.started = player.tickCount; state.level = player.level();
        movement.jumps.clear(); movement.predictedJumps.clear();
        movement.active = false; movement.mode = movement.predictedMode = MoveMode.IDLE; movement.activeTicks = 0;
    }
    public static boolean riptide(Player player) {
        var state = player.getData(SpiritData.MOVEMENT).vanillaMotion;
        if (!state.riptide) return false;
        int age = player.tickCount - state.started;
        if (!SpiritData.isSpirit(player) || !player.isAlive() || player.isSpectator() || player.isSleeping()
                || player.isPassenger() || player.level() != state.level || player.getAbilities().flying
                || player.isFallFlying() || player.onClimbable() || age < 0
                || age > 1 && (player.isInWaterOrBubble() || player.isInLava())
                || player.onGround() && (state.airborne || age > 2)) {
            state.clear(); return false;
        }
        if (!player.onGround()) state.airborne = true;
        return true;
    }
    public static void clear(Player player) { SlimeBounce.clear(player); player.getData(SpiritData.MOVEMENT).vanillaMotion.clear(); }
}
