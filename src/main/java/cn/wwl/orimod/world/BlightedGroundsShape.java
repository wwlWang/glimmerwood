package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Irregular radial shore, separated pools, sparse brambles, and bent forked dead trees.
 * All coordinates are offsets; terrain projection and obstruction checks belong to the feature. */
public final class BlightedGroundsShape {
    public static final int LIMIT=14;
    public record Plan(int radius, Set<BlockPos> soil, Set<BlockPos> pools, Set<BlockPos> thorns, Map<BlockPos,Integer> trees) {}
    public static Plan plan(long seed) {
        // Mix neighboring numeric seeds before drawing the radius (Random's first nextInt(4) otherwise correlates).
        var random=new Random(new SplittableRandom(seed).nextLong());int radius=10+random.nextInt(4);
        double phase=random.nextDouble()*Math.PI*2;
        var soil=new LinkedHashSet<BlockPos>();
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++) {
            double angle=Math.atan2(z,x),edge=radius-.6+.6*Math.sin(angle*3+phase)+.35*Math.sin(angle*7-phase);
            if(x*x+z*z<=edge*edge)soil.add(new BlockPos(x,0,z));
        }
        var pools=new LinkedHashSet<BlockPos>();var thorns=new LinkedHashSet<BlockPos>();var trees=new LinkedHashMap<BlockPos,Integer>();
        int poolCount=1+random.nextInt(3);
        for(int i=0;i<poolCount;i++) {
            double angle=phase+i*Math.PI*2/poolCount;int x=(int)Math.round(Math.cos(angle)*6),z=(int)Math.round(Math.sin(angle)*6);
            for(var p:List.of(new BlockPos(x,0,z),new BlockPos(x+1,0,z),new BlockPos(x,0,z+1)))if(soil.contains(p))pools.add(p);
        }
        var candidates=new ArrayList<>(soil);Collections.shuffle(candidates,random);
        int treeCount=2+random.nextInt(3),thornCount=8+random.nextInt(13);
        for(var p:candidates) {
            if(p.distSqr(BlockPos.ZERO)<16 || Math.abs(p.getX())>radius-2 || Math.abs(p.getZ())>radius-2 || pools.contains(p))continue;
            if(trees.size()<treeCount && trees.keySet().stream().allMatch(t->t.distSqr(p)>=16))trees.put(p,3+random.nextInt(4));
        }
        for(var p:candidates) {
            // Keep a clear central approach; no brambles in the northward walking corridor.
            if(thorns.size()==thornCount)break;
            if(p.distSqr(BlockPos.ZERO)<9 || (Math.abs(p.getX())<=1 && p.getZ()<0) || pools.contains(p) || trees.containsKey(p))continue;
            thorns.add(p);
        }
        return new Plan(radius,Collections.unmodifiableSet(soil),Collections.unmodifiableSet(pools),Collections.unmodifiableSet(thorns),Collections.unmodifiableMap(trees));
    }
    private BlightedGroundsShape() {}
}
