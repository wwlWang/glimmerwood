package cn.wwl.orimod.world;

/** R117 ecology limits. One budget is shared by the server scheduler, never multiplied by core count. */
public final class BlightEcologyRules {
    public static final int VERSION=1, RADIUS=48, MAX_RADIUS=64, MAX_COLUMNS=16641, MAX_RECORDS=32768;
    public static final int READS=512, WRITES=32, INDEX_VISITS=128, CANDIDATES=32;
    public static final int INTERVAL=20, COLUMNS_PER_INTERVAL=2;
    public static final int PROTECTION_VERSION=1, PROTECTION_TICKS=24000;
    public static final int ARTIFICIAL_RADIUS=3, ARTIFICIAL_BELOW=2, ARTIFICIAL_ABOVE=6;
    public static final int ARTIFICIAL_READS=7*7*9;
    public static final class Budget {
        private final int readLimit,writeLimit;
        private int reads,writes,indices,candidates,chunkMisses;
        public Budget(){this(READS,WRITES);}
        public Budget(int reads,int writes){
            if(reads<0||reads>READS||writes<0||writes>WRITES)throw new IllegalArgumentException("Blight budget exceeds server ceiling");
            readLimit=reads;writeLimit=writes;
        }
        public boolean read(){if(reads==readLimit)return false;reads++;return true;}
        public boolean write(){if(writes==writeLimit)return false;writes++;return true;}
        public boolean index(){if(indices==INDEX_VISITS)return false;indices++;return true;}
        public boolean candidate(){if(candidates==CANDIDATES)return false;candidates++;return true;}
        public int reads(){return reads;}
        public int writes(){return writes;}
        public int indices(){return indices;}
        public int candidates(){return candidates;}
        public int remainingIndices(){return INDEX_VISITS-indices;}
        public void missingChunk(){chunkMisses++;}
        public int chunkMisses(){return chunkMisses;}
        public int remainingReads(){return readLimit-reads;}
        public int remainingWrites(){return writeLimit-writes;}
    }
    public static boolean within(int dx,int dz,int radius){return (long)dx*dx+(long)dz*dz<=(long)radius*radius;}
    public static int duration(double radius){return Math.max(600,(int)Math.ceil(600*Math.min(MAX_RADIUS,radius)/13));}
    public static int reward(int newColumns){return 60+Math.min(120,Math.max(0,newColumns)/64);}
    /** Keep fractional credit at slow rates, while bounding a stalled core to a small single burst. */
    public static double credit(double current,double multiplier){return Math.min(Math.max(2,2*multiplier),current+2*multiplier);}
    private BlightEcologyRules(){}
}
