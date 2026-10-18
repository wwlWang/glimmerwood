package cn.wwl.orimod.client.render;

import com.wf.gemrender.gltf.*;
import com.wf.gemrender.gltf.blend.AnimationBlend;
import com.wf.gemrender.gltf.blend.BlendEvaluator;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import com.mojang.blaze3d.vertex.PoseStack;

/** Solves hand contacts after blending, without changing the arm lengths or the player's movement. */
final class SpiritArmPose {
    private final NodeTable table;
    private final float[] state;
    private final Matrix4f[] world;
    private final Matrix4f local = new Matrix4f();
    private final BlendEvaluator.Scratch scratch = new BlendEvaluator.Scratch();
    private final Quaternionf restHead;
    private float contactError;

    SpiritArmPose(NodeTable table) {
        this.table = table;
        state = table.newScratch();
        world = new Matrix4f[table.nodeCount()];
        Arrays.setAll(world, i -> new Matrix4f());
        compose();
        restHead = rotation(slot("head"));
    }

    float contactError() { return contactError; }

    void bow(AnimationBlend blend, float pitch, float headYaw) {
        contactError = 0;
        float excess = pitch - Math.clamp(pitch, -25, 25);
        if (Math.abs(excess) < .001f) return;
        BlendEvaluator.evaluate(table, blend, state, scratch);
        compose();
        var left = new Matrix4f(world[slot("grip_r")]);
        var right = new Matrix4f(world[slot("grip_l")]);
        var leftPole = point("forearm_r").sub(point("upper_arm_r"));
        var rightPole = point("forearm_l").sub(point("upper_arm_l"));
        var shoulderCentre = point("upper_arm_l").add(point("upper_arm_r")).mul(.5f);
        var head = rotation(slot("head"));
        float yaw = (float)Math.toRadians(-headYaw);
        var correction = new Quaternionf().rotationY(yaw)
                .rotateX((float)Math.toRadians(-excess)).rotateY(-yaw);
        turn(slot("spine"), correction);
        var shift = point("upper_arm_l").add(point("upper_arm_r")).mul(.5f).sub(shoulderCentre);
        setWorldRotation(slot("head"), head);
        contact("l", right, shift, rightPole);
        contact("r", left, shift, leftPole);
        finish(blend);
    }

    void itemUse(AnimationBlend blend, Player player, ItemStack item, UseAnim use, boolean left,
                 float weight, float pitch, float headYaw) {
        BlendEvaluator.evaluate(table, blend, state, scratch);
        compose();
        contactError = 0;
        float radians = (float)Math.PI / 180;
        var view = new Quaternionf().rotationY(-headYaw * radians).rotateX(pitch * radians);
        if (use == UseAnim.SPYGLASS || use == UseAnim.CROSSBOW) {
            var torso = new Quaternionf().rotationY(-headYaw * radians)
                    .rotateX(Math.clamp(pitch, -25, 25) * radians);
            turn(slot("spine"), new Quaternionf().slerp(torso, weight));
            setWorldRotation(slot("head"), rotation(slot("head")).slerp(new Quaternionf(view).mul(restHead), weight));
        }
        var headDelta = rotation(slot("head")).mul(new Quaternionf(restHead).invert());
        if (use == UseAnim.CROSSBOW) {
            crossbow(player, item, left, weight, view);
            finish(blend);
            return;
        }
        var desiredItem = new Quaternionf(view).rotateX(use == UseAnim.SPYGLASS ? -(float)Math.PI / 2 : (float)Math.PI / 2);
        if (use == UseAnim.SPYGLASS) desiredItem.rotateY((float)Math.PI); // Palm below the barrel, wrist within reach.
        var displayPose = new PoseStack();
        var display = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
        // Vanilla's throwing predicate compares the live use stack by identity.
        var liveItem = player.isUsingItem() && player.getUseItem().getItem() == item.getItem() ? player.getUseItem() : item;
        var model = Minecraft.getInstance().getItemRenderer().getModel(liveItem, player.level(), player, player.getId());
        model.applyTransform(display, displayPose, left);
        var itemFromPalm = new Quaternionf().rotationX(-(float)Math.PI / 2).rotateY((float)Math.PI)
                .mul(displayPose.last().pose().getUnnormalizedRotation(new Quaternionf()));
        var palmRotation = desiredItem.mul(itemFromPalm.invert());
        String side = left ? "r" : "l";
        int grip = slot("grip_" + side);
        Vector3f target;
        if (use == UseAnim.SPYGLASS) {
            // The narrow ocular is at model Y=13.5/16. Grip the upper barrel,
            // keeping that ocular against the corresponding eye.
            target = point("head").add(headDelta.transform(new Vector3f(left ? .065f : -.065f, .11f, .10f)));
            target.add(new Quaternionf(view).transform(new Vector3f(0, 0, .1125f)));
        } else {
            target = point("head").add(new Quaternionf(view).transform(new Vector3f(left ? .18f : -.18f, .06f, -.07f)));
        }
        target = point(grip).lerp(target, weight);
        palmRotation = rotation(grip).slerp(palmRotation, weight);
        var targetMatrix = new Matrix4f().translationRotate(target, palmRotation);
        contact(side, targetMatrix, new Vector3f(), new Vector3f(left ? 1 : -1, -.4f, 0));
        finish(blend);
    }

    private void crossbow(Player player, ItemStack item, boolean left, float weight, Quaternionf view) {
        // The stock runs along model (-X,+Y); its flat limbs lie across the view.
        var itemRotation = new Quaternionf(view).rotateY(3 * (float)Math.PI / 4).rotateX(-(float)Math.PI / 2);
        String side = left ? "r" : "l", other = left ? "l" : "r";
        var desired = point("head").add(new Quaternionf(view).transform(new Vector3f(left ? .12f : -.12f, -.16f, .24f)));
        var palm = palmForItem(player, item, left, itemRotation);
        var target = point("grip_" + side).lerp(desired, weight);
        palm = rotation(slot("grip_" + side)).slerp(palm, weight);
        contact(side, new Matrix4f().translationRotate(target, palm), new Vector3f(), new Vector3f(left ? 1 : -1, -.7f, 0));

        boolean main = left == (player.getMainArm() == HumanoidArm.LEFT);
        if (!player.getItemInHand(main ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND).isEmpty()) return;
        boolean charging = player.isUsingItem() && player.getUseItem().getUseAnimation() == UseAnim.CROSSBOW;
        float pull = charging ? Math.clamp(player.getTicksUsingItem() / (float)CrossbowItem.getChargeDuration(item, player), 0, 1) : 0;
        // Follow the string towards the trigger while loading, then support the fore-stock.
        float forward = charging ? .24f * (1 - pull) + .05f : .20f;
        var support = desired.add(new Quaternionf(view).transform(new Vector3f(0, -.025f, forward)));
        support = point("grip_" + other).lerp(support, weight);
        var otherPalm = rotation(slot("grip_" + other)).slerp(palmForItem(player, item, !left, itemRotation), weight);
        contact(other, new Matrix4f().translationRotate(support, otherPalm), new Vector3f(), new Vector3f(left ? -1 : 1, -.7f, 0));
    }

    private Quaternionf palmForItem(Player player, ItemStack item, boolean left, Quaternionf itemRotation) {
        var poses = new PoseStack();
        var model = Minecraft.getInstance().getItemRenderer().getModel(item, player.level(), player, player.getId());
        model.applyTransform(left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, poses, left);
        var fromPalm = new Quaternionf().rotationX(-(float)Math.PI / 2).rotateY((float)Math.PI)
                .mul(poses.last().pose().getUnnormalizedRotation(new Quaternionf()));
        return new Quaternionf(itemRotation).mul(fromPalm.invert());
    }

    private void finish(AnimationBlend blend) {
        // The cache requires immutable value drivers. Never retain this solver's reused state.
        blend.clear().override(GltfAnimation.procedural("ori_arm_contacts", new Snapshot(state)), 0, 1);
    }

    private void contact(String side, Matrix4f target, Vector3f shift, Vector3f pole) {
        int upper = slot("upper_arm_" + side), lower = slot("forearm_" + side);
        int hand = slot("hand_" + side), grip = slot("grip_" + side);
        var gripRotation = target.getUnnormalizedRotation(new Quaternionf());
        var handRotation = rotation(hand);
        var relativeGrip = new Quaternionf(handRotation).invert().mul(rotation(grip));
        var offset = point(grip).sub(point(hand));
        new Quaternionf(handRotation).invert().transform(offset);
        handRotation.set(gripRotation).mul(relativeGrip.invert());
        var desired = target.getTranslation(new Vector3f()).add(shift);
        var wrist = new Vector3f(desired).sub(handRotation.transform(offset));
        var shoulder = point(upper);
        float a = shoulder.distance(point(lower)), b = point(lower).distance(point(hand));
        var direction = wrist.sub(shoulder);
        float rawDistance = direction.length();
        float distance = Math.clamp(rawDistance, Math.abs(a - b) + .0001f, a + b - .0001f);
        direction.div(Math.max(rawDistance, .00001f));
        float along = (a * a - b * b + distance * distance) / (2 * distance);
        float height = (float)Math.sqrt(Math.max(0, a * a - along * along));
        var bend = new Vector3f(pole).sub(new Vector3f(direction).mul(pole.dot(direction)));
        if (bend.lengthSquared() < .000001f) {
            bend.set(0, -1, 0).sub(new Vector3f(direction).mul(-direction.y));
            if (bend.lengthSquared() < .000001f) bend.set(1, 0, 0);
        }
        bend.normalize();
        var elbow = new Vector3f(shoulder).fma(along, direction).fma(height, bend);
        turn(upper, new Quaternionf().rotationTo(point(lower).sub(shoulder).normalize(), new Vector3f(elbow).sub(shoulder).normalize()));
        var actualElbow = point(lower);
        var clampedWrist = new Vector3f(shoulder).fma(distance, direction);
        turn(lower, new Quaternionf().rotationTo(point(hand).sub(actualElbow).normalize(), clampedWrist.sub(actualElbow).normalize()));
        setWorldRotation(hand, handRotation);
        contactError = Math.max(contactError, point(grip).distance(desired));
    }

    private int slot(String name) { return table.slotOfName(name); }
    private Vector3f point(String name) { return point(slot(name)); }
    private Vector3f point(int slot) { return world[slot].getTranslation(new Vector3f()); }
    private Quaternionf rotation(int slot) { return world[slot].getUnnormalizedRotation(new Quaternionf()); }

    private void turn(int slot, Quaternionf delta) { setWorldRotation(slot, delta.mul(rotation(slot))); }

    private void setWorldRotation(int slot, Quaternionf desired) {
        int parent = table.parentSlots()[slot];
        var relative = parent < 0 ? new Quaternionf(desired) : rotation(parent).invert().mul(desired);
        table.setRotation(state, slot, relative.normalize());
        compose();
    }

    private void compose() {
        for (int slot : table.evaluationOrder()) {
            int parent = table.parentSlots()[slot];
            table.localTransform(state, slot, local);
            if (parent < 0) world[slot].set(local);
            else world[slot].set(world[parent]).mul(local);
        }
    }

    private static final class Snapshot implements PoseDriver {
        private final float[] values;
        private final int hash;
        Snapshot(float[] state) { values = state.clone(); hash = Arrays.hashCode(values); }
        @Override public void apply(float time, float[] target) { System.arraycopy(values, 0, target, 0, values.length); }
        @Override public float cycleSeconds() { return 0; }
        @Override public boolean equals(Object other) { return other instanceof Snapshot that && Arrays.equals(values, that.values); }
        @Override public int hashCode() { return hash; }
    }
}
