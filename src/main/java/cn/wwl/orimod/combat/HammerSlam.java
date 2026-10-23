package cn.wwl.orimod.combat;

import cn.wwl.orimod.item.SpiritHammerItem;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Air hammer is a falling action, not a delayed explosion at its release position. */
public final class HammerSlam {
    public static final String MODE = "hammer_slam";
    public static final int TIMEOUT = 200;
    public static final double FALL_SPEED = -1.2;
    private HammerSlam() {}

    public enum End { FALLING, LAND, CANCEL }
    public static End end(boolean grounded, boolean wet, boolean outsideWorld, boolean available, long age) {
        if (!available || wet || outsideWorld || age < 0 || age >= TIMEOUT) return End.CANCEL;
        return grounded ? End.LAND : End.FALLING;
    }

    public static boolean falling(CombatView view, long now) {
        return MODE.equals(view.mode()) && now >= view.startedAt() && now - view.startedAt() < TIMEOUT;
    }

    public static Vec3 velocity(Vec3 current) {
        return new Vec3(current.x, Math.min(FALL_SPEED, current.y), current.z);
    }

    public static void release(ServerPlayer player) {
        if (SpiritMovement.grounded(player)) { SpiritHammerItem.smash(player); return; }
        // Swimming has no fall to ride: strike in place, as before air slams existed (energy is already spent).
        if (cn.wwl.orimod.block.OriFluids.inWater(player) || player.isInLava()) { SpiritHammerItem.smash(player); return; }
        SpiritCombat.cancel(player, "hammer");
        SpiritMovement.cancel(player, player.getData(SpiritData.MOVEMENT));
        var state = player.getData(SpiritData.COMBAT);
        state.mode = MODE; state.stage = 0; state.startedAt = player.level().getGameTime();
        state.weapon = player.getUseItem();
        player.setData(SpiritData.COMBAT_VIEW, new CombatView(++state.actionId, MODE, 0, state.startedAt));
        player.setDeltaMovement(velocity(player.getDeltaMovement().multiply(.3, 1, .3)));
        player.hurtMarked = true;
    }

    public static void tick(ServerPlayer player) {
        var state = player.getData(SpiritData.COMBAT);
        End end = end(SpiritMovement.grounded(player), cn.wwl.orimod.block.OriFluids.inWater(player) || player.isInLava(),
                player.getY() < player.level().getMinBuildHeight(), SpiritCombat.available(player)
                        && !player.getAbilities().flying && !player.isFallFlying(), player.level().getGameTime() - state.startedAt);
        if (end == End.FALLING) { player.setDeltaMovement(velocity(player.getDeltaMovement())); return; }
        // Clear/sync first: impact damage callbacks may trigger other actions. It can resolve only once.
        SpiritCombat.cancel(player, end == End.LAND ? "hammer_land" : "hammer_cancel");
        if (end == End.LAND) SpiritHammerItem.smash(player);
    }
}
