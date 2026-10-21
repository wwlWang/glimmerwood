package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.combat.*;
import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.network.MovementInputPayload;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class RuinBridgesGameTests {
    // Center and four corners, inset 0.3 blocks so the player's full width is on the bank.
    private static final double[][] FOOTPRINT={{.5,.5},{.3,.3},{.3,.7},{.7,.3},{.7,.7}};
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var kind:RuinKind.values())for(var rotation:Rotation.values())
            tests.add(new TestFunction("ruin_bridges","ruin_bridges."+kind.id+"_"+rotation.name().toLowerCase(Locale.ROOT),"orimod_gametest:room",100,0,true,h->{
                try(var c=Fixtures.connectedPlayer(h)){
                    CrackedRuinGameTests.prepare(c);var level=h.getLevel();var p=c.player();
                    var placement=RuinPlacement.atMinimum(kind,h.absolutePos(new BlockPos(32,30,32)),rotation);
                    var original=new RuinPiece(level.getStructureManager(),kind,placement);
                    var piece=new RuinPiece(level.getStructureManager(),original.createTag(StructurePieceSerializationContext.fromLevel(level)));
                    h.assertValueEqual(piece.bridges(),kind.layout().bridges(),"Piece bridge contract roundtrip");
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(104),piece.getBoundingBox(),new ChunkPos(placement.origin()),BlockPos.ZERO);
                    var bridge=piece.bridges().getFirst();var entry=placement.block(bridge.entry());var exit=placement.block(bridge.exit());
                    var blocks=bridge.platforms().stream().map(placement::block).toList();var lamps=bridge.lanterns().stream().map(placement::block).toList();
                    for(var at:blocks)h.assertTrue(level.getBlockState(at).is(OriBlocks.SPIRIT_PLATFORM),"Generated platform missing");
                    p.setPos(placement.point(Vec3.atBottomCenterOf(bridge.entry())));
                    if(kind==RuinKind.FOREST){
                        var lamp=lamps.getFirst();double farthest=0;
                        for(var offset:FOOTPRINT){
                            p.setPos(placement.point(new Vec3(bridge.entry().getX()+offset[0],bridge.entry().getY(),bridge.entry().getZ()+offset[1])));
                            p.setDeltaMovement(Vec3.ZERO);
                            // Independent starting positions: reset only the fixture's previous launch/source state.
                            p.getData(SpiritData.MOVEMENT).targetCooldowns.clear();
                            level.setBlock(lamp,OriBlocks.BASH_ANCHOR.get().defaultBlockState(),Block.UPDATE_CLIENTS);
                            for(var at:blocks){level.setBlock(at,OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),Block.UPDATE_CLIENTS);PlatformTimers.get(level).remove(at.asLong());}
                            farthest=Math.max(farthest,assertSelectable(h,c,lamp));
                            lookAndBash(h,c,lamp,rotation);
                            // Release invokes lantern ignition synchronously, before any player motion/collision.
                            h.assertTrue(level.getBlockState(lamp).getValue(SpiritLanternBlock.LIT),"Selected lamp did not ignite");
                            for(var at:blocks)h.assertTrue(SpiritPlatformGameTests.lit(h,at),"Landing path not solid at Bash release");
                            p.move(MoverType.SELF,p.getDeltaMovement());p.move(MoverType.SELF,new Vec3(0,-.1,0));
                            h.assertTrue(blocks.contains(BlockPos.containing(p.position().add(0,-.05,0))),"Actual Bash did not land on bridge from "+Arrays.toString(offset)+": "+p.position());
                            h.assertTrue(p.getY()>=entry.getY()-.01,"Bash fell into water");
                        }
                        com.mojang.logging.LogUtils.getLogger().info("Bridge Bash margin {} {}: farthest={}",kind.id,rotation,farthest);
                    }else if(kind==RuinKind.CREEK){
                        // A single real burst covers all five staggered steps, with a base-energy affordable budget.
                        p.setData(SpiritData.ENERGY,SpiritProgression.maxEnergy(p));
                        h.assertTrue(SpiritProgression.spendEnergy(p,LightBurst.COST),"Base energy cannot afford bridge burst");
                        var orb=new LightBurst(OriEntities.LIGHT_BURST.get(),level);orb.setOwner(p);orb.setPos(placement.point(new Vec3(15.5,8.5,11.5)));orb.detonate();
                        for(var at:blocks)h.assertTrue(SpiritPlatformGameTests.lit(h,at),"Burst left a shaft step dark");
                        h.assertTrue(5*12*Flash.COST_PER_TICK<SpiritProgression.maxEnergy(p),"Five jumps exceed base Flash energy");
                    }else{
                        double firstDistance=assertFootprint(h,c,placement,bridge.entry(),lamps.getFirst());
                        SpiritLanternBlock.ignite(level,lamps.getFirst(),100);
                        h.assertTrue(SpiritPlatformGameTests.lit(h,blocks.getFirst()),"First lamp missed near half");
                        h.assertFalse(SpiritPlatformGameTests.lit(h,blocks.getLast()),"First lamp lit entire gallery");
                        // A lit midpoint is within the second lamp's actual Bash reach/LOS.
                        var midpoint=bridge.platforms().get(5).above();
                        h.assertTrue(SpiritPlatformGameTests.lit(h,placement.block(midpoint.below())),"Second lamp approach lacks footing");
                        double secondDistance=assertFootprint(h,c,placement,midpoint,lamps.getLast());
                        SpiritLanternBlock.ignite(level,lamps.getLast(),100);
                        com.mojang.logging.LogUtils.getLogger().info("Bridge Bash margin {} {}: first={}, second={}",kind.id,rotation,firstDistance,secondDistance);
                    }
                    p.setPos(Vec3.atBottomCenterOf(entry).add(0,12,0));
                    var route=new ArrayList<BlockPos>();route.add(entry);blocks.forEach(b->route.add(b.above()));route.add(exit);
                    h.assertTrue(reachable(h,entry,exit,route) && reachable(h,exit,entry,route),"Lit route not traversable both ways");
                    for(var feet:route){h.assertTrue(clear(h,feet),"Blocked landing headroom "+feet);h.assertFalse(level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty(),"Unsupported lit route");}
                    // Exercise the fallback with every platform dark, regardless of the source ledger.
                    for(var at:blocks)level.setBlock(at,OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),Block.UPDATE_CLIENTS);
                    var escape=bridge.escape().stream().map(placement::block).toList();
                    for(var feet:escape){h.assertTrue(clear(h,feet),"Blocked dark escape "+feet);h.assertTrue(level.getBlockState(feet).is(Blocks.LADDER) || !level.getBlockState(feet.below()).getCollisionShape(level,feet.below()).isEmpty(),"Escape has no floor/ladder");}
                    // Whole-template navigation below separately proves dark return without a forward bypass.
                    // Shared loaded structure-reference lookup exposes saved contracts, including rotations.
                    p.setPos(Vec3.atBottomCenterOf(entry));var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(kind.location());
                    var origin=level.getChunkAt(placement.origin());var current=level.getChunkAt(entry);var starts=new HashMap<>(origin.getAllStarts());
                    var refs=new HashMap<Structure,it.unimi.dsi.fastutil.longs.LongSet>();current.getAllReferences().forEach((k,v)->refs.put(k,new it.unimi.dsi.fastutil.longs.LongOpenHashSet(v)));
                    try{
                        origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(piece))));current.addReferenceForStructure(structure,origin.getPos().toLong());
                        var result=RuinSecrets.find(p);h.assertTrue(result.bridges().size()==1,"Bridge devtools lookup failed");
                        h.assertValueEqual(result.bridges().getFirst(),Map.of("kind",kind.id,"entry",xyz(entry),"exit",xyz(exit),"lanterns",lamps.stream().map(RuinBridgesGameTests::xyz).toList(),"platforms",blocks.size()),"Bridge devtools schema");
                        var old=piece.createTag(StructurePieceSerializationContext.fromLevel(level));old.remove("OriBridges");var legacy=new RuinPiece(level.getStructureManager(),old);
                        h.assertTrue(legacy.bridges().isEmpty(),"Old piece invented bridges");h.assertFalse(legacy.createTag(StructurePieceSerializationContext.fromLevel(level)).contains("OriBridges"),"Old piece migrated bridge metadata");
                        origin.setStartForStructure(structure,new StructureStart(structure,origin.getPos(),0,new PiecesContainer(List.of(legacy))));h.assertTrue(RuinSecrets.find(p).bridges().isEmpty(),"Legacy devtools bridges not empty");
                    }finally{origin.setAllStarts(starts);current.setAllReferences(refs);}
                    for(var anchor:kind.layout().critical())h.assertValueEqual(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(placement.block(anchor.position())).getBlock()),anchor.block(),"Bridge changed original critical anchor");
                    for(var at:lamps)level.setBlock(at,Blocks.AIR.defaultBlockState(),3);for(var at:blocks)level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
                }h.succeed();
            }));
        return tests;
    }
    private static double assertFootprint(GameTestHelper h,Fixtures.ConnectedPlayer c,RuinPlacement placement,BlockPos feet,BlockPos lamp){
        double farthest=0;
        for(var offset:FOOTPRINT){
            c.player().setPos(placement.point(new Vec3(feet.getX()+offset[0],feet.getY(),feet.getZ()+offset[1])));
            farthest=Math.max(farthest,assertSelectable(h,c,lamp));
        }
        return farthest;
    }
    private static double assertSelectable(GameTestHelper h,Fixtures.ConnectedPlayer c,BlockPos lamp){
        var p=c.player();var direction=Vec3.atCenterOf(lamp).subtract(p.getEyePosition());
        h.assertTrue(direction.length()<=SpiritTargets.RANGE-.5,"Lamp lacks 0.5-block Bash margin at "+p.position()+": "+direction.length());
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
        var target=SpiritTargets.find(p,yaw,pitch);
        h.assertTrue(target!=null && lamp.equals(target.block()) && !target.grapple() && target.valid(p),"Cannot select visible lamp from "+p.position());
        return direction.length();
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private static boolean clear(GameTestHelper h,BlockPos feet){return h.getLevel().noCollision(new AABB(feet.getX()+.2,feet.getY()+.001,feet.getZ()+.2,feet.getX()+.8,feet.getY()+1.8,feet.getZ()+.8));}
    private static boolean reachable(GameTestHelper h,BlockPos start,BlockPos end,List<BlockPos> nodes){
        var seen=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();seen.add(start);queue.add(start);
        while(!queue.isEmpty()){
            var a=queue.removeFirst();
            for(var b:nodes)if(!seen.contains(b) && Math.abs(a.getY()-b.getY())<=1 && Math.pow(a.getX()-b.getX(),2)+Math.pow(a.getZ()-b.getZ(),2)<=4 && clear(h,b)){
                // A conservative jump arc clears intermediate walls as well as each landing.
                var from=Vec3.atBottomCenterOf(a);var to=Vec3.atBottomCenterOf(b);boolean pass=true;
                for(int i=1;i<8;i++){double t=i/8.;var p=from.lerp(to,t).add(0,Math.sin(Math.PI*t)*(a.getY()==b.getY() && a.distSqr(b)<=1 ? 0 : 1.2),0);if(!h.getLevel().noCollision(new AABB(p.x-.29,p.y+.01,p.z-.29,p.x+.29,p.y+1.8,p.z+.29))){pass=false;break;}}
                if(pass){seen.add(b);queue.add(b);}
            }
        }return seen.contains(end);
    }
    private static void lookAndBash(GameTestHelper h,Fixtures.ConnectedPlayer c,BlockPos lamp,Rotation rotation){
        var p=c.player();var direction=Vec3.atCenterOf(lamp).subtract(p.getEyePosition());
        float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));float pitch=(float)-Math.toDegrees(Math.atan2(direction.y,Math.hypot(direction.x,direction.z)));
        var s=p.getData(SpiritData.MOVEMENT);int seq=s.sequence+1,accepted=s.acceptedBashes;long now=h.getLevel().getGameTime();
        SpiritMovement.accept(p,new MovementInputPayload(seq,now,MovementInputPayload.ACTIVE|MovementInputPayload.BASH,0,0,yaw,pitch));
        h.assertTrue(s.bash!=null && lamp.equals(s.bash.target.block()),"Cannot select actual lamp from bank");
        var d=rotation.rotate(Direction.SOUTH);float across=d.toYRot();
        SpiritMovement.accept(p,new MovementInputPayload(seq+1,now,MovementInputPayload.ACTIVE,0,0,across,0));
        h.assertTrue(s.bash==null && s.acceptedBashes==accepted+1,"Actual Bash release failed");
    }
}
