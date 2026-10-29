package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Real feature pass before the neighbouring structure slice; no generated tree may straddle LAND. */
@GameTestHolder("orimod_forest_red")
public final class ForestTerrainFeatureGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(boolean outside:new boolean[]{false,true})for(boolean tree:new boolean[]{false,true}){
            String name=(tree?"tree_first_":"forest_rock_")+(outside?"outside":"inside");
            out.add(new TestFunction("forest_features","forest_features."+name,"orimod_forest_red:room",Rotation.NONE,600,0,true,h->feature(h,tree,outside)));
        }
        return out;
    }
    private static void feature(GameTestHelper h,boolean tree,boolean outside){
        try(var s=new ForestSpringLayoutGameTests.Site(h,Rotation.NONE,23000+(tree?400:0)+(outside?200:0),9000,false,1)){
            var l=h.getLevel();s.publish();var b=s.piece.getBoundingBox();
            // A neighbouring tree origin is OUTSIDE the footprint, but its crown
            // would cross the edge. Reject the whole feature before cutting a
            // trunk could leave detached leaves outside template LAND.
            var at=new BlockPos(outside?b.maxX()+28:b.minX()+(tree?-2:4),s.p.origin().getY()+8,b.minZ()+20);
            for(int x=(at.getX()-10)>>4;x<=(at.getX()+10)>>4;x++)for(int z=(at.getZ()-10)>>4;z<=(at.getZ()+10)>>4;z++){
                var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);s.forced.add(cp);}l.getChunk(x,z);
            }
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)for(int y=-1;y<=16;y++)ForestPolicyRedGameTests.set(l,at.offset(x,y,z),(y==-1?Blocks.DIRT:Blocks.AIR).defaultBlockState());
            var region=new CreekNaturalFeaturesGameTests.ReadyRegion(l,new ChunkPos(at));
            boolean placed;
            if(tree){try(var enabled=new StructuresEnabled(l.getServer().getWorldData())){
                h.assertTrue(l.getServer().getWorldData().worldGenOptions().generateStructures(),"Fixture needs structures enabled for vanilla tree guard");
                var oak=l.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(ResourceLocation.withDefaultNamespace("oak"));
                placed=Objects.requireNonNull(oak).place(region,l.getChunkSource().getGenerator(),RandomSource.create(73),at);
            }}else placed=Feature.FOREST_ROCK.place(new FeaturePlaceContext<>(Optional.empty(),region,l.getChunkSource().getGenerator(),RandomSource.create(73),at.above(3),new BlockStateConfiguration(Blocks.MOSSY_COBBLESTONE.defaultBlockState())));
            int changed=0,insideWrites=0,outsideWrites=0;
            for(var p:BlockPos.betweenClosed(at.offset(-8,0,-8),at.offset(8,16,8)))if(!l.isEmptyBlock(p)){
                changed++;if(b.isInside(p))insideWrites++;else outsideWrites++;
            }
            System.out.println("FOREST_FEATURE tree="+tree+" outside="+outside+" placed="+placed+" changed="+changed
                    +" insideWrites="+insideWrites+" outsideWrites="+outsideWrites);
            if(outside)h.assertTrue(changed>0,"Control feature outside bounds did not generate");
            else h.assertValueEqual(changed,0,"Earlier neighbouring feature wrote tree/rock inside registered new forest");
            h.succeed();
        }
    }
    /** GameTest disables structures globally; temporarily model a natural FEATURES pass.
     * No chunks are requested in this scope, and the exact WorldOptions object is restored. */
    private static final class StructuresEnabled implements AutoCloseable {
        private final Object data;private final java.lang.reflect.Field field;private final Object original;
        StructuresEnabled(net.minecraft.world.level.storage.WorldData data){try{
            this.data=data;field=net.minecraft.world.level.storage.PrimaryLevelData.class.getDeclaredField("worldOptions");field.setAccessible(true);
            original=field.get(data);field.set(data,data.worldGenOptions().withStructures(true));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        @Override public void close(){try{field.set(data,original);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    }
}
