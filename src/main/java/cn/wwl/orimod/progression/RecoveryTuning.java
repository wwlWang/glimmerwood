package cn.wwl.orimod.progression;

/** R113 recovery economy. Health is vanilla HP; time is server ticks (20 per second). */
public final class RecoveryTuning {
    private RecoveryTuning() {}
    public static final int LIFE_DELAY = 160, ENERGY_DELAY = 80;
    public static final float LIFE_CAP = .8f, LIFE_PER_SECOND = .5f;
    public static final float ENERGY_FLOOR = 20, ENERGY_PER_SECOND = 2, RECOVERY_PER_SECOND = 3;
    public static final int CHANNEL_TICKS = 20, KEEPALIVE_TICKS = 5, LEASE_TICKS = 20;
    public static final float CAST_COST = 10, CAST_HEAL = 6;
    public static final float ORB_LIFE = 2, ORB_ENERGY = 5, ORB_CHANCE = .5f;
    public static final int ORB_LIFETIME = 1200, ORB_PICKUP_DELAY = 10, ORB_MAX_GROUPS = 4;
    public static final double ORB_MAGNET_RADIUS = 4, ORB_PICKUP_RADIUS = .65, ORB_SPEED = .25;
}
