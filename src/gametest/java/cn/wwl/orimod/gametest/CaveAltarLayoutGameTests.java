package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_cave_rebuild")
public final class CaveAltarLayoutGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var out=new ArrayList<TestFunction>();
        for(var r:Rotation.values())out.add(new TestFunction("cave_rebuild","cave_rebuild."+r.name().toLowerCase(),"orimod_cave_rebuild:room",
                Rotation.NONE,300,0,true,h->run(h,r)));
        out.add(new TestFunction("cave_rebuild","cave_rebuild.walkable_block_light","orimod_cave_rebuild:room",Rotation.NONE,500,0,true,CaveAltarLayoutGameTests::lighting));
        out.add(new TestFunction("cave_rebuild","cave_rebuild.aquifer_keeps_rooms_dry","orimod_cave_rebuild:room",Rotation.NONE,400,0,true,CaveAltarLayoutGameTests::aquifer));
        out.add(new TestFunction("cave_rebuild","cave_rebuild.missing_version_stays_legacy","orimod_cave_rebuild:room",Rotation.NONE,100,0,true,h->{
            var l=h.getLevel();var p=new RuinPiece(l.getStructureManager(),RuinKind.CAVE,new RuinPlacement(h.absolutePos(BlockPos.ZERO),Rotation.NONE));
            var tag=p.createTag(StructurePieceSerializationContext.fromLevel(l));tag.remove("LayoutVersion");
            var restored=new RuinPiece(l.getStructureManager(),tag);
            h.assertValueEqual(restored.layoutVersion(),0,"Missing version migrated old piece");
            h.assertValueEqual(restored.layout(),RuinKind.CAVE.layout(),"Old route changed");
            h.assertValueEqual(restored.getBoundingBox(),p.getBoundingBox(),"Old bounds changed");h.succeed();
        }));return out;
    }
    private static void lighting(GameTestHelper h){
        var level=h.getLevel();var p=new RuinPlacement(h.absolutePos(new BlockPos(900,50,600)),Rotation.NONE);
        var piece=new RuinPiece(level.getStructureManager(),RuinKind.CAVE,p,1);var b=piece.getBoundingBox();var forced=new ArrayList<ChunkPos>();
        Runnable cleanup=()->{for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);};
        try{
            for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++){
                var cp=new ChunkPos(x,z);if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(x,z,true);forced.add(cp);}level.getChunk(x,z);
            }
            for(int x=b.minX()>>4;x<=b.maxX()>>4;x++)for(int z=b.minZ()>>4;z<=b.maxZ()>>4;z++)
                piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(99),
                    new BoundingBox(x*16,level.getMinBuildHeight(),z*16,x*16+15,level.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
            // The companion can destroy every vessel. Permanent ambient lighting must survive that.
            for(var cell:level.getStructureManager().getOrCreate(RuinKind.CAVE.template(1)).filterBlocks(p.origin(),new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),OriBlocks.SPIRIT_LIGHT_VESSEL.get()))
                level.setBlock(cell.pos(),Blocks.AIR.defaultBlockState(),2);
            h.runAfterDelay(160,()->{try{
                var rooms=new TreeMap<String,IntSummaryStatistics>();var focus=new TreeMap<String,IntSummaryStatistics>();var dark=new ArrayList<String>();var dim=new ArrayList<String>();
                var foci=Map.of("shrine",new BlockPos(10,6,29),"map",new BlockPos(36,6,29),"altar",new BlockPos(23,7,60),"door",new BlockPos(15,6,44),"grapple_takeoff",new BlockPos(36,7,40));
                for(int x=0;x<47;x++)for(int z=0;z<71;z++)for(int y2=12;y2<62;y2++){
                    // Authored interior only; half-block resolution includes stair treads, dais and basin rims.
                    var local=new Vec3(x+.5,y2*.5,z+.5);var at=BlockPos.containing(p.point(local));
                    if(!level.getBlockState(at).getCollisionShape(level,at).isEmpty() && y2%2==0)continue;
                    if(!RuinSiteInspector.standable(level,p.point(local)))continue;
                    // Exclude uncarved outer roof surfaces: at least one authored roof cell must be above.
                    boolean roof=false;for(int yy=(y2+1)/2+2;yy<32;yy++)if(!level.getBlockState(p.block(new BlockPos(x,yy,z))).isAir()){roof=true;break;}
                    if(!roof)continue;
                    int light=level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,at);
                    String room=z<26?"descent":x<=11&&z>=52&&z<=59?"secret":x<=15&&z>=39&&z<=49?"vault":z>=53?"altar":x>=32&&z>=38?"grapple":z>=38?"nave":x<18?"shrine":x>29?"map":"vestibule";
                    rooms.computeIfAbsent(room,k->new IntSummaryStatistics()).accept(light);
                    if(light<1)dark.add(local.toString());
                    for(var e:foci.entrySet())if(Math.abs(x-e.getValue().getX())<=3&&Math.abs(z-e.getValue().getZ())<=3&&Math.abs(local.y-e.getValue().getY())<=3){
                        focus.computeIfAbsent(e.getKey(),k->new IntSummaryStatistics()).accept(light);
                        if(light<7)dim.add(e.getKey()+":"+local+"="+light);
                    }
                }
                rooms.forEach((name,v)->System.out.println("CAVE_LIGHT room="+name+" count="+v.getCount()+" min="+v.getMin()+" mean="+v.getAverage()));
                focus.forEach((name,v)->System.out.println("CAVE_LIGHT focus="+name+" count="+v.getCount()+" min="+v.getMin()+" mean="+v.getAverage()));
                // Include the flower and all six neighbouring faces: the target must read
                // from the approach even after the companion breaks every light vessel.
                var flower=new BlockPos(36,12,42);var targetLight=new IntSummaryStatistics();
                for(var offset:List.of(BlockPos.ZERO,new BlockPos(1,0,0),new BlockPos(-1,0,0),new BlockPos(0,1,0),new BlockPos(0,-1,0),new BlockPos(0,0,1),new BlockPos(0,0,-1))){
                    var local=flower.offset(offset);int light=level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,p.block(local));
                    targetLight.accept(light);if(light<7)dim.add("grapple_flower:"+local+"="+light);
                }
                System.out.println("CAVE_LIGHT target=grapple_flower count="+targetLight.getCount()+" min="+targetLight.getMin()+" mean="+targetLight.getAverage());
                System.out.println("CAVE_LIGHT dark="+dark.size()+" "+dark+" dim="+dim.size()+" "+dim);
                h.assertTrue(rooms.size()>=9,"Light scan missed a room");h.assertTrue(focus.size()==5,"Light scan missed a focus");
                h.assertTrue(dark.isEmpty(),"Walkable floor has block light 0: "+dark.stream().limit(12).toList());
                h.assertTrue(dim.isEmpty(),"Focus approach below block light 7: "+dim.stream().limit(12).toList());h.succeed();
            }finally{cleanup.run();}});
        }catch(RuntimeException e){cleanup.run();throw e;}
    }
    private static void aquifer(GameTestHelper h){
        var level=h.getLevel();var placement=new RuinPlacement(h.absolutePos(new BlockPos(600,50,600)),Rotation.NONE);
        var piece=new RuinPiece(level.getStructureManager(),RuinKind.CAVE,placement,1);var b=piece.getBoundingBox();var forced=new ArrayList<ChunkPos>();
        Runnable cleanup=()->{for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);};
        try{
            for(int x=(b.minX()-1)>>4;x<=(b.maxX()+1)>>4;x++)for(int z=(b.minZ()-1)>>4;z<=(b.maxZ()+1)>>4;z++){
                var cp=new ChunkPos(x,z);if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(x,z,true);forced.add(cp);}level.getChunk(x,z);
            }
            // Closed water reservoir around all vaults, including high side pockets; entry remains above/dry.
            for(int x=-1;x<=47;x++)for(int y=2;y<=33;y++)for(int z=5;z<=71;z++)
                level.setBlock(placement.block(new BlockPos(x,y,z)),(x==-1||x==47||y==2||y==33||z==5||z==71?Blocks.STONE:Blocks.WATER).defaultBlockState(),2);
            for(int x=b.maxX()>>4;x>=b.minX()>>4;x--)for(int z=b.maxZ()>>4;z>=b.minZ()>>4;z--)
                piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(99),
                    new BoundingBox(x*16,level.getMinBuildHeight(),z*16,x*16+15,level.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
            var air=level.getStructureManager().getOrCreate(RuinKind.CAVE.template(1)).filterBlocks(placement.origin(),new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),Blocks.AIR);
            h.runAfterDelay(200,()->{try{
                for(var cell:air)h.assertTrue(level.getFluidState(cell.pos()).isEmpty(),"Aquifer entered authored air at "+placement.local(Vec3.atLowerCornerOf(cell.pos())));
                h.assertTrue(level.getFluidState(placement.block(new BlockPos(36,13,42))).isEmpty(),"Template inherited aquifer waterlogging in hanging roots");
                h.assertTrue(level.getFluidState(placement.block(new BlockPos(20,6,57))).isEmpty(),"Template inherited aquifer waterlogging in dais slab");
                h.succeed();
            }finally{cleanup.run();}});
        }catch(RuntimeException e){cleanup.run();throw e;}
    }
    private static void run(GameTestHelper h,Rotation r){
        var level=h.getLevel();var layout=RuinKind.CAVE.layout(1);
        var p=RuinPlacement.atMinimum(layout,h.absolutePos(new BlockPos(160+r.ordinal()*96,35,160)),r);
        var piece=new RuinPiece(level.getStructureManager(),RuinKind.CAVE,p,1);
        var box=piece.getBoundingBox();var forced=new ArrayList<ChunkPos>();
        var c=Fixtures.connectedPlayer(h);var metadata=new ArrayList<Runnable>();RuntimeException failure=null;
        try{
            for(int x=(box.minX()>>4)-1;x<=(box.maxX()>>4)+1;x++)for(int z=(box.minZ()>>4)-1;z<=(box.maxZ()>>4)+1;z++){
                var cp=new ChunkPos(x,z);if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(x,z,true);forced.add(cp);}level.getChunk(x,z);
            }
            try(var timing=GameTestDiskBackpressure.measured(level,"cave-construct-and-slices")){
            long cold=System.nanoTime();
            var tag=piece.createTag(StructurePieceSerializationContext.fromLevel(level));piece=new RuinPiece(level.getStructureManager(),tag);
            h.assertValueEqual(piece.layoutVersion(),1,"Saved version lost");
            long max=0,writes=0;
            // Reverse chunk order exercises parts generated before their cores across boundaries.
            for(int x=box.maxX()>>4;x>=box.minX()>>4;x--)for(int z=box.maxZ()>>4;z>=box.minZ()>>4;z--){
                long start=System.nanoTime();piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(99),
                        new BoundingBox(x*16,level.getMinBuildHeight(),z*16,x*16+15,level.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
                long elapsed=System.nanoTime()-start;max=Math.max(max,elapsed);writes+=elapsed;
            }
            System.out.println("CAVE_V1_PERF rotation="+r+" totalMs="+writes/1e6+" maxSliceMs="+max/1e6+" constructAndWriteMs="+(System.nanoTime()-cold)/1e6);
            }
            for(var local:List.of(layout.shrine(),layout.mapStone(),layout.bossAltar())){
                var pos=p.block(local);var s=level.getBlockState(pos);h.assertTrue(SanctuaryAssembly.assembled(s),"Core remains legacy at "+local);
                var owner=SanctuaryAssembly.owner(pos,s);h.assertTrue(level.getBlockEntity(pos)!=null,"Core BE missing "+local);
                for(var cell:SanctuaryShape.cells(owner.kind(),owner.facing())){
                    var at=pos.offset(cell.offset());h.assertTrue(SanctuaryAssembly.matches(owner,at,level.getBlockState(at)),"Part lost across rotated chunk boundary "+at);
                    if(!at.equals(pos))h.assertTrue(level.getBlockEntity(at)==null,"Part has unexpected BE");
                }
            }
            var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(RuinKind.CAVE.location());
            var originChunk=level.getChunkAt(p.origin());var oldStarts=new HashMap<>(originChunk.getAllStarts());
            metadata.add(()->originChunk.setAllStarts(oldStarts));
            originChunk.setStartForStructure(structure,new net.minecraft.world.level.levelgen.structure.StructureStart(structure,originChunk.getPos(),0,
                    new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(List.of(piece))));
            for(var local:List.of(layout.mapStone(),layout.shrine())){
                var chunk=level.getChunkAt(p.block(local));var refs=new HashMap<net.minecraft.world.level.levelgen.structure.Structure,it.unimi.dsi.fastutil.longs.LongSet>();
                chunk.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
                metadata.add(()->chunk.setAllReferences(refs));chunk.addReferenceForStructure(structure,originChunk.getPos().toLong());
            }
            h.assertValueEqual(MapStoneBlock.survey(c.player(),p.block(layout.mapStone())),new MapStoneBlock.Survey(2,2,1),"Large ruin survey silently missed distant vault/altar");
            var door=p.block(new BlockPos(15,6,44));h.assertValueEqual(KeystoneDoorBlock.door(level,door).size(),9,"Owned doorway does not have nine parts");
            var nav=new Navigation(h,p,layout.size());var entry=layout.entry();
            h.assertTrue(nav.reachable(entry,new Vec3(23.5,6.5,57.5)),"Entry to altar route blocked");
            h.assertTrue(nav.reachable(entry,new Vec3(10.5,6,26.5)),"Shrine approach blocked");
            h.assertTrue(nav.reachable(entry,new Vec3(36.5,6,26.5)),"Map approach blocked");
            h.assertTrue(nav.reachable(entry,new Vec3(9.5,9,35.5)),"West keystone route blocked");
            h.assertTrue(RuinSiteInspector.standable(level,p.point(new Vec3(8.5,6,44.5))),"Vault approach beside reward unsupported");
            h.assertFalse(nav.reachable(entry,new Vec3(8.5,6,44.5)),"Closed keystone vault bypassable on foot");
            var player=c.player();player.setPos(p.point(layout.safe()));SpiritForm.toggle(player);player.setData(SpiritData.ABILITIES,63);
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_BLADE.get()));
            h.assertTrue(player.distanceToSqr(Vec3.atCenterOf(p.block(layout.shrine())))<=36,"Safe return outside shrine use range");
            h.assertTrue(RuinSiteInspector.standable(level,p.point(layout.safe())),"Safe point not standable");
            for(var checkpoint:layout.checkpoints())h.assertTrue(RuinSiteInspector.standable(level,p.point(checkpoint.position())),"Blocked checkpoint "+checkpoint);
            SpiritChallenge.request(player,p.block(layout.shrine()),true);
            h.assertTrue(SpiritChallenge.session(player)!=null,"New piece challenge rejected");
            h.assertValueEqual(SpiritChallenge.session(player).layout,layout,"Challenge used old coordinates");SpiritChallenge.finish(player,"exit",true);
            // Grapple anchor is within the unchanged target range of the real lower floor, with supported destination.
            var anchor=p.block(new BlockPos(36,12,42));var from=p.point(new Vec3(36.5,7,40.5));
            h.assertTrue(from.add(0,player.getEyeHeight(),0).distanceTo(Vec3.atCenterOf(anchor))<cn.wwl.orimod.movement.SpiritTargets.RANGE,"Grapple reach increased");
            player.setPos(from);player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,Vec3.atCenterOf(anchor));
            var picked=cn.wwl.orimod.movement.SpiritTargets.find(player,player.getYRot(),player.getXRot());
            h.assertTrue(picked!=null && picked.grapple() && anchor.equals(picked.block()),"Actual grapple selection/line of sight failed");
            var movement=player.getData(SpiritData.MOVEMENT);int completed=movement.completedGrapples;
            cn.wwl.orimod.movement.AirMovement.beginGrapple(player,movement,picked,level.getGameTime());
            for(int tick=0;tick<24 && movement.grapple!=null;tick++){
                cn.wwl.orimod.movement.AirMovement.tickOwned(player,movement,level.getGameTime()+tick,false);
                // A connected client consumes MovementResultPayload.velocity; the fake connection does not.
                player.setDeltaMovement(movement.velocity);
                player.move(net.minecraft.world.entity.MoverType.SELF,movement.velocity);
            }
            h.assertTrue(movement.completedGrapples>completed,"Pull hit gallery lip/ceiling before completing at "+p.local(player.position()));
            cn.wwl.orimod.movement.SpiritMovement.cancel(player,movement);
            h.assertTrue(RuinSiteInspector.standable(level,p.point(layout.checkpoints().getFirst().position())),"Grapple landing unsupported");
            h.assertTrue(nav.reachable(layout.checkpoints().getFirst().position(),new Vec3(37.5,11,46.5)),"East key unreachable from grapple landing");
            var keys=new ItemStack(OriItems.KEYSTONE.get(),2);h.assertValueEqual(KeystoneDoorBlock.insert(player,door,keys),1,"First key not inserted");
            h.assertValueEqual(KeystoneDoorBlock.insert(player,door,keys),0,"Second key not opened");
            h.assertTrue(nav.reachable(entry,new Vec3(8.5,6,44.5)),"Open vault route blocked");
            if(r==Rotation.NONE){
                var oldPlacement=new RuinPlacement(p.origin().offset(0,0,112),Rotation.NONE);
                for(int x=oldPlacement.origin().getX()>>4;x<=(oldPlacement.origin().getX()+22)>>4;x++)for(int z=oldPlacement.origin().getZ()>>4;z<=(oldPlacement.origin().getZ()+38)>>4;z++){
                    var cp=new ChunkPos(x,z);if(!level.getForcedChunks().contains(cp.toLong())){level.setChunkForced(x,z,true);forced.add(cp);}level.getChunk(x,z);
                }
                try(var timing=GameTestDiskBackpressure.measured(level,"cave-legacy-slices")){
                long start=System.nanoTime();var old=new RuinPiece(level.getStructureManager(),RuinKind.CAVE,oldPlacement);double loadMs=(System.nanoTime()-start)/1e6;
                long total=0,peak=0;var bounds=old.getBoundingBox();
                for(int x=bounds.minX()>>4;x<=bounds.maxX()>>4;x++)for(int z=bounds.minZ()>>4;z<=bounds.maxZ()>>4;z++){
                    start=System.nanoTime();old.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(99),
                            new BoundingBox(x*16,level.getMinBuildHeight(),z*16,x*16+15,level.getMaxBuildHeight()-1,z*16+15),new ChunkPos(x,z),BlockPos.ZERO);
                    long elapsed=System.nanoTime()-start;peak=Math.max(peak,elapsed);total+=elapsed;
                }
                System.out.println("CAVE_V0_PERF cachedConstructMs="+loadMs+" totalMs="+total/1e6+" maxSliceMs="+peak/1e6);
                }
            }
        }catch(RuntimeException e){failure=e;}
        finally{SpiritChallenge.finish(c.player(),"exit",false);c.close();for(int i=metadata.size()-1;i>=0;i--)metadata.get(i).run();for(var cp:forced)level.setChunkForced(cp.x,cp.z,false);}
        // Let forced-chunk release settle before an isolated run shuts down, including failures.
        RuntimeException result=failure;h.runAfterDelay(20,()->{if(result!=null)throw result;h.succeed();});
    }
    /** 0.6x1.8 player collision, at most one-block rise/three-block drop, swept headroom. No flight/teleport edges. */
    static final class Navigation{
        final GameTestHelper h;final RuinPlacement p;final BlockPos size;
        Navigation(GameTestHelper h,RuinPlacement p,BlockPos size){this.h=h;this.p=p;this.size=size;}
        boolean stand(int x,int y2,int z){return RuinSiteInspector.standable(h.getLevel(),p.point(new Vec3(x+.5,y2*.5,z+.5)));}
        boolean reachable(Vec3 a,Vec3 b){
            var start=new BlockPos((int)a.x,(int)Math.round(a.y*2),(int)a.z);var goal=new BlockPos((int)b.x,(int)Math.round(b.y*2),(int)b.z);
            if(!stand(start.getX(),start.getY(),start.getZ())||!stand(goal.getX(),goal.getY(),goal.getZ())){System.out.println("CAVE_NAV endpoint "+a+"="+stand(start.getX(),start.getY(),start.getZ())+" / "+b+"="+stand(goal.getX(),goal.getY(),goal.getZ()));return false;}
            var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(start);seen.add(start);
            while(!queue.isEmpty()){
                var n=queue.removeFirst();if(n.equals(goal))return true;
                for(var d:Direction.Plane.HORIZONTAL)for(int dy=2;dy>=-6;dy--){
                    var next=n.relative(d).offset(0,dy,0);int x=next.getX(),z=next.getZ(),y=next.getY();
                    if(x<0||z<0||x>=size.getX()||z>=size.getZ()||y<0||y>=size.getY()*2||seen.contains(next)||!stand(x,y,z))continue;
                    double top=Math.max(y,n.getY())*.5;boolean clear=true;
                    for(int i=0;i<=4;i++){double f=i/4.;var center=p.point(new Vec3(n.getX()+.5+d.getStepX()*f,top,n.getZ()+.5+d.getStepZ()*f));
                        if(!h.getLevel().noCollision(null,new AABB(center.x-.3,center.y+.001,center.z-.3,center.x+.3,center.y+1.8,center.z+.3))){clear=false;break;}}
                    if(clear){seen.add(next);queue.add(next);}
                }
            }System.out.println("CAVE_NAV blocked "+a+" -> "+b+" visited="+seen.size());return false;
        }
    }
}
