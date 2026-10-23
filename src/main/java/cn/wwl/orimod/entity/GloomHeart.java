package cn.wwl.orimod.entity;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.Vec3;

/**
 * R50 Gloom Heart: the first original boss, woken at the cave altar ruin's gloom altar. It hovers over its
 * altar and cycles telegraphed attacks (the core swells before each one): aimed orb volleys, radial orb rings
 * and, in its second phase, a slam marked on the ground. Every orb is a Bashable {@link GloomShot}; orbs sent
 * back by the player hurt the Heart far more than ordinary hits. At half health it shrieks, turns briefly
 * invulnerable, calls two Gloom Spitters and speeds up.
 */
public final class GloomHeart extends Monster {
    public static final float MAX_HEALTH = 120, REFLECT_DAMAGE = 10, SLAM_DAMAGE = 6;
    public static final double SLAM_RADIUS = 3, HOVER = 4, ORBIT = 3.5, ARENA = 24;
    public static final int IDLE = 0, VOLLEY = 1, RING = 2, SLAM = 3;
    public static final int VOLLEY_TELEGRAPH = 20, RING_TELEGRAPH = 30, SLAM_TELEGRAPH = 25, ENRAGE_TICKS = 40;
    private static final EntityDataAccessor<Integer> ATTACK = SynchedEntityData.defineId(GloomHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIMER = SynchedEntityData.defineId(GloomHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(GloomHeart.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ENRAGE_LEFT = SynchedEntityData.defineId(GloomHeart.class, EntityDataSerializers.INT);
    private final ServerBossEvent bar = new ServerBossEvent(Component.translatable("entity.orimod.gloom_heart"), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private BlockPos home;
    private Vec3 slamAt;
    private int rest = 40, enragedUntil, cycle;

    public GloomHeart(EntityType<? extends GloomHeart> type, Level level) {
        super(type, level); setNoGravity(true); setPersistenceRequired(); xpReward = 60;
    }

    @Override public void setTarget(@javax.annotation.Nullable LivingEntity target) {
        super.setTarget(GloomTargets.attackable(target) ? target : null);
        cn.wwl.orimod.progression.CompanionLines.bossStarted(this, ARENA);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, MAX_HEALTH).add(Attributes.FOLLOW_RANGE, ARENA)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.ARMOR, 4);
    }

    // ---- pure rules -------------------------------------------------------------------------------------
    public static int phaseFor(float health, float max) { return health <= max / 2 ? 2 : 1; }
    public static int volleyCount(int phase) { return phase >= 2 ? 5 : 3; }
    public static int ringCount(int phase) { return phase >= 2 ? 12 : 8; }
    public static int restTicks(int phase) { return phase >= 2 ? 22 : 36; }
    /** Attack order: volley, ring, then (phase 2) slam. */
    public static int nextAttack(int cycle, int phase) {
        int[] order = phase >= 2 ? new int[]{VOLLEY, SLAM, RING, VOLLEY, SLAM} : new int[]{VOLLEY, VOLLEY, RING};
        return order[Math.floorMod(cycle, order.length)];
    }
    public static int telegraph(int attack) { return attack == RING ? RING_TELEGRAPH : attack == SLAM ? SLAM_TELEGRAPH : VOLLEY_TELEGRAPH; }

    // ---- state ----------------------------------------------------------------------------------------
    @Override public boolean canAttack(LivingEntity target) {
        return GloomTargets.attackable(target) && super.canAttack(target);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder); builder.define(ATTACK, IDLE); builder.define(TIMER, 0); builder.define(PHASE, 1); builder.define(ENRAGE_LEFT, 0);
    }
    public int attack() { return entityData.get(ATTACK); }
    public int timer() { return entityData.get(TIMER); }
    public int phase() { return entityData.get(PHASE); }
    public int enrageRemaining() { return entityData.get(ENRAGE_LEFT); }
    public boolean enraging() { return level().isClientSide ? enrageRemaining() > 0 : tickCount < enragedUntil; }
    public Vec3 slamTarget() { return slamAt; }
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
        if (hasCustomName()) bar.setName(getDisplayName());
    }

    @Override public void startSeenByPlayer(ServerPlayer player) { super.startSeenByPlayer(player); bar.addPlayer(player); }
    @Override public void stopSeenByPlayer(ServerPlayer player) { super.stopSeenByPlayer(player); bar.removePlayer(player); }
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    // ---- combat -----------------------------------------------------------------------------------------
    @Override public boolean hurt(DamageSource source, float amount) {
        if (enraging() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        boolean reflected = source.getDirectEntity() instanceof GloomShot && source.getEntity() instanceof Player;
        boolean hurt = super.hurt(source, reflected ? Math.max(amount, REFLECT_DAMAGE) : amount);
        if (hurt && !level().isClientSide && phase() == 1 && phaseFor(getHealth(), getMaxHealth()) == 2) enrage();
        return hurt;
    }

    /** Phase change: shriek, a short invulnerable window, two Gloom Spitters, faster cycle. */
    public void enrage() {
        entityData.set(PHASE, 2); enragedUntil = tickCount + ENRAGE_TICKS; entityData.set(ENRAGE_LEFT, ENRAGE_TICKS);
        entityData.set(ATTACK, IDLE); entityData.set(TIMER, 0); rest = ENRAGE_TICKS; cycle = 0;
        bar.setColor(BossEvent.BossBarColor.RED);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 1.4f, 1.3f);
        if (level() instanceof ServerLevel server) {
            cn.wwl.orimod.fx.SpiritFx.emit(server, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, getX(), home().getY() + 1, getZ(), null);
            for (int side = -1; side <= 1; side += 2) {
                var spitter = OriMobs.GLOOM_SPITTER.get().create(server);
                if (spitter == null) continue;
                spitter.moveTo(home().getX() + .5 + side * 3, home().getY() + 1, home().getZ() + .5, 0, 0);
                spitter.setPersistenceRequired();
                server.addFreshEntity(spitter);
            }
        }
    }

    /** Begin a telegraphed attack. */
    public void startAttack(int attack, LivingEntity target) {
        if (!GloomTargets.attackable(target)) return;
        entityData.set(ATTACK, attack); entityData.set(TIMER, telegraph(attack));
        setTarget(target);
        if (attack == SLAM) slamAt = target.position();
        level().playSound(null, getX(), getY(), getZ(), attack == SLAM ? SoundEvents.WARDEN_SONIC_CHARGE : SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.HOSTILE, 1.2f, attack == RING ? .5f : .8f);
    }

    /** Advance the current telegraph. @return orbs released this tick (empty until the telegraph ends). */
    public java.util.List<GloomShot> tickAttack() {
        if (getTarget()!=null && !GloomTargets.attackable(getTarget())) {
            setTarget(null); entityData.set(ATTACK, IDLE); entityData.set(TIMER,0); slamAt=null; return java.util.List.of();
        }
        int attack = attack(), left = timer();
        if (attack == IDLE) return java.util.List.of();
        if (left > 1) { entityData.set(TIMER, left - 1); return java.util.List.of(); }
        entityData.set(ATTACK, IDLE); entityData.set(TIMER, 0);
        rest = restTicks(phase());
        var target = getTarget();
        return switch (attack) {
            case VOLLEY -> target == null ? java.util.List.of() : volley(target);
            case RING -> ring();
            default -> { slam(); yield java.util.List.of(); }
        };
    }

    private java.util.List<GloomShot> volley(LivingEntity target) {
        var shots = new java.util.ArrayList<GloomShot>();
        int n = volleyCount(phase());
        Vec3 from = position().add(0, getBbHeight() / 2, 0), aim = target.position().add(0, target.getBbHeight() / 2, 0).subtract(from);
        double base = Math.atan2(aim.z, aim.x), flat = Math.hypot(aim.x, aim.z);
        for (int i = 0; i < n; i++) {
            double a = base + (i - (n - 1) / 2.0) * Math.toRadians(14);
            shots.add(fire(from, new Vec3(Math.cos(a) * flat, aim.y, Math.sin(a) * flat), phase() >= 2 ? 1.2f : 1));
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.LLAMA_SPIT, SoundSource.HOSTILE, 1.2f, .5f);
        return shots;
    }

    private java.util.List<GloomShot> ring() {
        var shots = new java.util.ArrayList<GloomShot>();
        int n = ringCount(phase());
        Vec3 from = position().add(0, getBbHeight() / 2, 0);
        double offset = tickCount * .3;
        for (int i = 0; i < n; i++) {
            double a = offset + i * Math.PI * 2 / n;
            shots.add(fire(from, new Vec3(Math.cos(a), -.25, Math.sin(a)), .9f));
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.2f, .7f);
        return shots;
    }

    private GloomShot fire(Vec3 from, Vec3 direction, float speedScale) {
        var shot = new GloomShot(OriMobs.GLOOM_SHOT.get(), level());
        shot.setOwner(this); shot.setPos(from.x, from.y, from.z);
        shot.shoot(direction.x, direction.y, direction.z, (float) GloomShot.SPEED * speedScale, 0);
        level().addFreshEntity(shot);
        return shot;
    }

    /** @return players hit by the slam shockwave. */
    public int slam() {
        if (slamAt == null || !(level() instanceof ServerLevel server)) return 0;
        int hits = 0;
        for (var player : server.getEntitiesOfClass(Player.class, new net.minecraft.world.phys.AABB(slamAt, slamAt).inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS),
                p -> GloomTargets.canHit(this, p) && p.position().distanceTo(slamAt) <= SLAM_RADIUS)) {
            if (player.hurt(damageSources().mobAttack(this), SLAM_DAMAGE)) { player.push(0, .6, 0); player.hurtMarked = true; hits++; }
        }
        cn.wwl.orimod.fx.SpiritFx.emit(server, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, slamAt.x, slamAt.y, slamAt.z, null);
        server.playSound(null, slamAt.x, slamAt.y, slamAt.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.HOSTILE, 1.4f, .6f);
        slamAt = null;
        return hits;
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide) entityData.set(ENRAGE_LEFT, Math.max(0, enragedUntil - tickCount));
        if (level().isClientSide) { cn.wwl.orimod.client.fx.GloomLook.heart(this); return; }
        bar.setProgress(getHealth() / getMaxHealth());
        if (isNoAi()) return;
        // Hover and slowly orbit the altar.
        var h = home();
        double a = tickCount * (phase() >= 2 ? .03 : .02);
        Vec3 goal = new Vec3(h.getX() + .5 + Math.cos(a) * ORBIT, h.getY() + HOVER + Math.sin(tickCount * .07) * .5, h.getZ() + .5 + Math.sin(a) * ORBIT);
        setDeltaMovement(goal.subtract(position()).scale(.08));
        var target = level().getNearestPlayer(getX(), getY(), getZ(), ARENA, p -> p instanceof Player pl && GloomTargets.canHit(this, pl));
        if (target != null) getLookControl().setLookAt(target);
        if (attack() != IDLE) { tickAttack(); return; }
        if (target == null || enraging() || --rest > 0) return;
        startAttack(nextAttack(cycle++, phase()), target);
    }

    /** Players who must escape when the Heart dies: everyone alive within {@link #ARENA} blocks. */
    public java.util.List<ServerPlayer> escapees(ServerLevel server) {
        return server.players().stream().filter(p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) <= ARENA * ARENA).toList();
    }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel server) {
            cn.wwl.orimod.fx.SpiritFx.emit(server, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, getX(), getY(), getZ(), null);
            cn.wwl.orimod.block.GloomWaterBlock.cleanse(server, home(), (int) ARENA);   // R61
            // R54: the corruption breaks loose; the reward waits outside (EscapeSequence).
            bar.removeAllPlayers();
            var origin = Vec3.atBottomCenterOf(home()).add(0, 1, 0);
            for (var player : escapees(server)) {
                cn.wwl.orimod.progression.WillWisps.offer(player,cn.wwl.orimod.progression.WillWisp.HEART,server,home());
                cn.wwl.orimod.progression.CompanionLines.offer(player, cn.wwl.orimod.progression.CompanionLines.Line.BOSS_DEFEATED);
                cn.wwl.orimod.world.EscapeSequence.start(player, origin);
            }
        }
    }
}
