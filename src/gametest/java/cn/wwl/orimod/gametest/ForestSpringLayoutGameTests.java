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

@GameTestHolder("orimod_forest_rebuild")
public final class ForestSpringLayoutGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(var r:Rotation.values())out.add(new TestFunction("forest_rebuild","forest_rebuild."+r.name().toLowerCase(),"orimod_forest_rebuild:room",Rotation.NONE,500,0,true,h->navigation(h,r)));
        for(var r:Rotation.values())out.add(new TestFunction("forest_rebuild","forest_rebuild.carved_foundation_"+r.name().toLowerCase(),"orimod_forest_rebuild:room",Rotation.NONE,300,0,true,h->foundation(h,r)));
        for(var r:Rotation.values())out.add(new TestFunction("forest_rebuild","forest_rebuild.graded_rim_"+r.name().toLowerCase(),"orimod_forest_rebuild:room",Rotation.NONE,400,0,true,h->gradedRim(h,r)));
        for(boolean outside:new boolean[]{false,true})out.add(new TestFunction("forest_rebuild","forest_rebuild.dark_bridge_"+outside,"orimod_forest_rebuild:room",Rotation.NONE,300,0,true,h->bridge(h,outside)));
        out.add(new TestFunction("forest_rebuild","forest_rebuild.permanent_block_light","orimod_forest_rebuild:room",Rotation.NONE,500,0,true,ForestSpringLayoutGameTests::lighting));
        out.add(new TestFunction("forest_rebuild","forest_rebuild.rain_stream_aquifer_no_spill","orimod_forest_rebuild:room",Rotation.NONE,500,0,true,ForestSpringLayoutGameTests::water));
        out.add(new TestFunction("forest_rebuild","forest_rebuild.missing_version_stays_legacy","orimod_forest_rebuild:room",Rotation.NONE,100,0,true,h->{
            var l=h.getLevel();var p=new RuinPiece(l.getStructureManager(),RuinKind.FOREST,new RuinPlacement(h.absolutePos(BlockPos.ZERO),Rotation.NONE));
            var n=p.createTag(StructurePieceSerializationContext.fromLevel(l));n.remove("LayoutVersion");var restored=new RuinPiece(l.getStructureManager(),n);
            h.assertValueEqual(restored.layoutVersion(),0,"Old piece upgraded");h.assertValueEqual(restored.layout(),RuinKind.FOREST.layout(),"Old layout changed");h.assertValueEqual(restored.bridges(),p.bridges(),"Old bridges changed");h.assertValueEqual(restored.getBoundingBox(),p.getBoundingBox(),"Old bounds changed");h.succeed();
        }));return out;
    }
    static final class Site implements AutoCloseable {
        final GameTestHelper h;final RuinPlacement p;final RuinPiece piece;final int[] approachSamples;final List<ChunkPos> forced=new ArrayList<>();final List<Runnable> undo=new ArrayList<>();
        Site(GameTestHelper h,Rotation r,int dx,int dz){this(h,r,dx,dz,false);}
        Site(GameTestHelper h,Rotation r,int dx,int dz,boolean approach){this(h,r,dx,dz,approach,0);}
        Site(GameTestHelper h,Rotation r,int dx,int dz,boolean approach,int terrainVersion){this.h=h;
            p=RuinPlacement.atMinimum(RuinKind.FOREST.layout(1),h.absolutePos(new BlockPos(dx,35,dz)),r);
            int[] samples=approach?new int[RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH]:new int[0];Arrays.fill(samples,p.origin().getY()+4);approachSamples=samples.clone();
            int[] natural=new int[terrainVersion==1?(39+40)*(55+40):0];Arrays.fill(natural,1);
            piece=new RuinPiece(h.getLevel().getStructureManager(),RuinKind.FOREST,p,1,samples,natural,terrainVersion);
            var b=piece.getBoundingBox();var l=h.getLevel();int prepared=0;
            GameTestDiskBackpressure.drain(l,"forest-prepare-begin");
            try{for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++){
                var cp=new ChunkPos(x,z);if(!l.getForcedChunks().contains(cp.toLong())){l.setChunkForced(x,z,true);forced.add(cp);}l.getChunk(x,z);
                GameTestDiskBackpressure.preparedChunk(l,++prepared,"forest-prepare");
            }GameTestDiskBackpressure.drain(l,"forest-prepare-ready");}catch(RuntimeException e){close();throw e;}
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
            }}
            if(piece.forestTerrainVersion()==1&&approachSamples.length!=0){
                var approach=new RuinApproachPiece(RuinApproachPiece.doorway(RuinKind.FOREST,piece.layout(),p),approachSamples,false,true,piece.forestApproachSideProfile());
                var a=approach.getBoundingBox();for(int x=a.minX()>>4;x<=a.maxX()>>4;x++)for(int z=a.minZ()>>4;z<=a.maxZ()>>4;z++)
                    approach.postProcess(accessor,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),new BoundingBox(x*16,l.getMinBuildHeight(),z*16,x*16+15,l.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
            }
            System.out.println("FOREST_V1_PERF rotation="+p.rotation()+" totalMs="+total/1e6+" maxSliceMs="+max/1e6);
        }
        void publish(){
            var l=h.getLevel();var structure=l.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(RuinKind.FOREST.location());
            var origin=l.getChunkAt(p.origin());var starts=new HashMap<>(origin.getAllStarts());undo.add(()->origin.setAllStarts(starts));
            origin.setStartForStructure(structure,new net.minecraft.world.level.levelgen.structure.StructureStart(structure,origin.getPos(),0,new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece))));
            var chunks=new HashSet<ChunkPos>();for(var local:List.of(piece.layout().shrine(),piece.layout().mapStone()))chunks.add(new ChunkPos(p.block(local)));
            for(var cp:chunks){var chunk=l.getChunk(cp.x,cp.z);var refs=new HashMap<net.minecraft.world.level.levelgen.structure.Structure,it.unimi.dsi.fastutil.longs.LongSet>();chunk.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));undo.add(()->chunk.setAllReferences(refs));chunk.addReferenceForStructure(structure,origin.getPos().toLong());}
        }
        @Override public void close(){for(int i=undo.size()-1;i>=0;i--)undo.get(i).run();undo.clear();CreekFixtureChunks.release(h.getLevel(),forced);forced.clear();}
    }
    private static void foundation(GameTestHelper h,Rotation r){
        try(var s=new Site(h,r,2700+r.ordinal()*160,500)){
            var l=h.getLevel();var holes=new ArrayList<BlockPos>();
            for(int z=0;z<3;z++){
                var base=s.p.block(new BlockPos(16,0,z));holes.add(base);
                l.setBlock(base.below(4),Blocks.STONE.defaultBlockState(),2);
                for(int d=1;d<=3;d++)l.setBlock(base.below(d),Blocks.AIR.defaultBlockState(),2);
            }
            // Natural dirt outside the authored footprint must not acquire a new pillar.
            var outside=s.p.block(new BlockPos(0,0,0));l.setBlock(outside,Blocks.DIRT.defaultBlockState(),2);
            l.setBlock(outside.below(4),Blocks.STONE.defaultBlockState(),2);for(int d=1;d<=3;d++)l.setBlock(outside.below(d),Blocks.AIR.defaultBlockState(),2);
            s.place();for(var base:holes)for(int d=1;d<=3;d++)h.assertTrue(l.getBlockState(base.below(d)).is(Blocks.DIRT),"Authored foundation floats over carved pocket: "+base+" depth="+d);
            h.assertTrue(l.isEmptyBlock(outside.below()),"Unwritten terrain acquired a pillar");
            var oldPos=s.p.block(new BlockPos(0,0,0));var old=new RuinPiece(l.getStructureManager(),RuinKind.FOREST,new RuinPlacement(oldPos,r));
            var check=old.placement().block(new BlockPos(9,0,0));l.setBlock(check.below(4),Blocks.STONE.defaultBlockState(),2);for(int d=1;d<=3;d++)l.setBlock(check.below(d),Blocks.AIR.defaultBlockState(),2);
            try(var timing=GameTestDiskBackpressure.measured(l,"forest-legacy-reference")){
            long start=System.nanoTime();old.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),s.piece.getBoundingBox(),new ChunkPos(oldPos),BlockPos.ZERO);
            System.out.println("FOREST_LEGACY_REFERENCE fullBoundsMs="+(System.nanoTime()-start)/1e6+" rotation="+r);
            }
            h.assertTrue(l.isEmptyBlock(check.below()),"Legacy placement changed its foundation");h.succeed();
        }
    }
    private static void gradedRim(GameTestHelper h,Rotation r){
        try(var s=new Site(h,r,3800+r.ordinal()*180,500,true)){
            var l=h.getLevel();var land=new HashSet<BlockPos>();
            var template=l.getStructureManager().getOrCreate(RuinKind.FOREST.template(1));
            for(var cell:template.filterBlocks(BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.DIRT))if(cell.pos().getY()==0)land.add(cell.pos());
            var distance=new HashMap<BlockPos,Integer>();for(var p:land)for(int dx=-9;dx<=9;dx++)for(int dz=-9;dz<=9;dz++)if(Math.abs(dx)+Math.abs(dz)<=9&&!land.contains(p.offset(dx,0,dz)))distance.merge(p.offset(dx,0,dz),Math.abs(dx)+Math.abs(dz),Math::min);
            var ring=new HashSet<BlockPos>();distance.forEach((p,d)->{if(d<=8)ring.add(p);});
            for(var p:distance.keySet()){int top=p.getX()<=1&&p.getZ()>=22&&p.getZ()<=29?-4:p.getX()>=14&&p.getX()<=24&&p.getZ()>=-8&&p.getZ()<=-3?0:3;
                for(int y=-6;y<=9;y++)l.setBlock(s.p.block(p.atY(y)),(y<top?Blocks.DIRT:y==top?Blocks.GRASS_BLOCK:Blocks.AIR).defaultBlockState(),2);
                if(distance.get(p)==8&&top==3)l.setBlock(s.p.block(p.atY(top+1)),Blocks.AZURE_BLUET.defaultBlockState(),2);
            }
            // A carved pocket below each entrance pier must be bridged by its
            // foundation, not mistaken for untouched natural ground.
            for(int x:new int[]{13,25}){
                h.assertTrue(land.contains(new BlockPos(x,0,3)),"Pier missing authored foundation: "+x+",3");
                l.setBlock(s.p.block(new BlockPos(x,-4,3)),Blocks.STONE.defaultBlockState(),2);
                for(int y=-3;y<=4;y++)l.setBlock(s.p.block(new BlockPos(x,y,3)),Blocks.AIR.defaultBlockState(),2);
            }
            s.place(true);
            int[] samples=new int[RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH];Arrays.fill(samples,s.p.origin().getY()+4);
            var approach=new RuinApproachPiece(RuinApproachPiece.doorway(RuinKind.FOREST,s.piece.layout(),s.p),samples);
            approach.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),approach.getBoundingBox(),new ChunkPos(s.p.origin()),BlockPos.ZERO);
            var top=new HashMap<BlockPos,Integer>();for(var p:distance.keySet()){int y=9;for(;y>=-6;y--){var at=s.p.block(p.atY(y));var state=l.getBlockState(at);if(state.isFaceSturdy(l,at,Direction.UP)||state.is(Blocks.MOSSY_STONE_BRICK_STAIRS))break;}top.put(p,y);}
            int worst=0,boundary45=0,retainedEdges=0,retainedDrop=0;String pair="none";for(var p:ring){int y=top.get(p);for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
                var q=p.relative(d);Integer neighbour=land.contains(q)?Integer.valueOf(5):top.get(q);
                if(neighbour!=null&&distance.get(p)==4&&distance.getOrDefault(q,0)==5)boundary45=Math.max(boundary45,Math.abs(y-neighbour));
                if(neighbour!=null&&distance.get(p)==8&&distance.getOrDefault(q,0)==9&&y-neighbour>1){
                    var lip=s.p.block(p.atY(y+1));var guard=l.getBlockState(lip);
                    h.assertTrue(guard.is(Blocks.MOSSY_STONE_BRICK_WALL)&&guard.getCollisionShape(l,lip).max(Direction.Axis.Y)>=1.5,
                            "Unprotected outer cliff: "+p.atY(y)+" -> "+q.atY(neighbour)+" guard="+guard);
                    for(int yy=neighbour+1;yy<y;yy++)h.assertTrue(l.getBlockState(s.p.block(p.atY(yy))).is(Blocks.MOSSY_STONE_BRICKS),"Bare retaining face at "+p.atY(yy));
                    retainedEdges++;retainedDrop=Math.max(retainedDrop,y-neighbour);continue;
                }
                if(neighbour!=null&&Math.abs(y-neighbour)>worst){
                    worst=Math.abs(y-neighbour);var a=p.atY(y);var b=q.atY(neighbour);
                    pair="local="+a+" world="+s.p.block(a)+" state="+l.getBlockState(s.p.block(a))
                            +" <-> local="+b+" world="+s.p.block(b)+" state="+l.getBlockState(s.p.block(b));
                }
            }}
            System.out.println("FOREST_RIM rotation="+r+" ring="+ring.size()+" maxStep="+worst+" boundary4to5="+boundary45+" retainedEdges="+retainedEdges+" retainedDrop="+retainedDrop+" worstPair="+pair);
            h.assertTrue(worst<=1,"Deep rim trench remains: neighbour step "+worst+" "+pair);
            int guards=0,boundary=0;for(var p:ring){
                boolean outer=distance.get(p)==8;if(outer)boundary++;
                boolean guard=l.getBlockState(s.p.block(p.atY(top.get(p)+1))).is(Blocks.MOSSY_STONE_BRICK_WALL);
                if(p.getX()>=17&&p.getX()<=21&&p.getZ()<0)h.assertFalse(guard,"Entrance main path fenced off "+p);
                if(!guard)continue;guards++;
                var lip=s.p.block(p.atY(top.get(p)+1));
                h.assertFalse(Blocks.AZURE_BLUET.defaultBlockState().canSurvive(l,lip),"Later flower feature can overwrite lip "+p);
                h.assertTrue(outer,"Guard away from outer cliff "+p);
                h.assertTrue(Direction.Plane.HORIZONTAL.stream().anyMatch(d->{var q=p.relative(d);return distance.getOrDefault(q,0)==9&&top.get(p)-top.get(q)>1;}),"Guard without a real drop "+p);
            }
            h.assertTrue(guards>0&&guards<boundary,"Retaining fixture missing lip or encircled");
            System.out.println("FOREST_RIM_GUARDS rotation="+r+" columns="+guards+" outerBoundary="+boundary+" fraction="+(double)guards/boundary+" entranceGuards=0");
            for(var p:ring){var at=s.p.block(p.atY(top.get(p)));var state=l.getBlockState(at);
                boolean lip=l.getBlockState(at.above()).is(Blocks.MOSSY_STONE_BRICK_WALL);
                boolean entry=p.getX()>=17&&p.getX()<=21&&p.getZ()<0;
                h.assertTrue(state.is(Blocks.GRASS_BLOCK)||lip&&state.is(Blocks.MOSSY_STONE_BRICKS)||entry&&(state.is(Blocks.MOSSY_STONE_BRICKS)||state.is(Blocks.MOSSY_STONE_BRICK_STAIRS)),"Rim not grass or authored masonry "+p+" "+state);
            }
            for(int x:new int[]{13,25})for(int y=-4;y<=5;y++){
                var at=s.p.block(new BlockPos(x,y,3));
                h.assertTrue(l.getBlockState(at).isFaceSturdy(l,at,Direction.UP),"Pier support gap: local="+new BlockPos(x,y,3)+" world="+at+" state="+l.getBlockState(at));
            }
            System.out.println("FOREST_PIER_SUPPORT rotation="+r+" columns=2 continuousY=-4..5");
            var restored=new RuinPiece(l.getStructureManager(),s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)));
            h.assertValueEqual(restored.getBoundingBox(),s.piece.getBoundingBox(),"Skirt bounds lost on save");
            h.assertTrue(Arrays.equals(restored.createTag(StructurePieceSerializationContext.fromLevel(l)).getIntArray("ForestApproachTerrain"),samples),"Grading lost saved approach profile");
            // Walk in from untouched natural ground beyond the eight-column skirt,
            // through the actual saved approach stairs, with no movement ability.
            for(int x=17;x<=21;x++)for(int z=-12;z<=-9;z++)for(int y=-6;y<=7;y++)
                l.setBlock(s.p.block(new BlockPos(x,y,z)),(y==3?Blocks.GRASS_BLOCK:y<3?Blocks.DIRT:Blocks.AIR).defaultBlockState(),2);
            approach.postProcess(l,l.structureManager(),l.getChunkSource().getGenerator(),RandomSource.create(116),approach.getBoundingBox(),new ChunkPos(s.p.origin()),BlockPos.ZERO);
            var shifted=new RuinPlacement(s.p.block(new BlockPos(0,0,-12)),r);
            var nav=new CaveAltarLayoutGameTests.Navigation(h,shifted,new BlockPos(39,32,67));
            h.assertTrue(nav.reachable(new Vec3(19.5,4,.5),s.piece.layout().entry().add(0,0,12)),"Natural ground to entrance blocked "+r);
            System.out.println("FOREST_RIM_ENTRY rotation="+r+" naturalZ=-12 to=entry path=true abilities=none");h.succeed();
        }
    }
    private static void navigation(GameTestHelper h,Rotation r){
        var l=h.getLevel();try(var s=new Site(h,r,500+r.ordinal()*160,500);var c=Fixtures.connectedPlayer(h)){
            s.place();s.publish();var p=s.p;var layout=s.piece.layout();
            h.assertTrue(RuinLayouts.find(l,p.block(layout.shrine()),piece->piece==s.piece)!=null,"Saved piece lookup missed start");
            h.assertValueEqual(MapStoneBlock.survey(c.player(),p.block(layout.mapStone())),new MapStoneBlock.Survey(1,2,1),"Map missed versioned remote rewards/storm altar");
            var saved=new RuinPiece(l.getStructureManager(),s.piece.createTag(StructurePieceSerializationContext.fromLevel(l)));h.assertValueEqual(saved.layoutVersion(),1,"Version lost");h.assertValueEqual(saved.layout(),layout,"Contract lost");
            for(var local:List.of(layout.shrine(),layout.mapStone(),layout.stormAltar())){
                var at=p.block(local);var state=l.getBlockState(at);h.assertTrue(SanctuaryAssembly.assembled(state),"Legacy core "+local);var owner=SanctuaryAssembly.owner(at,state);
                h.assertTrue(l.getBlockEntity(at)!=null,"Missing core BE");for(var cell:SanctuaryShape.cells(owner.kind(),owner.facing()))h.assertTrue(SanctuaryAssembly.matches(owner,at.offset(cell.offset()),l.getBlockState(at.offset(cell.offset()))),"Part mismatch "+local+" "+cell);
            }
            h.assertValueEqual(RuinLayouts.atShrine(l,RuinKind.FOREST,p.block(layout.shrine())).layout(),layout,"New shrine fallback used old coordinates");
            var nav=new CaveAltarLayoutGameTests.Navigation(h,p,layout.size());
            for(var goal:List.of(new Vec3(19.5,6,42.5),new Vec3(7.5,6,14.5),new Vec3(31.5,6,14.5),new Vec3(8.5,10,28.5),new Vec3(19.5,8,18.5)))h.assertTrue(nav.reachable(layout.entry(),goal),"Entry cannot reach "+goal);
            for(var cp:layout.checkpoints())h.assertTrue(RuinSiteInspector.standable(l,p.point(cp.position())),"Checkpoint obstructed "+cp);
            var door=p.block(new BlockPos(29,6,40));h.assertValueEqual(KeystoneDoorBlock.door(l,door).size(),9,"Door part ownership");
            h.assertFalse(nav.reachable(layout.entry(),new Vec3(34.5,6,39.5)),"Closed vault bypass");
            var player=c.player();CrackedRuinGameTests.prepare(c);player.setData(SpiritData.ABILITIES,63);
            var bridge=layout.bridges().getFirst();var lamp=p.block(bridge.lanterns().getFirst());
            for(var offset:List.of(new Vec3(.5,0,.5),new Vec3(.3,0,.3),new Vec3(.3,0,.7),new Vec3(.7,0,.3),new Vec3(.7,0,.7))){
                SpiritMovement.cancel(player,player.getData(SpiritData.MOVEMENT));var m=player.getData(SpiritData.MOVEMENT);m.targetCooldowns.clear();m.recentBash="";m.recentBashUntil=0;
                player.setPos(p.point(Vec3.atLowerCornerOf(bridge.entry()).add(offset)));player.setDeltaMovement(Vec3.ZERO);
                l.setBlock(lamp,OriBlocks.BASH_ANCHOR.get().defaultBlockState(),2);
                for(var pos:bridge.platforms()){var at=p.block(pos);l.setBlock(at,OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),2);PlatformTimers.get(l).remove(at.asLong());}
                var aim=Vec3.atCenterOf(lamp).subtract(player.getEyePosition());h.assertTrue(aim.length()<=SpiritTargets.RANGE-.5,"Bash reach margin");
                float yaw=(float)Math.toDegrees(Math.atan2(-aim.x,aim.z)),pitch=(float)-Math.toDegrees(Math.atan2(aim.y,Math.hypot(aim.x,aim.z)));int seq=m.sequence+1,accepted=m.acceptedBashes;long now=l.getGameTime();
                SpiritMovement.accept(player,new MovementInputPayload(seq,now,MovementInputPayload.ACTIVE|MovementInputPayload.BASH,0,0,yaw,pitch));h.assertTrue(m.bash!=null&&lamp.equals(m.bash.target.block()),"Target not selectable");
                SpiritMovement.accept(player,new MovementInputPayload(seq+1,now,MovementInputPayload.ACTIVE,0,0,r.rotate(Direction.SOUTH).toYRot(),0));h.assertTrue(m.acceptedBashes==accepted+1,"Real Bash not released");
                for(var at:bridge.platforms())h.assertTrue(l.getBlockState(p.block(at)).getValue(SpiritPlatformBlock.LIT),"Landing not lit at release "+at);
                player.move(net.minecraft.world.entity.MoverType.SELF,player.getDeltaMovement());player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,-.1,0));
                h.assertTrue(bridge.platforms().stream().map(p::block).toList().contains(BlockPos.containing(player.position().add(0,-.05,0))),"Bash missed bridge "+p.local(player.position()));
            }
            h.assertTrue(nav.reachable(Vec3.atBottomCenterOf(bridge.entry()),Vec3.atBottomCenterOf(bridge.exit())),"Lit bridge not traversable");
            h.assertTrue(nav.reachable(Vec3.atBottomCenterOf(bridge.exit()),new Vec3(20.5,8,29.5)),"Island key inaccessible");
            var keys=new ItemStack(OriItems.KEYSTONE.get(),2);h.assertValueEqual(KeystoneDoorBlock.insert(player,door,keys),1,"First key");h.assertValueEqual(KeystoneDoorBlock.insert(player,door,keys),0,"Second key");h.assertTrue(nav.reachable(layout.entry(),new Vec3(34.5,6,39.5)),"Open vault blocked");
            player.setPos(p.point(layout.safe()));SpiritChallenge.request(player,p.block(layout.shrine()),true);h.assertTrue(SpiritChallenge.session(player)!=null,"New shrine challenge rejected");h.assertValueEqual(SpiritChallenge.session(player).layout,layout,"Challenge selected old coordinates");SpiritChallenge.finish(player,"exit",false);
            SpiritMovement.cancel(player,player.getData(SpiritData.MOVEMENT));h.succeed();
        }
    }
    private static void bridge(GameTestHelper h,boolean outside){
        try(var s=new Site(h,Rotation.NONE,2100+(outside?100:0),500)){
            s.place();var l=h.getLevel();var layout=s.piece.layout();var b=layout.bridges().getFirst();int rim=outside?5:0;
            if(outside)for(int x=-rim;x<39+rim;x++)for(int z=-rim;z<55+rim;z++)if(x<0||z<0||x>=39||z>=55)for(int y=0;y<=7;y++)l.setBlock(s.p.block(new BlockPos(x,y,z)),(y==7?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),2);
            for(var p:b.platforms())l.setBlock(s.p.block(p),OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),2);
            var dark=new RuinBridgeNavigationGameTests.WalkGraph(l,s.p.origin(),layout.size(),rim,1.0,false);
            var bypass=dark.path(b.entry(),b.exit());h.assertTrue(bypass.isEmpty(),"Dark bridge bypass "+bypass);
            var retreat=new RuinBridgeNavigationGameTests.WalkGraph(l,s.p.origin(),layout.size(),rim,1.8);
            h.assertFalse(retreat.path(b.exit(),b.entry()).isEmpty(),"Dark island cannot retreat");
            for(var p:b.platforms()){var landing=retreat.below(p.above());h.assertTrue(landing!=null&&!retreat.path(landing,Vec3.atBottomCenterOf(b.entry())).isEmpty(),"Dark fall trapped "+p);}
            h.succeed();
        }
    }
    private static void lighting(GameTestHelper h){lighting(h,false);}
    static void slopeLighting(GameTestHelper h){lighting(h,true);}
    private static void lighting(GameTestHelper h,boolean slope){
        var s=new Site(h,Rotation.NONE,1400,500,false,slope?1:0);var l=h.getLevel();try{
            if(slope)ForestPolicyRedGameTests.slope(s);
            s.place();for(var pos:BlockPos.betweenClosed(s.piece.getBoundingBox().minX(),s.piece.getBoundingBox().minY(),s.piece.getBoundingBox().minZ(),s.piece.getBoundingBox().maxX(),s.piece.getBoundingBox().maxY(),s.piece.getBoundingBox().maxZ())){
                var state=l.getBlockState(pos);
                if(state.is(OriBlocks.SPIRIT_LIGHT_VESSEL))l.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                else if(state.is(Blocks.CAVE_VINES)||state.is(Blocks.CAVE_VINES_PLANT)){
                    state=state.setValue(net.minecraft.world.level.block.CaveVines.BERRIES,false);
                    // Prevent a random growth tick adding a fresh berry below the harvested head.
                    if(state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25))state=state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AGE_25,25);
                    l.setBlock(pos,state,2);
                }
            }
            h.runAfterDelay(160,()->{try{
                int vines=0;for(var pos:BlockPos.betweenClosed(s.piece.getBoundingBox().minX(),s.piece.getBoundingBox().minY(),s.piece.getBoundingBox().minZ(),s.piece.getBoundingBox().maxX(),s.piece.getBoundingBox().maxY(),s.piece.getBoundingBox().maxZ())){
                    var state=l.getBlockState(pos);if(state.is(Blocks.CAVE_VINES)||state.is(Blocks.CAVE_VINES_PLANT)){vines++;h.assertFalse(state.getValue(net.minecraft.world.level.block.CaveVines.BERRIES),"Harvested baseline regrew berries "+pos);}
                }
                System.out.println("FOREST_LIGHT_BASELINE containers_removed=true harvested_vines="+vines+" luminous_berries=0");
                var rooms=new TreeMap<String,IntSummaryStatistics>();var focus=new TreeMap<String,IntSummaryStatistics>();var dark=new ArrayList<String>();var dim=new ArrayList<String>();
                var foci=Map.of("shrine",new BlockPos(7,6,17),"map",new BlockPos(31,6,17),"storm",new BlockPos(19,6,46),"door",new BlockPos(29,6,40),"bridge_entry",new BlockPos(19,8,18),"bridge_exit",new BlockPos(19,8,27));
                for(int x=0;x<39;x++)for(int z=0;z<55;z++)for(int y2=12;y2<62;y2++){
                    var local=new Vec3(x+.5,y2*.5,z+.5);var at=BlockPos.containing(s.p.point(local));if(y2%2==0&&!l.getBlockState(at).getCollisionShape(l,at).isEmpty())continue;
                    if(!RuinSiteInspector.standable(l,s.p.point(local)))continue;
                    int v=l.getBrightness(LightLayer.BLOCK,at);String room=z<10?"entrance":x<=10&&z>=35?"secret":x>=29&&z>=36?"vault":z>=38?"storm":x<12?"shrine_course":x>26?"map": "spring";
                    rooms.computeIfAbsent(room,k->new IntSummaryStatistics()).accept(v);if(v<1)dark.add(local.toString());
                    for(var e:foci.entrySet())if(Math.abs(x-e.getValue().getX())<=3&&Math.abs(z-e.getValue().getZ())<=3&&Math.abs(local.y-e.getValue().getY())<=3){focus.computeIfAbsent(e.getKey(),k->new IntSummaryStatistics()).accept(v);if(v<7)dim.add(e.getKey()+":"+local+"="+v);}
                }
                var lamp=s.p.block(new BlockPos(19,9,22));for(var d:Direction.values()){int v=l.getBrightness(LightLayer.BLOCK,lamp.relative(d));if(v<7)dim.add("lamp:"+d+"="+v);}
                rooms.forEach((k,v)->System.out.println("FOREST_LIGHT room="+k+" count="+v.getCount()+" min="+v.getMin()+" mean="+v.getAverage()));focus.forEach((k,v)->System.out.println("FOREST_LIGHT focus="+k+" count="+v.getCount()+" min="+v.getMin()+" mean="+v.getAverage()));
                System.out.println("FOREST_LIGHT dark="+dark+" dim="+dim);h.assertTrue(rooms.size()>=7,"Scan missed region");h.assertTrue(dark.isEmpty(),"Zero light "+dark.stream().limit(12).toList());h.assertTrue(dim.isEmpty(),"Focus below seven "+dim.stream().limit(20).toList());h.succeed();
            }finally{s.close();}});
        }catch(RuntimeException e){s.close();throw e;}
    }
    private static void water(GameTestHelper h){
        var s=new Site(h,Rotation.NONE,1700,500);var l=h.getLevel();boolean rain=l.isRaining(),thunder=l.isThundering();
        Runnable cleanup=()->{l.setWeatherParameters(0,0,rain,thunder);s.close();};
        try{
            // Shallow natural runoff at deck height and an aquifer beneath the structure.
            for(int x=-1;x<=39;x++)for(int z=-1;z<=55;z++)for(int y=0;y<=4;y++)l.setBlock(s.p.block(new BlockPos(x,y,z)),Blocks.WATER.defaultBlockState(),2);
            s.place();l.setWeatherParameters(0,300,true,false);
            var initial=new HashSet<BlockPos>();var template=l.getStructureManager().getOrCreate(RuinKind.FOREST.template(1));
            var expected=template.filterBlocks(s.p.origin(),new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.WATER);expected.forEach(c->initial.add(c.pos()));
            // A source at the garden's outer ground level may spread outside, never uphill onto the path.
            l.setBlock(s.p.block(new BlockPos(38,5,26)),Blocks.WATER.defaultBlockState(),3);
            h.runAfterDelay(220,()->{try{
                int remaining=0;for(var cell:template.filterBlocks(s.p.origin(),new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.AIR))h.assertTrue(l.getFluidState(cell.pos()).isEmpty(),"External water reached walkway "+s.p.local(Vec3.atLowerCornerOf(cell.pos())));
                for(var pos:initial){h.assertTrue(l.getFluidState(pos).isSource(),"Pool source lost "+pos);remaining++;}
                for(var cell:template.filterBlocks(s.p.origin(),new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.GLOW_LICHEN))h.assertTrue(l.getFluidState(cell.pos()).isEmpty(),"Inherited aquifer waterlogging");
                System.out.println("FOREST_WATER original_sources="+initial.size()+" retained="+remaining+" new_on_authored_air=0 ticks=220 rain=true aquifer=true runoff=true");h.succeed();
            }finally{cleanup.run();}});
        }catch(RuntimeException e){cleanup.run();throw e;}
    }
}
