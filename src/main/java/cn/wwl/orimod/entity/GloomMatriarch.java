package cn.wwl.orimod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;

/**
 * R70 Gloom Matriarch: the second original boss, an aerial one woken at the storm altar in the forest spring ruin.
 * Inspired by the role of Kuro (a great bird hunting from the sky) without copying her design or attacks.
 * Each attack is telegraphed: a red ring marks the dive point before she plunges (dash aside; a missed dive leaves
 * her stunned on the ground, taking double damage); feather fans are Bashable {@link GloomShot}s that hurt her when
 * returned; in phase two she climbs out of reach and adds a wing gust that throws players outward (glide back on the
 * ruin's updrafts) and calls two Gloom Hawks.
 */
public final class GloomMatriarch extends Monster {
    public static final float MAX_HEALTH = 150, REFLECT_DAMAGE = 10, DIVE_DAMAGE = 7, STUN_MULTIPLIER = 2;
    public static final double ORBIT = 6, ARENA = 28, DIVE_SPEED = 1.4, GUST_RADIUS = 10, GUST_PUSH = 1.3;
    public static final int IDLE = 0, DIVE_TELEGRAPH = 1, DIVE = 2, STUNNED = 3, FEATHERS = 4, GUST = 5;
    public static final int DIVE_TELEGRAPH_TICKS = 30, DIVE_TICKS = 30, STUN_TICKS = 50, FEATHER_TELEGRAPH = 20, GUST_TELEGRAPH = 25, ENRAGE_TICKS = 40;
    public static final int REWARD_LIGHT = 100;
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(GloomMatriarch.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(GloomMatriarch.class, EntityDataSerializers.INT);
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.orimod.gloom_matriarch"), BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.NOTCHED_10);
    private BlockPos home;
    private Vec3 diveAt;
    private int timer, rest = 40, cycle, enragedUntil;
    private boolean diveHit;

    public GloomMatriarch(EntityType<? extends GloomMatriarch> type, Level level) {
        super(type, level); setNoGravity(true); setPersistenceRequired(); xpReward = 80;
    }

    @Override public void setTarget(@javax.annotation.Nullable LivingEntity target) {
        super.setTarget(target);
        cn.wwl.orimod.progression.CompanionLines.bossStarted(this, ARENA);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.FOLLOW_RANGE, ARENA)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FLYING_SPEED, .6);
    }

    // ---- pure rules -------------------------------------------------------------------------------------
    public static int phaseFor(float health, float max) { return health <= max / 2 ? 2 : 1; }
    public static double hover(int phase) { return phase >= 2 ? 10 : 6; }
    public static int featherCount(int phase) { return phase >= 2 ? 7 : 5; }
    public static int restTicks(int phase) { return phase >= 2 ? 24 : 36; }
    public static int nextAttack(int cycle, int phase) {
        int[] order = phase >= 2 ? new int[]{DIVE_TELEGRAPH, GUST, FEATHERS, DIVE_TELEGRAPH, FEATHERS} : new int[]{FEATHERS, DIVE_TELEGRAPH, FEATHERS, DIVE_TELEGRAPH};
        return order[Math.floorMod(cycle, order.length)];
    }
    public static int telegraph(int attack) { return attack == DIVE_TELEGRAPH ? DIVE_TELEGRAPH_TICKS : attack == GUST ? GUST_TELEGRAPH : FEATHER_TELEGRAPH; }
    /** Outward gust velocity for a player at {@code offset} from the boss. */
    public static Vec3 gustVelocity(Vec3 offset) {
        var flat = new Vec3(offset.x, 0, offset.z);
        if (flat.lengthSqr() < 1e-6) flat = new Vec3(1, 0, 0);
        flat = flat.normalize().scale(GUST_PUSH);
        return new Vec3(flat.x, .35, flat.z);
    }

    // ---- state ------------------------------------------------------------------------------------------
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { super.defineSynchedData(builder); builder.define(ATTACK, IDLE); builder.define(PHASE, 1); }
    public int attack() { return entityData.get(ATTACK); }
    public int phase() { return entityData.get(PHASE); }
    public int timer() { return timer; }
    public Vec3 diveTarget() { return diveAt; }
    public boolean enraging() { return tickCount < enragedUntil; }
    public void setHome(BlockPos pos) { home = pos.immutable(); }
    public BlockPos home() { return home == null ? blockPosition() : home; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) { tag.putInt("HomeX", home.getX()); tag.putInt("HomeY", home.getY()); tag.putInt("HomeZ", home.getZ()); }
        tag.putInt("Phase", phase());
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("HomeX")) home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        entityData.set(PHASE, Math.max(1, tag.getInt("Phase")));
    }
    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bar.removePlayer(player); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    // ---- combat -----------------------------------------------------------------------------------------
    @Override public boolean hurt(DamageSource source, float amount) {
        if (enraging() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.getDirectEntity() instanceof GloomShot shot && shot.getOwner() == this && !(source.getEntity() instanceof Player)) return false;
        boolean reflected = source.getDirectEntity() instanceof GloomShot && source.getEntity() instanceof Player;
        float dealt = reflected ? Math.max(amount, REFLECT_DAMAGE) : amount;
        if (attack() == STUNNED) dealt *= STUN_MULTIPLIER;
        boolean hurt = super.hurt(source, dealt);
        if (hurt && !level().isClientSide && phase() == 1 && phaseFor(getHealth(), getMaxHealth()) == 2) enrage();
        return hurt;
    }

    public void enrage() {
        entityData.set(PHASE, 2); enragedUntil = tickCount + ENRAGE_TICKS;
        entityData.set(ATTACK, IDLE); timer = 0; rest = ENRAGE_TICKS; cycle = 0;
        bar.setColor(BossEvent.BossBarColor.RED);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_DEATH, SoundSource.HOSTILE, 2, .5f);
        if (level() instanceof ServerLevel server) for (int side = -1; side <= 1; side += 2) {
            var hawk = OriMobs.GLOOM_HAWK.get().create(server);
            if (hawk == null) continue;
            hawk.moveTo(home().getX() + .5 + side * 4, home().getY() + 7, home().getZ() + .5, 0, 0);
            hawk.setPersistenceRequired(); server.addFreshEntity(hawk);
        }
    }

    public void startAttack(int attack, LivingEntity target) {
        setTarget(target); entityData.set(ATTACK, attack); timer = telegraph(attack);
        if (attack == DIVE_TELEGRAPH) diveAt = target.position();
        level().playSound(null, getX(), getY(), getZ(), attack == DIVE_TELEGRAPH ? SoundEvents.PHANTOM_SWOOP : attack == GUST ? SoundEvents.ENDER_DRAGON_FLAP : SoundEvents.PHANTOM_AMBIENT,
                SoundSource.HOSTILE, 1.6f, .6f);
    }

    public void stun() {
        entityData.set(ATTACK, STUNNED); timer = STUN_TICKS; setDeltaMovement(Vec3.ZERO);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, .8f, .6f);
        if (level() instanceof ServerLevel server) cn.wwl.orimod.fx.SpiritFx.emit(server, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, getX(), getY(), getZ(), null);
    }

    /** One AI step. @return feathers released this step (empty unless a fan was fired). */
    public List<GloomShot> step() {
        var target = getTarget();
        switch (attack()) {
            case IDLE -> {
                var h = home(); double a = tickCount * (phase() >= 2 ? .035 : .025);
                Vec3 goal = new Vec3(h.getX() + .5 + Math.cos(a) * ORBIT, h.getY() + hover(phase()) + Math.sin(tickCount * .08) * .6, h.getZ() + .5 + Math.sin(a) * ORBIT);
                setDeltaMovement(goal.subtract(position()).scale(.07));
                var near = level().getNearestPlayer(getX(), getY(), getZ(), ARENA, p -> p instanceof Player pl && !pl.isCreative() && !pl.isSpectator());
                if (near != null) target = near;
                if (target != null && !enraging() && --rest <= 0) startAttack(nextAttack(cycle++, phase()), target);
            }
            case DIVE_TELEGRAPH -> {
                setDeltaMovement(getDeltaMovement().scale(.5));
                if (level() instanceof ServerLevel server && diveAt != null && timer % 3 == 0)
                    for (int i = 0; i < 10; i++) {
                        double a = i * Math.PI / 5 + timer * .2;
                        server.sendParticles(new DustParticleOptions(new Vector3f(1, .15f, .15f), 1.8f), diveAt.x + Math.cos(a) * 1.4, diveAt.y + .15, diveAt.z + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
                    }
                if (--timer <= 0) {
                    entityData.set(ATTACK, DIVE); timer = DIVE_TICKS; diveHit = false;
                    setDeltaMovement(diveVelocity());
                    level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_BITE, SoundSource.HOSTILE, 1.6f, .5f);
                }
            }
            case DIVE -> {
                setDeltaMovement(diveVelocity());
                if (!diveHit) for (var e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(.6), e -> e != this && e == getTarget() && e.isAlive()))
                    if (e.hurt(damageSources().mobAttack(this), DIVE_DAMAGE)) { e.push(getDeltaMovement().x * .6, .5, getDeltaMovement().z * .6); e.hurtMarked = true; diveHit = true; }
                if (--timer <= 0 || diveAt == null || position().distanceTo(diveAt) < 1.2 || onGround() || horizontalCollision) {
                    if (diveHit) { entityData.set(ATTACK, IDLE); rest = restTicks(phase()); } else stun();
                }
            }
            case STUNNED -> { setDeltaMovement(Vec3.ZERO); if (--timer <= 0) { entityData.set(ATTACK, IDLE); rest = restTicks(phase()); } }
            case FEATHERS -> {
                setDeltaMovement(getDeltaMovement().scale(.6));
                if (--timer <= 0) { entityData.set(ATTACK, IDLE); rest = restTicks(phase()); return target == null ? List.of() : feathers(target); }
            }
            case GUST -> {
                setDeltaMovement(getDeltaMovement().scale(.6));
                if (--timer <= 0) { entityData.set(ATTACK, IDLE); rest = restTicks(phase()); gust(); }
            }
            default -> entityData.set(ATTACK, IDLE);
        }
        return List.of();
    }

    public Vec3 diveVelocity() {
        if (diveAt == null) return Vec3.ZERO;
        var d = diveAt.add(0, .3, 0).subtract(position());
        return d.lengthSqr() < 1e-6 ? Vec3.ZERO : d.normalize().scale(DIVE_SPEED);
    }

    private List<GloomShot> feathers(LivingEntity target) {
        var shots = new ArrayList<GloomShot>();
        int n = featherCount(phase());
        Vec3 from = position().add(0, getBbHeight() / 2, 0), aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(from);
        double base = Math.atan2(aim.z, aim.x), flat = Math.hypot(aim.x, aim.z);
        for (int i = 0; i < n; i++) {
            double a = base + (i - (n - 1) / 2.0) * Math.toRadians(12);
            var shot = new GloomShot(OriMobs.GLOOM_SHOT.get(), level());
            shot.setOwner(this); shot.setPos(from.x, from.y, from.z);
            shot.shoot(Math.cos(a) * flat, aim.y, Math.sin(a) * flat, (float) GloomShot.SPEED * (phase() >= 2 ? 1.2f : 1), 0);
            level().addFreshEntity(shot); shots.add(shot);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1.2f, 1.4f);
        return shots;
    }

    /** @return players thrown outward by the gust. */
    public int gust() {
        int hits = 0;
        for (var p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(GUST_RADIUS), p -> p.isAlive() && !p.isSpectator() && p.distanceTo(this) <= GUST_RADIUS)) {
            p.setDeltaMovement(gustVelocity(p.position().subtract(position()))); p.hurtMarked = true; hits++;
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2, .5f);
        if (level() instanceof ServerLevel server) cn.wwl.orimod.fx.SpiritFx.emit(server, cn.wwl.orimod.fx.SpiritFxKind.DASH, getX(), getY(), getZ(), null);
        return hits;
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide) return;
        bar.setProgress(getHealth() / getMaxHealth());
        if (!isNoAi()) step();
    }

    public List<ServerPlayer> victors(ServerLevel server) {
        return server.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) <= ARENA * ARENA).toList();
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (!(level() instanceof ServerLevel server)) return;
        bar.removeAllPlayers();
        cn.wwl.orimod.fx.SpiritFx.emit(server, cn.wwl.orimod.fx.SpiritFxKind.CHECKPOINT_REACHED, getX(), getY(), getZ(), null);
        spawnAtLocation(new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.MAP_FRAGMENT.get()));
        for (var player : victors(server)) victory(player);
    }

    /** First defeat per player: Spirit Light and a life and energy cell; later defeats only the light chime. */
    public static boolean victory(ServerPlayer player) {
        cn.wwl.orimod.progression.CompanionLines.offer(player, cn.wwl.orimod.progression.CompanionLines.Line.BOSS_DEFEATED);
        boolean first = cn.wwl.orimod.progression.SpiritProgression.reward(player, "boss/gloom_matriarch", REWARD_LIGHT, 5);
        if (first) cn.wwl.orimod.progression.SpiritCells.grantFirstClear(player);
        player.displayClientMessage(Component.translatable(first ? "message.orimod.matriarch.victory" : "message.orimod.matriarch.again"), false);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1, 1);
        return first;
    }
}
