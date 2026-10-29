package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;

/** Real fluid ticks; original production path must fail, not an assertion about implementation. */
@GameTestHolder("orimod_forest_liquid")
public final class ForestFluidBoundaryGameTests {
    static final List<BlockPos> LAKE=List.of(new BlockPos(21,5,53),new BlockPos(20,5,54),new BlockPos(20,5,53),new BlockPos(19,5,55),new BlockPos(19,5,54),new BlockPos(18,5,54),new BlockPos(18,5,53));
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(var r:Rotation.values())for(boolean lava:new boolean[]{false,true})for(int mode=0;mode<3;mode++){
            int m=mode;String name=(mode==0?"existing_":mode==1?"late_inside_":"late_outside_")+(lava?"lava_":"water_")+r.name().toLowerCase();
            out.add(new TestFunction("forest_fluid_"+r.name().toLowerCase(),"forest_fluid."+name,"orimod_forest_liquid:room",Rotation.NONE,900,0,true,h->run(h,r,lava,m)));
        }
        return out;
    }
    @GameTestGenerator public static Collection<TestFunction> replayCases(){
        var out=new ArrayList<TestFunction>();for(var r:Rotation.values())for(boolean lava:new boolean[]{false,true})
            out.add(new TestFunction("forest_fluid_replay","forest_fluid_replay."+r.name().toLowerCase()+"_"+lava,"orimod_forest_liquid:room",Rotation.NONE,900,0,true,h->CreekConcurrentSliceGameTests.forestFluids(h,r,lava)));
        return out;
    }
    private static Set<BlockPos> fluids(net.minecraft.server.level.ServerLevel l,BoundingBox b,boolean lava){
        var out=new HashSet<BlockPos>();for(var p:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()))
            if(l.getFluidState(p).is(lava?FluidTags.LAVA:FluidTags.WATER))out.add(p.immutable());return out;
    }
    static void checkEdges(GameTestHelper h,RuinPiece piece,net.minecraft.world.level.BlockGetter level){
        int edges=0;var walls=new HashSet<BlockPos>();
        for(var q:LAKE)for(var d:Direction.Plane.HORIZONTAL)if(!LAKE.contains(q.relative(d)))walls.add(q.relative(d));
        for(var q:walls){var at=piece.placement().block(q);var support=level.getBlockState(at);
            h.assertTrue(support.isFaceSturdy(level,at,Direction.UP),"Lake wall lost its solid top "+at);
            double top=CreekTerrainSurface.standingTop(support,level,at);
            for(var d:Direction.Plane.HORIZONTAL){var side=at.relative(d);double feet=Double.NaN;
                // Check the unchanged high wall against every low outgoing edge,
                // not just the original three leaked cells.
                if(!level.getBlockState(side.above()).getCollisionShape(level,side.above()).isEmpty())continue;
                for(int y=side.getY();y>=at.getY()-12;y--){var pos=side.atY(y);var state=level.getBlockState(pos);
                    if(!state.getFluidState().isEmpty()){feet=top;break;}
                    feet=CreekTerrainSurface.standingTop(state,level,pos);if(Double.isFinite(feet))break;
                }
                if(Double.isFinite(feet)&&CreekTerrainSurface.dropHalfUnits(top,feet)>2){edges++;var lip=at.above();
                    h.assertTrue(piece.ownsTerrainGuard(lip,level.getBlockState(lip)),"Unprotected preserved lake-wall drop "+at+" -> "+side+" feet="+feet);
                    h.assertTrue(level.getBlockState(lip).getCollisionShape(level,lip).max(Direction.Axis.Y)>=1.5,"Lake lip is jumpable "+lip);
                }
            }
        }
        h.assertTrue(edges>0,"Fluid edge fixture did not exercise a retained high wall");
    }
    private static void run(GameTestHelper h,Rotation r,boolean lava,int mode){
        int id=r.ordinal()*6+(lava?3:0)+mode;
        var s=new ForestSpringLayoutGameTests.Site(h,r,30000+id*512,11000,true,1);var l=h.getLevel();
        try{
            // Flat low terrain plus a raised natural lake shelf. Set up before any structure writes.
            for(int x=-13;x<=51;x++)for(int z=-13;z<=68;z++)for(int y=-8;y<=12;y++){
                int top=mode==0&&x>=14&&x<=23&&z>=49&&z<=60?5:1;
                ForestPolicyRedGameTests.set(l,s.p.block(new BlockPos(x,y,z)),(y<top?Blocks.DIRT:y==top?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState());
            }
            var fluid=(lava?Blocks.LAVA:Blocks.WATER).defaultBlockState();
            var extra=mode==0?List.of(new BlockPos(19,10,6),new BlockPos(10,10,51),new BlockPos(16,5,-4)):List.<BlockPos>of();
            Set<BlockPos> before;BoundingBox scan;boolean placed=false;
            if(mode==0){
                var locals=LAKE;
                // Seal every horizontal edge of the same seven-cell lake observed in seed116118.
                for(var q:locals)for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN))
                    if(!locals.contains(q.relative(d)))ForestPolicyRedGameTests.set(l,s.p.block(q.relative(d)),Blocks.STONE.defaultBlockState());
                for(var q:locals)ForestPolicyRedGameTests.set(l,s.p.block(q),q.equals(locals.getLast())?fluid.setValue(LiquidBlock.LEVEL,lava?2:1):fluid);
                scan=s.p.blocks(new BlockPos(11,-5,47),new BlockPos(26,10,64));before=fluids(l,scan,lava);
                h.assertValueEqual(before.size(),7,"Fixture lake must have exactly seven enclosed fluid cells (source and flowing)");
                // Explicit interior AIR, one of the eight clearance supplements,
                // and the separately owned approach. UP adjacency also protects
                // each container's floor, not only its four horizontal walls.
                for(var q:extra){
                    for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN))
                        ForestPolicyRedGameTests.set(l,s.p.block(q.relative(d)),Blocks.STONE.defaultBlockState());
                    ForestPolicyRedGameTests.set(l,s.p.block(q),fluid);
                }
                s.place();s.publish();checkEdges(h,s.piece,l);
                for(var q:locals)l.scheduleTick(s.p.block(q),fluid.getFluidState().getType(),1);
                for(var q:extra)l.scheduleTick(s.p.block(q),fluid.getFluidState().getType(),1);
            }else{
                s.place();s.publish();
                var corner=s.p.blocks(new BlockPos(mode==1?-10:-40,0,26),new BlockPos(mode==1?5:-25,0,41));
                var at=new BlockPos(corner.minX(),s.p.origin().getY()+2,corner.minZ());
                var volume=new BoundingBox(at.getX(),at.getY()-4,at.getZ(),at.getX()+15,at.getY()+3,at.getZ()+15);
                // The outside positive control is natural flat soil, never overlaps the ruin.
                if(mode==2){
                    for(int x=(volume.minX()>>4)-1;x<=(volume.maxX()>>4)+1;x++)for(int z=(volume.minZ()>>4)-1;z<=(volume.maxZ()>>4)+1;z++){
                        var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);s.forced.add(cp);}l.getChunk(x,z);
                    }
                    for(var p:BlockPos.betweenClosed(volume.minX()-2,volume.minY()-3,volume.minZ()-2,volume.maxX()+2,volume.maxY()+2,volume.maxZ()+2))
                        ForestPolicyRedGameTests.set(l,p,(p.getY()<s.p.origin().getY()+1?Blocks.DIRT:p.getY()==s.p.origin().getY()+1?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState());
                }
                scan=volume.inflatedBy(5);before=fluids(l,scan,lava);
                var region=new CreekNaturalFeaturesGameTests.ReadyRegion(l,new ChunkPos(at));
                placed=Feature.LAKE.place(new FeaturePlaceContext<>(Optional.empty(),region,l.getChunkSource().getGenerator(),RandomSource.create(73),at,
                        new LakeFeature.Configuration(BlockStateProvider.simple(fluid),BlockStateProvider.simple(Blocks.STONE))));
                for(var p:fluids(l,scan,lava))l.scheduleTick(p,fluid.getFluidState().getType(),1);
            }
            final boolean lakePlaced=placed;final var original=Set.copyOf(before);final var bounds=scan;
            h.runAfterDelay(220,()->{try{
                var after=fluids(l,bounds,lava);var added=new HashSet<>(after);added.removeAll(original);
                long inside=added.stream().filter(s.piece.getBoundingBox()::isInside).count();
                System.out.println("FOREST_FLUID_BOUNDARY rotation="+r+" lava="+lava+" mode="+mode+" lakePlaced="+lakePlaced+" before="+original.size()+" after="+after.size()+" new="+added.size()+" inside="+inside+" examples="+added.stream().sorted().limit(10).toList());
                if(mode==0){h.assertTrue(after.containsAll(original),"Forest replaced a natural lake source");h.assertTrue(added.isEmpty(),"Existing natural "+(lava?"lava":"water")+" leaked through forest grade after 220 ticks: "+added.size());
                    for(var q:extra){var at=s.p.block(q);h.assertTrue(l.getFluidState(at).is(lava?FluidTags.LAVA:FluidTags.WATER),"A different forest writing path filled foreign fluid "+at);
                        for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST,Direction.DOWN)){
                            var wall=s.p.block(q.relative(d));h.assertTrue(l.getBlockState(wall).isCollisionShapeFullBlock(l,wall),"Template/supplement/approach opened foreign container "+wall);
                        }
                    }
                }
                else if(mode==1)h.assertTrue(inside==0,"Late real LakeFeature put fluid into registered forest rim after 220 ticks: "+inside+" placed="+lakePlaced);
                else h.assertTrue(lakePlaced&&!added.isEmpty(),"Outside control lake must still generate");
                h.succeed();
            }finally{s.close();}});
        }catch(Throwable t){s.close();throw t;}
    }
}
