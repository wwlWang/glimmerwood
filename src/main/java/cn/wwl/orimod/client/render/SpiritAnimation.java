package cn.wwl.orimod.client.render;

import cn.wwl.orimod.combat.SpiritCombat;
import cn.wwl.orimod.spirit.SpiritData;
import com.wf.gemrender.gltf.*;
import com.wf.gemrender.gltf.blend.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Visual observations only. No clip changes velocity, damage windows or movement charges. */
final class SpiritAnimation {
    private final Player player;
    private GemRenderGltfModel loaded;
    private Crossfade base;
    private BlendMask arms, rightArm, leftArm, upperBody, belowArms, belowRight, belowLeft, belowUpper, headMask;
    private AdditiveReference rest;
    private GltfAnimation lookYaw, lookPitch, aimYaw, aimPitch, earLag, earPitch;
    // R74 procedural feel: facing, body lean, squash-stretch, ear/tail springs, breathing.
    private final cn.wwl.orimod.movement.FeelTuning.Spring leanPitch = new cn.wwl.orimod.movement.FeelTuning.Spring(0, cn.wwl.orimod.movement.FeelTuning.POSE_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.POSE_DAMPING);
    private final cn.wwl.orimod.movement.FeelTuning.Spring leanRoll = new cn.wwl.orimod.movement.FeelTuning.Spring(0, cn.wwl.orimod.movement.FeelTuning.POSE_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.POSE_DAMPING);
    private final cn.wwl.orimod.movement.FeelTuning.Spring squash = new cn.wwl.orimod.movement.FeelTuning.Spring(1, cn.wwl.orimod.movement.FeelTuning.SQUASH_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.SQUASH_DAMPING);
    private final cn.wwl.orimod.movement.FeelTuning.Spring earYaw = new cn.wwl.orimod.movement.FeelTuning.Spring(0, cn.wwl.orimod.movement.FeelTuning.EAR_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.EAR_DAMPING);
    private final cn.wwl.orimod.movement.FeelTuning.Spring earFall = new cn.wwl.orimod.movement.FeelTuning.Spring(0, cn.wwl.orimod.movement.FeelTuning.EAR_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.EAR_DAMPING);
    private float facing = Float.NaN, smoothSpeed, lastSpeedForAccel;
    private boolean freeFacing;
    private SpiritArmPose armPose;
    private ItemStack posedItem = ItemStack.EMPTY;
    private UseAnim posedUse = UseAnim.NONE;
    private float itemPoseWeight;
    private boolean posedLeft;
    private float lastNow = -1, clock, createdAt, shotAt = -100, shotUntil = -100, lastHurtAt = -100;
    private float previousYaw, lag, speed, lastVertical, lastMovingAt = -100;
    private int lastMoveId, lastPrediction, lastHurt;
    private boolean grounded, wasMoving, wasBow, bowLeft, runningGait;
    private float locoWeight, locoPhase, gaitWalk = 1, gaitRun, gaitSprint;
    float[] gait() { return new float[] {gaitWalk * locoWeight, gaitRun * locoWeight, gaitSprint * locoWeight}; }
    private float bowReleasedAt = -100, selectedPlaySpeed = 1;
    private Vec3 position;
    private String previousMode = "idle", selected = "", shot = "", overlay = "";
    private float selectedClock, clipTime;
    private String baseClipName = "", overlayClipName = "";

    SpiritAnimation(Player player) { this.player = player; }

    String selected() { return selected; }
    String overlay() { return overlay; }
    float clipTime() { return clipTime; }
    /** R74: model yaw (degrees): follows the velocity while running, else the vanilla body yaw. */
    float facing(float vanillaYaw) { return freeFacing && Float.isFinite(facing) ? facing : vanillaYaw; }
    float leanPitch() { return leanPitch.value; }
    float leanRoll() { return leanRoll.value; }
    float squash() { return squash.value; }
    float speed() { return speed; }
    String baseClip() { return baseClipName; }
    String overlayClip() { return overlayClipName; }
    java.util.List<String> missingClips() { return loaded == null ? java.util.List.of() : SpiritClips.missing(loaded); }
    float aimContactError() { return armPose == null || !overlay.startsWith("bow_") && itemPoseWeight == 0 ? 0 : armPose.contactError(); }

    void write(GemRenderGltfModel model, float partial, AnimationBlend blend) {
        float now = (player.tickCount + partial) / 20f;
        if (model != loaded || now < lastNow) reset(model, now, partial);
        createdAt = SpiritAppearance.enteredAt(player);
        var movement = player.getData(SpiritData.MOVEMENT);
        Vec3 at = player.getPosition(partial);
        boolean discontinuity = now - lastNow > .25f || position != null && at.distanceToSqr(position) > 9;
        if (discontinuity) {
            position = null; shotUntil = -100; lag = speed = 0;
            previousYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
            lastMoveId = movement.actionId; lastPrediction = movement.predictedJumpCount; previousMode = movement.mode.id();
            grounded = player.onGround(); wasMoving = runningGait = false; lastHurt = player.hurtTime;
            wasBow = false; bowReleasedAt = -100;
            itemPoseWeight = 0;
        }
        float dt = Math.clamp(now - lastNow, 0, .1f);
        double distance = position == null ? 0 : at.subtract(position).horizontalDistance();
        float observedSpeed = dt > .00001f ? (float)(distance / dt) : speed;
        if (dt > 0) speed = Mth.lerp(1 - (float)Math.exp(-dt * 18), speed, observedSpeed);
        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float angular = 0;
        if (dt > 0) {
            angular = Mth.wrapDegrees(yaw - previousYaw) / dt;
            lag = Mth.lerp(1 - (float)Math.exp(-dt * 9), lag, Math.clamp(-angular * .0013f, -.17f, .17f));
        }
        previousYaw = yaw;
        String mode = movement.mode.id();
        feel(at, yaw, angular, dt, mode, discontinuity);
        boolean newPrediction = movement.predictedJumpCount != lastPrediction;
        boolean newAction = movement.actionId != lastMoveId;
        if (newPrediction || newAction) {
            String action = newPrediction ? movement.lastAction.id() : mode;
            switch (action) {
                case "double_jump" -> { if (trigger("double_jump", now, .5f)) squash.kick(cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP * .8f); }
                case "wall_jump" -> trigger(movement.left < 0 ? "wall_jump_r" : "wall_jump_l", now, .4f);
                case "bash_launch" -> trigger("bash_launch", now, .4f);
                case "jump" -> { if (trigger("jump_start", now, .15f)) squash.kick(cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP); }
            }
        }
        if (!mode.equals(previousMode)) {
            if (previousMode.equals("dash") && !mode.equals("stomp")) trigger("dash_end", now, .25f);
            if (mode.equals("glide")) trigger("glide_open", now, .25f);
            else if (previousMode.equals("glide") && !player.onGround()) trigger("glide_close", now, .25f);
        }
        if (player.hurtTime > lastHurt) { lastHurtAt = now; shotUntil = -100; }
        if (player.onGround() && !grounded && !player.isInWater()) {
            if (trigger(lastVertical < -.6f ? "land_heavy" : "land_light", now, lastVertical < -.6f ? .5f : .3f)) squash.kick(-cn.wwl.orimod.movement.FeelTuning.SQUASH_LAND * Math.min(1.6f, .4f + Math.abs(lastVertical)));
        }
        boolean moving = speed > .22f;
        // A single interpolation spike must not reset a slow walk to a run and back.
        runningGait = speed > (runningGait ? 1.8f : 2.3f);
        if (moving) lastMovingAt = now;
        if (!moving && wasMoving && player.onGround()) trigger("stop", now, .30f);
        if (moving && !wasMoving && player.onGround()) trigger("start", now, .12f);
        if (!moving && player.onGround() && now >= shotUntil && Math.abs(angular) > 75)
            trigger(angular > 0 ? "turn_right" : "turn_left", now, .5f);

        String next;
        boolean special = true;
        if (!player.isAlive()) next = "death";
        else if (!SpiritData.isSpirit(player)) next = "transform_out";
        else if (player.isSleeping()) next = "sleep";
        else if (player.isPassenger()) next = "sit";
        else if (player.isAutoSpinAttack()) next = "swim";
        else if (now - lastHurtAt < .3f) next = "hurt";
        else if (now - lastHurtAt < .55f) next = "recover";
        else if (player.isVisuallySwimming() || player.isFallFlying()) next = "swim";
        else if (player.onClimbable()) next = "climb";
        else if (mode.equals("bash_aim")) next = "bash_aim";
        else if (mode.equals("grapple")) next = "grapple";
        else if (mode.equals("stomp")) next = "stomp";
        else if (mode.equals("dash")) next = "dash_start";
        else if (mode.equals("wall_slide")) next = Math.abs(player.getDeltaMovement().y) < .03 ? "wall_hold" : "wall_slide";
        else if (now < shotUntil) next = shot;
        else if (mode.equals("glide")) next = "glide";
        else if (!player.onGround() && !player.isInWater()) {
            special = false;
            double dy = player.getDeltaMovement().y;
            next = dy > .07 ? "rise" : dy < -.07 ? "fall" : "apex";
        } else {
            special = false;
            if (now - createdAt < .6f) next = "transform_in";
            else if (player.isCrouching()) next = "crouch";
            else if (moving) next = "walk";   // R80: one locomotion base; run/sprint are weighted on top (gait blend)
            else next = now - lastMovingAt > 9 ? "alert" : "idle";
        }
        GltfAnimation nextClip = SpiritClips.get(model, next);
        baseClipName = nextClip.name();
        float rate = nextClip.name().equals(next) ? switch (next) {
            case "walk" -> speed * nextClip.duration() / cn.wwl.orimod.movement.FeelTuning.stride(speed);
            case "run" -> speed * nextClip.duration() / 2f;
            case "climb" -> Math.clamp((float)Math.abs(player.getDeltaMovement().y) * 10, .2f, 2);
            default -> 1;
        } : 1;
        clock += dt * Math.max(.05f, rate);
        if (!next.equals(selected)) {
            float duration = nextClip.duration();
            float playSpeed = !nextClip.name().equals(next) ? 1 : next.equals("start") ? duration / .12f : next.equals("jump_start") ? duration / .15f : 1;
            float fade = next.equals("double_jump") || next.equals("jump_start") || next.equals("dash_start") ? cn.wwl.orimod.movement.FeelTuning.ANIM_CROSSFADE_FAST : cn.wwl.orimod.movement.FeelTuning.ANIM_CROSSFADE;
            base.play(nextClip, clock, fade, FadeCurve.SMOOTH, 0, playSpeed, loops(nextClip.name()));
            selected = next; selectedClock = clock; selectedPlaySpeed = playSpeed;
        }
        float elapsedClip = (clock - selectedClock) * selectedPlaySpeed;
        clipTime = loops(nextClip.name()) ? nextClip.loop(elapsedClip) : Math.min(nextClip.duration(), elapsedClip);

        var combat = player.getData(SpiritData.COMBAT_VIEW);
        overlay = "";
        overlayClipName = "";
        float overlayTime = 0;
        BlendMask mask = null, below = null;
        boolean mainLeft = player.getMainArm() == HumanoidArm.LEFT;
        boolean useLeft = player.getUsedItemHand() == InteractionHand.MAIN_HAND ? mainLeft : !mainLeft;
        UseAnim use = player.isUsingItem() ? player.getUseItem().getUseAnimation() : UseAnim.NONE;
        boolean bow = use == UseAnim.BOW;
        if (wasBow && !bow) bowReleasedAt = now;
        if (bow) bowLeft = useLeft;
        wasBow = bow;
        boolean overlayLeft = false;
        boolean handsAvailable = !special || switch (next) {
            case "start", "stop", "turn_left", "turn_right", "jump_start", "land_light", "land_heavy" -> true;
            default -> false;
        };
        ItemStack contactItem = player.getUseItem();
        UseAnim contactAnimation = use;
        boolean contactLeft = useLeft;
        if (!player.isUsingItem()) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(held)) {
                    contactItem = held; contactAnimation = UseAnim.CROSSBOW;
                    contactLeft = hand == InteractionHand.MAIN_HAND ? mainLeft : !mainLeft;
                    break;
                }
            }
        }
        // R87: spirit hammer/spear use SPEAR UseAnim but must not drive vanilla humanoid arm posing — use our overlays.
        boolean spiritWeapon = contactItem.is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER) || contactItem.is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)
                || contactItem.is(cn.wwl.orimod.item.OriItems.SPIRIT_BLADE) || contactItem.is(cn.wwl.orimod.item.OriItems.SPIRIT_BOW);
        boolean contactUse = handsAvailable && !spiritWeapon && (contactAnimation == UseAnim.SPYGLASS
                || contactAnimation == UseAnim.SPEAR || contactAnimation == UseAnim.CROSSBOW);
        if (contactUse) {
            if (posedUse != contactAnimation || posedLeft != contactLeft || posedItem.isEmpty()) {
                posedUse = contactAnimation; posedLeft = contactLeft; itemPoseWeight = 0;
            }
            posedItem = contactItem.copy();
            itemPoseWeight = Math.min(1, itemPoseWeight + dt / .15f);
        } else itemPoseWeight = handsAvailable ? Math.max(0, itemPoseWeight - dt / .2f) : 0;
        if (handsAvailable) {
            if (combat.mode().equals("blade")) {
                overlay = handed("blade_" + Math.clamp(combat.stage(), 1, 3), mainLeft);
                float elapsed = (player.level().getGameTime() - combat.startedAt() + partial) / 20f;
                overlayTime = elapsed * SpiritClips.get(model, overlay).duration() / (SpiritCombat.duration(combat.stage()) / 20f);
                mask = upperBody; below = belowUpper;
            } else if (player.isUsingItem()) {
                float elapsed = (player.getTicksUsingItem() + partial) / 20f;
                if (use == UseAnim.CROSSBOW) {
                    overlay = handed("hold", useLeft); overlayTime = now;
                    mask = useLeft ? leftArm : rightArm; below = useLeft ? belowLeft : belowRight;
                } else if (bow) {
                    overlay = handed(elapsed < 1 ? "bow_charge" : "bow_hold", useLeft);
                    overlayTime = elapsed < 1 ? elapsed : elapsed - 1;
                    mask = upperBody; below = belowUpper;
                } else if (player.getUseItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER) || player.getUseItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) {
                    // Wind-up / throw: full upper-body clip so sprint gait on the legs stays intact.
                    overlay = handed(player.getUseItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER) ? "blade_3" : "use_item", useLeft);
                    overlayTime = Math.min(now, SpiritClips.get(model, overlay).duration() * .95f);
                    mask = upperBody; below = belowUpper;
                } else {
                    overlay = handed(use == UseAnim.BLOCK ? "block" : "use_item", useLeft); overlayTime = now; overlayLeft = useLeft;
                    mask = useLeft ? leftArm : rightArm; below = useLeft ? belowLeft : belowRight;
                }
            } else if (now - bowReleasedAt < .3f) {
                overlay = handed("bow_release", bowLeft); overlayTime = now - bowReleasedAt;
                mask = upperBody; below = belowUpper;
            } else if (player.getAttackAnim(partial) > .001f) {
                overlayLeft = player.swingingArm == InteractionHand.OFF_HAND ? !mainLeft : mainLeft;
                overlay = handed("blade_1", overlayLeft);
                overlayTime = player.getAttackAnim(partial) * SpiritClips.get(model, overlay).duration();
                mask = overlayLeft ? leftArm : rightArm; below = overlayLeft ? belowLeft : belowRight;
            }
        }
        boolean holdLeft = handsAvailable && !(mainLeft ? player.getMainHandItem() : player.getOffhandItem()).isEmpty()
                && mask != upperBody && mask != leftArm;
        boolean holdRight = handsAvailable && !(mainLeft ? player.getOffhandItem() : player.getMainHandItem()).isEmpty()
                && mask != upperBody && mask != rightArm;
        boolean leftCovered = holdLeft || mask == leftArm, rightCovered = holdRight || mask == rightArm;
        if (mask != upperBody) below = leftCovered && rightCovered ? belowArms : leftCovered ? belowLeft : rightCovered ? belowRight : null;
        if (below == null) base.write(clock, blend);
        else base.write(clock, blend, 1, below);
        // R80: walk/run/sprint blend by speed. The run clip follows the walk's phase, weighted by speed and by how much
        // of the base is locomotion right now (fades with the base crossfade).
        var gait = cn.wwl.orimod.movement.FeelTuning.gaitWeights(speed);
        locoWeight = cn.wwl.orimod.movement.FeelTuning.approach(locoWeight, selected.equals("walk") ? 1 : 0, dt / cn.wwl.orimod.movement.FeelTuning.ANIM_CROSSFADE);
        gaitWalk = gait[0]; gaitRun = gait[1]; gaitSprint = gait[2];
        float runWeight = (gaitRun + gaitSprint) * locoWeight;
        if (runWeight > .002f) {
            GltfAnimation walkClip = SpiritClips.get(model, "walk"), runClip = SpiritClips.get(model, "run");
            float phase = walkClip.duration() > 0 ? walkClip.loop((clock - selectedClock) * selectedPlaySpeed) / walkClip.duration() : 0;
            if (selected.equals("walk")) { locoPhase = phase; }
            // R87: gait always stays below the spine so blade/bow/hammer overlays keep the upper body.
            var gaitMask = below != null ? below : belowUpper;
            blend.override(runClip, runClip.loop(locoPhase * runClip.duration()), runWeight, gaitMask);
        }
        if (mask != null) {
            GltfAnimation action = SpiritClips.get(model, overlay);
            overlayClipName = action.name();
            float time = loops(action.name()) ? action.loop(overlayTime) : Math.clamp(overlayTime, 0, Math.max(0, action.duration() - .0001f));
            blend.override(action, time, 1, mask);
        }
        if (holdLeft) hold(model, blend, now, true);
        if (holdRight) hold(model, blend, now, false);
        if (overlay.isEmpty()) overlay = holdLeft && holdRight ? "hold_both" : holdLeft ? "hold_left" : holdRight ? "hold" : "";
        if (!next.equals("death") && !next.equals("sleep") && !next.equals("double_jump")) {
            float headYaw = Math.clamp(Mth.wrapDegrees(Mth.rotLerp(partial, player.yHeadRotO, player.yHeadRot) - facing(yaw)), -45, 45);
            boolean aiming = overlay.startsWith("bow_");
            float pitch = Math.clamp(player.getViewXRot(partial), aiming ? -85 : -40, aiming ? 85 : 45);
            // Undo the clip's torso twist before aiming, then restore it. Otherwise
            // its rotated pitch axis also pulls the arrow sideways at steep angles.
            float authoredYaw = 0;
            float aimWeight = 1;
            if (aiming) {
                float t = overlay.startsWith("bow_charge") ? Math.clamp(overlayTime, 0, 1)
                        : overlay.startsWith("bow_release") ? 1 - Math.clamp(overlayTime / .3f, 0, 1) : 1;
                float amount = t * t * (3 - 2 * t);
                authoredYaw = (overlay.endsWith("_left") ? .12f : -.12f) * amount;
                float fade = overlay.startsWith("bow_charge") ? Math.clamp(overlayTime / .18f, 0, 1)
                        : overlay.startsWith("bow_release") ? 1 - Math.clamp(overlayTime / .3f, 0, 1) : 1;
                aimWeight = fade * fade * (3 - 2 * fade);
            }
            blend.additive(aiming ? aimYaw : lookYaw, -headYaw * aimWeight * Mth.DEG_TO_RAD - authoredYaw, 1,
                    aiming ? upperBody : headMask, rest);
            blend.additive(aiming ? aimPitch : lookPitch, pitch * aimWeight * Mth.DEG_TO_RAD, 1,
                    aiming ? upperBody : headMask, rest);
            if (aiming) {
                blend.additive(aimYaw, authoredYaw, 1, upperBody, rest);
                blend.additive(lookYaw, -headYaw * (1 - aimWeight) * Mth.DEG_TO_RAD, 1, headMask, rest);
                blend.additive(lookPitch, Math.clamp(pitch, -40, 45) * (1 - aimWeight) * Mth.DEG_TO_RAD, 1, headMask, rest);
            }
            blend.additive(earLag, lag + earYaw.value, 1, BlendMask.ALL, rest);
            blend.additive(earPitch, earFall.value, 1, BlendMask.ALL, rest);
            float breath = Mth.sin(now * Mth.TWO_PI / cn.wwl.orimod.movement.FeelTuning.BREATH_PERIOD) * cn.wwl.orimod.movement.FeelTuning.BREATH_AMPLITUDE
                    * Math.max(0, 1 - speed / 3f) * (player.onGround() ? 1 : 0);
            if (!aiming) blend.additive(aimPitch, breath, 1, upperBody, rest);
            if (aiming) armPose.bow(blend, pitch * aimWeight, headYaw * aimWeight);
            else if (itemPoseWeight > 0) armPose.itemUse(blend, player, posedItem, posedUse, posedLeft,
                    itemPoseWeight * itemPoseWeight * (3 - 2 * itemPoseWeight),
                    Math.clamp(player.getViewXRot(partial), -85, 85), headYaw);
        }
        lastNow = now; position = at; grounded = player.onGround(); wasMoving = moving;
        lastVertical = (float)player.getDeltaMovement().y;
        lastMoveId = movement.actionId; lastPrediction = movement.predictedJumpCount; lastHurt = player.hurtTime; previousMode = mode;
    }

    /** R74: per-frame springs for facing, lean, squash and ears (constants in FeelTuning). */
    private void feel(Vec3 at, float bodyYaw, float angular, float dt, String mode, boolean discontinuity) {
        Vec3 v = player.getDeltaMovement();
        if (discontinuity || !Float.isFinite(facing)) {
            facing = bodyYaw; leanPitch.reset(0); leanRoll.reset(0); squash.reset(1); earYaw.reset(0); earFall.reset(0);
            smoothSpeed = lastSpeedForAccel = speed; return;
        }
        if (dt <= 0) return;
        boolean busy = player.isUsingItem() || !player.getData(SpiritData.COMBAT_VIEW).mode().equals("idle") || mode.equals("bash_aim")
                || mode.equals("wall_slide") || player.isSleeping() || player.isPassenger() || player.isVisuallySwimming() || player.isFallFlying();
        freeFacing = !busy;
        double hx = v.x, hz = v.z;
        float targetYaw = speed > cn.wwl.orimod.movement.FeelTuning.FACING_MIN_SPEED && hx * hx + hz * hz > 1e-4
                ? (float) (Mth.atan2(hz, hx) * Mth.RAD_TO_DEG) - 90 : bodyYaw;
        facing = busy ? bodyYaw : cn.wwl.orimod.movement.FeelTuning.approachAngle(facing, targetYaw, cn.wwl.orimod.movement.FeelTuning.FACING_RATE, dt);
        smoothSpeed = Mth.lerp(1 - (float) Math.exp(-dt * 10), smoothSpeed, speed);
        float accel = (smoothSpeed - lastSpeedForAccel) / dt; lastSpeedForAccel = smoothSpeed;
        float max = cn.wwl.orimod.movement.FeelTuning.LEAN_MAX;
        float pitchTarget = player.onGround() || mode.equals("dash") ? Math.clamp(smoothSpeed / 6.4f * cn.wwl.orimod.movement.FeelTuning.LEAN_SPEED + gaitSprint * cn.wwl.orimod.movement.FeelTuning.SPRINT_LEAN + accel * cn.wwl.orimod.movement.FeelTuning.LEAN_ACCEL, -max, max) : 0;
        float rollTarget = Math.clamp(-angular * smoothSpeed / 6.4f * cn.wwl.orimod.movement.FeelTuning.ROLL_PER_TURN, -cn.wwl.orimod.movement.FeelTuning.ROLL_MAX, cn.wwl.orimod.movement.FeelTuning.ROLL_MAX);
        if (busy) pitchTarget = rollTarget = 0;
        leanPitch.step(pitchTarget, dt); leanRoll.step(rollTarget, dt);
        squash.step(1, dt); squash.value = Math.clamp(squash.value, .75f, 1.25f);
        float em = cn.wwl.orimod.movement.FeelTuning.EAR_MAX;
        earYaw.step(Math.clamp(-angular * cn.wwl.orimod.movement.FeelTuning.EAR_TURN, -em, em), dt);
        earFall.step(Math.clamp((float) -v.y * cn.wwl.orimod.movement.FeelTuning.EAR_FALL, -em, em), dt);
    }

    private void hold(GemRenderGltfModel model, AnimationBlend blend, float now, boolean left) {
        GltfAnimation clip = SpiritClips.get(model, handed("hold", left));
        blend.override(clip, clip.loop(now), 1, left ? leftArm : rightArm);
    }

    private static String handed(String name, boolean left) { return left ? name + "_left" : name; }

    private boolean trigger(String action, float now, float length) {
        if (shot.equals(action) && now - shotAt < .22f) return false; // Prediction acknowledgement is the same visual event.
        shot = action; shotAt = now; shotUntil = now + length;
        return true;
    }

    private void reset(GemRenderGltfModel model, float now, float partial) {
        loaded = model;
        var table = model.layout().nodeTable();
        armPose = new SpiritArmPose(table);
        posedItem = ItemStack.EMPTY; posedUse = UseAnim.NONE; itemPoseWeight = 0;
        base = new Crossfade(table);
        arms = BlendMask.subtree(table, "upper_arm_l", "upper_arm_r"); belowArms = arms.complement(table);
        rightArm = BlendMask.subtree(table, "upper_arm_l"); belowRight = rightArm.complement(table);
        leftArm = BlendMask.subtree(table, "upper_arm_r"); belowLeft = leftArm.complement(table);
        upperBody = BlendMask.subtree(table, "spine"); belowUpper = upperBody.complement(table);
        headMask = BlendMask.nodes(table, "head");
        rest = AdditiveReference.rest(table);
        var scratch = new GltfPose.Scratch();
        Matrix4f[] palette = scratch.palette(model.jointCount());
        GltfPose.evaluate(model.layout(), (GltfAnimation)null, 0, palette);
        lookYaw = GltfAnimation.procedural("ori_look_yaw", angle(table, palette, "head", new Vector3f(0,1,0), 1));
        lookPitch = GltfAnimation.procedural("ori_look_pitch", angle(table, palette, "head", new Vector3f(1,0,0), 1));
        aimYaw = GltfAnimation.procedural("ori_aim_yaw", angle(table, palette, "spine", new Vector3f(0,1,0), 1));
        aimPitch = GltfAnimation.procedural("ori_aim_pitch", angle(table, palette, "spine", new Vector3f(1,0,0), 1));
        earPitch = GltfAnimation.procedural("ori_ear_flow",
                angle(table,palette,"ear_l_1",new Vector3f(1,0,0),1), angle(table,palette,"ear_r_1",new Vector3f(1,0,0),1),
                angle(table,palette,"tail_1",new Vector3f(1,0,0),-.5f), angle(table,palette,"tail_2",new Vector3f(1,0,0),-.35f));
        earLag = GltfAnimation.procedural("ori_ear_inertia",
                angle(table,palette,"ear_l_1",new Vector3f(0,1,0),1), angle(table,palette,"ear_r_1",new Vector3f(0,1,0),1),
                angle(table,palette,"tail_1",new Vector3f(0,1,0),.6f), angle(table,palette,"tail_2",new Vector3f(0,1,0),.4f));
        createdAt = lastNow = now; clock = 0; position = player.getPosition(partial); grounded = player.onGround();
        selected = ""; shotUntil = -100; lag = speed = 0; runningGait = false; previousYaw = player.yBodyRot;
        var movement = player.getData(SpiritData.MOVEMENT);
        lastMoveId = movement.actionId; lastPrediction = movement.predictedJumpCount;
    }

    static PoseDriver angle(NodeTable table, Matrix4f[] palette, String name, Vector3f axis, float scale) {
        int slot = table.slotOfName(name);
        palette[slot].getUnnormalizedRotation(new Quaternionf()).invert().transform(axis);
        axis.normalize();
        return new Angle(NodeRotation.offsetOf(table, slot), axis.x, axis.y, axis.z, scale);
    }

    private record Angle(int offset, float x, float y, float z, float scale) implements PoseDriver {
        @Override public void apply(float parameter, float[] scratch) { NodeRotation.compose(scratch, offset, x, y, z, parameter * scale); }
        @Override public float cycleSeconds() { return 0; }
    }

    private static boolean loops(String name) {
        if (name.endsWith("_left")) name = name.substring(0, name.length() - 5);
        return switch (name) {
            case "idle", "alert", "walk", "run", "crouch", "rise", "apex", "fall", "wall_hold", "wall_slide",
                    "bash_aim", "glide", "grapple", "stomp", "bow_hold", "hold", "use_item", "block", "swim", "climb", "sit", "sleep" -> true;
            default -> false;
        };
    }
}
