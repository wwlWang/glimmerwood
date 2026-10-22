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
    private final AnimationBlend baseLayers = new AnimationBlend();
    private final java.util.Map<String, Float> sampledTimes = new java.util.HashMap<>();
    private float climbPhase, crawlPhase, lastHealth, hurtDamage, hurtImpulse, hurtFront, hurtRight;
    private Vec3 lastVelocity = Vec3.ZERO, hurtVelocity = Vec3.ZERO;
    private int hurtSequence;
    private String hurtClip = "hurt";
    private float[] layerWeights = {1, 0, 0, 0};
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
    // R95 pose overrides: swim / glide / elytra body pose, held-item cradle per hand, run extras, whole-body pitch.
    // cradleRight drives the "_l" bones (the player's right hand: the model faces +Z, -X is its right).
    private final SpiritPoseLayer bodyPose = new SpiritPoseLayer("ori_pose_body"), cradleRight = new SpiritPoseLayer("ori_pose_hold_right"),
            cradleLeft = new SpiritPoseLayer("ori_pose_hold_left");
    private BlendMask cradleRightMask, cradleLeftMask;
    private final cn.wwl.orimod.movement.FeelTuning.Spring bodyPitch = new cn.wwl.orimod.movement.FeelTuning.Spring(0,
            cn.wwl.orimod.movement.FeelTuning.BODY_PITCH_STIFFNESS, cn.wwl.orimod.movement.FeelTuning.BODY_PITCH_DAMPING);
    private float swimMix, glideMix, dart, cruise, swimPhase, speed3, cradleWeightRight, cradleWeightLeft, runPoseWeight;
    private float loweredRight, loweredLeft;
    /** "", "swim", "spin" (riptide), "glide" (spirit glide) or "elytra". */
    private String family = "";
    private NodeTable table;
    private float[] keep;
    private float bodyShare;

    SpiritAnimation(Player player) { this.player = player; }

    String selected() { return selected; }
    String overlay() { return overlay; }
    float clipTime() { return clipTime; }
    /** R74: model yaw (degrees): follows the velocity while running, else the vanilla body yaw. */
    float facing(float vanillaYaw) { return freeFacing && Float.isFinite(facing) ? facing : vanillaYaw; }
    float leanPitch() { return leanPitch.value; }
    float leanRoll() { return leanRoll.value; }
    float squash() { return squash.value; }
    /** R95: whole-body pitch (rad) about the body centre: lie along the swim / elytra direction, tilt into a glide. */
    float bodyPitch() { return bodyPitch.value; }
    /** R95: how far the lying body is lowered toward vanilla's 0.6-high swim / elytra box (blocks). */
    float bodyDrop() { return cn.wwl.orimod.movement.FeelTuning.SWIM_DROP * cn.wwl.orimod.movement.FeelTuning.smoothstep(.6f, 1.4f, bodyPitch.value); }
    String family() { return family; }
    float speed() { return speed; }
    String baseClip() { return baseClipName; }
    String overlayClip() { return overlayClipName; }
    java.util.Map<String, Object> secondary() {
        var out = new java.util.LinkedHashMap<String, Object>(secondary.diagnostics());
        out.put("weights", layerWeights.clone()); out.put("hurtAge", lastNow - lastHurtAt); out.put("hurt", hurtClip); out.put("hurtSequence", hurtSequence); out.put("landRun", landRunWeight); out.put("mantle", mantling); out.put("twist", twist); out.put("spineArch", spineArch);
        var pose = new java.util.LinkedHashMap<String, Object>();
        pose.put("family", family); pose.put("swim", swimMix); pose.put("glide", glideMix); pose.put("dart", dart); pose.put("cruise", cruise);
        pose.put("bodyPitch", bodyPitch.value); pose.put("cradleRight", cradleWeightRight); pose.put("cradleLeft", cradleWeightLeft);
        pose.put("loweredRight", loweredRight); pose.put("loweredLeft", loweredLeft);
        pose.put("throw", overlay.startsWith("throw_ready") ? 1f : 0f); pose.put("run", runPoseWeight); pose.put("speed3", speed3);
        out.put("pose", pose);
        if (DEBUG_POSE) { out.put("poseError", new java.util.LinkedHashMap<>(poseError)); }
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
            lastMoveId = movement.animationImpulseId; lastPrediction = movement.predictedJumpCount; previousMode = movement.mode.id();
            grounded = player.onGround(); wasMoving = runningGait = false; lastHurt = player.hurtTime;
            wasBow = false; bowReleasedAt = -100;
            itemPoseWeight = 0;
            secondary.reset();
            speed3 = 0; cradleWeightLeft = cradleWeightRight = loweredLeft = loweredRight = 0;
        }
        float dt = Math.clamp(now - lastNow, 0, .1f);
        double distance = position == null ? 0 : at.subtract(position).horizontalDistance();
        float observedSpeed = dt > .00001f ? (float)(distance / dt) : speed;
        if (dt > 0) speed = Mth.lerp(1 - (float)Math.exp(-dt * 18), speed, observedSpeed);
        // R95: 3D speed for the swim cruise / water-dash dart.
        float observed3 = dt > .00001f && position != null ? (float) (at.distanceTo(position) / dt) : speed3;
        if (dt > 0) speed3 = Mth.lerp(1 - (float)Math.exp(-dt * 18), speed3, observed3);
        float yaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        float angular = 0;
        if (dt > 0) {
            angular = Mth.wrapDegrees(yaw - previousYaw) / dt;
            lag = Mth.lerp(1 - (float)Math.exp(-dt * 9), lag, Math.clamp(-angular * .0013f, -.17f, .17f));
        }
        previousYaw = yaw;
        String mode = cn.wwl.orimod.movement.FeatherGlide.gliding(player) ? "glide" : movement.mode.id();
        wasMantling = mantling;
        mantling = player == net.minecraft.client.Minecraft.getInstance().player && cn.wwl.orimod.client.SpiritMantle.active(player);
        var state = new SpiritAnimationRules.State();
        state.alive = player.isAlive(); state.spirit = SpiritData.isSpirit(player);
        state.sleeping = player.isSleeping(); state.passenger = player.isPassenger();
        state.rowing = player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat
                && boat.getControllingPassenger() == player && (boat.getPaddleState(0) || boat.getPaddleState(1));
        state.spin = player.isAutoSpinAttack(); state.elytra = player.isFallFlying();
        state.crawling = player.isVisuallyCrawling(); state.water = player.isInWater(); state.submerged = player.isUnderWater();
        state.climbing = player.onClimbable(); state.grounded = player.onGround(); state.mantle = mantling;
        state.burrow = player == net.minecraft.client.Minecraft.getInstance().player && cn.wwl.orimod.client.SpiritControls.burrowing(player);
        state.crouching = player.isCrouching(); state.speed = speed3; state.vertical = (float)player.getDeltaMovement().y;
        if (state.climbing || mode.equals("wall_slide")) state.vertical = SpiritAnimationRules.contactVertical(
                position == null ? 0 : at.y - position.y, dt, !discontinuity && position != null, state.vertical);
        state.mode = mode;
        state.launchCharge = state.grounded && state.crouching && mode.equals("idle") && !player.getAbilities().mayfly
                && cn.wwl.orimod.progression.Ability.LAUNCH.unlocked(player);
        family = SpiritAnimationRules.family(state);
        feel(at, yaw, angular, dt, mode, discontinuity);
        if (!discontinuity && position != null && dt > 0) {
            Vec3 velocity = at.subtract(position).scale(1 / dt);
            boolean swimFamily = family.startsWith("swim") || family.equals("spin");
            // R95: in water / in a glide the ears, feelers and tail stream back along the body instead of reacting to
            // vertical speed as a fall.
            secondary.medium(swimFamily ? 1 + dart : family.equals("elytra") ? 1 : family.equals("glide") ? .6f : 0,
                    swimFamily || family.equals("elytra") ? 0 : 1);
            secondary.step(velocity, facing(yaw), locoPhase, locoWeight * Math.max(gaitRun + gaitSprint, .4f), player.onGround(), dt);
        }
        boolean newPrediction = movement.predictedJumpCount != lastPrediction;
        boolean newAction = movement.animationImpulseId != lastMoveId;
        if (newPrediction || newAction) {
            String action = newPrediction ? movement.lastAction.id() : movement.animationImpulse.id();
            switch (action) {
                case "double_jump" -> { if (trigger("double_jump", now, .5f)) { squash.kick(cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP * .8f); secondary.jump(); } }
                case "wall_jump" -> trigger(movement.left < 0 ? "wall_jump_r" : "wall_jump_l", now, .4f);
                case "bash_launch" -> trigger("bash_launch", now, .4f);
                case "launch" -> trigger("launch", now, SpiritClips.get(model, "launch").duration());
                case "stomp_land" -> trigger("land_heavy", now, SpiritClips.get(model, "land_heavy").duration());
                case "jump" -> { if (trigger("jump_start", now, .15f)) { squash.kick(cn.wwl.orimod.movement.FeelTuning.SQUASH_JUMP); secondary.jump(); } }
            }
        }
        if (!mode.equals(previousMode)) {
            if (mode.equals("stomp")) trigger("stomp_start", now, SpiritClips.get(model, "stomp_start").duration());
            if (previousMode.equals("dash") && !mode.equals("stomp")) trigger("dash_end", now, .25f);
            if (mode.equals("glide")) trigger("glide_open", now, .25f);
            else if (previousMode.equals("glide") && !player.onGround()) trigger("glide_close", now, .25f);
        }
        var observed = SpiritAnimationEvents.of(player);
        if (observed.hurtSequence != hurtSequence || player.hurtTime > lastHurt) {
            hurtSequence = observed.hurtSequence; lastHurtAt = now; shotUntil = -100;
            hurtDamage = Math.max(0, Math.max(lastHealth, observed.healthBefore) - player.getHealth());
            hurtVelocity = lastVelocity;
            hurtImpulse = (float)player.getDeltaMovement().subtract(hurtVelocity).length();
            Vec3 from = observed.hurtFrom == null ? Vec3.ZERO : observed.hurtFrom.subtract(player.position());
            double r = Math.toRadians(yaw);
            hurtFront = (float)(-Math.sin(r) * from.x + Math.cos(r) * from.z);
            hurtRight = (float)(-Math.cos(r) * from.x - Math.sin(r) * from.z);
        }
        if (now - lastHurtAt < .15f) {
            // Health and motion arrive in adjacent vanilla packets; allow the reaction to upgrade in its first 3 ticks.
            hurtDamage = Math.max(hurtDamage, Math.max(lastHealth, observed.healthBefore) - player.getHealth());
            hurtImpulse = Math.max(hurtImpulse, (float)player.getDeltaMovement().subtract(hurtVelocity).length());
            hurtClip = SpiritAnimationRules.hurt(hurtFront, hurtRight, hurtDamage, player.getMaxHealth(), hurtImpulse);
        }
        if (player.onGround() && !grounded && !player.isInWater() && !mode.equals("stomp_land")) {
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
        if (!moving && wasMoving && player.onGround() && family.isEmpty() && !state.crouching) trigger("stop", now, .30f);
        if (moving && !wasMoving && player.onGround() && family.isEmpty() && !state.crouching && now - landRunAt > .1f) trigger("start", now, .12f);
        if (!moving && player.onGround() && family.isEmpty() && !state.crouching && now >= shotUntil && Math.abs(angular) > 75)
            trigger(angular > 0 ? "turn_right" : "turn_left", now, .5f);

        state.moving = moving; state.entering = now - createdAt < .6f; state.idleSeconds = now - lastMovingAt;
        state.shot = now < shotUntil ? shot : "";
        String next = SpiritAnimationRules.base(state);
        if (next.equals("grapple")) next = handed(next, player.getMainArm() == HumanoidArm.LEFT);
        GltfAnimation nextClip = SpiritClips.get(model, next);
        baseClipName = nextClip.name();
        // Crossfade time always advances monotonically. Distance/signed climbing phase are sampled separately below.
        clock += dt;
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
        if (next.equals("walk")) {
            locoPhase = SpiritAnimationRules.cycle(locoPhase + (float)distance / cn.wwl.orimod.movement.FeelTuning.stride(speed));
            clipTime = locoPhase * nextClip.duration();
        } else if (next.equals("climb") || next.equals("wall_climb")) {
            climbPhase = SpiritAnimationRules.cycle(climbPhase + dt * SpiritAnimationRules.climbRate(state.vertical));
            clipTime = climbPhase * nextClip.duration();
        } else if (next.equals("crawl") || next.equals("crouch_walk")) {
            crawlPhase = SpiritAnimationRules.cycle(crawlPhase + (float)distance / .65f);
            clipTime = crawlPhase * nextClip.duration();
        } else if (next.equals("mantle")) clipTime = cn.wwl.orimod.client.SpiritMantle.progress() * nextClip.duration();
        else if (next.equals("boat_row") && player.getVehicle() instanceof net.minecraft.world.entity.vehicle.Boat boat)
            clipTime = SpiritAnimationRules.cycle(Math.max(boat.getRowingTime(0, partial), boat.getRowingTime(1, partial)) / Mth.TWO_PI) * nextClip.duration();
        if (java.util.Set.of("walk", "climb", "wall_climb", "crawl", "crouch_walk", "mantle", "boat_row").contains(next))
            sampledTimes.put(nextClip.name(), clipTime);

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
        boolean handsAvailable = SpiritAnimationRules.hands(state);
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
        // R95: a trident (SPEAR) no longer reaches the vanilla stretched arm; it is cocked by the shoulder (throwReady).
        boolean contactUse = handsAvailable && !spiritWeapon && (contactAnimation == UseAnim.SPYGLASS
                || contactAnimation == UseAnim.CROSSBOW);
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
                overlay = handed(player.onGround() ? "blade_" + stage : "blade_air", mainLeft);
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
                    overlay = handed("hammer_charge", useLeft); overlayTime = elapsed;
                    hammerLeft = useLeft;
                    mask = upperBody; below = belowUpper;
                } else if (use == UseAnim.SPEAR) {
                    overlay = handed("throw_ready", useLeft); overlayTime = elapsed;
                    mask = upperBody; below = belowUpper;
                } else {
                    overlay = handed(SpiritAnimationRules.use(use.name()), useLeft); overlayTime = elapsed; overlayLeft = useLeft;
                    mask = use == UseAnim.EAT ? upperBody : useLeft ? leftArm : rightArm; below = use == UseAnim.EAT ? belowUpper : useLeft ? belowLeft : belowRight;
                }
            } else if (now - hammerReleasedAt < SpiritClips.get(model, "hammer_smash").duration()) {
                overlay = handed("hammer_smash", hammerLeft);
                float d = SpiritClips.get(model, overlay).duration(), k = (now - hammerReleasedAt) / d;
                overlayTime = now - hammerReleasedAt;
                long key = (long)(hammerReleasedAt * 1000) * 4;
                if (k >= .28f && swingKey != key) {
                    swingKey = key; secondary.land(.3f); secondary.flick(.6f);
                }
                mask = upperBody; below = belowUpper;
            } else if (now - bowReleasedAt < .3f) {
                overlay = handed("bow_release", bowLeft); overlayTime = now - bowReleasedAt;
                mask = upperBody; below = belowUpper;
            } else if (observed.action != null && SpiritAnimationRules.actionVisible(now - observed.actionAt,
                    SpiritClips.get(model, observed.action.clip()).duration(), player.getAttackAnim(partial) > .001f)) {
                overlayLeft = observed.offhand ? !mainLeft : mainLeft;
                overlay = handed(observed.action.clip(), overlayLeft);
                float elapsed = now - observed.actionAt, d = SpiritClips.get(model, overlay).duration();
                overlayTime = observed.action == cn.wwl.orimod.network.AnimationActionPayload.Action.SPEAR_THROW
                        ? d * cn.wwl.orimod.movement.FeelTuning.swingCurve(Math.min(1, elapsed / d), .28f, .55f) : Math.min(d, elapsed);
                mask = observed.action == cn.wwl.orimod.network.AnimationActionPayload.Action.SPEAR_THROW ? upperBody : overlayLeft ? leftArm : rightArm;
                below = mask == upperBody ? belowUpper : overlayLeft ? belowLeft : belowRight;
            } else if (now < observed.castUntil || now - observed.castRelease < SpiritClips.get(model, "cast_release").duration()
                    || cn.wwl.orimod.client.fx.SpiritFxClient.flashing(player.getId())) {
                overlay = now - observed.castRelease < SpiritClips.get(model, "cast_release").duration() ? "cast_release"
                        : now < observed.castUntil ? "cast_charge" : "flash";
                overlayTime = overlay.equals("cast_release") ? now - observed.castRelease : now < observed.castUntil ? now - observed.castStarted : now;
                mask = upperBody; below = belowUpper;
            } else if (player.getAttackAnim(partial) > .001f) {
                overlayLeft = player.swingingArm == InteractionHand.OFF_HAND ? !mainLeft : mainLeft;
                float k = player.getAttackAnim(partial);
                if (player.getItemInHand(player.swingingArm).is(cn.wwl.orimod.item.OriItems.SPIRIT_SPEAR)) {
                    // R94: spear throw: an overhand heavy swing with the whole upper body, coiling then whipping forward.
                    overlay = handed("spear_throw", overlayLeft);
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
        boolean holdLeft = handsAvailable && !family.equals("glide") && !(mainLeft ? player.getMainHandItem() : player.getOffhandItem()).isEmpty()
                && mask != upperBody && mask != leftArm;
        boolean holdRight = handsAvailable && !family.equals("glide") && !(mainLeft ? player.getOffhandItem() : player.getMainHandItem()).isEmpty()
                && mask != upperBody && mask != rightArm;
        // R95: only weapon / use overlays exclude an arm from the base; cradles and body poses scale it instead (keepOut).
        boolean leftCovered = mask == leftArm, rightCovered = mask == rightArm;
        if (mask != upperBody) below = leftCovered && rightCovered ? belowArms : leftCovered ? belowLeft : rightCovered ? belowRight : null;
        // R95: swim / glide / elytra body pose (replaces the base on the body, limbs and the roots of ears, feelers, tail).
        boolean swimFamily = family.startsWith("swim") || family.equals("spin"), glideFamily = family.equals("glide") || family.equals("elytra");
        float poseFade = dt / cn.wwl.orimod.movement.FeelTuning.POSE_FADE;
        swimMix = cn.wwl.orimod.movement.FeelTuning.approach(swimMix, swimFamily ? 1 : 0, poseFade);
        glideMix = cn.wwl.orimod.movement.FeelTuning.approach(glideMix, glideFamily ? 1 : 0, poseFade);
        float dartTarget = family.equals("spin") ? 1 : cn.wwl.orimod.movement.FeelTuning.swimDart(speed3);
        dart = cn.wwl.orimod.movement.FeelTuning.approach(dart, dartTarget, dt / (dartTarget > dart ? .06f : .35f));
        cruise = cn.wwl.orimod.movement.FeelTuning.approach(cruise, cn.wwl.orimod.movement.FeelTuning.swimCruise(speed3), dt / .3f);
        swimPhase = (swimPhase + dt * Mth.TWO_PI * Mth.lerp(cruise, cn.wwl.orimod.movement.FeelTuning.SWIM_STROKE_FLOAT,
                cn.wwl.orimod.movement.FeelTuning.SWIM_STROKE_CRUISE) * (1 - .85f * dart)) % (Mth.TWO_PI * 64);
        // Held tools ease into a chest cradle or a lowered one-handed carry, with a small stride bob.
        float holdFade = dt / cn.wwl.orimod.movement.FeelTuning.HOLD_FADE;
        boolean leftFree = handsAvailable && mask != upperBody && mask != leftArm;
        boolean rightFree = handsAvailable && mask != upperBody && mask != rightArm;
        cradleWeightLeft = leftFree ? cn.wwl.orimod.movement.FeelTuning.approach(cradleWeightLeft, holdLeft ? 1 : 0, holdFade) : 0;
        cradleWeightRight = rightFree ? cn.wwl.orimod.movement.FeelTuning.approach(cradleWeightRight, holdRight ? 1 : 0, holdFade) : 0;
        loweredRight = cn.wwl.orimod.movement.FeelTuning.approach(loweredRight,
                SpiritItemGrip.lowered(mainLeft ? player.getOffhandItem() : player.getMainHandItem()) ? 1 : 0, holdFade);
        loweredLeft = cn.wwl.orimod.movement.FeelTuning.approach(loweredLeft,
                SpiritItemGrip.lowered(mainLeft ? player.getMainHandItem() : player.getOffhandItem()) ? 1 : 0, holdFade);
        bodyShare = 0; // v014 owns swim/glide/throw-ready. Never overlay the old R95 procedural silhouettes.
        keep = null;
        keepOut(cradleRightMask, ease(cradleWeightRight)); keepOut(cradleLeftMask, ease(cradleWeightLeft));
        var gait = cn.wwl.orimod.movement.FeelTuning.gaitWeights(speed);
        locoWeight = cn.wwl.orimod.movement.FeelTuning.approach(locoWeight, selected.equals("walk") ? 1 : 0, dt / .2f);
        gaitWalk = gait[0]; gaitRun = gait[1]; gaitSprint = gait[2];
        airWeight = cn.wwl.orimod.movement.FeelTuning.approach(airWeight, selected.equals("apex") ? 1 : 0, dt / .09f);
        layerWeights = SpiritAnimationRules.weights(speed, locoWeight, state.vertical, airWeight);
        var locomotionMask = kept(below == null ? BlendMask.ALL : below);
        baseLayers.clear(); base.write(clock, baseLayers);
        for (int i = 0; i < baseLayers.layerCount(); i++) {
            var clip = baseLayers.clip(i);
            blend.override(clip, sampledTimes.getOrDefault(clip.name(), baseLayers.time(i)),
                    baseLayers.weight(i) * layerWeights[0], locomotionMask);
        }
        GltfAnimation runClip = SpiritClips.get(model, "run"), riseClip = SpiritClips.get(model, "rise"), fallClip = SpiritClips.get(model, "fall");
        if (layerWeights[1] > .001f) blend.override(runClip, locoPhase * runClip.duration(), layerWeights[1], locomotionMask);
        if (layerWeights[2] > .001f) blend.override(riseClip, riseClip.loop(now), layerWeights[2], locomotionMask);
        if (layerWeights[3] > .001f) blend.override(fallClip, fallClip.loop(now), layerWeights[3], locomotionMask);
        // Full authored run supplies the spine and knees. Old additive gait/runPose would double both motions.
        secondary.gait(locoPhase, 0, 0, 0); runPoseWeight = 0; landRunWeight = 0;
        if (mask != null) {
            GltfAnimation action = SpiritClips.get(model, overlay);
            overlayClipName = action.name();
            float time = loops(action.name()) ? action.loop(overlayTime) : Math.clamp(overlayTime, 0, Math.max(0, action.duration() - .0001f));
            blend.override(action, time, 1, kept(mask));
        }
        // R94: torso twist / arch layered on any weapon overlay (spring-smoothed so starts and ends never snap).
        float tk = 1 - (float) Math.exp(-dt * 30);
        twist = Mth.lerp(tk, twist, swingTwist * .15f); spineArch = Mth.lerp(tk, spineArch, arch * .15f);
        if (Math.abs(twist) > 1e-4f) blend.additive(aimYaw, twist, 1, upperBody, rest);
        if (Math.abs(spineArch) > 1e-4f) blend.additive(aimPitch, spineArch, 1, upperBody, rest);
        float bob = .07f * Mth.sin(locoPhase * Mth.TWO_PI * 2) * locoWeight * (player.onGround() ? 1 : 0) + .03f * Mth.sin(now * 2.1f);
        if (cradleWeightRight > .002f) {
            cradleRight.clear(); SpiritPoses.held(cradleRight, 0, bob, loweredRight); cradleRight.publish();
            blend.override(cradleRight.clip(), now, ease(cradleWeightRight), cradleRightMask);
        }
        if (cradleWeightLeft > .002f) {
            cradleLeft.clear(); SpiritPoses.held(cradleLeft, 1, bob, loweredLeft); cradleLeft.publish();
            blend.override(cradleLeft.clip(), now, ease(cradleWeightLeft), cradleLeftMask);
        }
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
        if (state.alive && state.spirit && !state.sleeping) {
            float hurtAge = now - lastHurtAt;
            var hurt = SpiritClips.get(model, hurtClip); var recover = SpiritClips.get(model, "recover");
            float total = hurt.duration() + recover.duration();
            if (hurtAge >= 0 && hurtAge < total) {
                boolean recovering = hurtAge >= hurt.duration();
                GltfAnimation reaction = recovering ? recover : hurt;
                float reactionTime = recovering ? hurtAge - hurt.duration() : hurtAge;
                float share = .85f * Math.min(1, (total - hurtAge) / .12f);
                // Scale existing layers on the hurt mask, so this share is exact even under a weapon overlay.
                attenuate(blend, upperBody, share);
                blend.override(reaction, reactionTime, share, upperBody);
            }
        }
        if (secondary.clip() != null && !next.equals("death")) blend.additive(secondary.clip(), now, 1, BlendMask.ALL, rest);
        if (DEBUG_POSE) debugPose(blend);
        lastVelocity = player.getDeltaMovement(); lastHealth = player.getHealth(); lastNow = now; position = at; grounded = player.onGround(); wasMoving = moving;
        lastVertical = (float)player.getDeltaMovement().y;
        lastMoveId = movement.animationImpulseId; lastPrediction = movement.predictedJumpCount; lastHurt = player.hurtTime; previousMode = mode;
    }

    /** R74: per-frame springs for facing, lean, squash and ears (constants in FeelTuning). */
    private void feel(Vec3 at, float bodyYaw, float angular, float dt, String mode, boolean discontinuity) {
        Vec3 v = player.getDeltaMovement();
        // R95: whole-body pitch target for the pose family.
        float vh = (float) Math.sqrt(v.x * v.x + v.z * v.z) * 20, vv = (float) v.y * 20;
        float bodyTarget = SpiritAnimationRules.bodyPitch(family, vh, vv, player.getXRot());
        if (discontinuity || !Float.isFinite(facing)) {
            bodyPitch.reset(bodyTarget);
            facing = bodyYaw; leanPitch.reset(0); leanRoll.reset(0); squash.reset(1);
            smoothSpeed = lastSpeedForAccel = speed; return;
        }
        if (dt <= 0) return;
        bodyPitch.step(bodyTarget, dt);
        boolean busy = player.isUsingItem() || !player.getData(SpiritData.COMBAT_VIEW).mode().equals("idle") || mode.equals("bash_aim")
                || mode.equals("wall_slide") || player.isSleeping() || player.isPassenger() || player.isVisuallySwimming() || player.isFallFlying()
                || family.startsWith("swim") || family.equals("spin");
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
        if (player.onGround() && !mode.equals("dash")) pitchTarget *= .2f; // v014 already carries the load-bearing crouch/run lean.
        if (busy) pitchTarget = rollTarget = 0;
        // R94 mantle: lean into the wall on the pop, pitch well forward over the lip, knees tucked.
        boolean over = cn.wwl.orimod.client.SpiritMantle.overLip();
        if (mantling) { pitchTarget = 0; rollTarget = 0; freeFacing = false; facing = bodyYaw; }
        // v014 already authors the legs for airborne, water, crawl and mantle poses.
        secondary.tuck(0);
        leanPitch.step(pitchTarget, dt); leanRoll.step(rollTarget, dt);
        // R88/R93: run bounce on the ground; air stretches while rising and tucks while falling (WotW squash-stretch).
        float squashTarget = player.onGround()
                ? 1 + cn.wwl.orimod.movement.FeelTuning.GAIT_BOUNCE * (gaitRun + gaitSprint) * locoWeight * Mth.cos(locoPhase * Mth.TWO_PI * 2)
                : cn.wwl.orimod.movement.FeelTuning.airScale((float) v.y);
        if (busy) squashTarget = 1;
        // R95: the water-dash dart stretches the lying body along its length.
        if (family.startsWith("swim") || family.equals("spin")) squashTarget = 1 + .12f * dart;
        if (mantling || family.equals("crawl")) squashTarget = 1;
        squash.step(squashTarget, dt);
        squash.value = Math.clamp(squash.value, cn.wwl.orimod.movement.FeelTuning.SQUASH_MIN, cn.wwl.orimod.movement.FeelTuning.SQUASH_MAX);
    }

    private static final boolean DEBUG_POSE = Boolean.getBoolean("orimod.debugPose");
    private final java.util.Map<String, Float> poseError = new java.util.LinkedHashMap<>();
    /** Debug only (-Dorimod.debugPose=true): angle (rad) between the blended local rotation and each layer's target. */
    private void debugPose(AnimationBlend blend) {
        float[] state = table.newScratch();
        com.wf.gemrender.gltf.blend.BlendEvaluator.evaluate(table, blend, state, new com.wf.gemrender.gltf.blend.BlendEvaluator.Scratch());
        poseError.clear();
        var q = new Quaternionf();
        for (var e : java.util.Map.of("cradleR", cradleRight, "body", bodyPose).entrySet())
            for (int bone : new int[] {SpiritPoseLayer.UPPER_ARM_L, SpiritPoseLayer.FOREARM_L}) {
                int slot = table.slotOfName(SpiritPoseLayer.BONES[bone]); int o = NodeRotation.offsetOf(table, slot);
                q.set(state[o], state[o + 1], state[o + 2], state[o + 3]).normalize();
                var t = e.getValue().target(bone);
                poseError.put(e.getKey() + "_" + SpiritPoseLayer.BONES[bone], 2 * (float) Math.acos(Math.min(1, Math.abs(q.dot(t)))));
            }
    }

    private static float ease(float w) { w = Math.clamp(w, 0, 1); return w * w * (3 - 2 * w); }

    /**
     * R95: GemRender averages all override layers by weight (BlendEvaluator.accumulate) and weights cap at 1, so a pose
     * cannot simply be laid on top. Every other layer's mask is scaled by {@link #keep} (1 - the pose's share per node),
     * which makes the pose's share exact and fades in and out without a T-pose.
     */
    private BlendMask kept(BlendMask mask) {
        if (keep == null) return mask;
        float[] w = new float[keep.length];
        for (int i = 0; i < w.length; i++) w[i] = (mask == null ? 1 : mask.weight(i)) * keep[i];
        return BlendMask.weights(table, w);
    }

    private void keepOut(BlendMask mask, float share) {
        if (share <= 0) return;
        if (keep == null) { keep = new float[table.nodeCount()]; java.util.Arrays.fill(keep, 1); }
        for (int i = 0; i < keep.length; i++) keep[i] = Math.min(keep[i], 1 - share * mask.weight(i));
    }

    float cradleWeight(boolean left) { return ease(left ? cradleWeightLeft : cradleWeightRight); }
    float lowered(boolean left) { return left ? loweredLeft : loweredRight; }

    private static String handed(String name, boolean left) { return SpiritAnimationRules.handed(name, left); }

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
        this.table = table;
        bodyPose.bind(table); cradleRight.bind(table); cradleLeft.bind(table); sampledTimes.clear();
        lastHealth = player.getHealth(); hurtSequence = SpiritAnimationEvents.of(player).hurtSequence; lastHurtAt = -100;
        cradleRightMask = cradleRight.mask(SpiritPoseLayer.UPPER_ARM_L, SpiritPoseLayer.FOREARM_L, SpiritPoseLayer.HAND_L);
        cradleLeftMask = cradleLeft.mask(SpiritPoseLayer.UPPER_ARM_R, SpiritPoseLayer.FOREARM_R, SpiritPoseLayer.HAND_R);
        swimMix = glideMix = dart = cruise = 0; cradleWeightLeft = cradleWeightRight = loweredLeft = loweredRight = 0;
        createdAt = lastNow = now; clock = 0; position = player.getPosition(partial); grounded = player.onGround();
        selected = ""; shotUntil = -100; lag = speed = 0; runningGait = false; previousYaw = player.yBodyRot;
        var movement = player.getData(SpiritData.MOVEMENT);
        lastMoveId = movement.animationImpulseId; lastPrediction = movement.predictedJumpCount;
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

    private static boolean loops(String name) { return SpiritAnimationRules.loops(name); }

    private final AnimationBlend hurtLayers = new AnimationBlend();
    private void attenuate(AnimationBlend blend, BlendMask mask, float share) {
        hurtLayers.clear();
        for (int i = 0; i < blend.layerCount(); i++) {
            float[] weights = new float[table.nodeCount()];
            var original = blend.mask(i);
            for (int n = 0; n < weights.length; n++) weights[n] = (original == null ? 1 : original.weight(n)) * (1 - share * mask.weight(n));
            var kept = BlendMask.weights(table, weights);
            if (blend.mode(i) == BlendMode.ADDITIVE) hurtLayers.additive(blend.clip(i), blend.time(i), blend.weight(i), kept, blend.reference(i));
            else hurtLayers.override(blend.clip(i), blend.time(i), blend.weight(i), kept);
        }
        blend.clear();
        for (int i = 0; i < hurtLayers.layerCount(); i++) {
            if (hurtLayers.mode(i) == BlendMode.ADDITIVE) blend.additive(hurtLayers.clip(i), hurtLayers.time(i), hurtLayers.weight(i), hurtLayers.mask(i), hurtLayers.reference(i));
            else blend.override(hurtLayers.clip(i), hurtLayers.time(i), hurtLayers.weight(i), hurtLayers.mask(i));
        }
    }
}
