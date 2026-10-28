package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;

import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import java.util.*;

/** Audit-only exhaustive v1 reasons, including checks hidden by earlier rejection.
 * Uses production constants/predicates, but deliberately does NOT short-circuit.
 * No pieces, chunk loads, writes, or production trace events. */
public final class CreekRejectionAudit {
    private final CreekReservationAudit reservations=new CreekReservationAudit();
    public Map<String,Object> sample(Structure.GenerationContext c){
        long start=System.nanoTime();var reasons=new LinkedHashSet<String>();
        var layout=RuinKind.CREEK.layout(1);
        int reservation=(int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8))+12;
        if(GreatTreeSites.reserved(c.randomState(),c.chunkPos().getMiddleBlockX(),c.chunkPos().getMiddleBlockZ(),reservation))reasons.add("GREAT_TREE");
        if(GinsoStructure.reserved(c,reservation))reasons.add("GINSO");
        var rotation=Rotation.getRandom(c.random());
        var footprint=RuinPlacement.atMinimum(layout,c.chunkPos().getWorldPosition(),rotation);
        var entryColumn=BlockPos.containing(footprint.point(layout.entry()));
        int delta=(int)Math.floor(layout.entry().y)-layout.surfaceY();boolean possibleBiome=false;
        for(int y=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE+delta);y<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE+delta);y++)
            if(c.validBiome().test(c.biomeSource().getNoiseBiome(QuartPos.fromBlock(entryColumn.getX()),y,QuartPos.fromBlock(entryColumn.getZ()),c.randomState().sampler()))){possibleBiome=true;break;}
        if(!possibleBiome)reasons.add("BIOME_COARSE");
        var variants=reservations.sample(c,footprint,layout);
        reasons.addAll((List<String>)((Map<?,?>)variants.get("a")).get("reasons"));
        var cells=new Cells(c);
        var heights=new HashMap<BlockPos,Surface>();
        java.util.function.ToIntFunction<BlockPos> height=p->heights.computeIfAbsent(p.atY(0),k->cells.at(k.getX(),k.getZ())).floor();
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(var p:points("CORE")){int y=height.applyAsInt(footprint.block(new BlockPos(p[0],0,p[1])));min=Math.min(min,y);max=Math.max(max,y);}
        int coreMin=min,coreMax=max;
        if(!RuinSiteRules.footprintOk(min,max))reasons.add("CORE_RELIEF");
        int outerMin=min,outerMax=max;
        for(var p:points("OUTER")){int y=height.applyAsInt(footprint.block(new BlockPos(p[0],0,p[1])));outerMin=Math.min(outerMin,y);outerMax=Math.max(outerMax,y);}
        if(!RuinSiteRules.surfaceInRange(outerMin)||!RuinSiteRules.surfaceInRange(outerMax)||outerMax-outerMin>((Number)staticField("CreekSiteRules","OUTER_RELIEF")).intValue())reasons.add("OUTER_RELIEF");
        var approach=RuinApproachPiece.doorway(RuinKind.CREEK,layout,footprint);
        for(int dx=-2;dx<=2;dx++){int y=height.applyAsInt(approach.block(new BlockPos(dx,0,-1)));min=Math.min(min,y);max=Math.max(max,y);}
        if(!RuinSiteRules.footprintOk(min,max))reasons.add("ENTRY_RELIEF");
        int surface=(min+max+1)/2;
        var placement=new RuinPlacement(footprint.origin().atY(surface-layout.surfaceY()),rotation);
        var doorway=RuinApproachPiece.doorway(RuinKind.CREEK,layout,placement);
        for(int dx=-2;dx<=2;dx++)for(int dz=-9;dz<=-1;dz++)
            if(!RuinSiteRules.approachOk(height.applyAsInt(doorway.block(new BlockPos(dx,0,dz))),doorway.origin().getY()))reasons.add("APPROACH");
        var entry=BlockPos.containing(placement.point(layout.entry()));
        if(!c.validBiome().test(c.biomeSource().getNoiseBiome(QuartPos.fromBlock(entry.getX()),QuartPos.fromBlock(entry.getY()),QuartPos.fromBlock(entry.getZ()),c.randomState().sampler())))reasons.add("BIOME_ENTRY");
        if(heights.values().stream().anyMatch(s->!s.dry()))reasons.add("WATER");
        if(!GinsoGenerationGuard.checkBounds(c.randomState(),(net.minecraft.world.level.levelgen.structure.BoundingBox)callStatic("CreekRuinTerrain","bounds",new Class<?>[]{net.minecraft.world.level.levelgen.structure.BoundingBox.class},placement.blocks(BlockPos.ZERO,layout.size().offset(-1,-1,-1)))).allowed())reasons.add("GENERATED");
        if(!RuinSiteRules.fitsWorld(placement.origin().getY(),layout.size().getY(),c.heightAccessor().getMinBuildHeight(),c.heightAccessor().getMaxBuildHeight()))reasons.add("BUILD_HEIGHT");
        return Map.of("reasons",List.copyOf(reasons),"coreMin",coreMin,"coreMax",coreMax,"outerMin",outerMin,"outerMax",outerMax,
                "noiseColumns",heights.size(),"noiseCells",cells.count(),"ms",(System.nanoTime()-start)/1e6,"variants",variants);
    }

    // Test module cannot share a Java package with the production module. Reflect
    // package-private helpers rather than duplicate production constants/terrain math.
    record Surface(int floor,boolean dry){}
    private static Class<?> type(String name){try{return Class.forName("cn.wwl.orimod.world."+name);}catch(Exception e){throw new RuntimeException(e);}}
    static Object staticField(String owner,String name){try{var f=type(owner).getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(Exception e){throw new RuntimeException(e);}}
    static Object callStatic(String owner,String name,Class<?>[] types,Object...args){return call(type(owner),null,name,types,args);}
    private static Object call(Class<?> owner,Object target,String name,Class<?>[] types,Object...args){try{var m=owner.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}catch(Exception e){throw new RuntimeException(e);}}
    static List<int[]> points(String name){var out=new ArrayList<int[]>();for(var p:(List<?>)staticField("CreekSiteRules",name))out.add(new int[]{(int)call(p.getClass(),p,"x",new Class<?>[0]),(int)call(p.getClass(),p,"z",new Class<?>[0])});return out;}
    static final class Cells {
        private final Object target;
        Cells(Structure.GenerationContext c){try{var ctor=type("CreekSurfaceSample$Cells").getDeclaredConstructor(net.minecraft.world.level.chunk.ChunkGenerator.class,net.minecraft.world.level.LevelHeightAccessor.class,net.minecraft.world.level.levelgen.RandomState.class);ctor.setAccessible(true);target=ctor.newInstance(c.chunkGenerator(),c.heightAccessor(),c.randomState());}catch(Exception e){throw new RuntimeException(e);}}
        Surface at(int x,int z){var s=call(target.getClass(),target,"at",new Class<?>[]{int.class,int.class},x,z);return new Surface((int)call(s.getClass(),s,"floor",new Class<?>[0]),(boolean)call(s.getClass(),s,"dry",new Class<?>[0]));}
        int count(){return (int)call(target.getClass(),target,"count",new Class<?>[0]);}
    }
}
