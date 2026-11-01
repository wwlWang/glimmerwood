package cn.wwl.orimod.world;

/** R106 rules, independent of world state and rendering. */
public final class BlightRules {
    public static final int HEALTH=40, DURATION=600, READ_BUDGET=128, WRITE_BUDGET=32,
            MAX_PARTICIPANTS=16, REWARD=60, SANCTUARY_RADIUS=32, DEFENDER_LIMIT=3, DEFENDER_COOLDOWN=100;
    public enum Source { CHARGE_FLAME, SMASH, LIGHT_BURST, SPEAR, FLASH, STOMP, BASHED_IMPACT, BLADE, ARROW, HAND, FLAME }
    public static int damage(Source source) {
        return switch(source) { case CHARGE_FLAME->12; case SMASH->10; case LIGHT_BURST->8; case SPEAR->6; case FLASH,ARROW->4; case BLADE->3; default->0; };
    }
    /** Clearing a hazard is a deliberate attack, not a side effect of the companion or sustained Flash. */
    public static boolean clearsThorns(Source source) {
        return switch(source) {
            case BLADE, ARROW, CHARGE_FLAME, LIGHT_BURST, SMASH, SPEAR, STOMP -> true;
            default -> false;
        };
    }
    /** A pool cell (water replacing the floor of a column whose ground top is {@code ground}) cannot flow sideways into a
     * neighbour whose ground top is at least as high. */
    public static boolean poolContained(int ground,int neighbourGround) { return neighbourGround>=ground; }
    public static int crack(int health) { return Math.min(4,Math.max(0,(HEALTH-health)/8)); }
    public static int due(double distance,double radius) { return Math.max(1,Math.min(DURATION,(int)Math.ceil(DURATION*distance/Math.max(1,radius)))); }
    public static boolean protects(double squaredDistance) { return squaredDistance<=SANCTUARY_RADIUS*SANCTUARY_RADIUS; }
    public static boolean defenderReady(long now,long next,int count) { return now>=next && count<DEFENDER_LIMIT; }
    private BlightRules() {}
}
