package cn.wwl.orimod.world;

/** Versioned immutable field. Saved pieces never use the current version implicitly. */
public final class GinsoShape {
    public static final int VERSION=3, RADIUS=35, MIN_Y=-20, MAX_Y=124, WIDTH=71;
    public static final byte EMPTY=0, WOOD=1, LEAF=2, VINE=3, AIR=4, SAP=5, FUNGUS=6;
    private final LegacyGinsoShape legacy;
    private final TallGinsoShape tall;
    public final int height, version;
    public GinsoShape(long seed){this(seed,VERSION);}
    public GinsoShape(long seed,int version){
        this.version=checkedVersion(version);
        legacy=version<=2?new LegacyGinsoShape(seed):null;
        tall=version==3?new TallGinsoShape(seed):null;
        height=legacy!=null?legacy.height:tall.height;
    }
    public static int checkedVersion(int version){
        if(version<1||version>VERSION)throw new IllegalArgumentException("Unsupported Ginso shape version: "+version);
        return version;
    }
    // v1 predates the course; its saved exterior was already interpreted as v2 in 42a50ec.
    public static int radius(int version){return checkedVersion(version)<=2?28:RADIUS;}
    public static int minY(int version){return checkedVersion(version)<=2?-8:MIN_Y;}
    public static int maxY(int version){return checkedVersion(version)<=2?64:MAX_Y;}
    public byte cell(int x,int y,int z){return legacy!=null?legacy.cell(x,y,z):tall.cell(x,y,z);}
    /** An opaque backing under the v3 cutout skin; never changes a v2 voxel/state. */
    public boolean coreLeaf(int x,int y,int z){
        return version>=3&&cell(x,y,z)==LEAF&&cell(x-1,y,z)==LEAF&&cell(x+1,y,z)==LEAF
                &&cell(x,y-1,z)==LEAF&&cell(x,y+1,z)==LEAF&&cell(x,y,z-1)==LEAF&&cell(x,y,z+1)==LEAF;
    }
    public boolean moss(int x,int y,int z){return legacy!=null?LegacyGinsoShape.mossy(x,y,z):TallGinsoShape.mossy(x,y,z);}
    public static boolean timber(byte c){return c==WOOD||c==SAP;}
}
