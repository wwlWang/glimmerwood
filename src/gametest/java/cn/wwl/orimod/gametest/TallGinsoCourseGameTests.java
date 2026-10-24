package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class TallGinsoCourseGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(int turn=0;turn<4;turn++){
            final int rotation=turn;
            tests.add(new TestFunction("ginso_escape_geometry","ginso.escape_geometry_rotation_"+turn,"orimod_gametest:room",180,0,true,h->{
                try(var site=new Site(h,rotation,1);var connected=Fixtures.connectedPlayer(h)){
                    var p=connected.player();var piece=site.piece;var level=h.getLevel();
                    p.setGameMode(GameType.SURVIVAL);p.setData(SpiritData.FORM,false);p.setData(SpiritData.ABILITIES,0);
                    var e=piece.escape();
                    e.blocks().forEach((local,kind)->{
                        if(kind==TallGinsoInterior.RAIL||kind==GinsoEscapeLayout.ROOT_LIP){
                            var at=piece.point(local);var state=level.getBlockState(at);
                            h.assertTrue(state.getCollisionShape(level,at).max(Direction.Axis.Y)>=1.5,"Root guard is not a real high fence "+local);
                        }
                    });
                    for(var q:e.path()){
                        var foot=localFoot(piece,q.x(),q.feet(),q.z());
                        h.assertTrue(level.noCollision(body(foot)),"Escape body blocked "+q);
                        h.assertFalse(level.noCollision(body(foot).move(0,-.01,0)),"Missing escape tread "+q);
                    }
                    for(int z=0;z<=1;z++){
                        var door=piece.point(new BlockPos(-1,94,z));
                        var target=new BlockHitResult(Vec3.atCenterOf(door),Direction.WEST,door,false);
                        for(int side:new int[]{-2,0}){
                            p.setPos(localFoot(piece,side,side==-2?93.5:94,z));
                            h.assertTrue(level.noCollision(p),"No place to stand while using door "+side);
                            p.gameMode.useItemOn(p,level,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,target);
                            h.assertTrue(level.getBlockState(door).getValue(DoorBlock.OPEN),"Hand could not open gate from "+side);
                            for(double t=0;t<=1.001;t+=.1){
                                var feet=localFoot(piece,-2,94,z).lerp(localFoot(piece,0,94,z),t);
                                h.assertTrue(level.noCollision(body(feet)),"Open gate blocks arrival/return "+side+" "+t);
                            }
                            p.gameMode.useItemOn(p,level,net.minecraft.world.item.ItemStack.EMPTY,net.minecraft.world.InteractionHand.MAIN_HAND,target);
                            h.assertFalse(level.getBlockState(door).getValue(DoorBlock.OPEN),"Manual gate could not close");
                        }
                    }
                    var retreat=piece.point(new BlockPos(0,94,-5));
                    ((DoorBlock)level.getBlockState(retreat).getBlock()).setOpen(p,level,level.getBlockState(retreat),retreat,true);
                    for(double t=0;t<=1.001;t+=.05){
                        var feet=localFoot(piece,0,94,-3).lerp(localFoot(piece,0,94,-6),t);
                        h.assertTrue(level.noCollision(body(feet)),"Escape overlay blocks original retreat "+t);
                    }
                    for(var well:piece.course().wells())if(well.getY()==94)
                        h.assertTrue(level.getBlockState(piece.point(well)).is(OriBlocks.GLADES_WELL),"Escape erased well");
                    h.assertTrue(p.getData(SpiritData.SOUL_LINK).isEmpty(),"Geometry/use changed SoulLink");
                    var tag=piece.createTag(StructurePieceSerializationContext.fromLevel(level));
                    h.assertValueEqual(new GinsoPiece(tag).escapeVersion(),1,"Escape lost on reload");
                    tag.remove("EscapeVersion");h.assertTrue(new GinsoPiece(tag).escape().blocks().isEmpty(),"Old tree migrated");
                    h.succeed();
                }
            }));
            tests.add(new TestFunction("ginso_tall_course","ginso.tall_targets_wells_collision_rotation_"+turn,"orimod_gametest:room",180,0,true,h->{
                try(var site=new Site(h,rotation);var connected=Fixtures.connectedPlayer(h)){
                    var p=connected.player();var level=h.getLevel();var piece=site.piece;var course=piece.course();
                    p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);
                    p.setData(SpiritData.ABILITIES,Ability.BASH.bit|Ability.GRAPPLE.bit|Ability.GLIDE.bit);
                    for(var hop:course.hops()){
                        var start=piece.point(hop.start());var goal=piece.point(hop.goal());
                        p.setPos(Vec3.atBottomCenterOf(start).add(0,hop.kind()==GinsoInterior.WIND?3.0/16:0,0));
                        h.assertTrue(level.noCollision(p),"Blocked hop start "+hop);
                        h.assertTrue(level.noCollision(new AABB(goal).setMaxY(goal.getY()+1.8).deflate(.01)),"Blocked full-height landing "+hop);
                        h.assertTrue(level.getBlockState(goal.below()).isFaceSturdy(level,goal.below(),Direction.UP),"Unsupported landing "+hop);
                        if(hop.kind()==GinsoInterior.LAMP||hop.kind()==GinsoInterior.FLOWER){
                            var at=piece.point(hop.interaction());var v=Vec3.atCenterOf(at).subtract(p.getEyePosition());
                            float yaw=(float)Math.toDegrees(Math.atan2(-v.x,v.z)),pitch=(float)-Math.toDegrees(Math.atan2(v.y,v.horizontalDistance()));
                            var target=SpiritTargets.find(p,yaw,pitch);
                            h.assertTrue(target!=null&&at.equals(target.block()),"Unselectable hop "+hop+" distance="+v.length());
                            if(hop.kind()==GinsoInterior.FLOWER){
                                // Check the whole rising body, including the last speed-
                                // sized sweep used by AirMovement before its arrival test.
                                // The old rear root clipped off-centre starts near the top.
                                for(double dx:new double[]{-.2,0,.2})for(double dz:new double[]{-.2,0,.2}){
                                    p.setPos(Vec3.atBottomCenterOf(start).add(dx,0,dz));
                                    boolean arrived=false;
                                    for(int step=0;step<30;step++){
                                        var point=Vec3.atCenterOf(at);
                                        var velocity=cn.wwl.orimod.movement.AirMovement.grappleVelocity(p,point);
                                        h.assertTrue(level.noCollision(p,p.getBoundingBox().deflate(.01).expandTowards(velocity)),"Grapple flight clips support "+hop+" offset="+dx+","+dz+" at="+p.position());
                                        if(p.position().add(0,p.getBbHeight()*.5,0).distanceTo(point)<.7){arrived=true;break;}
                                        p.move(MoverType.SELF,velocity);
                                    }
                                    h.assertTrue(arrived,"Grapple did not reach flower "+hop);
                                }
                            }
                        }
                        if(hop.kind()==GinsoInterior.WIND){
                            for(int dy=1;dy<=hop.top()-hop.floor()+1;dy++){
                                p.setPos(Vec3.atBottomCenterOf(start.above(dy)));
                                h.assertTrue(UpdraftBlock.lifting(level,p),"Blocked wind at "+hop+" +"+dy);
                            }
                        }
                        if(hop.kind()==GinsoInterior.FLOWER)for(int z=0;z<=1;z++)for(double x=0;x<=2;x+=.2){
                            var mouth=piece.point(new BlockPos((int)Math.floor(x*hop.side()),hop.top()+1,z));
                            h.assertTrue(level.noCollision(new AABB(mouth.getX()+.2,mouth.getY(),mouth.getZ()+.2,mouth.getX()+.8,mouth.getY()+1.8,mouth.getZ()+.8)),"Grapple mouth blocks forward arrival "+hop);
                        }
                    }
                    for(var cell:course.blocks().entrySet())if(cell.getValue()==TallGinsoInterior.RAIL){
                        var at=piece.point(cell.getKey());var state=level.getBlockState(at);
                        h.assertTrue(state.is(Blocks.DARK_OAK_FENCE)&&state.getCollisionShape(level,at).max(Direction.Axis.Y)==1.5,"Not a full-height fence "+at);
                    }
                    for(var local:course.wells()){
                        var well=piece.point(local);p.setPos(Vec3.atBottomCenterOf(piece.point(local.north(2))));
                        h.assertTrue(GinsoCourse.checkpoint(p,well),"Well did not bind "+local);
                        var link=p.getData(SpiritData.SOUL_LINK).orElseThrow();
                        h.assertValueEqual(link.pos(),piece.point(local.north(2)),"Well bound wrong landing");
                        h.assertTrue(SoulLink.usable(level,link.pos()),"Bound landing not usable");
                    }
                    // Closed retreat doors physically stop a full-width body, not just a cell probe.
                    for(var hop:course.hops()){
                        var door=piece.point(new BlockPos(0,hop.top()+1,-5));
                        h.assertTrue(level.getBlockState(door).is(Blocks.DARK_OAK_DOOR),"Missing retreat door "+hop);
                        h.assertFalse(level.noCollision(new AABB(door).inflate(-.01)),"Door has no collision");
                        var a=piece.point(new BlockPos(0,hop.top()+1,-3));
                        var b=piece.point(new BlockPos(0,hop.top()+1,-4));
                        // Sweep a full human body up to the closed door in all rotations.
                        for(double t=0;t<=1.001;t+=.1){
                            var feet=Vec3.atBottomCenterOf(a).lerp(Vec3.atBottomCenterOf(b),t);
                            h.assertTrue(level.noCollision(new AABB(feet.x-.3,feet.y,feet.z-.3,feet.x+.3,feet.y+1.8,feet.z+.3)),"Retreat door approach blocked "+hop+" t="+t);
                        }
                    }
                    var zombie=EntityType.ZOMBIE.create(level);var cow=EntityType.COW.create(level);
                    for(var hop:course.hops())for(var reason:MobSpawnType.values()){
                        zombie.setPos(Vec3.atBottomCenterOf(piece.point(hop.start())));
                        var event=new MobSpawnEvent.PositionCheck(zombie,level,reason,null);GinsoSanctuary.spawn(event);
                        h.assertTrue((event.getResult()==MobSpawnEvent.PositionCheck.Result.FAIL)==(reason==MobSpawnType.NATURAL||reason==MobSpawnType.CHUNK_GENERATION),"Wrong spawn refusal "+reason);
                    }
                    cow.setPos(Vec3.atBottomCenterOf(piece.point(course.hops().getFirst().start())));
                    var passive=new MobSpawnEvent.PositionCheck(cow,level,MobSpawnType.NATURAL,null);GinsoSanctuary.spawn(passive);
                    h.assertFalse(passive.getResult()==MobSpawnEvent.PositionCheck.Result.FAIL,"Passive animals refused");
                    var loaded=new GinsoPiece(piece.createTag(StructurePieceSerializationContext.fromLevel(level)));
                    h.assertValueEqual(loaded.shapeVersion(),3,"Shape version");h.assertValueEqual(loaded.courseVersion(),2,"Course version");
                    h.succeed();
                }
            }));
        }
        tests.add(new TestFunction("ginso_tall_falls","ginso.tall_real_middle_and_high_falls_stay_in_their_lesson","orimod_gametest:room",180,0,true,h->{
            try(var site=new Site(h,0);var connected=Fixtures.connectedPlayer(h)){
                var p=connected.player();p.setData(SpiritData.FORM,false);p.setData(SpiritData.ABILITIES,0);
                for(int i=0;i<61;i++)p.tick();
                for(var hop:site.piece.course().hops()){
                    // Falling back through the exact ascent opening, including guard-top height.
                    var start=site.piece.point(hop.kind()==GinsoInterior.FLOWER?new BlockPos(2*hop.side(),hop.start().getY(),0):hop.start());
                    p.setHealth(20);p.fallDistance=0;p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);
                    double headroom=hop.section().equals("jump")||hop.top()==78?1.01:2.5;
                    p.setPos(start.getX()+.5,site.base.getY()+hop.top()+headroom,start.getZ()+.5);
                    h.assertTrue(h.getLevel().noCollision(p),"Fall started inside a block "+hop);
                    double from=p.getY(),last=from;int ticks=0;
                    while(!p.onGround()&&ticks++<80){p.travel(Vec3.ZERO);p.doCheckFallDamage(0,p.getY()-last,0,p.onGround());last=p.getY();}
                    h.assertTrue(p.onGround()&&p.isAlive(),"Did not land "+hop);
                    h.assertTrue(from-p.getY()<=10.0001,"Fall too long "+hop+" drop="+(from-p.getY()));
                    h.assertTrue(p.getY()>=site.base.getY()+hop.floor()+1,"Lost completed lesson "+hop);
                    h.assertValueEqual(p.getHealth(),20f,"Cushion fall damage "+hop);
                    cn.wwl.orimod.OriMod.LOGGER.info("GINSO_FALL {} top={} landed={} drop={} hp={} ticks={}",hop.section(),hop.top(),p.getY()-site.base.getY(),from-p.getY(),p.getHealth(),ticks);
                }
                // Check the explicit last-resort block too, with an ordinary human and real gravity.
                var at=site.piece.point(new BlockPos(0,4,0));
                for(int y=5;y<=30;y++)h.getLevel().setBlock(at.atY(site.base.getY()+y),Blocks.AIR.defaultBlockState(),2);
                for(boolean cushion:new boolean[]{true,false}){
                    h.getLevel().setBlock(at,cushion?OriBlocks.GINSO_LANDING.get().defaultBlockState():Blocks.STONE.defaultBlockState(),2);
                    p.setHealth(20);p.invulnerableTime=0;p.fallDistance=0;p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);p.setPos(Vec3.atBottomCenterOf(at.above(20)));
                    double last=p.getY();int ticks=0;
                    while(!p.onGround()&&ticks++<80){p.travel(Vec3.ZERO);p.doCheckFallDamage(0,p.getY()-last,0,p.onGround());last=p.getY();}
                    h.assertTrue(cushion?p.getHealth()==20:p.getHealth()<20,"Cushion/stone negative control "+cushion);
                }
                h.succeed();
            }
        }));
        return tests;
    }
    private static Vec3 localFoot(GinsoPiece piece,int x,double y,int z){return Vec3.atBottomCenterOf(piece.point(new BlockPos(x,(int)Math.floor(y),z))).add(0,y%1,0);}
    private static AABB body(Vec3 at){return new AABB(at.x-.3,at.y,at.z-.3,at.x+.3,at.y+1.8,at.z+.3);}
    /** Every remote chunk is pinned, and restored on setup failure as well as assertion failure. */
    private static final class Site implements AutoCloseable {
        final ServerLevel level;final BlockPos base;final GinsoPiece piece;
        final Map<BlockPos,BlockState> saved=new HashMap<>();final List<ChunkPos> pinned=new ArrayList<>();
        final Map<LevelChunk,Map<Structure,it.unimi.dsi.fastutil.longs.LongSet>> refs=new HashMap<>();
        Map<Structure,StructureStart> starts;LevelChunk startChunk;
        Site(GameTestHelper h,int turn){this(h,turn,0);}
        Site(GameTestHelper h,int turn,int escape){
            // Published c750c0a course assertions stay on EscapeVersion=0; the new cases
            // independently verify the opened east rail and manual incoming gate.
            level=h.getLevel();var at=h.absolutePos(new BlockPos(160,0,0));base=new BlockPos(at.getX(),80,at.getZ());piece=new GinsoPiece(base,115117,turn,0,3,2,escape);
            int radius=escape==0?15:23;
            try{
                var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.parse("orimod:ginso_tree"));
                for(int x=(base.getX()-radius)>>4;x<=(base.getX()+radius)>>4;x++)for(int z=(base.getZ()-radius)>>4;z<=(base.getZ()+radius)>>4;z++){
                    if(level.setChunkForced(x,z,true))pinned.add(new ChunkPos(x,z));var chunk=level.getChunk(x,z);
                    refs.put(chunk,new HashMap<>(chunk.getAllReferences()));chunk.setAllReferences(new HashMap<>(chunk.getAllReferences()));
                    chunk.addReferenceForStructure(structure,new ChunkPos(base).toLong());
                    if(!chunk.hasPrimedHeightmap(Heightmap.Types.OCEAN_FLOOR_WG))Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                }
                startChunk=level.getChunkAt(base);starts=new HashMap<>(startChunk.getAllStarts());
                startChunk.setStartForStructure(structure,new StructureStart(structure,new ChunkPos(base),0,new PiecesContainer(List.of(piece))));
                var clip=new BoundingBox(base.getX()-radius,base.getY()+(escape==0?-1:90),base.getZ()-radius,base.getX()+radius,base.getY()+(escape==0?99:124),base.getZ()+radius);
                for(var pos:BlockPos.betweenClosed(clip.minX(),clip.minY(),clip.minZ(),clip.maxX(),clip.maxY(),clip.maxZ()))saved.put(pos.immutable(),level.getBlockState(pos));
                piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(base),base);
            }catch(Throwable error){close();throw error;}
        }
        public void close(){
            saved.forEach((p,s)->level.setBlock(p,s,2));if(startChunk!=null&&starts!=null)startChunk.setAllStarts(starts);
            refs.forEach(LevelChunk::setAllReferences);for(var c:pinned)level.setChunkForced(c.x,c.z,false);
        }
    }
}
