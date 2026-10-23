package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class GreatTreeInteriorGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){return List.of(
        new TestFunction("great_tree_interior","great_tree.interior_walks_both_ways_without_abilities","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var base=h.absolutePos(new BlockPos(160,100,0));
            var old=new HashMap<BlockPos,BlockState>();var pinned=new ArrayList<ChunkPos>();
            try{
                for(int x=(base.getX()-12)>>4;x<=(base.getX()+12)>>4;x++)for(int z=(base.getZ()-22)>>4;z<=(base.getZ()+12)>>4;z++){
                    if(level.setChunkForced(x,z,true))pinned.add(new ChunkPos(x,z));level.getChunk(x,z);
                }
                var s=GreatTreeInterior.layout();var exterior=new GreatTreeShape(115115);
                for(int x=-12;x<=12;x++)for(int z=-22;z<=12;z++)for(int y=-10;y<=82;y++){
                    var at=base.offset(x,y,z);old.put(at,level.getBlockState(at));byte c=s.cell(x,y,z);if(c==0)c=exterior.cell(x,y,z);
                    BlockState state=switch(c){
                        case GreatTreeInterior.WOOD->OriBlocks.GREAT_SPIRIT_WOOD.get().defaultBlockState();
                        case GreatTreeInterior.VEIN->OriBlocks.GREAT_SPIRIT_WOOD.get().defaultBlockState().setValue(GreatSpiritWoodBlock.VEIN,true);
                        case GreatTreeInterior.INNER,GreatTreeInterior.FINE_VEIN,GreatTreeInterior.KNOT->OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(cn.wwl.orimod.block.GreatSpiritRootBlock.LIGHT,
                            c==GreatTreeInterior.KNOT?cn.wwl.orimod.block.GreatSpiritRootBlock.Light.KNOT:c==GreatTreeInterior.FINE_VEIN?cn.wwl.orimod.block.GreatSpiritRootBlock.Light.VEIN:cn.wwl.orimod.block.GreatSpiritRootBlock.Light.NONE);
                        case GreatTreeInterior.HEART->OriBlocks.GREAT_TREE_HEART.get().defaultBlockState();
                        case GreatTreeInterior.REWARD->OriBlocks.ENERGY_CELL_PEDESTAL.get().defaultBlockState();
                        case GreatTreeInterior.NORTH,GreatTreeInterior.EAST,GreatTreeInterior.SOUTH,GreatTreeInterior.WEST->Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,switch(c){case 8->Direction.NORTH;case 9->Direction.EAST;case 10->Direction.SOUTH;default->Direction.WEST;});
                        default->Blocks.AIR.defaultBlockState();
                    };level.setBlock(at,state,2);
                }
                var ascent=new ArrayList<GreatTreeInterior.Point>();ascent.add(new GreatTreeInterior.Point(0,0,-14));ascent.add(new GreatTreeInterior.Point(0,0,-3));ascent.addAll(s.ascent());ascent.add(new GreatTreeInterior.Point(0,24,-2));ascent.add(new GreatTreeInterior.Point(2,24,-2));
                walk(h,base,ascent);Collections.reverse(ascent);walk(h,base,ascent);
                var cave=new ArrayList<GreatTreeInterior.Point>();cave.add(new GreatTreeInterior.Point(0,0,-8));cave.add(new GreatTreeInterior.Point(-4,0,-8));cave.addAll(s.descent());cave.add(new GreatTreeInterior.Point(-1,-8,2));
                walk(h,base,cave);Collections.reverse(cave);walk(h,base,cave);
                try(var connected=Fixtures.connectedPlayer(h)){
                    var p=connected.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,0);
                    p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withWisps(7));
                    var reward=base.offset(-1,-8,3);p.setPos(Vec3.atBottomCenterOf(reward.north()));
                    var pedestal=(CellPedestalBlock)OriBlocks.ENERGY_CELL_PEDESTAL.get();
                    h.assertTrue(pedestal.claim(p,reward),"Root cave reward missing");h.assertFalse(pedestal.claim(p,reward),"Root cave reward repeated");
                    var heart=base.offset(2,25,1);p.setPos(Vec3.atBottomCenterOf(heart.offset(0,-1,-3)));
                    Restoration.reset(p.server);h.assertTrue(Restoration.start(p,heart),"Great heart requires an unlearned ability or ancestral blessing");
                    h.assertTrue(Restoration.finish(p),"Great heart did not settle");h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),500,"Ritual reward changed");
                    h.assertFalse(Restoration.start(p,heart),"Duplicate ritual allowed");Restoration.reset(p.server);
                }
                h.succeed();
            }finally{Restoration.reset(level.getServer());old.forEach((p,s)->level.setBlock(p,s,2));for(var chunk:pinned)level.setChunkForced(chunk.x,chunk.z,false);}
        }),
        new TestFunction("great_tree_lighting","great_tree.root_knots_light_steps_and_heart_syncs_be","orimod_gametest:room",100,0,true,h->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(5,3,5));
            var knot=OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(GreatSpiritRootBlock.LIGHT,GreatSpiritRootBlock.Light.KNOT);
            level.setBlock(at,knot,2);
            h.assertValueEqual(knot.getLightEmission(level,at),14,"Root knot is only a painted glow");
            var heart=at.offset(3,0,0);level.setBlock(heart,OriBlocks.GREAT_TREE_HEART.get().defaultBlockState(),2);
            var b=level.getBlockEntity(heart);
            h.assertTrue(b instanceof SanctuaryBlockEntity,"Great heart has no render BE");
            h.assertTrue(b.getType().isValid(b.getBlockState())&&b.getUpdatePacket()!=null,"Heart cannot reach client chunk state");
            h.assertValueEqual(b.saveWithFullMetadata(level.registryAccess()).getString("id"),"orimod:sanctuary","Heart BE save missing");
            h.runAfterDelay(25,()->{
                h.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK,at.north())>=13,"Root light does not propagate onto stairs");
                level.setBlock(heart,Blocks.AIR.defaultBlockState(),2);h.assertTrue(level.getBlockEntity(heart)==null,"Removed heart retains BE");h.succeed();
            });
        }),
        new TestFunction("great_tree_admission","great_tree.ancestral_keeps_blessing_but_refuses_ritual","orimod_gametest:room",80,0,true,h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var p=c.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);p.setData(SpiritData.ABILITIES,0);p.setData(SpiritData.PROGRESS,SpiritProgress.EMPTY.withWisps(7));
                var at=h.absolutePos(new BlockPos(5,2,5));h.getLevel().setBlock(at,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),2);p.setPos(Vec3.atBottomCenterOf(at.north()));
                h.assertTrue(SpiritTreeHeartBlock.claim(p,at),"Ancestral blessing lost");h.assertTrue(p.getData(SpiritData.ABILITIES)!=0,"Ancestral no longer grants a skill");
                h.assertFalse(SpiritTreeHeartBlock.claim(p,at),"Ancestral repeated gift");h.assertFalse(Restoration.start(p,at),"Ancestral started restoration");h.assertFalse(Restoration.active(p),"Ancestral left a session");h.succeed();
            }
        }),
        new TestFunction("great_tree_discovery","great_tree.discovery_and_guidance_do_not_load_remote_site","orimod_gametest:room",80,0,true,h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var p=c.player();var level=h.getLevel();var data=GreatTreeSite.get(level);var previous=data.site();boolean begun=data.begun();
                try{data.choose(new GreatTreeSite.Site(new BlockPos(24000000,80,24000000),BlockPos.ZERO,1,1));int beforePosition=level.getChunkSource().getLoadedChunksCount();
                    p.setPos(24000010,80,24000010);int loaded=level.getChunkSource().getLoadedChunksCount();
                    h.assertTrue(GreatTreeDiscovery.visible(p)==null,"Unloaded tree was seen");
                    int afterVisibility=level.getChunkSource().getLoadedChunksCount();
                    h.assertTrue(GreatTreeDiscovery.guide(p),"Persisted site not guidable");
                    cn.wwl.orimod.OriMod.LOGGER.info("R115_DISCOVERY_CHUNKS beforePosition={} fixtureReady={} afterVisibility={} afterGuide={}",beforePosition,loaded,afterVisibility,level.getChunkSource().getLoadedChunksCount());
                    h.assertValueEqual(level.getChunkSource().getLoadedChunksCount(),loaded,"Discovery loaded remote tree");h.succeed();
                }finally{data.choose(previous);data.begun(begun);}
            }
        })
    );}
    /** Vanilla player-size swept body, half-block stair steps only; no jump, climb or Ori movement. */
    static void walk(GameTestHelper h,BlockPos base,List<GreatTreeInterior.Point> route){
        var level=h.getLevel();var first=route.getFirst();double y=base.getY()+first.y();
        for(int i=1;i<route.size();i++){
            var a=route.get(i-1);var b=route.get(i);int samples=(int)Math.ceil(Math.hypot(b.x()-a.x(),b.z()-a.z())*12);
            for(int j=0;j<=samples;j++){
                double t=j/(double)samples,x=base.getX()+.5+a.x()+(b.x()-a.x())*t,z=base.getZ()+.5+a.z()+(b.z()-a.z())*t;
                double floor=Double.NEGATIVE_INFINITY;
                for(var at:BlockPos.betweenClosed(BlockPos.containing(x-.29,y-1.1,z-.29),BlockPos.containing(x+.29,y+.6,z+.29)))
                    for(var shape:level.getBlockState(at).getCollisionShape(level,at).toAabbs()){
                        var box=shape.move(at);if(box.maxX>x-.29&&box.minX<x+.29&&box.maxZ>z-.29&&box.minZ<z+.29&&box.maxY<=y+.60001)floor=Math.max(floor,box.maxY);
                    }
                h.assertTrue(Double.isFinite(floor)&&Math.abs(floor-y)<=.60001,"Step needs a jump/drop at "+x+","+y+","+z+" floor="+floor);
                y=floor;h.assertTrue(level.noCollision(new AABB(x-.29,y+.001,z-.29,x+.29,y+1.799,z+.29)),"Walk body blocked at "+x+","+y+","+z);
            }
        }
    }
}
