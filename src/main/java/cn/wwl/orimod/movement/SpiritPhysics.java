package cn.wwl.orimod.movement;

import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/** Attribute adapters for ordinary Ori motion. Ability impulses and human travel never use these multipliers. */
public final class SpiritPhysics {
    private static final ResourceLocation SPRINTING = ResourceLocation.withDefaultNamespace("sprinting");
    public static final double MIN_SPEED_MULTIPLIER = .15;
    private SpiritPhysics() {}

    public static double speedMultiplier(Player player) {
        var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        double value = speed.getValue();
        if (speed.hasModifier(SPRINTING)) {
            // Recompute, rather than temporarily mutate a synced attribute or divide an already-clamped value.
            double base = speed.getBaseValue();
            for (var modifier : speed.getModifiers())
                if (!modifier.id().equals(SPRINTING) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) base += modifier.amount();
            value = base;
            for (var modifier : speed.getModifiers())
                if (!modifier.id().equals(SPRINTING) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) value += base * modifier.amount();
            for (var modifier : speed.getModifiers())
                if (!modifier.id().equals(SPRINTING) && modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) value *= 1 + modifier.amount();
            value = speed.getAttribute().value().sanitizeValue(value);
        }
        return Math.max(MIN_SPEED_MULTIPLIER, value / .1);
    }

    public static final class Vertical {
        private boolean pending, held, released, shape;
        private double minimum;
        public void clear() { pending = false; }
    }
    public static void prepareVertical(Player player, boolean held, boolean released, boolean shape, double minimum) {
        var v = player.getData(SpiritData.MOVEMENT).vertical;
        v.pending = true; v.held = held; v.released = released; v.shape = shape; v.minimum = minimum;
    }
    /** The travel hook passes vanilla's actual gravity AFTER its slow-falling adjustment. */
    public static void beforeTravel(Player player, double gravity) {
        SlimeBounce.observeFall(player);
        var v = player.getData(SpiritData.MOVEMENT).vertical;
        if (!v.pending) return;
        v.pending = false;
        if (!SpiritData.isSpirit(player) || VanillaMotion.riptide(player)) return;
        var motion = player.getDeltaMovement();
        double y = motion.y;
        if (v.shape && !SlimeBounce.active(player) && !(player.onGround() && y <= 0) && gravity > 0
                // Slow Falling is a vanilla travel rule, not an attribute modifier in 1.21.
                // Let that path own descent instead of adding Ori's heavy fall or terminal-speed compensation.
                && !(y <= 0 && player.hasEffect(MobEffects.SLOW_FALLING)))
            y = FeelTuning.vertical(y, v.held, v.released, gravity);
        player.setDeltaMovement(motion.x, Math.max(y, v.minimum), motion.z);
    }
}
