package cn.wwl.orimod.world;

/** Immutable noise/plan coordinates define the cave test, never a neighbour's newly graded height. */
final class CreekTerrainExceptions {
    static final int MAX_UNPLANNED_FILL=6;
    static int caveThreshold(int natural,int planned){return Math.min(natural,planned)-MAX_UNPLANNED_FILL;}
    static boolean cave(int natural,int planned,int actual){return actual<caveThreshold(natural,planned);}
    private CreekTerrainExceptions(){}
}
