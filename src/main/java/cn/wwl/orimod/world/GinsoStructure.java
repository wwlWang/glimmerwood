package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import java.util.*;

public final class GinsoStructure extends Structure {
    public static final MapCodec<GinsoStructure> CODEC=simpleCodec(GinsoStructure::new);
    public GinsoStructure(StructureSettings settings){super(settings);}
    public record Site(GinsoSiteRules.Reason reason,BlockPos origin){}
    /** No chunk reads or writes; locate and generation share this exact acceptance rule. */
    public static Site preflight(GenerationContext c){
        int x=c.chunkPos().getMiddleBlockX(),z=c.chunkPos().getMiddleBlockZ();
        int y=height(c,x,z,Heightmap.Types.WORLD_SURFACE_WG);
        var at=new BlockPos(x,y,z);var great=GreatTreeSites.site(c.randomState());
        // Initial spawn chunks can generate before the unique site is published. Refuse those
        // early candidates as well; otherwise a saved Ginso start could predate the 300-block guard.
        if(great==null||!GinsoSiteRules.awayFromGreatTree(x-great.origin().getX(),z-great.origin().getZ()))return new Site(GinsoSiteRules.Reason.GREAT_TREE,at);
        if(!c.validBiome().test(c.biomeSource().getNoiseBiome(x>>2,y>>2,z>>2,c.randomState().sampler())))return new Site(GinsoSiteRules.Reason.BIOME,at);
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE,wet=0,total=0,deepest=0;
        var heights=new ArrayList<Integer>();
        // Full base disc (17 blocks wide), not just a river's centre column. 49 samples.
        for(int dx=-8;dx<=8;dx+=2)for(int dz=-8;dz<=8;dz+=2){
            if(dx*dx+dz*dz>64)continue;
            int floor=height(c,x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG);
            int top=height(c,x+dx,z+dz,Heightmap.Types.WORLD_SURFACE_WG),depth=Math.max(0,top-floor);
            min=Math.min(min,floor);max=Math.max(max,top);total++;if(depth>0)wet++;deepest=Math.max(deepest,depth);heights.add(top);
        }
        Collections.sort(heights);y=heights.get(heights.size()/2);at=new BlockPos(x,y,z);
        var reason=GinsoSiteRules.terrain(min,max,wet,total,deepest);
        if(reason!=GinsoSiteRules.Reason.OK)return new Site(reason,at);
        if(y+GinsoShape.MAX_Y>=c.heightAccessor().getMaxBuildHeight()||y+GinsoShape.MIN_Y<c.heightAccessor().getMinBuildHeight())return new Site(GinsoSiteRules.Reason.BUILD_HEIGHT,at);
        return new Site(GinsoSiteRules.Reason.OK,at);
    }
    private static int height(GenerationContext c,int x,int z,Heightmap.Types map){return c.chunkGenerator().getBaseHeight(x,z,map,c.heightAccessor(),c.randomState());}
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext c){
        var s=preflight(c);if(s.reason()!=GinsoSiteRules.Reason.OK)return Optional.empty();
        long seed=c.seed()^s.origin().asLong()^0x115C;
        int[][] profile=new int[4][3];
        for(int turn=0;turn<4;turn++)for(int i=0;i<3;i++){
            var offset=GinsoInterior.rotate(new BlockPos(0,0,-10-i*6),turn);
            profile[turn][i]=height(c,s.origin().getX()+offset.getX(),s.origin().getZ()+offset.getZ(),Heightmap.Types.OCEAN_FLOOR_WG);
        }
        int entrance=GinsoInterior.entrance(s.origin().getY()+1,profile);
        int approach=profile[entrance][2]-1-s.origin().getY();
        return Optional.of(new GenerationStub(s.origin(),b->b.addPiece(new GinsoPiece(s.origin(),seed,entrance,approach))));
    }
    /** Conservative candidate reservations avoid generation-order races, even if a candidate later fails terrain. */
    public static boolean reserved(GenerationContext c,int margin){
        var set=c.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
        if(set==null || !(set.placement() instanceof RandomSpreadStructurePlacement placement))return false;
        int cx=c.chunkPos().x,cz=c.chunkPos().z,range=(GinsoShape.RADIUS+margin+15)/16;
        int spacing=placement.spacing();
        for(int gx=Math.floorDiv(cx-range,spacing);gx<=Math.floorDiv(cx+range,spacing);gx++)
            for(int gz=Math.floorDiv(cz-range,spacing);gz<=Math.floorDiv(cz+range,spacing);gz++){
                var p=placement.getPotentialStructureChunk(c.seed(),gx*spacing,gz*spacing);
                if(Math.abs(p.x-cx)<=range&&Math.abs(p.z-cz)<=range)return true;
            }
        return false;
    }
    @Override public StructureType<?> type(){return OriWorldgen.GINSO_TREE.get();}
}
