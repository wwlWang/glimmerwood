package cn.wwl.orimod.spirit;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class SpiritForm {
    private SpiritForm() {}

    public static boolean toggle(ServerPlayer player) {
        if (!SpiritData.isAwakened(player)) return reject(player, "message.orimod.core_required");
        if (!player.isAlive() || player.isSpectator() || player.isSleeping() || player.isPassenger()) {
            return reject(player, "message.orimod.form_unavailable");
        }
        boolean becomingSpirit = !SpiritData.isSpirit(player);
        if (!becomingSpirit) {
            // Check a standing human before growing, so refreshDimensions cannot push the player out of a ceiling.
            EntityDimensions human = net.minecraft.world.entity.EntityType.PLAYER.getDimensions().scale(player.getScale());
            if (!player.level().noCollision(player, human.makeBoundingBox(player.position()).deflate(1.0E-5))) {
                return reject(player, "message.orimod.not_enough_space");
            }
        }
        apply(player, becomingSpirit);
        player.displayClientMessage(Component.translatable(becomingSpirit ? "message.orimod.spirit_form" : "message.orimod.human_form"), true);
        return true;
    }

    /** Drop spirit silhouette when the player becomes a spectator (hardcore death) without clearing awakening. */
    public static void idleIfSpectator(ServerPlayer player) {
        if (!player.isSpectator() || !SpiritData.isSpirit(player)) return;
        apply(player, false);
    }

    private static void apply(ServerPlayer player, boolean becomingSpirit) {
        cn.wwl.orimod.combat.SpiritCombat.cancel(player, "form");
        cn.wwl.orimod.progression.Regenerate.release(player);
        cn.wwl.orimod.movement.VanillaMotion.clear(player);
        player.setData(SpiritData.FORM, becomingSpirit);
        if (becomingSpirit) cn.wwl.orimod.progression.StoryProgress.award(player, cn.wwl.orimod.progression.StoryProgress.Step.SPIRIT);
        cn.wwl.orimod.progression.DeepBreath.update(player);
        cn.wwl.orimod.movement.FeatherGlide.formChanged(player);
        player.refreshDimensions();
        cn.wwl.orimod.fx.SpiritFxLifecycle.formChanged(player);
    }

    public static void tick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer server) idleIfSpectator(server);
        EntityDimensions desired = player.getDimensions(player.getPose());
        // Attachments synchronize independently of vanilla pose packets; reconcile on both sides.
        if (Math.abs(player.getBbHeight() - desired.height()) > 0.001f
                || Math.abs(player.getBbWidth() - desired.width()) > 0.001f) player.refreshDimensions();
    }

    public static EntityDimensions dimensions(Pose pose, EntityDimensions vanilla) {
        return switch (pose) {
            case STANDING -> vanilla.scale(1, 1.2f / vanilla.height()).withEyeHeight(1.04f);
            case CROUCHING -> vanilla.scale(1, 1.0f / vanilla.height()).withEyeHeight(0.85f);
            default -> vanilla; // Other poses keep their vanilla rules; LivingEntity applies entity scale afterwards.
        };
    }

    private static boolean reject(Player player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        return false;
    }
}
