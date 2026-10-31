package cn.wwl.orimod.world;

import cn.wwl.orimod.block.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Accepted noise plan is authoritative for a registered start; decoration cannot move its anchor. */
record FeatureSitePlan(String reason,BlockPos base,Map<BlockPos,BlockState> writes,Set<BlockPos> submergedSupportStarts) {
    static final int MAX_SUPPORT_EXTENSION=48;
    FeatureSitePlan(String reason,BlockPos base,Map<BlockPos,BlockState> writes){this(reason,base,writes,Set.of());}
    boolean allowed(){return reason.equals("OK");}
    static FeatureSitePlan check(FeatureSiteStructure.Site site,FeatureTerrain terrain,BlockPos origin,long placementSeed){
        if(site==FeatureSiteStructure.Site.SPIRIT_TREE){
            var p=SpiritTreeFeature.preflight(terrain,origin);
            return new FeatureSitePlan(p.reason().name(),p.base(),p.writes()).inspectSupports(terrain,site);
        }
        var p=BlightedGroundsFeature.preflight(terrain,origin,RandomSource.create(placementSeed).nextLong());
        return new FeatureSitePlan(p.reason().name(),p.center(),p.writes()).inspectSupports(terrain,site);
    }

    /** Build the entire transaction first; never clear a failed site's vegetation as a side effect. */
    String place(WorldGenLevel level,FeatureSiteStructure.Site site){
        if(!allowed())return "NOISE_PLAN_"+reason;
        var terrain=FeatureTerrain.world(level);
        if(!terrain.available(base,14))return "UNLOADED";
        // Legacy, already-generated sites and foreign structures cannot be predicted from new placement grids.
        if(terrain.ruin(base,14))return "UNEXPECTED_RUIN";
        if(terrain.near(base,12,OriBlocks.GLOOM_CORE.get()) || site==FeatureSiteStructure.Site.BLIGHTED_GROUNDS
                && terrain.near(base,12,OriBlocks.SPIRIT_TREE_HEART.get()))return "EXISTING_SANCTUARY";
        var transaction=new LinkedHashMap<BlockPos,BlockState>();
        for(int dx=-14;dx<=14;dx++)for(int dz=-14;dz<=14;dz++){
            if(dx*dx+dz*dz>14*14)continue;
            int x=base.getX()+dx,z=base.getZ()+dz;
            int top=Math.min(level.getMaxBuildHeight()-1,Math.max(base.getY()+SpiritTreeShape.MAX_HEIGHT+8,
                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,x,z)));
            for(int y=top;y>=base.getY()-6;y--){
                var p=new BlockPos(x,y,z);var state=level.getBlockState(p);
                if(naturalVegetation(state)||y>=base.getY() && boulder(state))transaction.put(p,Blocks.AIR.defaultBlockState());
            }
        }
        for(var entry:writes.entrySet()){
            var p=entry.getKey();var state=level.getBlockState(p);
            if(!replaceable(state))return "UNEXPECTED_BLOCK "+state+" at "+p.toShortString();
            transaction.put(p,entry.getValue());
        }
        // Surface eligibility was decided by preflight. Later carvers can expose deep air/water below it.
        // Connect to a bed within 48 blocks, otherwise let the buried root end naturally; neither cavity rejects a site.
        var filledWater=new HashSet<BlockPos>();
        for(var foot:feet(site)){
            for(int depth=0;depth<MAX_SUPPORT_EXTENSION;depth++){
                var p=foot.below(depth);if(level.isOutsideBuildHeight(p))break;
                var state=transaction.getOrDefault(p,level.getBlockState(p));
                if(supported(level,p,state))break;
                if(!replaceable(state)&&!state.is(Blocks.WATER))return "SUPPORT_BLOCK "+state+" at "+p.toShortString();
                if(state.is(Blocks.WATER))filledWater.add(p);
                transaction.put(p,(site==FeatureSiteStructure.Site.SPIRIT_TREE?OriBlocks.SPIRIT_LOG.get():Blocks.DIRT).defaultBlockState());
            }
        }
        for(var p:transaction.keySet())if(level.isOutsideBuildHeight(p)||!level.ensureCanWrite(p))return "WRITE_BOUNDS at "+p.toShortString();
        for(var entry:transaction.entrySet())level.setBlock(entry.getKey(),entry.getValue(),
                filledWater.contains(entry.getKey())?Block.UPDATE_ALL:Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        if(site==FeatureSiteStructure.Site.SPIRIT_TREE)FeatureSites.generated(level,base.offset(SpiritTreeShape.HEART),FeatureSites.Kind.TREE);
        else{
            var positions=transaction.entrySet().stream().filter(e->!e.getKey().equals(base)&&!e.getValue().isAir()).mapToLong(e->e.getKey().asLong()).toArray();
            if(level.getBlockEntity(base) instanceof GloomCoreBlockEntity core)core.generatedTerrain(positions);
            if(level instanceof ServerLevel server)BlightData.get(server).register(base,positions,true);
            FeatureSites.generated(level,base,FeatureSites.Kind.BLIGHT);
        }
        return "OK";
    }
    /** Noise-visible water is part of the accepted plan. A later carver may introduce additional water at placement. */
    private FeatureSitePlan inspectSupports(FeatureTerrain terrain,FeatureSiteStructure.Site site){
        if(!allowed())return this;
        var starts=new HashSet<BlockPos>();
        for(var foot:feet(site))for(int depth=0;depth<MAX_SUPPORT_EXTENSION;depth++){
            var p=foot.below(depth);if(terrain.isOutsideBuildHeight(p))break;
            var state=writes.getOrDefault(p,terrain.getBlockState(p));
            if(state.is(Blocks.WATER)){starts.add(p);break;}
            if(supported(terrain,p,state)||!replaceable(state))break;
        }
        return new FeatureSitePlan(reason,base,writes,Set.copyOf(starts));
    }
    private Collection<BlockPos> feet(FeatureSiteStructure.Site site){
        var feet=new HashMap<Long,BlockPos>();
        for(var entry:writes.entrySet()){
            var p=entry.getKey();var state=entry.getValue();
            if(site==FeatureSiteStructure.Site.SPIRIT_TREE ? state.is(OriBlocks.SPIRIT_LOG)&&p.getY()<=base.getY()
                    : state.is(OriBlocks.BLIGHTED_SOIL)||state.is(OriBlocks.GLOOM_WATER))
                feet.merge(net.minecraft.world.level.ChunkPos.asLong(p.getX(),p.getZ()),p.below(),(a,b)->a.getY()<b.getY()?a:b);
        }
        if(site==FeatureSiteStructure.Site.SPIRIT_TREE)for(int x=-1;x<=1;x++)for(int z=-5;z<=-3;z++){
            var p=base.offset(x,-1,z);feet.put(net.minecraft.world.level.ChunkPos.asLong(p.getX(),p.getZ()),p);
        }
        return feet.values();
    }
    private static boolean supported(net.minecraft.world.level.BlockGetter level,BlockPos p,BlockState state){
        return state.getFluidState().isEmpty()&&!state.hasBlockEntity()&&!state.is(BlockTags.LEAVES)
                &&state.isFaceSturdy(level,p,Direction.UP);
    }
    private static boolean naturalVegetation(BlockState state){
        return !net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("orimod")
                && FeatureSitePiece.vegetation(state);
    }
    private static boolean replaceable(BlockState state){
        return state.getFluidState().isEmpty() && (!state.hasBlockEntity()||state.is(Blocks.BEE_NEST))
                && (SpiritTreeFeature.replaceable(state)||naturalVegetation(state)||surfaceDecoration(state)||BlightedGroundsFeature.naturalFloor(state));
    }
    /** Surface rules and LOCAL_MODIFICATIONS decorate after noise preflight; registered structures reserve their own masonry. */
    private static boolean surfaceDecoration(BlockState state){
        return boulder(state)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.POWDER_SNOW);
    }
    private static boolean boulder(BlockState state){
        return state.is(Blocks.MOSSY_COBBLESTONE)||state.is(Blocks.COBBLESTONE);
    }
}
