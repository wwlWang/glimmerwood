package cn.wwl.orimod.world;

/**
 * The world-height, slope and approach boundaries a natural ruin start must satisfy (WORLD02).
 * Pure so every boundary is unit tested; {@link RuinStructure} applies them to noise samples.
 */
public final class RuinSiteRules {
    /** Lowest accepted surface: sea level, so starts never sit in ocean or river beds. */
    public static final int MIN_SURFACE = 63;
    /** Highest accepted surface; above this mountain peaks and snow caps take over. */
    public static final int MAX_SURFACE = 145;
    /** Largest height difference across the footprint and doorway row. */
    public static final int MAX_SLOPE = 4;
    /** Largest difference between any approach sample and the doorway floor; the stairs cover 7 rows. */
    public static final int MAX_APPROACH_DELTA = 7;
    /** Blocks kept between the template origin and the bottom of the world. */
    public static final int BUILD_MARGIN = 8;
    /** Deepest support filled under an approach step or landing (carver holes). */
    public static final int MAX_FILL = 16;

    private RuinSiteRules() {}

    public static boolean surfaceInRange(int y) { return y >= MIN_SURFACE && y <= MAX_SURFACE; }

    public static boolean footprintOk(int min, int max) {
        return surfaceInRange(min) && surfaceInRange(max) && max - min <= MAX_SLOPE;
    }

    public static boolean approachOk(int sample, int doorY) {
        return surfaceInRange(sample) && Math.abs(sample - doorY) <= MAX_APPROACH_DELTA;
    }

    /** The whole template must fit with a margin below and strictly under the build limit. */
    public static boolean fitsWorld(int originY, int sizeY, int minBuild, int maxBuild) {
        return originY >= minBuild + BUILD_MARGIN && originY + sizeY < maxBuild;
    }

    /** Floor height of approach step {@code step} (0 = landing side, 7 = at the door). */
    public static int stepFloor(int ground, int doorY, int step) {
        return ground - 1 + Math.round((doorY - ground) * step / 7.0f);
    }
}
