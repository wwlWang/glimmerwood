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
    private GltfAnimation lookYaw, lookPitch, aimYaw, aimPitch;
    // R74 procedural feel: facing, body lean, squash-stretch, ear/tail springs, breathing.
    private final cn.wwl.orimod.movement.FeelTuning.Spring leanPitch = new cn.wwl.orimod.movement.FeelTuning.Spring(0, cn.wwl.orimod.movement.FeelTuning.POSE_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.POSE_DAMPING);
    private final cn.wwl.orimod.movement.FeelTuning.Spring leanRoll = new cn.wwl.orimod.movement.FeelTuning.Spring(0, cn.wwl.orimod.movement.FeelTuning.POSE_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.POSE_DAMPING);
    private final cn.wwl.orimod.movement.FeelTuning.Spring squash = new cn.wwl.orimod.movement.FeelTuning.Spring(1, cn.wwl.orimod.movement.FeelTuning.SQUASH_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.SQUASH_DAMPING);
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
    private float locoWeight, locoPhase, gaitWalk = 1, gaitRun, gaitSprint, airWeight;
    float[] gait() { return new float[] {gaitWalk * locoWeight, gaitRun * locoWeight, gaitSprint * locoWeight}; }
    private float bowReleasedAt = -100, selectedPlaySpeed = 1, hammerReleasedAt = -100;
    private boolean wasHammer, hammerLeft;
    /** R88: share of the heavy swing clip used as the hammer wind-up, and the release swing length (s). */
    // R94: blade_3 has the arm highest at ~22% of the clip (measured from ori.glb); R88's .32 held it already striking down.
    private static final float HAMMER_WINDUP = .22f, HAMMER_SWING = .32f;
    private Vec3 position;
    /** R92: per-segment spring chains for ears, feelers and tail (replaces R74's single ear spring). */
    private final SpiritSecondary secondary = new SpiritSecondary();
    private String previousMode = "idle", selected = "", shot = "", overlay = "";
    private float selectedClock, clipTime;
    // R94: land into run, mantle, weapon swing polish.
    private float landRunAt = -100, landRunImpact, landRunWeight, twist, spineArch;
    private boolean mantling, wasMantling;
    private long swingKey = Long.MIN_VALUE;
    private float spearSwingAt = -100;

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
    java.util.Map<String, Object> secondary() {
        var out = new java.util.LinkedHashMap<String, Object>(secondary.diagnostics());
        out.put("landRun", landRunWeight); out.put("mantle", mantling); out.put("twist", twist); out.put("spineArch", spineArch);
        return out;
    }
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
            secondary.reset();
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
        wasMantling = mantling;
        mantling = player == net.minecraft.client.Minecraft.getInstance().player && cn.wwl.orimod.client.SpiritMantle.active(player);
        feel(at, yaw, angular, dt, mode, discontinuity);
        if (!discontinuity && position != null && dt > 0) {
            Vec3 velocity = at.subtract(position).scale(1 / dt);
            secondary.step(velocity, facing(yaw), locoPhase, locoWeight * Math.max(gaitRun + gaitSprint, .4f), player.onGround(), dt);
        }
        boolean newPrediction = movement.predictedJumpCount != lastPrediction;
        boolean newAction = movement.actionId != lastMoveId;
        if (newPrediction || newAction) {
            String action = newPrediction ? movement.lastAction.id() : mode;
            switch (action) {
                case "double_jump" -> { if (trigger("double_jump", now, .5f)) { squash.kick(cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP * .8f); secondary.jump(); } }
                case "wall_jump" -> trigger(movement.left < 0 ? "wall_jump_r" : "wall_jump_l", now, .4f);
                case "bash_launch" -> trigger("bash_launch", now, .4f);
                case "jump" -> { if (trigger("jump_start", now, .15f)) { squash.kick(cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP); secondary.jump(); } }
            }
        }
        if (!mode.equals(previousMode)) {
            if (previousMode.equals("dash") && !mode.equals("stomp")) trigger("dash_end", now, .25f);
            if (mode.equals("glide")) trigger("glide_open", now, .25f);
            else if (previousMode.equals("glide") && !player.onGround()) trigger("glide_close", now, .25f);
        }
        if (player.hurtTime > lastHurt) { lastHurtAt = now; shotUntil = -100; }
        if (player.onGround() && !grounded && !player.isInWater()) {
            float impact = Math.abs(lastVertical);
            boolean afterMantle = mantling || wasMantling || player.level().getGameTime() - cn.wwl.orimod.client.SpiritMantle.endedAt() < 4;
            // R94: touching down while still running (WotW's special land when the stick is held) keeps the run going:
            // no full-body land clip; the legs absorb on a short masked layer, the body dives forward and pushes off.
            if (!afterMantle && speed > cn.wwl.orimod.movement.FeelTuning.LAND_RUN_SPEED && lastVertical > -1.2f) {
                landRunAt = now; landRunImpact = Math.min(1, .45f + impact);
                if (shot.startsWith("land_") || shot.equals("jump_start") || shot.equals("double_jump")) shotUntil = -100;
                squash.kick(-cn.wwl.orimod.movement.FeelTuning.SQUASH_LAND * .55f * Math.min(1.6f, .4f + impact));
                leanPitch.kick(cn.wwl.orimod.movement.FeelTuning.LAND_RUN_LEAN_KICK * landRunImpact);
                secondary.land(impact * .8f);
                locoWeight = Math.max(locoWeight, .55f);
            } else if (trigger(lastVertical < -.6f ? "land_heavy" : "land_light", now, lastVertical < -.6f ? .5f : .3f)) { squash.kick(-cn.wwl.orimod.movement.FeelTuning.SQUASH_LAND * Math.min(1.6f, .4f + impact)); secondary.land(impact); }
        }
        boolean moving = speed > .22f;
        // A single interpolation spike must not reset a slow walk to a run and back.
        runningGait = speed > (runningGait ? 1.8f : 2.3f);
        if (moving) lastMovingAt = now;
        if (!moving && wasMoving && player.onGround()) trigger("stop", now, .30f);
        if (moving && !wasMoving && player.onGround() && now - landRunAt > .1f) trigger("start", now, .12f);
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
        else if (mantling) next = cn.wwl.orimod.client.SpiritMantle.overLip() ? "land_light" : "climb";
        else if (mode.equals("wall_slide")) next = Math.abs(player.getDeltaMovement().y) < .03 ? "wall_hold" : "wall_slide";
        else if (now < shotUntil) next = shot;
        else if (mode.equals("glide")) next = "glide";
        else if (!player.onGround() && !player.isInWater()) {
            special = false;
            // R88: one airborne base; rise/apex/fall are weighted by vertical speed below (no hard switches).
            next = "apex";
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
            case "climb" -> mantling ? 2.4f : Math.clamp((float)Math.abs(player.getDeltaMovement().y) * 10, .2f, 2);
            default -> 1;
        } : 1;
        clock += dt * Math.max(.05f, rate);
        if (!next.equals(selected)) {
            float duration = nextClip.duration();
            float playSpeed = !nextClip.name().equals(next) ? 1 : next.equals("start") ? duration / .12f : next.equals("jump_start") ? duration / .15f : 1;
            boolean quick = next.equals("double_jump") || next.equals("jump_start") || next.equals("dash_start") || next.equals("apex")
                    || next.startsWith("land_") || next.startsWith("wall_jump") || mantling || now - landRunAt < .06f;
            float fade = quick ? cn.wwl.orimod.movement.FeelTuning.ANIM_CROSSFADE_FAST : cn.wwl.orimod.movement.FeelTuning.ANIM_CROSSFADE;
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
        boolean hammer = player.isUsingItem() && player.getUseItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER);
        if (wasHammer && !hammer) hammerReleasedAt = now;
        wasHammer = hammer;
        if (bow) bowLeft = useLeft;
        wasBow = bow;
        boolean overlayLeft = false;
        float swingTwist = 0, arch = 0;
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
                int stage = Math.clamp(combat.stage(), 1, 3);
                overlay = handed("blade_" + stage, mainLeft);
                float ticks = player.level().getGameTime() - combat.startedAt() + partial, total = SpiritCombat.duration(stage);
                // R94: anticipation, a fast hit and an eased recovery instead of a linear scrub; the torso coils and whips.
                float k = ticks / total, wind = SpiritCombat.windup(stage) / total, hit = SpiritCombat.recoveryStart(stage) / total;
                overlayTime = SpiritClips.get(model, overlay).duration() * cn.wwl.orimod.movement.FeelTuning.swingCurve(k, wind, hit);
                swingTwist = cn.wwl.orimod.movement.FeelTuning.SWING_TWIST * (stage == 2 ? -1 : stage == 3 ? 1.3f : 1) * (mainLeft ? -1 : 1)
                        * cn.wwl.orimod.movement.FeelTuning.swingTwist(k, wind, hit);
                long key = combat.startedAt() * 4 + stage;
                if (k >= wind && swingKey != key) {
                    swingKey = key;
                    leanPitch.kick(cn.wwl.orimod.movement.FeelTuning.SWING_LEAN_KICK * (stage == 3 ? 1.4f : 1));
                    secondary.flick(stage == 3 ? 1.3f : 1);
                    if (stage == 3) squash.kick(-cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP);
                }
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
                } else if (player.getUseItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_HAMMER)) {
                    // R88: wind the hammer back (first third of the heavy swing) and hold it there, breathing,
                    // instead of freezing on the swing's last frame; the release plays the rest of the swing.
                    overlay = handed("blade_3", useLeft);
                    float d = SpiritClips.get(model, overlay).duration(), k = Math.min(1, elapsed * 20f / cn.wwl.orimod.item.SpiritHammerItem.WIND_UP_TICKS);
                    overlayTime = d * (HAMMER_WINDUP * k * k * (3 - 2 * k) + .015f * Mth.sin(now * 5) * k);
                    hammerLeft = useLeft;
                    // R94: arch back as the light gathers, breathing with it (the head of the hammer swells in step).
                    arch = -cn.wwl.orimod.movement.FeelTuning.HAMMER_ARCH * k * k * (3 - 2 * k)
                            * (1 + cn.wwl.orimod.movement.FeelTuning.HAMMER_BREATH * Mth.sin(now * 5));
                    swingTwist = -cn.wwl.orimod.movement.FeelTuning.SWING_TWIST * .5f * k * (useLeft ? -1 : 1);
                    mask = upperBody; below = belowUpper;
                } else if (player.getUseItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) {
                    overlay = handed("use_item", useLeft);
                    overlayTime = Math.min(now, SpiritClips.get(model, overlay).duration() * .95f);
                    mask = upperBody; below = belowUpper;
                } else {
                    overlay = handed(use == UseAnim.BLOCK ? "block" : "use_item", useLeft); overlayTime = now; overlayLeft = useLeft;
                    mask = useLeft ? leftArm : rightArm; below = useLeft ? belowLeft : belowRight;
                }
            } else if (now - hammerReleasedAt < HAMMER_SWING) {
                overlay = handed("blade_3", hammerLeft);
                float d = SpiritClips.get(model, overlay).duration(), k = (now - hammerReleasedAt) / HAMMER_SWING;
                overlayTime = d * (HAMMER_WINDUP + (1 - HAMMER_WINDUP) * (1 - (1 - k) * (1 - k)));
                // R94: slam: the spine whips forward then settles; the impact squashes the body and flops the ears.
                float slam = k < .3f ? k / .3f : (1 - k) / .7f;
                arch = cn.wwl.orimod.movement.FeelTuning.HAMMER_SLAM * slam * slam * (3 - 2 * slam);
                swingTwist = cn.wwl.orimod.movement.FeelTuning.SWING_TWIST * (hammerLeft ? -1 : 1) * (k < .3f ? -.5f + 1.5f * k / .3f : 1 - (k - .3f) / .7f);
                long key = (long) (hammerReleasedAt * 1000) * 4;
                if (k >= .3f && swingKey != key) {
                    swingKey = key;
                    squash.kick(-cn.wwl.orimod.movement.FeelTuning.SQUASH_LAND * .45f); secondary.land(.6f); secondary.flick(1.2f);
                }
                mask = upperBody; below = belowUpper;
            } else if (now - bowReleasedAt < .3f) {
                overlay = handed("bow_release", bowLeft); overlayTime = now - bowReleasedAt;
                mask = upperBody; below = belowUpper;
            } else if (player.getAttackAnim(partial) > .001f) {
                overlayLeft = player.swingingArm == InteractionHand.OFF_HAND ? !mainLeft : mainLeft;
                float k = player.getAttackAnim(partial);
                if (player.getItemInHand(player.swingingArm).is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) {
                    // R94: spear throw: an overhand heavy swing with the whole upper body, coiling then whipping forward.
                    overlay = handed("blade_3", overlayLeft);
                    overlayTime = SpiritClips.get(model, overlay).duration() * cn.wwl.orimod.movement.FeelTuning.swingCurve(k, .28f, .55f);
                    swingTwist = cn.wwl.orimod.movement.FeelTuning.SPEAR_THROW_TWIST * (overlayLeft ? -1 : 1) * cn.wwl.orimod.movement.FeelTuning.swingTwist(k, .28f, .55f);
                    arch = k < .3f ? -.18f * k / .3f : .22f * (1 - (k - .3f) / .7f);
                    if (k < .5f && now - spearSwingAt > .4f) { spearSwingAt = now; leanPitch.kick(cn.wwl.orimod.movement.FeelTuning.SWING_LEAN_KICK); secondary.flick(1); }
                    mask = upperBody; below = belowUpper;
                } else {
                    overlay = handed("blade_1", overlayLeft);
                    overlayTime = cn.wwl.orimod.movement.FeelTuning.swingCurve(k, .2f, .55f) * SpiritClips.get(model, overlay).duration();
                    swingTwist = cn.wwl.orimod.movement.FeelTuning.SWING_TWIST * .4f * (overlayLeft ? -1 : 1) * cn.wwl.orimod.movement.FeelTuning.swingTwist(k, .2f, .55f);
                    mask = overlayLeft ? leftArm : rightArm; below = overlayLeft ? belowLeft : belowRight;
                }
            } else if (player.getMainHandItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR) || player.getOffhandItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) {
                // R94: spear held at the ready: shoulders turned back a little, gently bobbing, not a stiff carry.
                boolean spearLeft = player.getMainHandItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR) ? mainLeft : !mainLeft;
                swingTwist = -cn.wwl.orimod.movement.FeelTuning.SPEAR_READY_TWIST * (spearLeft ? -1 : 1) * (1 + .15f * Mth.sin(now * 2.2f));
                arch = .04f * Mth.sin(now * 2.2f + 1);
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
        GltfAnimation walkClip = SpiritClips.get(model, "walk");
        if (selected.equals("walk") && walkClip.duration() > 0) locoPhase = walkClip.loop((clock - selectedClock) * selectedPlaySpeed) / walkClip.duration();
        // R87: gait always stays below the spine so blade/bow/hammer overlays keep the upper body.
        var gaitMask = below != null ? below : belowUpper;
        if (runWeight > .002f) {
            GltfAnimation runClip = SpiritClips.get(model, "run");
            blend.override(runClip, runClip.loop(locoPhase * runClip.duration()), runWeight, gaitMask);
        }
        // R94: deeper knee/thigh/ankle bend in phase with the stride (grounded locomotion only).
        float onFeet = player.onGround() && selected.equals("walk") ? locoWeight : 0;
        secondary.gait(locoPhase, gaitWalk * onFeet, gaitRun * onFeet, gaitSprint * onFeet);
        // R94: land into run: the legs absorb on a short layer under the running cycle, then push off into it.
        landRunWeight = selected.equals("walk") ? cn.wwl.orimod.movement.FeelTuning.landRunWeight(now - landRunAt) * landRunImpact : 0;
        if (landRunWeight > .002f) {
            GltfAnimation landClip = SpiritClips.get(model, "land_light");
            float lk = (now - landRunAt) / cn.wwl.orimod.movement.FeelTuning.LAND_RUN_TIME;
            blend.override(landClip, landClip.duration() * (.2f + .45f * lk), landRunWeight * cn.wwl.orimod.movement.FeelTuning.LAND_RUN_BLEND, gaitMask);
        }
        // R88: airborne pose scrubs between rise, apex and fall by vertical velocity, like Ori's jump arc.
        airWeight = cn.wwl.orimod.movement.FeelTuning.approach(airWeight, selected.equals("apex") ? 1 : 0, dt / cn.wwl.orimod.movement.FeelTuning.ANIM_CROSSFADE_FAST);
        if (airWeight > .002f) {
            float[] air = cn.wwl.orimod.movement.FeelTuning.airWeights((float) player.getDeltaMovement().y);
            var airMask = below != null ? below : BlendMask.ALL;
            GltfAnimation riseClip = SpiritClips.get(model, "rise"), fallClip = SpiritClips.get(model, "fall");
            if (air[0] > .002f) blend.override(riseClip, riseClip.loop(now), air[0] * airWeight, airMask);
            if (air[2] > .002f) blend.override(fallClip, fallClip.loop(now), air[2] * airWeight, airMask);
        }
        if (mask != null) {
            GltfAnimation action = SpiritClips.get(model, overlay);
            overlayClipName = action.name();
            float time = loops(action.name()) ? action.loop(overlayTime) : Math.clamp(overlayTime, 0, Math.max(0, action.duration() - .0001f));
            blend.override(action, time, 1, mask);
        }
        // R94: torso twist / arch layered on any weapon overlay (spring-smoothed so starts and ends never snap).
        float tk = 1 - (float) Math.exp(-dt * 30);
        twist = Mth.lerp(tk, twist, swingTwist); spineArch = Mth.lerp(tk, spineArch, arch);
        if (Math.abs(twist) > 1e-4f) blend.additive(aimYaw, twist, 1, upperBody, rest);
        if (Math.abs(spineArch) > 1e-4f) blend.additive(aimPitch, spineArch, 1, upperBody, rest);
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
            float breath = Mth.sin(now * Mth.TWO_PI / cn.wwl.orimod.movement.FeelTuning.BREATH_PERIOD) * cn.wwl.orimod.movement.FeelTuning.BREATH_AMPLITUDE
                    * Math.max(0, 1 - speed / 3f) * (player.onGround() ? 1 : 0);
            if (!aiming) blend.additive(aimPitch, breath, 1, upperBody, rest);
            if (aiming) armPose.bow(blend, pitch * aimWeight, headYaw * aimWeight);
            else if (itemPoseWeight > 0) armPose.itemUse(blend, player, posedItem, posedUse, posedLeft,
                    itemPoseWeight * itemPoseWeight * (3 - 2 * itemPoseWeight),
                    Math.clamp(player.getViewXRot(partial), -85, 85), headYaw);
        }
        if (secondary.clip() != null && !next.equals("death")) blend.additive(secondary.clip(), now, 1, BlendMask.ALL, rest);
        lastNow = now; position = at; grounded = player.onGround(); wasMoving = moving;
        lastVertical = (float)player.getDeltaMovement().y;
        lastMoveId = movement.actionId; lastPrediction = movement.predictedJumpCount; lastHurt = player.hurtTime; previousMode = mode;
    }

    /** R74: per-frame springs for facing, lean, squash and ears (constants in FeelTuning). */
    private void feel(Vec3 at, float bodyYaw, float angular, float dt, String mode, boolean discontinuity) {
        Vec3 v = player.getDeltaMovement();
        if (discontinuity || !Float.isFinite(facing)) {
            facing = bodyYaw; leanPitch.reset(0); leanRoll.reset(0); squash.reset(1);
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
        float pitchTarget = player.onGround() || mode.equals("dash") ? Math.clamp(smoothSpeed / 6.4f * cn.wwl.orimod.movement.FeelTuning.LEAN_SPEED + gaitSprint * cn.wwl.orimod.movement.FeelTuning.SPRINT_LEAN + accel * cn.wwl.orimod.movement.FeelTuning.LEAN_ACCEL, -max, max)
                : mode.equals("glide") ? 0 : cn.wwl.orimod.movement.FeelTuning.airLean(smoothSpeed, (float) v.y);
        float rollTarget = Math.clamp(-angular * smoothSpeed / 6.4f * cn.wwl.orimod.movement.FeelTuning.ROLL_PER_TURN, -cn.wwl.orimod.movement.FeelTuning.ROLL_MAX, cn.wwl.orimod.movement.FeelTuning.ROLL_MAX);
        // R92: Ori's ready stance: a slight forward dip while standing instead of a rigid upright doll.
        if (player.onGround() && !mode.equals("dash")) pitchTarget += cn.wwl.orimod.movement.FeelTuning.READY_LEAN * Math.max(0, 1 - smoothSpeed / 2.5f);
        if (busy) pitchTarget = rollTarget = 0;
        // R94 mantle: lean into the wall on the pop, pitch well forward over the lip, knees tucked.
        boolean over = cn.wwl.orimod.client.SpiritMantle.overLip();
        if (mantling) { pitchTarget = cn.wwl.orimod.movement.FeelTuning.MANTLE_LEAN * (over ? 1 : .45f); rollTarget = 0; freeFacing = false; facing = bodyYaw; }
        secondary.tuck(mantling ? cn.wwl.orimod.movement.FeelTuning.MANTLE_TUCK * (over ? 1 : .6f) : Float.NaN);
        leanPitch.step(pitchTarget, dt); leanRoll.step(rollTarget, dt);
        // R88/R93: run bounce on the ground; air stretches while rising and tucks while falling (WotW squash-stretch).
        float squashTarget = player.onGround()
                ? 1 + cn.wwl.orimod.movement.FeelTuning.GAIT_BOUNCE * (gaitRun + gaitSprint) * locoWeight * Mth.cos(locoPhase * Mth.TWO_PI * 2)
                : cn.wwl.orimod.movement.FeelTuning.airScale((float) v.y);
        if (busy) squashTarget = 1;
        if (mantling) squashTarget = over ? .88f : 1.08f;
        squash.step(squashTarget, dt);
        squash.value = Math.clamp(squash.value, cn.wwl.orimod.movement.FeelTuning.SQUASH_MIN, cn.wwl.orimod.movement.FeelTuning.SQUASH_MAX);
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
        secondary.bind(table, palette); secondary.bindGait(table, SpiritClips.get(model, "walk"), SpiritClips.get(model, "run")); secondary.reset();
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
