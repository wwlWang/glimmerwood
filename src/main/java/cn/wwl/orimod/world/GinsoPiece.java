package cn.wwl.orimod.world;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.block.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import java.util.*;

/** Same seed in every chunk; immutable sculpture cached with a bounded lifetime/size. */
public final class GinsoPiece extends StructurePiece {
    private final BlockPos origin;private final long seed;
    private final int entrance, approach, courseVersion;
    private static final Map<Long,GinsoShape> CACHE=new LinkedHashMap<>();
    public GinsoPiece(BlockPos at,long seed){this(at,seed,0,0);}
    public GinsoPiece(BlockPos at,long seed,int entrance,int approach){super(OriWorldgen.GINSO_PIECE.get(),0,new BoundingBox(at.getX()-28,at.getY()-8,at.getZ()-28,at.getX()+28,at.getY()+64,at.getZ()+28));origin=at.immutable();this.seed=seed;this.entrance=Math.floorMod(entrance,4);this.approach=Math.clamp(approach,-6,10);courseVersion=GinsoInterior.VERSION;}
    public GinsoPiece(CompoundTag tag){super(OriWorldgen.GINSO_PIECE.get(),tag);origin=BlockPos.of(tag.getLong("Origin"));seed=tag.getLong("ShapeSeed");entrance=Math.floorMod(tag.getInt("Entrance"),4);approach=Math.clamp(tag.getInt("Approach"),-6,10);courseVersion=tag.getInt("CourseVersion");}
    public BlockPos origin(){return origin;}
    public long shapeSeed(){return seed;}
    public int entrance(){return entrance;}
    public int approach(){return approach;}
    public boolean hasCourse(){return courseVersion>0;}
    public BlockPos point(BlockPos local){return origin.offset(GinsoInterior.rotate(local,entrance));}
    public BlockPos local(BlockPos world){return GinsoInterior.rotate(world.subtract(origin),-entrance);}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag tag){tag.putLong("Origin",origin.asLong());tag.putLong("ShapeSeed",seed);tag.putInt("ShapeVersion",GinsoShape.VERSION);tag.putInt("Entrance",entrance);tag.putInt("Approach",approach);tag.putInt("CourseVersion",courseVersion);}
    public static GinsoShape shape(long seed){synchronized(CACHE){var s=CACHE.get(seed);if(s!=null)return s;if(CACHE.size()>=8)CACHE.remove(CACHE.keySet().iterator().next());s=new GinsoShape(seed);CACHE.put(seed,s);return s;}}
    @Override public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot){
        long started=System.nanoTime();var shape=shape(seed);long shaped=System.nanoTime();int writes=0;
        var wood=OriBlocks.GINSO_WOOD.get().defaultBlockState();var leaf=OriBlocks.GINSO_LEAVES.get().defaultBlockState();var vine=OriBlocks.GINSO_VINE.get().defaultBlockState();
        var p=new BlockPos.MutableBlockPos();
        for(int x=Math.max(clip.minX(),boundingBox.minX());x<=Math.min(clip.maxX(),boundingBox.maxX());x++)
            for(int z=Math.max(clip.minZ(),boundingBox.minZ());z<=Math.min(clip.maxZ(),boundingBox.maxZ());z++){
                int dx=x-origin.getX(),dz=z-origin.getZ();
                var local=GinsoInterior.rotate(new BlockPos(dx,0,dz),-entrance);
                int lx=local.getX(),lz=local.getZ();
                boolean entry=hasCourse()&&Math.abs(lx)<=3&&lz>=-22&&lz<=-5;
                int entryFloor=origin.getY()+GinsoInterior.approachFloor(lz,approach);
                int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
                double r=Math.hypot(dx,dz),blend=Math.max(0,1-r/16);
                int top=Math.max(ground,(int)Math.floor(ground+(origin.getY()-1-ground)*blend*blend));
                if(r<7)top=Math.max(top,origin.getY()-1);
                if(dz>=-21&&dz<=-4&&Math.abs(dx)<5){double a=Math.clamp((dz+21)/9.0,0,1)*(5-Math.abs(dx))/5.0;top=Math.max(top,(int)Math.floor(ground+(origin.getY()-1-ground)*a));}
                for(int y=Math.max(ground+1,boundingBox.minY());y<=top;y++){
                    p.set(x,y,z);if(!clip.isInside(p))continue;level.setBlock(p,y==top?Blocks.MUD.defaultBlockState():Blocks.DIRT.defaultBlockState(),Block.UPDATE_CLIENTS);writes++;
                }
                if(entry){
                    // Narrow graded path. Clear only its headroom, not the surrounding terrain/roots.
                    for(int y=Math.max(boundingBox.minY(),Math.min(ground,entryFloor));y<=Math.max(entryFloor+6,ground+3);y++){
                        p.set(x,y,z);if(!clip.isInside(p)||level.isOutsideBuildHeight(p))continue;
                        level.setBlock(p,y<=entryFloor?(y==entryFloor?Blocks.MOSS_BLOCK.defaultBlockState():Blocks.DIRT.defaultBlockState()):Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);writes++;
                    }
                }
                for(int dy=GinsoShape.MIN_Y;dy<=GinsoShape.MAX_Y;dy++){
                    byte c=shape.cell(dx,dy,dz);
                    byte inside=hasCourse()?GinsoInterior.layout().cell(lx,dy,lz):0;
                    if(inside!=0)c=inside;
                    // Seal the old north-facing wall aperture if this piece faces a different way.
                    else if(hasCourse()&&entrance!=0&&Math.abs(dx)<=2&&dz>=-9&&dz<=-6&&dy>=1&&dy<=5)c=GinsoShape.WOOD;
                    if(entry&&dy<=entryFloor-origin.getY()+6){
                        if(dy==entryFloor-origin.getY())c=GinsoShape.WOOD;
                        else if(dy>entryFloor-origin.getY())c=GinsoShape.AIR;
                        else continue;
                    }
                    if(c==GinsoShape.EMPTY)continue;
                    p.set(x,origin.getY()+dy,z);if(!clip.isInside(p)||level.isOutsideBuildHeight(p))continue;
                    // Leave the gaps underneath arched roots open. Only low terminal feet descend to real ground.
                    if(c==GinsoShape.WOOD&&dy<=0&&r>12&&shape.cell(dx,dy-1,dz)==GinsoShape.EMPTY){
                        for(int y=Math.max(ground+1,boundingBox.minY());y<p.getY();y++){
                            var foot=new BlockPos(x,y,z);if(clip.isInside(foot)){level.setBlock(foot,wood,Block.UPDATE_CLIENTS);writes++;}
                        }
                    }
                    var state=switch(c){
                        case GinsoShape.WOOD->wood.setValue(GinsoWoodBlock.MOSS,GinsoShape.mossy(dx,dy,dz));
                        case GinsoShape.SAP->wood.setValue(GinsoWoodBlock.VEIN,true).setValue(GinsoWoodBlock.MOSS,GinsoShape.mossy(dx,dy,dz));
                        case GinsoInterior.LIGHT->OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(GreatSpiritRootBlock.LIGHT,GreatSpiritRootBlock.Light.KNOT);
                        case GinsoInterior.LAMP->OriBlocks.BASH_ANCHOR.get().defaultBlockState();
                        case GinsoInterior.FLOWER->OriBlocks.GRAPPLE_FLOWER.get().defaultBlockState();
                        case GinsoInterior.WIND->OriBlocks.UPDRAFT.get().defaultBlockState();
                        case GinsoInterior.WELL->OriBlocks.GLADES_WELL.get().defaultBlockState().setValue(SpiritWellBlock.FACING,net.minecraft.core.Direction.from2DDataValue((2+entrance)%4));
                        case GinsoInterior.STAIR, GinsoInterior.STAIR_SOUTH->Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,net.minecraft.core.Direction.from2DDataValue(((c==GinsoInterior.STAIR?3:0)+entrance)%4));
                        case GinsoShape.FUNGUS->OriBlocks.GINSO_FUNGUS.get().defaultBlockState();
                        case GinsoShape.LEAF->leaf;
                        case GinsoShape.VINE->vine.setValue(SpiritVineBlock.TIP,shape.cell(dx,dy-1,dz)!=GinsoShape.VINE);
                        default->Blocks.AIR.defaultBlockState();
                    };
                    level.setBlock(p,state,Block.UPDATE_CLIENTS);writes++;
                }
            }
        OriMod.LOGGER.info("Ginso Tree origin {} chunk {},{}: {} writes; shape {} ms; writes {} ms",origin.toShortString(),chunk.x,chunk.z,writes,(shaped-started)/1e6,(System.nanoTime()-shaped)/1e6);
    }
}
