package cn.wwl.orimod.client;

import cn.wwl.orimod.network.ToggleSpiritPayload;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.movement.SpiritMovement;
import cn.wwl.orimod.movement.AirMovement;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
    private static CameraType humanCamera = CameraType.FIRST_PERSON;
    private static CameraType spiritCamera = CameraType.THIRD_PERSON_BACK;
    private static boolean wasSpirit;
    private static boolean sentActive;
    private static boolean blockJumpUntilReleased, blockDashUntilReleased, blockBashUntilReleased;
    private static int inputSequence;
    private SpiritControls() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); event.register(DASH); event.register(BASH); event.register(PANEL); }

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
        if (!active) { blockJumpUntilReleased = blockDashUntilReleased = blockBashUntilReleased = true; }
        int flags = (active ? MovementInputPayload.ACTIVE : 0)
                | (input.jumping && !blockJumpUntilReleased ? MovementInputPayload.JUMP : 0)
                | (DASH.isDown() && !blockDashUntilReleased ? MovementInputPayload.DASH : 0)
                | (BASH.isDown() && !blockBashUntilReleased ? MovementInputPayload.BASH : 0)
                | (input.shiftKeyDown ? MovementInputPayload.CROUCH : 0);
        int sequence = ++inputSequence;
        var state = player.getData(SpiritData.MOVEMENT);
        if (SpiritJumpPrediction.apply(player, state, sequence, flags, input.forwardImpulse, input.leftImpulse)) flags |= MovementInputPayload.PREDICTED_JUMP;
        PacketDistributor.sendToServer(new MovementInputPayload(sequence, player.level().getGameTime(), flags, input.forwardImpulse, input.leftImpulse, player.getYRot(), player.getXRot()));
        sentActive = active;
        if (SpiritMovement.canMove(player)) input.jumping = false;
        state.active = active;
        if (!active) {
            if (state.mode.equals("bash_aim")) player.setDeltaMovement(state.suspendedVelocity);
            state.mode = "idle";
            state.activeTicks = 0;
        }
        if (state.activeTicks > 0 && active) {
            if (state.mode.equals("bash_aim")) {
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.fallDistance = state.suspendedFallDistance;
                input.forwardImpulse = input.leftImpulse = 0;
            } else if (state.mode.equals("grapple")) {
                if (input.shiftKeyDown) state.activeTicks = 0;
                else {
                    var velocity = AirMovement.grappleVelocity(player, state.targetPosition);
                    if (!player.level().noCollision(player, player.getBoundingBox().deflate(.01).expandTowards(velocity))) state.activeTicks = 0;
                    else { player.setDeltaMovement(velocity); player.fallDistance = 0; }
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode.equals("stomp") && !player.onGround()) {
                player.setDeltaMovement(0, Math.max(AirMovement.STOMP_SPEED, player.getDeltaMovement().y - .55), 0);
                input.forwardImpulse = input.leftImpulse = 0;
            } else if (state.mode.equals("glide")) {
                if ((flags & MovementInputPayload.JUMP) == 0 || input.shiftKeyDown || player.onGround() || player.horizontalCollision) state.activeTicks = 0;
                else {
                    var steer = new cn.wwl.orimod.movement.MovementState();
                    steer.forward = input.forwardImpulse; steer.left = input.leftImpulse; steer.yaw = player.getYRot();
                    var motion = player.getDeltaMovement();
                    var horizontal = new net.minecraft.world.phys.Vec3(motion.x, 0, motion.z).scale(.92)
                            .add(SpiritMovement.inputDirection(steer, false).scale(cn.wwl.orimod.progression.Shard.AIR_CONTROL.equipped(player) ? .075 : .06));
                    if (horizontal.lengthSqr() > .25) horizontal = horizontal.normalize().scale(.5);
                    player.setDeltaMovement(horizontal.x, Math.max(motion.y, AirMovement.GLIDE_FALL_SPEED), horizontal.z);
                    player.fallDistance = 0;
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode.equals("dash")) {
                if (player.horizontalCollision || input.shiftKeyDown) state.activeTicks = 0;
                else {
                    player.setDeltaMovement(state.velocity.x, Math.min(0, player.getDeltaMovement().y), state.velocity.z);
                    input.forwardImpulse = input.leftImpulse = 0;
                }
            } else if (state.mode.equals("wall_slide") && !player.onGround() && !input.shiftKeyDown) {
                var probe = new cn.wwl.orimod.movement.MovementState();
                probe.forward = input.forwardImpulse; probe.left = input.leftImpulse; probe.yaw = player.getYRot();
                if (SpiritMovement.touchingWall(player, SpiritMovement.inputDirection(probe, false), -1) != null) {
                    var velocity = player.getDeltaMovement();
                    player.setDeltaMovement(velocity.x, Math.max(velocity.y, SpiritMovement.WALL_SLIDE_SPEED), velocity.z);
                    player.fallDistance = 0;
                }
            }
            state.activeTicks--;
        }
        if (state.activeTicks <= 0) {
            if (state.mode.equals("bash_aim")) player.setDeltaMovement(state.suspendedVelocity);
            state.mode = "idle";
        }
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
            if (cn.wwl.orimod.config.OriClientConfig.SPEC.isLoaded()) spiritCamera = CameraType.valueOf(cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.get());
            humanCamera = mc.options.getCameraType();
            mc.options.setCameraType(spiritCamera);
        } else if (!spirit && wasSpirit) {
            spiritCamera = mc.options.getCameraType();
            mc.options.setCameraType(humanCamera);
        }
        if (spirit && wasSpirit && cn.wwl.orimod.config.OriClientConfig.SPEC.isLoaded()
                && !mc.options.getCameraType().name().equals(cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.get())) {
            cn.wwl.orimod.config.OriClientConfig.SPIRIT_CAMERA.set(mc.options.getCameraType().name());
            cn.wwl.orimod.config.OriClientConfig.SPEC.save();
        }
        wasSpirit = spirit;
        if (sentActive && (mc.player == null || mc.screen != null || !mc.isWindowActive() || !spirit)) {
            blockJumpUntilReleased = blockDashUntilReleased = blockBashUntilReleased = true;
            if (mc.player != null) {
                PacketDistributor.sendToServer(new MovementInputPayload(++inputSequence, mc.player.level().getGameTime(), 0, 0, 0, mc.player.getYRot(), mc.player.getXRot()));
                var state = mc.player.getData(SpiritData.MOVEMENT);
                if (state.mode.equals("bash_aim")) mc.player.setDeltaMovement(state.suspendedVelocity);
                state.mode = "idle"; state.activeTicks = 0; state.active = false;
                state.predictedJumps.clear();
            }
            sentActive = false;
        }
        while (DASH.consumeClick()) { /* Edge detection uses the held state; menus never queue a dash. */ }
        while (BASH.consumeClick()) { }
        while (PANEL.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.isWindowActive()) mc.setScreen(new SpiritScreen(!SpiritData.isAwakened(mc.player)));
        }
        while (TOGGLE.consumeClick()) {
            if (mc.player != null && mc.screen == null && mc.isWindowActive()) {
                PacketDistributor.sendToServer(new ToggleSpiritPayload());
            }
        }
    }
}
