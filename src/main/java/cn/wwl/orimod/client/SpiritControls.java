package cn.wwl.orimod.client;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.movement.MovementRules;
import static cn.wwl.orimod.movement.MoveMode.*;

import cn.wwl.orimod.network.ToggleSpiritPayload;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.AirMovement;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class SpiritControls {
    public static final KeyMapping TOGGLE = new KeyMapping("key.orimod.toggle_form", GLFW.GLFW_KEY_V, "key.categories.orimod");
    public static final KeyMapping DASH = new KeyMapping("key.orimod.dash", GLFW.GLFW_KEY_C, "key.categories.orimod");
    public static final KeyMapping BASH = new KeyMapping("key.orimod.bash", GLFW.GLFW_KEY_R, "key.categories.orimod");
    public static final KeyMapping PANEL = new KeyMapping("key.orimod.panel", GLFW.GLFW_KEY_G, "key.categories.orimod");
    public static final KeyMapping SOUL_LINK = new KeyMapping("key.orimod.soul_link", GLFW.GLFW_KEY_B, "key.categories.orimod");
    public static final KeyMapping LIGHT_BURST = new KeyMapping("key.orimod.light_burst", GLFW.GLFW_KEY_Z, "key.categories.orimod");
    /** Slot 2 retains its legacy mapping ID so custom Controls bindings migrate. */
    public static final KeyMapping CHARGE_FLAME = new KeyMapping("key.orimod.charge_flame", GLFW.GLFW_KEY_X, "key.categories.orimod");
    /** Slot 3 retains the original conflict-resolution policy. */
    public static final KeyMapping FLASH = new KeyMapping("key.orimod.flash", GLFW.GLFW_KEY_H, "key.categories.orimod");
    private static CameraType humanCamera = CameraType.FIRST_PERSON;
    private static CameraType spiritCamera = CameraType.THIRD_PERSON_BACK;
    private static boolean wasSpirit;
    /** Spirit camera last applied or saved while in spirit form; {@code null} outside it. */
    private static cn.wwl.orimod.config.SpiritCameraView syncedCamera;
    private static boolean sentActive;
    private static boolean blockJumpUntilReleased, blockDashUntilReleased, blockBashUntilReleased;
    private static int inputSequence;
    private SpiritControls() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(DASH); event.register(BASH); event.register(PANEL); event.register(SOUL_LINK); event.register(LIGHT_BURST); event.register(CHARGE_FLAME); event.register(FLASH); }

    public static void movement(MovementInputUpdateEvent event) {
        var mc = Minecraft.getInstance();
        var player = event.getEntity();
        if (player != mc.player) return;
        if (bladeOwner != player || bladeWorld != player.level()) {
            bladeOwner = player; bladeWorld = player.level();
            bladeMotion = new cn.wwl.orimod.movement.BladeMotion();
            feelBlade = net.minecraft.world.phys.Vec3.ZERO; feelControlled = false;
        }
        var input = event.getInput();
        if(RestorationClient.active()){input.forwardImpulse=input.leftImpulse=0;input.up=input.down=input.left=input.right=input.jumping=input.shiftKeyDown=false;player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);return;}
        boolean active = mc.screen == null && mc.isWindowActive() && SpiritMovement.canMove(player);
        if (!SpiritData.isSpirit(player) && !sentActive) return;
        if (!input.jumping) blockJumpUntilReleased = false;
        if (!DASH.isDown()) blockDashUntilReleased = false;
        if (!BASH.isDown()) blockBashUntilReleased = false;
        var state = player.getData(SpiritData.MOVEMENT);
        // Vanilla checks this jump edge after the input event. Try the same guarded
        // transition before Ori consumes it, so usable wings take priority in midair.
        boolean ownsJump = MovementRules.ownsJump(SpiritMovement.canMove(player), player.getAbilities().mayfly);
        if (active && ownsJump && input.jumping && !blockJumpUntilReleased && !state.jumpHeld
                && player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player)
                && player.tryToStartFallFlying()) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            active = false;
        }
        if (!active) { blockJumpUntilReleased = blockDashUntilReleased = blockBashUntilReleased = true; }
        int flags = (active ? MovementInputPayload.ACTIVE : 0)
                | (ownsJump && input.jumping && !blockJumpUntilReleased ? MovementInputPayload.JUMP : 0)
                | (DASH.isDown() && !blockDashUntilReleased ? MovementInputPayload.DASH : 0)
                | (BASH.isDown() && !blockBashUntilReleased ? MovementInputPayload.BASH : 0)
                | (input.shiftKeyDown ? MovementInputPayload.CROUCH : 0);
        cn.wwl.orimod.movement.FeatherGlide.input(player,active,(flags & MovementInputPayload.JUMP)!=0,input.shiftKeyDown);
        boolean handoff=cn.wwl.orimod.movement.FeatherGlide.migrating(player);
        if(handoff && active){state.mode=GLIDE;state.activeTicks=MovementRules.GLIDE_LEASE_TICKS;}
        int sequence = ++inputSequence;
        if (!handoff && SpiritJumpPrediction.apply(player, state, sequence, flags, input.forwardImpulse, input.leftImpulse)) flags |= MovementInputPayload.PREDICTED_JUMP;
        else if (!handoff && SpiritMovement.canMove(player)) SpiritActionPrediction.apply(player, state, flags, input.forwardImpulse, input.leftImpulse);
        PacketDistributor.sendToServer(new MovementInputPayload(sequence, player.level().getGameTime(), flags, input.forwardImpulse, input.leftImpulse, player.getYRot(), player.getXRot()));
        sentActive = active;
        if (ownsJump) input.jumping = false;
        state.active = active;
        if (!active) {
            if (state.mode == BASH_AIM) player.setDeltaMovement(state.suspendedVelocity);
            state.mode = IDLE;
            state.activeTicks = 0;
        }
        // R94: a ledge mantle owns the velocity while it runs; jump / crouch / other actions cancel it inside tick().
        boolean jumpEdge = (flags & MovementInputPayload.JUMP) != 0 && !prevJumpHeld;
        if (active && SpiritMovement.canMove(player) && SpiritMantle.tick(player, state, input, jumpEdge)) {
            if (state.activeTicks > 0 && --state.activeTicks <= 0) state.mode = IDLE;
            feelControlled = false;
            prevJumpHeld = (flags & MovementInputPayload.JUMP) != 0;
            return;
        }
        if (!active) SpiritMantle.end(player);
        if (state.activeTicks > 0 && active) {
            if (state.mode == BASH_AIM) {
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.fallDistance = state.suspendedFallDistance;
                input.forwardImpulse = input.leftImpulse = 0;
            } else if (state.mode == GRAPPLE) {
                if (input.shiftKeyDown) state.activeTicks = 0;
                else {
                    var velocity = AirMovement.grappleVelocity(player, state.targetPosition);
                    if (!player.level().noCollision(player, player.getBoundingBox().deflate(.01).expandTowards(velocity))) state.activeTicks = 0;
                    else { player.setDeltaMovement(velocity); player.fallDistance = 0; }
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode == STOMP && !player.onGround()) {
                player.setDeltaMovement(MovementRules.stompVelocity(player.getDeltaMovement().y));
                input.forwardImpulse = input.leftImpulse = 0;
            } else if (state.mode == GLIDE) {
                if ((flags & MovementInputPayload.JUMP) == 0 && !handoff || input.shiftKeyDown || player.onGround() || player.horizontalCollision) state.activeTicks = 0;
                else {
                    var direction = MovementRules.inputDirection(input.forwardImpulse, input.leftImpulse, player.getYRot(), false);
                    var glide = cn.wwl.orimod.movement.FeatherGlide.velocity(player, direction);
                    // R40: an updraft vent below lifts the glide.

                    player.setDeltaMovement(glide);
                    player.fallDistance = 0;
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode == MoveMode.DASH) {
                if (player.horizontalCollision || input.shiftKeyDown) state.activeTicks = 0;
                else {
                    // R73: burst then ease out (FeelTuning.DASH_PROFILE); the exit speed carries into the run.
                    var dash = MovementRules.dashVelocity(state.velocity, player.getDeltaMovement().y);
                    double k = cn.wwl.orimod.movement.FeelTuning.dashScale(state.activeTicks);
                    player.setDeltaMovement(dash.x * k, dash.y, dash.z * k);
                    if (state.activeTicks == 1) dashExit = new net.minecraft.world.phys.Vec3(state.velocity.x, 0, state.velocity.z);
                    wasDash = true; // R77: the server may end the dash before the client's last tick

                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode == WALL_SLIDE && !player.onGround() && !input.shiftKeyDown) {
                var direction = MovementRules.inputDirection(input.forwardImpulse, input.leftImpulse, player.getYRot(), false);
                var wall = SpiritMovement.slidingWall(player, direction, state.wallDirection);
                if (wall != null) {
                    // R99: same Sticky/Climb priority and velocity as the server.
                    player.setDeltaMovement(SpiritMovement.wallVelocity(player, direction, input.forwardImpulse, wall));
                    player.fallDistance = 0;
                }
            }
            state.activeTicks--;
        }
        if (state.activeTicks <= 0) {
            if (state.mode == BASH_AIM) player.setDeltaMovement(state.suspendedVelocity);
            state.mode = IDLE;
            if (active) feel(player, state, input, (flags & MovementInputPayload.JUMP) != 0);
            else { feelControlled = false; cancelBlade(player); }
        } else { feelControlled = false; cancelBlade(player); }
        prevJumpHeld = (flags & MovementInputPayload.JUMP) != 0;
    }

    // ---- R73 game feel: explicit ground/air curves and vertical shaping (constants in FeelTuning) ----
    private static net.minecraft.world.phys.Vec3 feelH = net.minecraft.world.phys.Vec3.ZERO, dashExit;
    private static float feelFriction = .91f;
    private static boolean feelControlled, prevJumpHeld, wasDash;
    private static long wallJumpAt = -100;
    private static int feelAction, feelPrediction;
    private static cn.wwl.orimod.movement.BladeMotion bladeMotion = new cn.wwl.orimod.movement.BladeMotion();
    private static net.minecraft.world.entity.player.Player bladeOwner;
    private static net.minecraft.world.level.Level bladeWorld;
    private static net.minecraft.world.phys.Vec3 feelBlade = net.minecraft.world.phys.Vec3.ZERO;

    private static void cancelBlade(net.minecraft.world.entity.player.Player player) {
        bladeMotion.observe(player.getData(SpiritData.COMBAT_VIEW), false, player.onGround(), player.getYRot());
        feelBlade = net.minecraft.world.phys.Vec3.ZERO;
    }

    public static java.util.Map<String, Object> bladeDiagnostics() {
        return java.util.Map.of("groundTicks", bladeMotion.groundTicks(), "holdTicks", bladeMotion.holdTicks(),
                "step", new double[]{feelBlade.x, feelBlade.y, feelBlade.z});
    }

    private static void feel(net.minecraft.world.entity.player.Player player, cn.wwl.orimod.movement.MovementState state,
                             net.minecraft.client.player.Input input, boolean jumpHeld) {
        var d = player.getDeltaMovement();
        long now = player.level().getGameTime();
        boolean airImpulse = bladeMotion.observe(player.getData(SpiritData.COMBAT_VIEW), true, player.onGround(), player.getYRot());
        // Recover the pre-friction velocity vanilla travel stored last tick, so our curve owns the motion.
        // Anything external (collision, knockback, Bash/Launch impulses) shows up in the observed value and is kept.
        // Impulses written straight into the velocity (server-confirmed Launch/Bash/grapple end, predicted wall jumps,
        // knockback) were never through friction: take them as they are.
        boolean newAction = state.actionId != feelAction, newPrediction = state.predictedJumpCount != feelPrediction;
        boolean raw = airImpulse || player.hurtTime == player.hurtDuration - 1
                || newAction && (state.mode == RESUME || state.lastAction == LAUNCH || state.lastAction == BASH_LAUNCH || state.lastAction == GRAPPLE_END
                    || state.lastAction == WALL_JUMP && !newPrediction)
                || newPrediction && state.lastAction == WALL_JUMP;
        feelAction = state.actionId; feelPrediction = state.predictedJumpCount;
        float div = raw ? 1 : feelFriction;
        var observed = new net.minecraft.world.phys.Vec3(d.x / div, 0, d.z / div);
        // Remove last frame's forced step before steering; add this frame's step only AFTER braking.
        // A collision/external impulse breaks the match and is left untouched.
        var h = !raw && feelControlled && observed.distanceToSqr(feelH) < 1e-4 ? feelH.subtract(feelBlade) : observed;
        if (wasDash && dashExit == null) dashExit = player.getDeltaMovement();
        wasDash = false;
        if (dashExit != null) {
            var flat = new net.minecraft.world.phys.Vec3(dashExit.x, 0, dashExit.z);
            if (flat.lengthSqr() > 1e-6) h = flat.normalize().scale(cn.wwl.orimod.movement.FeelTuning.DASH_EXIT_SPEED);
            dashExit = null;
        }
        if (state.lastAction == WALL_JUMP && state.predictedJumpCount > 0 && now - state.jumpPressedAt <= 1) wallJumpAt = state.jumpPressedAt;
        boolean grounded = player.onGround();
        var wish = MovementRules.inputDirection(input.forwardImpulse, input.leftImpulse, player.getYRot(), false);
        double scale = input.shiftKeyDown ? cn.wwl.orimod.movement.FeelTuning.CROUCH_SCALE : player.isUsingItem() ? cn.wwl.orimod.movement.FeelTuning.USING_ITEM_SCALE : 1;
        double control = now - wallJumpAt < cn.wwl.orimod.movement.FeelTuning.WALL_JUMP_CONTROL_TICKS ? cn.wwl.orimod.movement.FeelTuning.WALL_JUMP_CONTROL_SCALE : 1;
        h = cn.wwl.orimod.movement.FeelTuning.horizontal(h, wish, grounded, scale, control);
        feelBlade = bladeMotion.ground(h, grounded, path -> cn.wwl.orimod.movement.BladeSupport.supported(player, path));
        h = h.add(feelBlade);
        double vy = grounded && d.y <= 0 ? d.y : cn.wwl.orimod.movement.FeelTuning.vertical(d.y, jumpHeld, prevJumpHeld && !jumpHeld);
        vy = bladeMotion.vertical(vy);
        player.setDeltaMovement(h.x, vy, h.z);
        input.forwardImpulse = input.leftImpulse = 0;   // vanilla moveRelative would add its own acceleration on top
        feelH = h; feelControlled = true;
        var below = player.level().getBlockState(player.getBlockPosBelowThatAffectsMyMovement());
        feelFriction = grounded ? below.getFriction(player.level(), player.getBlockPosBelowThatAffectsMyMovement(), player) * .91f : .91f;
    }


    /** R59: count the hold of the Charge Flame key; a charged release sends the burst, charging shows gathering sparks. */
    /** Menus cancel unfinished holds; opening the wheel never releases a charged attack. */
    public static void opening(net.neoforged.neoforge.client.event.ScreenEvent.Opening event) {
        if(event.getNewScreen()==null)return;
        SkillControls.cancel();
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&sentActive){
            PacketDistributor.sendToServer(new MovementInputPayload(++inputSequence,mc.player.level().getGameTime(),0,0,0,mc.player.getYRot(),mc.player.getXRot()));
            sentActive=false;
        }
    }

    private static boolean waterDashHeld;
    private static long waterDashReadyAt;

    /** R56: Water Dash and faster swimming (client displacement; the server validates and plays the FX). */
    private static boolean burrowing;

    /** R112 local-only visual observation; it grants no movement permission. */
    public static boolean burrowing(net.minecraft.world.entity.player.Player player) {
        return player == Minecraft.getInstance().player && burrowing;
    }
    /** R64: steer through spirit sand along the look direction; pop out with a boost. */
    private static void burrowTick(Minecraft mc) {
        var player = mc.player;
        if (player == null || !cn.wwl.orimod.block.SpiritSandBlock.canBurrow(player)) { burrowing = false; return; }
        boolean in = cn.wwl.orimod.block.SpiritSandBlock.inside(player);
        if (in) player.setDeltaMovement(cn.wwl.orimod.block.SpiritSandBlock.burrowVelocity(player.getLookAngle()));
        else if (burrowing) player.setDeltaMovement(cn.wwl.orimod.block.SpiritSandBlock.exitVelocity(player.getLookAngle()));
        burrowing = in;
    }

    private static void waterTick(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.screen != null || !cn.wwl.orimod.movement.WaterMovement.active(player)) { waterDashHeld = DASH.isDown(); return; }
        var look = player.getLookAngle();
        boolean down = DASH.isDown(), now = down && !waterDashHeld;
        waterDashHeld = down;
        long time = player.level().getGameTime();
        if (now && time >= waterDashReadyAt) {
            waterDashReadyAt = time + cn.wwl.orimod.movement.WaterMovement.DASH_COOLDOWN;
            player.setDeltaMovement(cn.wwl.orimod.movement.WaterMovement.dashVelocity(look));
            PacketDistributor.sendToServer(new cn.wwl.orimod.network.ProgressActionPayload("water_dash", ""));
        } else player.setDeltaMovement(cn.wwl.orimod.movement.WaterMovement.swimVelocity(player.getDeltaMovement(), look, player.input != null && player.input.forwardImpulse > 0));
    }

    public static void tick(ClientTickEvent.Post event) {
        if(RestorationClient.active()){SkillControls.cancel();net.minecraft.client.KeyMapping.releaseAll();return;}
        var mc = Minecraft.getInstance();
        boolean spirit = mc.player != null && SpiritData.isSpirit(mc.player);
        if (mc.player == null) {
            cn.wwl.orimod.network.ProgressReplyPayload.guidePending = false;
            cn.wwl.orimod.network.RuinOfferPayload.pending = null;
            cn.wwl.orimod.network.RuinGuidePayload.current = null;
        }
        else if (mc.screen == null && cn.wwl.orimod.network.ProgressReplyPayload.guidePending) {
            cn.wwl.orimod.network.ProgressReplyPayload.guidePending = false;
            mc.setScreen(new SpiritScreen(true));
        }
        if (mc.player != null && cn.wwl.orimod.network.RuinOfferPayload.pending != null) {
            var offer = cn.wwl.orimod.network.RuinOfferPayload.pending;
            cn.wwl.orimod.network.RuinOfferPayload.pending = null;
            mc.setScreen(new RuinScreen(offer));
        }
        SpiritKeyDefaults.tick(mc);
        if (spirit && !wasSpirit) {
            if (cn.wwl.orimod.config.OriClientConfig.SPEC.isLoaded()) spiritCamera = CameraType.valueOf(cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.get().name());
            humanCamera = mc.options.getCameraType();
            mc.options.setCameraType(spiritCamera);
        } else if (!spirit && wasSpirit) {
            spiritCamera = mc.options.getCameraType();
            mc.options.setCameraType(humanCamera);
        }
        if (spirit && wasSpirit && cn.wwl.orimod.config.OriClientConfig.SPEC.isLoaded()) {
            var configured = cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.get();
            var live = cn.wwl.orimod.config.SpiritCameraView.parse(mc.options.getCameraType().name());
            switch (cn.wwl.orimod.config.SpiritCameraSync.decide(configured, syncedCamera, live)) {
                case APPLY_SETTING -> mc.options.setCameraType(CameraType.valueOf(configured.name()));
                case SAVE_CAMERA -> {
                    cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.set(live);
                    cn.wwl.orimod.config.OriClientConfig.SPEC.save();
                    configured = live;
                }
                case NONE -> {}
            }
            syncedCamera = configured;
        } else if (!spirit) syncedCamera = null;
        wasSpirit = spirit;
        if (sentActive && (mc.player == null || mc.screen != null || !mc.isWindowActive() || !spirit)) {
            blockJumpUntilReleased = blockDashUntilReleased = blockBashUntilReleased = true;
            if (mc.player != null) {
                PacketDistributor.sendToServer(new MovementInputPayload(++inputSequence, mc.player.level().getGameTime(), 0, 0, 0, mc.player.getYRot(), mc.player.getXRot()));
                var state = mc.player.getData(SpiritData.MOVEMENT);
                if (state.mode == BASH_AIM) mc.player.setDeltaMovement(state.suspendedVelocity);
                state.mode = IDLE; state.activeTicks = 0; state.active = false;
                state.predictedJumps.clear();
            }
            sentActive = false;
        }
        while (DASH.consumeClick()) { /* Edge detection uses the held state; menus never queue a dash. */ }
        waterTick(mc);
        burrowTick(mc);
        while (BASH.consumeClick()) { }
        SkillControls.tick();
        while (SOUL_LINK.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.isWindowActive())
                PacketDistributor.sendToServer(new cn.wwl.orimod.network.ProgressActionPayload("soul_link", ""));
        }
        while (TOGGLE.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.isWindowActive()) {
                PacketDistributor.sendToServer(new ToggleSpiritPayload());
            }
        }
    }
}
