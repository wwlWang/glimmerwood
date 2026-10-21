package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.block.SpiritPlatformBlock;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

/** Search the actual placed template, not just the intended route's waypoint list. */
@GameTestHolder("orimod_gametest")
public final class RuinBridgeNavigationGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var kind:RuinKind.values())for(boolean outside:new boolean[]{false,true})
            tests.add(new TestFunction("bridge_navigation","bridge_navigation."+kind.id+(outside?"_outside_grass":"_template"),"orimod_gametest:platform_room",100,0,true,h->{
                var level=h.getLevel();var base=h.absolutePos(new BlockPos(8,8,8));
                var placement=RuinPlacement.atMinimum(kind,base,Rotation.NONE);
                var piece=new RuinPiece(level.getStructureManager(),kind,placement);
                piece.postProcess(level,level.structureManager(),level.getChunkSource().getGenerator(),RandomSource.create(104),piece.getBoundingBox(),new ChunkPos(base),BlockPos.ZERO);
                var bridge=piece.bridges().getFirst();var size=kind.layout().size();
                int rim=outside?5:0, deck=bridge.platforms().stream().mapToInt(BlockPos::getY).max().orElseThrow();
                if(outside)for(int x=-rim;x<size.getX()+rim;x++)for(int z=-rim;z<size.getZ()+rim;z++)
                    if(x<0 || z<0 || x>=size.getX() || z>=size.getZ())for(int y=0;y<=deck;y++)
                        level.setBlock(base.offset(x,y,z),(y==deck?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),Block.UPDATE_CLIENTS);
                for(var p:bridge.platforms())level.setBlock(placement.block(p),OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState(),Block.UPDATE_CLIENTS);
                // Reject bypasses even for crouched Ori's smaller body; require retreat for a full-size player.
                float smallHeight=cn.wwl.orimod.spirit.SpiritForm.dimensions(net.minecraft.world.entity.Pose.CROUCHING,net.minecraft.world.entity.EntityType.PLAYER.getDimensions()).height();
                var bypass=new WalkGraph(level,base,size,rim,smallHeight,false).path(bridge.entry(),bridge.exit());
                h.assertTrue(bypass.isEmpty(),"Dark bridge bypass "+kind.id+" outside="+outside+": "+bypass);
                var dark=new WalkGraph(level,base,size,rim,1.8);
                h.assertFalse(dark.path(kind.layout().safe(),Vec3.atBottomCenterOf(bridge.entry())).isEmpty(),"Original ruin entry cannot reach bridge entrance "+kind.id);
                // Falling off any dark platform or stepping off the reward end must still allow retreat.
                h.assertFalse(dark.path(bridge.exit(),bridge.entry()).isEmpty(),"Dark exit cannot retreat "+kind.id);
                for(var platform:bridge.platforms()){
                    var landing=dark.below(platform.above());
                    h.assertTrue(landing!=null && !dark.path(landing,Vec3.atBottomCenterOf(bridge.entry())).isEmpty(),"Dark fall traps player at "+platform);
                }
                for(var p:bridge.escape())h.assertFalse(dark.path(p,bridge.entry()).isEmpty(),"Escape does not return to entry "+p);
                if(kind==RuinKind.FOREST){
                    h.assertTrue(level.getBlockState(placement.block(bridge.exit().south())).is(OriBlocks.SPIRIT_LIGHT_VESSEL),"Island reward missing");
                    h.assertTrue(piece.secret()!=null && !piece.secret().entrance().equals(bridge.exit()),"Bridge reward became a secret");
                }
                if(!outside && kind!=RuinKind.CREEK){
                    // Sensitivity: recreate the old dry bank / far return step. The search must find its bypass.
                    var changed=new HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
                    var shortcut=kind==RuinKind.FOREST?bridge.platforms().stream().map(BlockPos::west).toList():List.of(new BlockPos(18,2,30));
                    for(var local:shortcut){var at=placement.block(local);changed.put(at,level.getBlockState(at));level.setBlock(at,Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);}
                    h.assertFalse(new WalkGraph(level,base,size,rim,1.8).path(bridge.entry(),bridge.exit()).isEmpty(),"Search missed deliberately restored dark bypass "+kind.id);
                    changed.forEach((at,state)->level.setBlock(at,state,Block.UPDATE_CLIENTS));
                }
                for(var p:bridge.platforms())level.setBlock(placement.block(p),OriBlocks.SPIRIT_PLATFORM.get().defaultBlockState().setValue(SpiritPlatformBlock.LIT,true),Block.UPDATE_CLIENTS);
                var lit=new WalkGraph(level,base,size,rim,1.8);
                h.assertFalse(lit.path(bridge.entry(),bridge.exit()).isEmpty(),"Lit bridge cannot reach exit "+kind.id);
                h.assertFalse(lit.path(bridge.exit(),bridge.entry()).isEmpty(),"Lit bridge cannot return "+kind.id);
                h.succeed();
            }));
        return tests;
    }

    /** Block-top graph including half-height surfaces and a 0.6-wide player collision box.
     * Generous jump envelope: rise <=1.25, traverse <=4 at that height, then descend. It permits
     * more airtime than a ballistic jump, so rejecting a bypass does not rely on a narrow arc.
     * Water is traversable at its supported bottom; no swimming/climbing, breaking, or Ori abilities.
     * Retreat requires drops <=3 blocks or water; bypass detection allows even unsafe drops.
     * Exterior variants include a five-block terrain ring.
     */
    private static final class WalkGraph {
        private final Map<BlockPos,List<AABB>> boxes=new HashMap<>();
        private final Set<BlockPos> water=new HashSet<>();
        private final Map<Long,List<Vec3>> columns=new HashMap<>();
        private final List<Vec3> nodes=new ArrayList<>();
        private final Map<Vec3,List<Vec3>> edges=new HashMap<>();
        private final double bodyHeight;
        private final boolean safeDrops;
        WalkGraph(ServerLevel level,BlockPos base,BlockPos size,int rim,double bodyHeight){
            this(level,base,size,rim,bodyHeight,true);
        }
        WalkGraph(ServerLevel level,BlockPos base,BlockPos size,int rim,double bodyHeight,boolean safeDrops){
            this.bodyHeight=bodyHeight;this.safeDrops=safeDrops;
            for(int x=-rim;x<size.getX()+rim;x++)for(int z=-rim;z<size.getZ()+rim;z++)for(int y=0;y<size.getY();y++){
                var local=new BlockPos(x,y,z);var pos=base.offset(local);var state=level.getBlockState(pos);
                boxes.put(local,state.getCollisionShape(level,pos).toAabbs().stream().map(b->b.move(local.getX(),local.getY(),local.getZ())).toList());
                if(!state.getFluidState().isEmpty())water.add(local);
            }
            for(var entry:boxes.entrySet())for(var box:entry.getValue()){
                var cell=entry.getKey();double x=cell.getX()+.5,z=cell.getZ()+.5;
                if(x<box.minX || x>box.maxX || z<box.minZ || z>box.maxZ)continue;
                var node=new Vec3(x,box.maxY,z);
                if(clear(node) && !nodes.contains(node)){nodes.add(node);columns.computeIfAbsent(column(cell.getX(),cell.getZ()),k->new ArrayList<>()).add(node);}
            }
        }
        private static long column(int x,int z){return ChunkPos.asLong(x,z);}
        private boolean clear(Vec3 p){
            var body=new AABB(p.x-.299,p.y+.001,p.z-.299,p.x+.299,p.y+bodyHeight-.001,p.z+.299);
            for(int x=(int)Math.floor(body.minX);x<=Math.floor(body.maxX);x++)for(int y=(int)Math.floor(body.minY);y<=Math.floor(body.maxY);y++)for(int z=(int)Math.floor(body.minZ);z<=Math.floor(body.maxZ);z++)
                for(var b:boxes.getOrDefault(new BlockPos(x,y,z),List.of()))if(b.intersects(body))return false;
            return true;
        }
        Vec3 below(BlockPos p){return columns.getOrDefault(column(p.getX(),p.getZ()),List.of()).stream().filter(n->n.y<=p.getY()).max(Comparator.comparingDouble(n->n.y)).orElse(null);}
        List<Vec3> path(BlockPos from,BlockPos to){return path(Vec3.atBottomCenterOf(from),Vec3.atBottomCenterOf(to));}
        List<Vec3> path(Vec3 from,Vec3 to){
            if(!nodes.contains(from) || !nodes.contains(to))return List.of();
            var seen=new HashMap<Vec3,Vec3>();var queue=new ArrayDeque<Vec3>();seen.put(from,from);queue.add(from);
            while(!queue.isEmpty()){
                var a=queue.removeFirst();
                if(a.equals(to)){var path=new ArrayList<Vec3>();for(var n=to;!n.equals(from);n=seen.get(n))path.add(n);path.add(from);Collections.reverse(path);return path;}
                for(var b:edges.computeIfAbsent(a,this::neighbors))if(!seen.containsKey(b)){seen.put(b,a);queue.addLast(b);}
            }
            return List.of();
        }
        private List<Vec3> neighbors(Vec3 a){
            var result=new ArrayList<Vec3>();int x=(int)Math.floor(a.x),z=(int)Math.floor(a.z);
            for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)if(dx*dx+dz*dz<=16)
                for(var b:columns.getOrDefault(column(x+dx,z+dz),List.of())){
                    double dy=b.y-a.y;
                    if(a.equals(b) || dy>1.25 || (safeDrops && dy < -3 && !water.contains(BlockPos.containing(b))))continue;
                    for(double height=0;height<=1.25;height+=.25)if(jump(a,b,height)){result.add(b);break;}
                }
            return result;
        }
        private boolean jump(Vec3 a,Vec3 b,double height){
            if(b.y-a.y>height)return false;
            var apex=a.add(0,height,0);var across=new Vec3(b.x,apex.y,b.z);
            if(!segment(a,apex) || !segment(across,b))return false;
            if(segment(apex,across))return true;
            // A player can turn in flight; testing straight segments alone misses corner bypasses.
            if(Math.abs(a.x-b.x)+Math.abs(a.z-b.z)>4)return false;
            var xFirst=new Vec3(b.x,apex.y,a.z);var zFirst=new Vec3(a.x,apex.y,b.z);
            return (segment(apex,xFirst) && segment(xFirst,across)) || (segment(apex,zFirst) && segment(zFirst,across));
        }
        private boolean segment(Vec3 a,Vec3 b){
            int steps=Math.max(1,(int)Math.ceil(a.distanceTo(b)*8));
            for(int i=0;i<=steps;i++)if(!clear(a.lerp(b,i/(double)steps)))return false;
            return true;
        }
    }
}
