package cn.wwl.orimod.client.render;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.block.TorchBlock;
import org.joml.Vector3f;

/** Contact in the centered item model, before its first/third-person display transform. */
final class SpiritItemGrip {
    private SpiritItemGrip() { }

    /** Match the eased procedural carry: raised tools face up/forward, one-handed weapons face down/forward.
     * Weight zero retains the authored aiming/attack/socket calibration. */
    static float attachmentPitch(float holdWeight, float lowered) {
        return -90 + (90 - 70 * Math.clamp(lowered, 0, 1)) * Math.clamp(holdWeight, 0, 1);
    }

    /** Shield's blocking display already pitches a vanilla arm; Ori's clip has posed that arm itself.
     * Cancel both orientations in palm space, retaining the animated handle position and body yaw/lean.
     * ShieldModel's own Y/Z flip then leaves its top upright and its decorated face toward body +Z.
     */
    static org.joml.Quaternionf shieldRotation(org.joml.Quaternionf palm, org.joml.Quaternionf display) {
        return new org.joml.Quaternionf(palm).invert().mul(new org.joml.Quaternionf(display).invert());
    }

    /** Paw rotation = item rotation in this view * inverse(item rotation relative to the grip bone). */
    static org.joml.Quaternionf palmFromItem(org.joml.Quaternionf itemInView, org.joml.Quaternionf itemInPalm) {
        return new org.joml.Quaternionf(itemInView).mul(new org.joml.Quaternionf(itemInPalm).invert()).normalize();
    }

    static boolean lowered(ItemStack item) {
        // Only small things are cradled at the chest; weapons (hammer and bows included) hang in one hand, which keeps the
        // weapon on the hand instead of floating in front of the face, and leaves aiming to the authored pose.
        return item.getItem() instanceof SwordItem || item.getItem() instanceof DiggerItem
                || item.is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR) || item.is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER)
                || item.getUseAnimation() == UseAnim.BOW || item.getUseAnimation() == UseAnim.CROSSBOW;
    }

    static void local(ItemStack item, Vector3f point, Vector3f axis) {
        point.set(0, -.16f, 0); axis.set(0,1,0);
        if (item.is(cn.wwl.orimod.item.OriItems.SPIRIT_CORE)) { point.set(0, -.18f, 0); return; }
        // R88: condensed-light hammer and spear (SpiritWeaponRenderer) are held low on the shaft, like a handheld tool;
        // their authored diagonal handles differ from the trident's flipped built-in grip below.
        if (item.is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER)) { point.set(-.2f, -.2f, 0); axis.set(1,1,0); return; }
        if (item.is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) { point.set(-.12f, -.12f, 0); axis.set(1,1,0); return; }
        if (item.getItem() instanceof DiggerItem || item.getItem() instanceof SwordItem) {
            point.set(-.125f, -.125f, 0); axis.set(1,1,0);
        } else if (item.getUseAnimation() == UseAnim.BOW) {
            point.set(-.21875f, .21875f, 0); axis.set(1,1,0);
        } else if (item.getUseAnimation() == UseAnim.CROSSBOW) {
            point.set(.21875f, -.21875f, 0); axis.set(-1,1,0);
        } else if (item.getItem() instanceof FishingRodItem) {
            point.set(-.21875f, -.21875f, 0); axis.set(1,1,0);
        } else if (item.getUseAnimation() == UseAnim.SPEAR) {
            // The built-in trident also uses the entity renderer's flipped Y/Z.
            point.set(-.5f, -1.25f, -.5f);
        } else if (item.getUseAnimation() == UseAnim.SPYGLASS) {
            point.set(0, .1875f, 0);
        } else if (item.getUseAnimation() == UseAnim.BLOCK) {
            // ShieldModel is a built-in entity: ItemRenderer first translates
            // by (-.5,-.5,-.5), then its renderer flips Y/Z. The handle centre
            // is (0,0,2/16) in model space, unlike a generated item quad.
            point.set(-.5f, -.5f, -.625f);
        } else if (item.getItem() instanceof BlockItem block && !(block.getBlock() instanceof TorchBlock)) {
            point.set(0, -.36f, 0);
        }
    }
}
