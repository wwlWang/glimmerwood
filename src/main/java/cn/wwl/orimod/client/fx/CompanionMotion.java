package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.item.SpiritLightItem;
import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.network.CompanionHintPayload;
import cn.wwl.orimod.spirit.SpiritData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** R97: Glimmer, an original light companion with Ori-like inertial following; entirely client-side, no entity. */
public final class CompanionMotion {
    public static final int MAX_STATES = 16, RANGE = 48, DART_TICKS = 4, HINT_TICKS = 50, SEEK_TICKS = 30,
            SEEK_INTERVAL = 160, COMBAT_TICKS = 60, SEEK_PEACE_TICKS = 100, PULSE_TICKS = 12;
    public static final double OMEGA = 9, STEP_SECONDS = .05, MAX_SUBSTEP = .025, SNAP_DISTANCE = 3,
            DART_FRACTION = .35, RUN_SPEED = 4, IDLE_SPEED = .5, SEEK_RANGE = 12, MOOD_SECONDS = .3,
            SHOULDER_RIGHT = .45, SHOULDER_BACK = .25, RUN_BACK = .35, SHOULDER_HEIGHT = .9,
            SWIM_HEIGHT = .55, GLIDE_ABOVE = .3, RETURN_DISTANCE = .05,
            DRIFT_X = .12, DRIFT_Y = .06, DRIFT_Z = .1, DRIFT_PERIOD_X = 3.1, DRIFT_PERIOD_Y = 2.3, DRIFT_PERIOD_Z = 3.7,
            SEEK_ORBIT = .25, SEEK_ABOVE = .2, SEEK_PERIOD = 1, ITEM_ABOVE = .3, HINT_ABOVE = 1;
    public static final int CALM = 0xF2F8FF, COMBAT = 0xBDF3FF, WORRIED = 0xFFD9A0;
    public static final float LOW_HEALTH = .3f, PETAL_RADIUS = .035f, PETAL_ORBIT = .16f, PETAL_PERIOD = 1.2f,
            PETAL_TILT = 25, COMBAT_PETAL_SPEED = 1.6f, COMBAT_ALPHA = .05f, LOW_HEALTH_PERIOD = 1.5f,
            DARK_BOOST = 1.3f, POOL_RADIUS = 3, TAIL_SCALE = .06f, TAIL_MAX = .5f, TAIL_WIDTH = .06f,
            PULSE_STRENGTH = .5f, HINT_VOLUME = .4f, HINT_PITCH = 1.8f;
    public static final int DARK_LIGHT_LIMIT = 5;
    private static final Int2ObjectMap<State> STATES = new Int2ObjectOpenHashMap<>();
    private static Object trackedLevel;

    private CompanionMotion() {}

    /** R97: tick state doubles as a pure spring fixture; velocities are in blocks/second. */
    public static final class State {
        public Vec3 pos, vel = Vec3.ZERO, prevPos, dartTarget, seekTarget;
        private Vec3 anchor;
        public int dartTicks, seekTicks, pulseTicks, sinceCombat = SEEK_PEACE_TICKS + 1;
        public Vec3 mood = rgb(CALM), prevMood = mood;
        private Player owner;
        private Vec3 lastOwner;
        private int nextSeek = SEEK_INTERVAL, seekEntity = -1;
        private boolean hintFlight, returning;
        private double petalPhase, previousPetalPhase;
        public State(Vec3 at) { pos = prevPos = anchor = at; }

        public java.util.Map<String, Object> diagnostics() {
            return java.util.Map.of("pos", coordinates(pos), "vel", coordinates(vel), "anchor", coordinates(anchor),
                    "anchorDistance", pos.distanceTo(anchor), "returning", returning, "seekTicks", seekTicks,
                    "dartTicks", dartTicks, "hintFlight", hintFlight);
        }

        public void dart(Vec3 target) {
            dartTarget = target;
            dartTicks = DART_TICKS;
            sinceCombat = 0;
        }

        public void seek(Vec3 target, boolean hint) {
            if (hintFlight && seekTicks > 0 && !hint) return;
            seekTarget = target;
            seekTicks = hint ? HINT_TICKS : SEEK_TICKS;
            hintFlight = hint;
            seekEntity = -1;
            returning = true;
        }

        /** Intentional excursions spring out and back; a real owner teleport always snaps and cancels the excursion. */
        public void step(Vec3 anchor, boolean ownerTeleported) {
            this.anchor = anchor;
            prevPos = pos;
            if (ownerTeleported) {
                pos = prevPos = anchor; vel = Vec3.ZERO;
                dartTicks = seekTicks = 0; dartTarget = seekTarget = null;
                hintFlight = returning = false; seekEntity = -1;
                return;
            }
            Vec3 target = anchor;
            boolean excursion = seekTicks > 0 || dartTicks > 0;
            if (seekTicks > 0) {
                target = seekTarget;
                if (!hintFlight) {
                    double a = (SEEK_TICKS - seekTicks) * STEP_SECONDS * Math.PI * 2 / SEEK_PERIOD;
                    target = target.add(Math.cos(a) * SEEK_ORBIT, SEEK_ABOVE, Math.sin(a) * SEEK_ORBIT);
                }
                if (--seekTicks == 0) { hintFlight = false; seekEntity = -1; seekTarget = null; }
            }
            if (dartTicks > 0) {
                target = anchor.lerp(dartTarget, DART_FRACTION);
                returning = true;
                if (--dartTicks == 0) dartTarget = null;
            }
            if (!excursion && !returning && pos.distanceToSqr(anchor) > SNAP_DISTANCE * SNAP_DISTANCE) {
                pos = prevPos = anchor; vel = Vec3.ZERO;
            } else {
                spring(target, STEP_SECONDS);
                if (!excursion && pos.distanceToSqr(anchor) < RETURN_DISTANCE * RETURN_DISTANCE) returning = false;
            }
        }

        public void spring(Vec3 target, double seconds) {
            int steps = Math.max(1, (int) Math.ceil(seconds / MAX_SUBSTEP));
            double dt = seconds / steps;
            for (int i = 0; i < steps; i++) {
                var acceleration = target.subtract(pos).scale(OMEGA * OMEGA).subtract(vel.scale(2 * OMEGA));
                vel = vel.add(acceleration.scale(dt));
                pos = pos.add(vel.scale(dt));
            }
        }

        public Vec3 position(float partial) { return prevPos.lerp(pos, Math.clamp(partial, 0, 1)); }
        public boolean combat() { return sinceCombat < COMBAT_TICKS; }
        public boolean guiding() { return hintFlight && seekTicks > 0; }
        public double petalPhase(float partial) { return previousPetalPhase + (petalPhase - previousPetalPhase) * partial; }
        public int color(float partial) {
            var c = prevMood.lerp(mood, Math.clamp(partial, 0, 1));
            return (int) Math.round(c.x) << 16 | (int) Math.round(c.y) << 8 | (int) Math.round(c.z);
        }
        public float pulse(float partial) {
            return pulseTicks > 0 ? 1 + PULSE_STRENGTH * (float) Math.sin(Math.PI * (PULSE_TICKS - pulseTicks + partial) / PULSE_TICKS) : 1;
        }
    }

    /** Pure body-relative anchor, with dash using only the spring's own lag. */
    public static Vec3 anchor(Vec3 owner, double height, float yaw, double seconds, double speed, boolean dash, boolean glide, boolean swim) {
        double a = Math.toRadians(yaw), right = SHOULDER_RIGHT * (glide ? .5 : 1);
        double back = (SHOULDER_BACK + (speed > RUN_SPEED && !dash ? RUN_BACK : 0)) * (glide ? .5 : 1);
        double up = glide ? height + GLIDE_ABOVE : height * (swim ? SWIM_HEIGHT : SHOULDER_HEIGHT);
        return owner.add(-Math.cos(a) * right + Math.sin(a) * back + DRIFT_X * Math.sin(seconds * Math.PI * 2 / DRIFT_PERIOD_X),
                up + DRIFT_Y * Math.sin(seconds * Math.PI * 2 / DRIFT_PERIOD_Y),
                -Math.sin(a) * right - Math.cos(a) * back + DRIFT_Z * Math.sin(seconds * Math.PI * 2 / DRIFT_PERIOD_Z));
    }

    private static Vec3 anchor(Player player, double speed) {
        var movement = player.getData(SpiritData.MOVEMENT);
        return anchor(player.position(), player.getBbHeight(), player.yBodyRot, player.level().getGameTime() * STEP_SECONDS,
                speed, movement.mode == MoveMode.DASH && movement.activeTicks > 0,
                player.isFallFlying() || movement.mode == MoveMode.GLIDE && movement.activeTicks > 0, player.isSwimming());
    }

    public static void clear() { STATES.clear(); trackedLevel = null; }

    private static void world() {
        var level = Minecraft.getInstance().level;
        if (trackedLevel != level) { STATES.clear(); trackedLevel = level; }
    }

    public static State state(Player player) {
        var state = STATES.get(player.getId());
        return state != null && state.owner == player ? state : null;
    }

    private static double[] coordinates(Vec3 at) { return new double[]{at.x, at.y, at.z}; }

    public static java.util.Map<String, Object> diagnostics(Player player) {
        var state = state(player);
        return state == null ? java.util.Map.of("tracked", false) : state.diagnostics();
    }

    private static State ensure(Player player) {
        var state = state(player);
        if (state != null) return state;
        if (STATES.size() >= MAX_STATES) {
            if (player != Minecraft.getInstance().player) return null;
            // Form changes can make the local player arrive after sixteen remote states. Keep their guide available.
            var farthest = STATES.values().stream().max(java.util.Comparator.comparingDouble(s -> s.owner.distanceToSqr(player))).orElseThrow();
            STATES.remove(farthest.owner.getId());
        }
        state = new State(anchor(player, 0));
        state.owner = player; state.lastOwner = player.position();
        STATES.put(player.getId(), state);
        return state;
    }

    public static Vec3 position(Player player, float partial) {
        if(player==Minecraft.getInstance().player && cn.wwl.orimod.client.RestorationClient.active())return cn.wwl.orimod.client.RestorationClient.companion(partial);
        var state = state(player);
        if (state != null) return state.position(partial);
        var old = SpiritFxClient.companion(player, player.level().getGameTime());
        var next = SpiritFxClient.companion(player, player.level().getGameTime() + 1);
        return old.lerp(next, partial).add(player.getPosition(partial).subtract(player.position()));
    }

    public static boolean eligibleForSeek(double horizontalSpeed, int sinceCombat, boolean guiding) {
        return !guiding && horizontalSpeed < IDLE_SPEED && sinceCombat > SEEK_PEACE_TICKS;
    }

    public static int moodTarget(boolean combat, boolean lowHealth, double seconds) {
        if (!lowHealth) return combat ? COMBAT : CALM;
        double k = .5 - .5 * Math.cos(seconds * Math.PI * 2 / LOW_HEALTH_PERIOD);
        var c = rgb(CALM).lerp(rgb(WORRIED), k);
        return (int) Math.round(c.x) << 16 | (int) Math.round(c.y) << 8 | (int) Math.round(c.z);
    }

    private static Vec3 rgb(int color) { return new Vec3(color >> 16 & 255, color >> 8 & 255, color & 255); }

    public static void tick() {
        world();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        STATES.int2ObjectEntrySet().removeIf(e -> {
            var p = e.getValue().owner;
            return mc.level.getEntity(e.getIntKey()) != p || !p.isAlive() || p.isSpectator()
                    || !SpiritData.isSpirit(p) || p.distanceToSqr(mc.player) > RANGE * RANGE;
        });
        // Reserve the local player's state before filling the bounded remote set.
        if (SpiritData.isSpirit(mc.player) && mc.player.isAlive() && !mc.player.isSpectator()) ensure(mc.player);
        for (var player : mc.level.players()) {
            if (!SpiritData.isSpirit(player) || !player.isAlive() || player.isSpectator() || player.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            var state = ensure(player);
            if (state == null) continue;
            var displacement = player.position().subtract(state.lastOwner);
            double speed = displacement.horizontalDistance() / STEP_SECONDS;
            boolean teleported = displacement.lengthSqr() > SNAP_DISTANCE * SNAP_DISTANCE;
            state.lastOwner = player.position();
            float flash = SpiritFxClient.flashAmount(player, 1);
            if (flash > 0) {
                state.dartTicks = state.seekTicks = 0; state.seekEntity = -1;
                state.dartTarget = state.seekTarget = null; state.hintFlight = false;
            }
            if (state.seekEntity >= 0) {
                var item = mc.level.getEntity(state.seekEntity);
                if (item == null || !item.isAlive()) { state.seekTicks = 0; state.seekEntity = -1; state.seekTarget = null; }
                else state.seekTarget = item.position().add(0, ITEM_ABOVE, 0);
            }
            if (--state.nextSeek <= 0) {
                state.nextSeek = SEEK_INTERVAL;
                if (flash == 0 && !teleported && eligibleForSeek(speed, state.sinceCombat, state.guiding())) seekLight(player, state);
            }
            var at = anchor(player, speed).lerp(player.position().add(0, player.getBbHeight() + cn.wwl.orimod.fx.FlashLight.COMPANION_ABOVE, 0), flash);
            state.step(at, teleported);
            state.prevMood = state.mood;
            int target = moodTarget(state.combat(), player.getHealth() < player.getMaxHealth() * LOW_HEALTH,
                    mc.level.getGameTime() * STEP_SECONDS);
            state.mood = state.mood.lerp(rgb(target), 1 - Math.exp(-STEP_SECONDS / MOOD_SECONDS));
            state.previousPetalPhase = state.petalPhase;
            state.petalPhase += STEP_SECONDS / PETAL_PERIOD * (state.combat() ? COMBAT_PETAL_SPEED : 1);
            if (state.sinceCombat < Integer.MAX_VALUE) state.sinceCombat++;
            if (state.pulseTicks > 0) state.pulseTicks--;
        }
    }

    private static void seekLight(Player player, State state) {
        ItemEntity nearest = null;
        double distance = SEEK_RANGE * SEEK_RANGE;
        for (var item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(SEEK_RANGE),
                e -> e.isAlive() && e.getItem().getItem() instanceof SpiritLightItem)) {
            double d = item.distanceToSqr(player);
            if (d >= distance) continue;
            var hit = player.level().clip(new ClipContext(player.getEyePosition(), item.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) continue;
            nearest = item; distance = d;
        }
        if (nearest != null) { state.seek(nearest.position().add(0, ITEM_ABOVE, 0), false); state.seekEntity = nearest.getId(); }
    }

    public static void attack(Player player, Vec3 target) {
        world();
        var mc = Minecraft.getInstance();
        if (mc.player == null || !player.isAlive() || player.isSpectator() || !SpiritData.isSpirit(player) || player.distanceToSqr(mc.player) > RANGE * RANGE) return;
        var state = ensure(player);
        if (state != null) state.dart(target);
    }

    public static void hint(CompanionHintPayload payload) {
        if (!OriClientConfig.companionHints()) return;
        world();
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !SpiritData.isSpirit(mc.player) || !SpiritData.isAwakened(mc.player)) return;
        var state = ensure(mc.player);
        if (state != null) {
            payload.pos().ifPresent(pos -> state.seek(Vec3.atCenterOf(pos).add(0, HINT_ABOVE, 0), true));
            state.pulseTicks = PULSE_TICKS;
        }
        if (payload.kind() == CompanionHintPayload.Kind.LINE) cn.wwl.orimod.client.CompanionLineOverlay.show(payload.id());
        else mc.player.displayClientMessage(Component.translatable("hint.orimod." + payload.id()), true);
        mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.PLAYERS, HINT_VOLUME, HINT_PITCH, false);
    }
}
