package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.movement.SpiritTargets;
import cn.wwl.orimod.progression.Ability;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

/** Real final block collision/selection, including the native crown behind the new overlays. */
@GameTestHolder("orimod_r117")
public final class GinsoCrownGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(int rotation=0;rotation<4;rotation++) {
            int turn=rotation;
            tests.add(new TestFunction("r117_crown", "r117_ginso.crown_targets_and_landings_"+turn,"orimod_r117:room",160,0,true,h->{
                var level=h.getLevel();var origin=h.absolutePos(new BlockPos(80,15,0));
                var piece=new GinsoPiece(origin,1387583691837622L,turn,0);
                var before=new HashMap<BlockPos,BlockState>();var pins=new ArrayList<ChunkPos>();
                var clip=new BoundingBox(origin.getX()-19,origin.getY()+90,origin.getZ()-19,origin.getX()+19,origin.getY()+116,origin.getZ()+19);
                try(var connected=Fixtures.connectedPlayer(h)) {
                    for(int x=clip.minX()>>4;x<=clip.maxX()>>4;x++)for(int z=clip.minZ()>>4;z<=clip.maxZ()>>4;z++) {
                        if(level.setChunkForced(x,z,true))pins.add(new ChunkPos(x,z));
                        var chunk=level.getChunk(x,z);Heightmap.primeHeightmaps(chunk,EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG));
                    }
                    for(var at:BlockPos.betweenClosed(clip.minX(),clip.minY(),clip.minZ(),clip.maxX(),clip.maxY(),clip.maxZ()))before.put(at.immutable(),level.getBlockState(at));
                    piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(0),clip,new ChunkPos(origin),origin);
                    var p=connected.player();p.setData(SpiritData.AWAKENED,true);p.setData(SpiritData.FORM,true);
                    p.setData(SpiritData.ABILITIES,Ability.BASH.bit|Ability.GRAPPLE.bit);
                    p.refreshDimensions();
                    for(var hop:GinsoClimbEscape.forTree(piece.shapeSeed(),turn,piece.sculpture()).crownHops()) {
                        p.setPos(Vec3.atBottomCenterOf(piece.point(hop.start())));
                        var anchor=piece.point(hop.interaction());var delta=Vec3.atCenterOf(anchor).subtract(p.getEyePosition());
                        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                        float pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
                        var selected=SpiritTargets.find(p,yaw,pitch);
                        h.assertTrue(selected!=null&&anchor.equals(selected.block()),"Crown flower occluded or out of reach: "+hop+" selected="+selected+" ray="+level.clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(),Vec3.atCenterOf(anchor),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p)).getBlockPos());
                        h.assertTrue(level.getBlockState(anchor).is(OriBlocks.GRAPPLE_FLOWER),"Flower missing");
                        var goal=piece.point(hop.goal());
                        h.assertTrue(level.getBlockState(goal.below()).isFaceSturdy(level,goal.below(),Direction.UP),"Crown landing unsupported");
                        h.assertTrue(level.noCollision(new AABB(goal.getX()+.2,goal.getY()+.001,goal.getZ()+.2,goal.getX()+.8,goal.getY()+1.201,goal.getZ()+.8)),"Crown landing blocked");
                    }
                    h.succeed();
                } finally {
                    before.forEach((at,state)->level.setBlock(at,state,2));
                    for(var chunk:pins)level.setChunkForced(chunk.x,chunk.z,false);
                }
            }));
        }
        return tests;
    }
}
