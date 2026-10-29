package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.item.OriItems;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_creek_rebuild")
public final class CreekRuinsLayoutGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(var r:Rotation.values()){
            out.add(test("navigation_"+r.name().toLowerCase(),h->navigation(h,r)));
            out.add(test("water_"+r.name().toLowerCase(),h->water(h,r,false)));
            out.add(test("aquifer_"+r.name().toLowerCase(),h->water(h,r,true)));
            out.add(test("glide_margin_"+r.name().toLowerCase(),h->glide(h,r)));
        }
        out.add(test("permanent_light",CreekRuinsLayoutGameTests::lighting));
        out.add(test("rain_runoff_keeps_routes_dry",CreekRuinsLayoutGameTests::rainRunoff));
        for(boolean outside:new boolean[]{false,true})out.add(test("dark_bridge_"+outside,h->bridge(h,outside)));
        out.add(test("missing_version",h->{var l=h.getLevel();var old=new RuinPiece(l.getStructureManager(),RuinKind.CREEK,new RuinPlacement(h.absolutePos(BlockPos.ZERO),Rotation.NONE));var tag=old.createTag(StructurePieceSerializationContext.fromLevel(l));tag.remove("LayoutVersion");var read=new RuinPiece(l.getStructureManager(),tag);h.assertValueEqual(read.layoutVersion(),0,"Legacy upgraded");h.assertFalse(read.createTag(StructurePieceSerializationContext.fromLevel(l)).contains("CreekNaturalTerrain"),"v0 acquired v1 terrain snapshot");h.assertValueEqual(read.getBoundingBox(),old.getBoundingBox(),"Legacy bounds changed");h.succeed();}));
        out.add(test("v1_missing_natural_snapshot_uses_noise",h->{
            try(var s=new Site(h,Rotation.CLOCKWISE_90,5700,900)){
                var l=h.getLevel();var tag=s.piece.createTag(StructurePieceSerializationContext.fromLevel(l));tag.remove("CreekNaturalTerrain");
                var loaded=new RuinPiece(l.getStructureManager(),tag);
                // A grid corner is exact; interior interpolation is covered by
                // the pure tests. Keep this assertion independent of interpolation.
                var witness=s.p.block(new BlockPos(48,0,32));
                // The fallback must ignore this artificial, very high 'surface'.
                l.setBlock(witness.atY(s.p.origin().getY()+21),Blocks.STONE.defaultBlockState(),2);
                var cp=new ChunkPos(witness);
                loaded.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),
                        new BoundingBox(cp.getMinBlockX(),l.getMinBuildHeight(),cp.getMinBlockZ(),cp.getMaxBlockX(),l.getMaxBuildHeight()-1,cp.getMaxBlockZ()),cp,BlockPos.ZERO);
                var saved=loaded.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("CreekNaturalTerrain");
                h.assertValueEqual(saved.length,85*107,"Incomplete noise fallback");
                int expected=l.getChunkSource().getGenerator().getBaseHeight(witness.getX(),witness.getZ(),net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,l,l.getChunkSource().randomState())-1-s.p.origin().getY();
                h.assertValueEqual(saved[(32+20)*85+48+20],expected,"Fallback read current placed blocks instead of pure noise");h.succeed();
            }
        }));
        return out;
    }
    private static TestFunction test(String name,java.util.function.Consumer<GameTestHelper> run){return new TestFunction("creek_rebuild","creek_rebuild."+name,"orimod_creek_rebuild:room",Rotation.NONE,600,0,true,run);}
    static final class Site implements AutoCloseable {
        final GameTestHelper h;final RuinPlacement p;RuinPiece piece;final List<ChunkPos> forced=new ArrayList<>();final List<Runnable> undo=new ArrayList<>();
        Site(GameTestHelper h,Rotation r,int dx,int dz){this(h,r,dx,dz,false);}
        Site(GameTestHelper h,Rotation r,int dx,int dz,boolean approach){this.h=h;
            p=RuinPlacement.atMinimum(RuinKind.CREEK.layout(1),h.absolutePos(new BlockPos(dx,35,dz)),r);
            System.out.println("CREEK_FIXTURE_SITE dx="+dx+" dz="+dz+" approach="+approach+" origin="+p.origin().getX()+","+p.origin().getY()+","+p.origin().getZ()+" rotation="+r);
            int[] samples=approach?new int[RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH]:new int[0];Arrays.fill(samples,p.origin().getY()+8);
            int[] natural=new int[0];
            if(approach){
                // This fixture writes its own flat/terraced ground, instead of the
                // flat-world generator's y=-60. Snapshot that same authored input.
                natural=new int[(45+40)*(67+40)];
                for(int z=-20;z<87;z++)for(int x=-20;x<65;x++)natural[(z+20)*85+x+20]=CreekTerrainGameTests.natural(new BlockPos(x,0,z));
            }
            piece=new RuinPiece(h.getLevel().getStructureManager(),RuinKind.CREEK,p,1,samples,natural);
            var b=piece.getBoundingBox();var l=h.getLevel();int prepared=0;
            GameTestDiskBackpressure.drain(l,"creek-prepare-begin");
            try{for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++){
                var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
                GameTestDiskBackpressure.preparedChunk(l,++prepared,"creek-prepare");
            }GameTestDiskBackpressure.drain(l,"creek-prepare-ready");}catch(RuntimeException e){close();throw e;}
        }
        void place(){place(false);}
        void place(boolean deferredShapes){var l=h.getLevel();var b=piece.getBoundingBox();long total=0,max=0;
            // WorldGenRegion does not immediately update neighbour shapes. A live
            // ServerLevel otherwise breaks an unsupported flower as the base changes,
            // concealing the worldgen-only skipped-lip failure.
            WorldGenLevel accessor=deferredShapes?(WorldGenLevel)java.lang.reflect.Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(),new Class<?>[]{WorldGenLevel.class},(proxy,method,args)->{
                if(method.getName().equals("setBlock")&&args.length>=3)args[2]=(int)args[2]|Block.UPDATE_KNOWN_SHAPE;
                try{return method.invoke(l,args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            }):l;
            try(var timing=GameTestDiskBackpressure.measured(l,"layout-slices")){
            for(int x=b.maxX()>>4;x>=b.minX()>>4;x--)for(int z=b.maxZ()>>4;z>=b.minZ()>>4;z--){long start=System.nanoTime();
                piece.postProcess(accessor,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),new BoundingBox(x*16,l.getMinBuildHeight(),z*16,x*16+15,l.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
                long took=System.nanoTime()-start;total+=took;max=Math.max(max,took);
            }}System.out.println("CREEK_V1_PERF rotation="+p.rotation()+" totalMs="+total/1e6+" maxSliceMs="+max/1e6);
        }
        void publish(){
            var l=h.getLevel();var structure=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(RuinKind.CREEK.location());
            var origin=l.getChunkAt(p.origin());var starts=new HashMap<>(origin.getAllStarts());undo.add(()->origin.setAllStarts(starts));
            origin.setStartForStructure(structure,new net.minecraft.world.level.levelgen.structure.StructureStart(structure,origin.getPos(),0,new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece))));
            var chunks=new HashSet<ChunkPos>();for(var local:List.of(piece.layout().shrine(),piece.layout().mapStone()))chunks.add(new ChunkPos(p.block(local)));
            for(var cp:chunks){var chunk=l.getChunk(cp.x,cp.z);var refs=new HashMap<net.minecraft.world.level.levelgen.structure.Structure,it.unimi.dsi.fastutil.longs.LongSet>();chunk.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));undo.add(()->chunk.setAllReferences(refs));chunk.addReferenceForStructure(structure,origin.getPos().toLong());}
        }
        @Override public void close(){for(int i=undo.size()-1;i>=0;i--)undo.get(i).run();undo.clear();CreekFixtureChunks.release(h.getLevel(),forced);}
    }
    private static void navigation(GameTestHelper h,Rotation r){
        try(var s=new Site(h,r,600+r.ordinal()*180,900);var c=Fixtures.connectedPlayer(h)){
            s.place();s.publish();var l=h.getLevel();var p=s.p;var layout=s.piece.layout();var player=c.player();
            h.assertValueEqual(MapStoneBlock.survey(player,p.block(layout.mapStone())),new MapStoneBlock.Survey(1,0,0),"Map used old radius/contract");
            var saved=new RuinPiece(l.getStructureManager(),s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)));
            h.assertValueEqual(saved.layoutVersion(),1,"Version lost");h.assertValueEqual(saved.layout(),layout,"Wrong saved contract");
            for(var local:List.of(layout.shrine(),layout.mapStone()))h.assertTrue(SanctuaryAssembly.assembled(l.getBlockState(p.block(local))),"Legacy core "+local);
            var nav=new CaveAltarLayoutGameTests.Navigation(h,p,layout.size());
            for(var goal:List.of(new Vec3(8.5,10,9.5),layout.safe(),new Vec3(33.5,10,34.5)))h.assertTrue(nav.reachable(layout.entry(),goal),"Public route blocked "+goal);
            for(var point:layout.checkpoints())h.assertTrue(nav.reachable(point.position(),layout.entry()),"No dry return "+point.position());
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setData(SpiritData.AWAKENED,true);player.setData(SpiritData.FORM,true);player.setData(SpiritData.ABILITIES,6);
            player.setPos(p.point(layout.safe()));System.out.println("CREEK_CHALLENGE_CHECK "+RuinSiteInspector.inspect(l,RuinKind.CREEK,layout,p).issues().stream().filter(v->!v.startsWith("approach:")).toList());SpiritChallenge.request(player,p.block(layout.shrine()),true);
            h.assertTrue(SpiritChallenge.session(player)!=null,"Challenge rejected new contract");SpiritChallenge.finish(player,"exit",false);
            var anchor=p.block(new BlockPos(12,13,29));
            for(var offset:List.of(new Vec3(.5,0,.5),new Vec3(.3,0,.3),new Vec3(.3,0,.7),new Vec3(.7,0,.3),new Vec3(.7,0,.7))){
                player.setPos(p.point(new Vec3(12,10,25).add(offset)));var aim=Vec3.atCenterOf(anchor).subtract(player.getEyePosition());
                h.assertTrue(aim.length()<=5.5,"Bash reach margin "+aim.length());float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance()));
                var found=SpiritTargets.find(player,yaw,pitch);h.assertTrue(found!=null&&anchor.equals(found.block()),"Bash target not visible "+offset);
            }
            h.succeed();
        }
    }
    private static Set<BlockPos> flow(){
        try(var stream=RuinKind.class.getResourceAsStream("/orimod/ruin_layout/creek_ruins_flow_v1.json")){
            var a=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();var out=new HashSet<BlockPos>();
            for(var v:a){var p=v.getAsJsonArray();out.add(new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt()));}return out;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void water(GameTestHelper h,Rotation r,boolean aquifer){
        var s=new Site(h,r,1700+r.ordinal()*180,aquifer?1400:900);var l=h.getLevel();try{
            // Artificial aquifer under every authored column; external source cells are tracked separately.
            if(aquifer)for(int x=0;x<45;x++)for(int z=0;z<67;z++)for(int y=0;y<3;y++)l.setBlock(s.p.block(new BlockPos(x,y,z)),Blocks.WATER.defaultBlockState(),2);
            s.place();var t=l.getStructureManager().getOrCreate(RuinKind.CREEK.template(1));var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings().setRotation(r);
            var expected=new HashSet<BlockPos>();for(var v:t.filterBlocks(s.p.origin(),settings,Blocks.WATER))expected.add(v.pos());
            for(var block:List.of(Blocks.IRON_BARS,Blocks.DARK_OAK_SLAB,Blocks.DARK_OAK_STAIRS,Blocks.DARK_OAK_FENCE))
                for(var v:t.filterBlocks(s.p.origin(),settings,block))if(!v.state().getFluidState().isEmpty())expected.add(v.pos());
            // Even a swimming/crawling player must hit the grille, including its submerged row.
            for(double y:new double[]{6.05,7,8})for(int x=19;x<=23;x++){
                var a=s.p.point(new Vec3(x+.5,y,6.5));
                h.assertFalse(l.noCollision(new AABB(a.x-.3,a.y,a.z-.3,a.x+.3,a.y+.6,a.z+.3)),"Swimmer can enter blind culvert "+x+":"+y);
            }
            var flowing=new HashSet<BlockPos>();flow().forEach(p->flowing.add(s.p.block(p)));var exterior=new HashSet<BlockPos>();
            for(int x=-1;x<=45;x++)for(int z=-1;z<=67;z++)for(int y=0;y<24;y++){
                var p=s.p.block(new BlockPos(x,y,z));if(!l.getFluidState(p).isEmpty()&&!expected.contains(p))exterior.add(p);
            }
            h.runAfterDelay(220,()->{try{
                int sources=0,moving=0;var extra=new ArrayList<String>();
                for(int x=-1;x<=45;x++)for(int z=-1;z<=67;z++)for(int y=0;y<24;y++){
                    var local=new BlockPos(x,y,z);var p=s.p.block(local);var f=l.getFluidState(p);
                    if(f.is(net.minecraft.tags.FluidTags.WATER)){
                        if(!expected.contains(p)&&!exterior.contains(p)&&(!aquifer||y>=3))extra.add(local+" "+l.getBlockState(p));
                        if(expected.contains(p)){if(f.isSource())sources++;else{moving++;h.assertTrue(flowing.contains(p),"Unregistered flow "+local+" "+f);}}
                    }
                }
                for(var q:s.piece.layout().updrafts())for(int y=0;y<=12;y++)h.assertTrue(l.getFluidState(s.p.block(q.above(y))).isEmpty(),"Wet wind "+q);
                System.out.println("CREEK_WATER rotation="+r+" aquifer="+aquifer+" ticks=220 sources="+sources+" moving="+moving+" extra="+extra);
                h.assertTrue(extra.isEmpty(),"Water escaped registered volume "+extra.stream().limit(12).toList());h.assertTrue(sources>=expected.size()-flowing.size(),"Sources disappeared");h.succeed();
            }finally{s.close();}});
        }catch(RuntimeException e){s.close();throw e;}
    }
    private static void glide(GameTestHelper h,Rotation r){
        try(var s=new Site(h,r,2700+r.ordinal()*180,900);var c=Fixtures.connectedPlayer(h)){
            s.place();var p=c.player();p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setData(SpiritData.FORM,true);p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.ABILITIES,6);
            p.setPos(s.p.point(new Vec3(17.5,21.8,45.5)));p.setOnGround(false);p.setDeltaMovement(0,-.05,0);
            var m=p.getData(SpiritData.MOVEMENT);m.jumpHeld=true;m.jumpEdgeAt=10;m.jumpPerformedAt=0;m.observedDy=-.05;m.groundLockUntil=0;
            AirMovement.glide(p,m,h.getLevel().getGameTime(),false,false);h.assertTrue(m.startedGlides==1,"Server rejected basic glide");
            Vec3 goal=s.p.point(new Vec3(18.5,19,54.5));int ticks=0;Vec3 v=p.getDeltaMovement();
            for(;ticks<180&&!p.onGround();ticks++){
                Vec3 d=goal.subtract(p.position());d=new Vec3(d.x,0,d.z);if(d.length()>.25)d=d.normalize();else d=Vec3.ZERO;
                p.setDeltaMovement(v);v=FeatherGlide.velocity(p,d);p.move(net.minecraft.world.entity.MoverType.SELF,v);
                v=new Vec3(v.x*.91,(v.y-.08)*.98,v.z*.91);
            }
            var at=s.p.local(p.position());double margin=Math.min(Math.min(at.x-16,23-at.x),Math.min(at.z-51,58-at.z));
            System.out.println("CREEK_GLIDE rotation="+r+" ticks="+ticks+" local="+at+" margin="+margin+" basic_glide=true feather=false");
            h.assertTrue(p.onGround()&&Math.abs(at.y-19)<.05,"Glide missed ledge "+at);h.assertTrue(margin>=1.5,"Landing lacks 1.5 margin: "+margin);
            SpiritMovement.cancel(p,m);h.succeed();
        }
    }
    private static void rainRunoff(GameTestHelper h){
        var s=new Site(h,Rotation.NONE,4900,900);var l=h.getLevel();
        boolean rain=l.isRaining(),thunder=l.isThundering();
        Runnable cleanup=()->{l.setWeatherParameters(0,0,rain,thunder);s.close();};
        try{
            // External shallow runoff belongs to the terrain, not the authored
            // creek. It may flow outside; it must never invade authored air,
            // waterlog dry decoration, or reach the entrance / wind corridor.
            s.place();l.setWeatherParameters(0,300,true,false);
            l.setBlock(s.p.block(new BlockPos(44,8,28)),Blocks.WATER.defaultBlockState(),3);
            var t=l.getStructureManager().getOrCreate(RuinKind.CREEK.template(1));
            var settings=new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings();
            h.runAfterDelay(220,()->{try{
                int checked=0;
                for(var b:List.of(Blocks.AIR,Blocks.GLOW_LICHEN))for(var cell:t.filterBlocks(s.p.origin(),settings,b)){
                    h.assertTrue(l.getFluidState(cell.pos()).isEmpty(),"Runoff invaded dry authored cell "+s.p.local(Vec3.atLowerCornerOf(cell.pos())));checked++;
                }
                for(var q:s.piece.layout().updrafts())for(int y=0;y<=12;y++)h.assertTrue(l.getFluidState(s.p.block(q.above(y))).isEmpty(),"Runoff wet wind "+q);
                h.assertTrue(l.getFluidState(s.p.block(BlockPos.containing(s.piece.layout().entry()))).isEmpty(),"Rain/runoff flooded entry");
                System.out.println("CREEK_RAIN_RUNOFF ticks=220 checkedDryCells="+checked+" new_on_authored_air=0 windDry=true entryDry=true");h.succeed();
            }finally{cleanup.run();}});
        }catch(RuntimeException e){cleanup.run();throw e;}
    }
    private static void bridge(GameTestHelper h,boolean outside){
        try(var s=new Site(h,Rotation.NONE,3600+(outside?180:0),900)){
            s.place();var l=h.getLevel();var layout=s.piece.layout();var b=layout.bridges().getFirst();int rim=outside?5:0;
            if(outside)for(int x=-rim;x<45+rim;x++)for(int z=-rim;z<67+rim;z++)if(x<0||z<0||x>=45||z>=67)for(int y=0;y<=14;y++)l.setBlock(s.p.block(new BlockPos(x,y,z)),(y==14?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),2);
            for(var p:b.platforms())l.setBlock(s.p.block(p),OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),2);
            var dark=new RuinBridgeNavigationGameTests.WalkGraph(l,s.p.origin(),layout.size(),rim,1.0,false);var bypass=dark.path(b.entry(),b.exit());h.assertTrue(bypass.isEmpty(),"Dark shaft bypass "+bypass);
            var back=new RuinBridgeNavigationGameTests.WalkGraph(l,s.p.origin(),layout.size(),rim,1.8);
            h.assertFalse(back.path(b.exit(),b.entry()).isEmpty(),"Dark exit trapped");
            for(var p:b.platforms()){var below=back.below(p.above());h.assertTrue(below!=null&&!back.path(below,Vec3.atBottomCenterOf(b.entry())).isEmpty(),"Dark fall trapped "+p);}
            for(var p:b.platforms())l.setBlock(s.p.block(p),OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState().setValue(SpiritPlatformBlock.LIT,true),2);
            h.assertFalse(new RuinBridgeNavigationGameTests.WalkGraph(l,s.p.origin(),layout.size(),rim,1.8).path(b.entry(),b.exit()).isEmpty(),"Lit shaft blocked");h.succeed();
        }
    }
    private static void lighting(GameTestHelper h){
        // Put the north-facing lintel moss on z=0 of a chunk: reverse placement
        // validates it before its supporting wall slice. Lighting cannot rely on it.
        int dz=900+Math.floorMod(-(h.absolutePos(BlockPos.ZERO).getZ()+900+45),16);
        var s=new Site(h,Rotation.NONE,4100,dz);var l=h.getLevel();try{
            h.assertValueEqual(Math.floorMod(s.p.block(new BlockPos(38,20,45)).getZ(),16),0,"Light boundary witness drifted");
            s.place();for(var q:BlockPos.betweenClosed(BlockPos.ZERO,s.piece.layout().size().offset(-1,-1,-1))){
                var p=s.p.block(q);var state=l.getBlockState(p);
                if(state.is(OriBlocks.SPIRIT_LIGHT_VESSEL))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
                else if(state.is(Blocks.CAVE_VINES)||state.is(Blocks.CAVE_VINES_PLANT)){
                    state=state.setValue(CaveVines.BERRIES,false);if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25))state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25,25);l.setBlock(p,state,2);
                }
            }
            h.runAfterDelay(160,()->{try{
                var rooms=new TreeMap<String,IntSummaryStatistics>();var focus=new TreeMap<String,IntSummaryStatistics>();var dark=new ArrayList<String>();var dim=new ArrayList<String>();
                var foci=Map.of("map",new BlockPos(8,10,11),"shrine",new BlockPos(11,10,22),"bash_start",new BlockPos(12,10,25),"bash_land",new BlockPos(12,15,35),"wind",new BlockPos(17,10,43),"glide_land",new BlockPos(18,19,54),"shaft",new BlockPos(33,10,34));
                for(int x=0;x<45;x++)for(int z=0;z<67;z++)for(int y2=8;y2<86;y2++){
                    var point=new Vec3(x+.5,y2*.5,z+.5);var p=BlockPos.containing(s.p.point(point));
                    if(y2%2==0&&!l.getBlockState(p).getCollisionShape(l,p).isEmpty())continue;if(!RuinSiteInspector.standable(l,s.p.point(point)))continue;
                    int light=l.getBrightness(LightLayer.BLOCK,p);String room=z<17?"entry_map":z<32?"shrine_mill":z<46?"bridge_wind": "rear_bank";
                    rooms.computeIfAbsent(room,k->new IntSummaryStatistics()).accept(light);if(light<1)dark.add(point.toString());
                    for(var e:foci.entrySet())if(Math.abs(x-e.getValue().getX())<=2&&Math.abs(z-e.getValue().getZ())<=2&&Math.abs(point.y-e.getValue().getY())<=2){focus.computeIfAbsent(e.getKey(),k->new IntSummaryStatistics()).accept(light);if(light<7)dim.add(e.getKey()+":"+point+"="+light);}
                }
                rooms.forEach((k,v)->System.out.println("CREEK_LIGHT zone="+k+" n="+v.getCount()+" min="+v.getMin()+" mean="+v.getAverage()));focus.forEach((k,v)->System.out.println("CREEK_LIGHT focus="+k+" n="+v.getCount()+" min="+v.getMin()+" mean="+v.getAverage()));
                System.out.println("CREEK_LIGHT dark="+dark+" dim="+dim);h.assertTrue(dark.isEmpty(),"Zero block light "+dark.stream().limit(20).toList());h.assertTrue(dim.isEmpty(),"Dim focus "+dim.stream().limit(20).toList());h.succeed();
            }finally{s.close();}});
        }catch(RuntimeException e){s.close();throw e;}
    }
}
