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
    private static CameraType humanCamera = CameraType.FIRST_PERSON;
    private static CameraType spiritCamera = CameraType.THIRD_PERSON_BACK;
    private static boolean wasSpirit;
    /** Spirit camera last applied or saved while in spirit form; {@code null} outside it. */
    private static cn.wwl.orimod.config.SpiritCameraView syncedCamera;
    private static boolean sentActive;
    private static boolean blockJumpUntilReleased, blockDashUntilReleased, blockBashUntilReleased;
    private static int inputSequence;
    private SpiritControls() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(DASH); event.register(BASH); event.register(PANEL); event.register(SOUL_LINK); event.register(LIGHT_BURST); }

    public static void movement(MovementInputUpdateEvent event) {
        var mc = Minecraft.getInstance();
        var player = event.getEntity();
        if (player != mc.player) return;
        var input = event.getInput();
        boolean active = mc.screen == null && mc.isWindowActive() && SpiritMovement.canMove(player);
        if (!SpiritData.isSpirit(player) && !sentActive) return;
        if (!input.jumping) blockJumpUntilReleased = false;
        if (!DASH.isDown()) blockDashUntilReleased = false;
        if (!BASH.isDown()) blockBashUntilReleased = false;
        var state = player.getData(SpiritData.MOVEMENT);
        // Vanilla checks this jump edge after the input event. Try the same guarded
        // transition before Ori consumes it, so usable wings take priority in midair.
        if (active && input.jumping && !blockJumpUntilReleased && !state.jumpHeld
                && player.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(player)
                && player.tryToStartFallFlying()) {
            mc.player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            active = false;
        }
        if (!active) { blockJumpUntilReleased = blockDashUntilReleased = blockBashUntilReleased = true; }
        int flags = (active ? MovementInputPayload.ACTIVE : 0)
                | (input.jumping && !blockJumpUntilReleased ? MovementInputPayload.JUMP : 0)
                | (DASH.isDown() && !blockDashUntilReleased ? MovementInputPayload.DASH : 0)
                | (BASH.isDown() && !blockBashUntilReleased ? MovementInputPayload.BASH : 0)
                | (input.shiftKeyDown ? MovementInputPayload.CROUCH : 0);
        int sequence = ++inputSequence;
        if (SpiritJumpPrediction.apply(player, state, sequence, flags, input.forwardImpulse, input.leftImpulse)) flags |= MovementInputPayload.PREDICTED_JUMP;
        PacketDistributor.sendToServer(new MovementInputPayload(sequence, player.level().getGameTime(), flags, input.forwardImpulse, input.leftImpulse, player.getYRot(), player.getXRot()));
        sentActive = active;
        if (SpiritMovement.canMove(player)) input.jumping = false;
        state.active = active;
        if (!active) {
            if (state.mode == BASH_AIM) player.setDeltaMovement(state.suspendedVelocity);
            state.mode = IDLE;
            state.activeTicks = 0;
        }
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
                if ((flags & MovementInputPayload.JUMP) == 0 || input.shiftKeyDown || player.onGround() || player.horizontalCollision) state.activeTicks = 0;
                else {
                    var direction = MovementRules.inputDirection(input.forwardImpulse, input.leftImpulse, player.getYRot(), false);
                    var glide = MovementRules.glideVelocity(player.getDeltaMovement(), direction,
                            cn.wwl.orimod.progression.Shard.AIR_CONTROL.equipped(player));
                    // R40: an updraft vent below lifts the glide.
                    if (cn.wwl.orimod.block.UpdraftBlock.lifting(player.level(), player)) glide = new net.minecraft.world.phys.Vec3(glide.x, MovementRules.updraftVelocity(glide.y), glide.z);
                    player.setDeltaMovement(glide);
                    player.fallDistance = 0;
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode == MoveMode.DASH) {
                if (player.horizontalCollision || input.shiftKeyDown) state.activeTicks = 0;
                else {
                    player.setDeltaMovement(MovementRules.dashVelocity(state.velocity, player.getDeltaMovement().y));
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode == WALL_SLIDE && !player.onGround() && !input.shiftKeyDown) {
                var direction = MovementRules.inputDirection(input.forwardImpulse, input.leftImpulse, player.getYRot(), false);
                if (SpiritMovement.touchingWall(player, direction, -1) != null) {
                    // R55: pushing into the wall climbs it once Climb is learned; otherwise slide down slowly.
                    boolean climb = MovementRules.climbing(cn.wwl.orimod.progression.Ability.CLIMB.unlocked(player), input.forwardImpulse);
                    player.setDeltaMovement(climb ? MovementRules.climbVelocity(player.getDeltaMovement()) : MovementRules.wallSlideVelocity(player.getDeltaMovement()));
                    player.fallDistance = 0;
                }
            }
            state.activeTicks--;
        }
        if (state.activeTicks <= 0) {
            if (state.mode == BASH_AIM) player.setDeltaMovement(state.suspendedVelocity);
            state.mode = IDLE;
        }
    }

    private static boolean waterDashHeld;
    private static long waterDashReadyAt;

    /** R56: Water Dash and faster swimming (client displacement; the server validates and plays the FX). */
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
        while (BASH.consumeClick()) { }
        while (PANEL.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.isWindowActive()) mc.setScreen(new SpiritScreen(!SpiritData.isAwakened(mc.player)));
        }
        while (LIGHT_BURST.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.isWindowActive())
                PacketDistributor.sendToServer(new cn.wwl.orimod.network.ProgressActionPayload("light_burst", ""));
        }
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
