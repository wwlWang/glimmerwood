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
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import java.util.*;

/** Same seed in every chunk; immutable sculpture cached with a bounded lifetime/size. */
public final class GinsoPiece extends StructurePiece {
    private final BlockPos origin;private final long seed;
    private final int entrance, approach, courseVersion, shapeVersion, escapeVersion;
    private record ShapeKey(long seed,int version){}
    private static final Map<ShapeKey,GinsoShape> CACHE=new LinkedHashMap<>();
    public GinsoPiece(BlockPos at,long seed){this(at,seed,0,0);}
    public GinsoPiece(BlockPos at,long seed,int entrance,int approach){this(at,seed,entrance,approach,GinsoShape.VERSION,GinsoCourses.VERSION);}
    public GinsoPiece(BlockPos at,long seed,int entrance,int approach,int shapeVersion,int courseVersion){
        this(at,seed,entrance,approach,shapeVersion,courseVersion,shapeVersion==3?(courseVersion==3?2:courseVersion==2?1:0):0);
    }
    public GinsoPiece(BlockPos at,long seed,int entrance,int approach,int shapeVersion,int courseVersion,int escapeVersion){
        super(OriWorldgen.GINSO_PIECE.get(),0,bounds(at,shapeVersion));origin=at.immutable();this.seed=seed;
        this.entrance=Math.floorMod(entrance,4);this.approach=Math.clamp(approach,-6,10);
        this.shapeVersion=GinsoShape.checkedVersion(shapeVersion);this.courseVersion=GinsoCourses.checked(shapeVersion,courseVersion);
        this.escapeVersion=GinsoEscapeLayout.checked(shapeVersion,courseVersion,escapeVersion);
    }
    public GinsoPiece(CompoundTag tag){this(OriWorldgen.GINSO_PIECE.get(),tag);}
    // Same deserialization path in the fixed-baseline JUnit; no runtime registry creation in a fixture.
    GinsoPiece(StructurePieceType type,CompoundTag tag){
        super(type,tag);origin=BlockPos.of(tag.getLong("Origin"));seed=tag.getLong("ShapeSeed");
        entrance=Math.floorMod(tag.getInt("Entrance"),4);approach=Math.clamp(tag.getInt("Approach"),-6,10);
        shapeVersion=GinsoShape.checkedVersion(tag.contains("ShapeVersion")?tag.getInt("ShapeVersion"):2);
        courseVersion=GinsoCourses.checked(shapeVersion,tag.getInt("CourseVersion"));
        // Missing is deliberately zero, even for an already generated Shape3/Course2 tree.
        escapeVersion=GinsoEscapeLayout.checked(shapeVersion,courseVersion,tag.getInt("EscapeVersion"));
    }
    static BoundingBox bounds(BlockPos p,int version){int r=GinsoShape.radius(version);return new BoundingBox(p.getX()-r,p.getY()+GinsoShape.minY(version),p.getZ()-r,p.getX()+r,p.getY()+GinsoShape.maxY(version),p.getZ()+r);}
    public int shapeVersion(){return shapeVersion;}
    public int courseVersion(){return courseVersion;}
    public int escapeVersion(){return escapeVersion;}
    public GinsoEscapeLayout.Layout escape(){return escapeVersion==0?GinsoEscapeLayout.empty():escapeVersion==2?GinsoClimbEscape.forTree(seed,entrance,sculpture()).layout():GinsoEscapeLayout.forTree(seed,entrance,sculpture());}
    public BlockPos escapeHeart(){return escapeVersion==2?GinsoClimbCourse.HEART:GinsoEscapeLayout.HEART;}
    public BlockPos escapeSafe(){return escapeVersion==2?GinsoClimbCourse.SAFE:GinsoEscapeLayout.SAFE;}
    public List<GinsoCourses.Hop> ascentHops(){
        if(escapeVersion!=2)return course().hops();
        var hops=new ArrayList<>(course().hops());hops.addAll(GinsoClimbEscape.forTree(seed,entrance,sculpture()).crownHops());return List.copyOf(hops);
    }
    public GinsoShape sculpture(){return shape(seed,shapeVersion);}
    public Block leafAt(int x,int y,int z){
        return leafBlock(sculpture(),x,y,z);
    }
    private Block leafBlock(GinsoShape field,int x,int y,int z){return shapeVersion<=2?OriBlocks.GINSO_LEAVES.get():field.coreLeaf(x,y,z)?OriBlocks.GINSO_LEAF_CORE.get():OriBlocks.GINSO_CANOPY.get();}
    public BlockPos origin(){return origin;}
    public long shapeSeed(){return seed;}
    public int entrance(){return entrance;}
    public int approach(){return approach;}
    public boolean hasCourse(){return courseVersion>0;}
    public GinsoCourses.Layout course(){return GinsoCourses.layout(courseVersion);}
    public BlockPos point(BlockPos local){return origin.offset(GinsoInterior.rotate(local,entrance));}
    public BlockPos local(BlockPos world){return GinsoInterior.rotate(world.subtract(origin),-entrance);}
    public int approachFloor(int z){return shapeVersion<=2?GinsoInterior.approachFloor(z,approach):(int)Math.round(Math.clamp((-z-8)/19.0,0,1)*approach);}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag tag){tag.putLong("Origin",origin.asLong());tag.putLong("ShapeSeed",seed);tag.putInt("ShapeVersion",shapeVersion);tag.putInt("Entrance",entrance);tag.putInt("Approach",approach);tag.putInt("CourseVersion",courseVersion);if(escapeVersion!=0)tag.putInt("EscapeVersion",escapeVersion);}
    public static GinsoShape shape(long seed,int version){synchronized(CACHE){
        var key=new ShapeKey(seed,version);var s=CACHE.get(key);if(s!=null)return s;
        if(CACHE.size()>=8)CACHE.remove(CACHE.keySet().iterator().next());
        long started=System.nanoTime();s=new GinsoShape(seed,version);CACHE.put(key,s);
        OriMod.LOGGER.info("Ginso Tree shape seed {} v{} cold build {} ms",seed,version,(System.nanoTime()-started)/1e6);return s;
    }}
    @Override public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot){
        long started=System.nanoTime();var shape=sculpture();long shapeEnd=System.nanoTime();var escape=escape();long shaped=System.nanoTime();int writes=0;
        var wood=OriBlocks.GINSO_WOOD.get().defaultBlockState();var vine=OriBlocks.GINSO_VINE.get().defaultBlockState();var course=course();
        var p=new BlockPos.MutableBlockPos();
        for(int x=Math.max(clip.minX(),boundingBox.minX());x<=Math.min(clip.maxX(),boundingBox.maxX());x++)
            for(int z=Math.max(clip.minZ(),boundingBox.minZ());z<=Math.min(clip.maxZ(),boundingBox.maxZ());z++){
                int dx=x-origin.getX(),dz=z-origin.getZ();
                var local=GinsoInterior.rotate(new BlockPos(dx,0,dz),-entrance);
                int lx=local.getX(),lz=local.getZ();
                boolean entry=hasCourse()&&Math.abs(lx)<=3&&lz>=-(shapeVersion<=2?22:27)&&lz<=-5;
                int entryFloor=origin.getY()+approachFloor(lz);
                int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
                double r=Math.hypot(dx,dz),blend=Math.max(0,1-r/(shapeVersion<=2?16:23));
                int top=Math.max(ground,(int)Math.floor(ground+(origin.getY()-1-ground)*blend*blend));
                if(r<(shapeVersion<=2?7:9))top=Math.max(top,origin.getY()-1);
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
                if(shapeVersion>=3&&r>15){
                    // Extend only terminal feet, never the high underside of an arch. Fill down
                    // to the actual carved terrain, not a noise-height estimate or a shared plane.
                    int toe=Integer.MAX_VALUE;
                    for(int dy=GinsoShape.minY(shapeVersion);dy<=1;dy++)if(GinsoShape.timber(shape.cell(dx,dy,dz))){toe=origin.getY()+dy;break;}
                    if(toe!=Integer.MAX_VALUE)for(int y=Math.max(ground+1,boundingBox.minY());y<toe;y++){
                        p.set(x,y,z);if(!clip.isInside(p)||level.isOutsideBuildHeight(p))continue;
                        level.setBlock(p,wood.setValue(GinsoWoodBlock.MOSS,true),Block.UPDATE_CLIENTS);writes++;
                    }
                }
                for(int dy=GinsoShape.minY(shapeVersion);dy<=GinsoShape.maxY(shapeVersion);dy++){
                    byte c=shape.cell(dx,dy,dz);
                    byte inside=course.cell(lx,dy,lz);
                    if(inside!=0)c=inside;
                    // Seal the old north-facing wall aperture if this piece faces a different way.
                    else if(hasCourse()&&entrance!=0&&Math.abs(dx)<=2&&dz>=-9&&dz<=-6&&dy>=1&&dy<=5)c=GinsoShape.WOOD;
                    if(entry&&dy<=entryFloor-origin.getY()+6){
                        if(dy==entryFloor-origin.getY())c=GinsoShape.WOOD;
                        else if(dy>entryFloor-origin.getY())c=GinsoShape.AIR;
                        else continue;
                    }
                    // v2 course owns its entry stair; the old grading/overlay ordering is frozen for v1.
                    if(courseVersion>=2&&inside!=0)c=inside;
                    byte escapeCell=escape.cell(lx,dy,lz);if(escapeCell!=0)c=escapeCell;
                    // Saved version selects the heart; missing versions never acquire a new start.
                    if(escapeVersion!=0&&lx==escapeHeart().getX()&&dy==escapeHeart().getY()&&lz==escapeHeart().getZ())c=21;
                    if(c==GinsoShape.EMPTY)continue;
                    p.set(x,origin.getY()+dy,z);if(!clip.isInside(p)||level.isOutsideBuildHeight(p))continue;
                    // Most of the tall shaft is already air. Avoid WorldGenRegion's write
                    // bookkeeping for those cells; still clear real terrain/vegetation.
                    if(shapeVersion>=3&&c==GinsoShape.AIR&&level.isEmptyBlock(p))continue;
                    // Leave the gaps underneath arched roots open. Only low terminal feet descend to real ground.
                    if(shapeVersion<=2&&c==GinsoShape.WOOD&&dy<=0&&r>12&&shape.cell(dx,dy-1,dz)==GinsoShape.EMPTY){
                        for(int y=Math.max(ground+1,boundingBox.minY());y<p.getY();y++){
                            var foot=new BlockPos(x,y,z);if(clip.isInside(foot)){level.setBlock(foot,wood,Block.UPDATE_CLIENTS);writes++;}
                        }
                    }
                    var state=switch(c){
                        case GinsoShape.WOOD->wood.setValue(GinsoWoodBlock.MOSS,shape.moss(dx,dy,dz));
                        case GinsoShape.SAP->wood.setValue(GinsoWoodBlock.VEIN,true).setValue(GinsoWoodBlock.MOSS,shape.moss(dx,dy,dz));
                        case GinsoInterior.LIGHT->OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(GreatSpiritRootBlock.LIGHT,GreatSpiritRootBlock.Light.KNOT);
                        case GinsoInterior.LAMP->OriBlocks.BASH_ANCHOR.get().defaultBlockState();
                        case GinsoInterior.FLOWER->OriBlocks.GRAPPLE_FLOWER.get().defaultBlockState();
                        case GinsoInterior.WIND->OriBlocks.UPDRAFT.get().defaultBlockState();
                        case GinsoInterior.WELL->OriBlocks.GLADES_WELL.get().defaultBlockState().setValue(SpiritWellBlock.FACING,net.minecraft.core.Direction.from2DDataValue((2+entrance)%4));
                        case GinsoInterior.STAIR, GinsoInterior.STAIR_SOUTH->Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING,net.minecraft.core.Direction.from2DDataValue(((c==GinsoInterior.STAIR?3:0)+entrance)%4));
                        case TallGinsoInterior.CUSHION->OriBlocks.GINSO_LANDING.get().defaultBlockState();
                        case 21->OriBlocks.GINSO_WATER_HEART.get().defaultBlockState();
                        case GinsoEscapeLayout.ROOT_LIP->OriBlocks.GINSO_ROOT_LIP.get().defaultBlockState();
                        case TallGinsoInterior.RAIL->Blocks.DARK_OAK_FENCE.defaultBlockState()
                                .setValue(FenceBlock.NORTH,railConnection(course,escape,lx,dy,lz,net.minecraft.core.Direction.NORTH,entrance))
                                .setValue(FenceBlock.SOUTH,railConnection(course,escape,lx,dy,lz,net.minecraft.core.Direction.SOUTH,entrance))
                                .setValue(FenceBlock.WEST,railConnection(course,escape,lx,dy,lz,net.minecraft.core.Direction.WEST,entrance))
                                .setValue(FenceBlock.EAST,railConnection(course,escape,lx,dy,lz,net.minecraft.core.Direction.EAST,entrance));
                        case TallGinsoInterior.WINDOW->Blocks.CYAN_STAINED_GLASS.defaultBlockState();
                        case TallGinsoInterior.STEP->Blocks.DARK_OAK_SLAB.defaultBlockState();
                        case TallGinsoInterior.DOOR,TallGinsoInterior.DOOR_TOP->Blocks.DARK_OAK_DOOR.defaultBlockState()
                                .setValue(DoorBlock.FACING,net.minecraft.core.Direction.from2DDataValue(((escapeCell==TallGinsoInterior.DOOR||escapeCell==TallGinsoInterior.DOOR_TOP?1:lz==0?(lx>0?3:1):2)+entrance)%4))
                                .setValue(DoorBlock.HALF,c==TallGinsoInterior.DOOR?net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER:net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
                        case GinsoShape.FUNGUS->OriBlocks.GINSO_FUNGUS.get().defaultBlockState();
                        case GinsoShape.LEAF->leafBlock(shape,dx,dy,dz).defaultBlockState();
                        case GinsoShape.VINE->vine.setValue(SpiritVineBlock.TIP,shape.cell(dx,dy-1,dz)!=GinsoShape.VINE);
                        default->Blocks.AIR.defaultBlockState();
                    };
                    level.setBlock(p,state,Block.UPDATE_CLIENTS);writes++;
                }
            }
        OriMod.LOGGER.info("Ginso Tree origin {} v{} chunk {},{}: {} writes; shape {} ms; escape layout {} ms; writes {} ms",origin.toShortString(),shapeVersion,chunk.x,chunk.z,writes,(shapeEnd-started)/1e6,(shaped-shapeEnd)/1e6,(System.nanoTime()-shaped)/1e6);
    }
    private static boolean railConnection(GinsoCourses.Layout course,GinsoEscapeLayout.Layout escape,int x,int y,int z,net.minecraft.core.Direction world,int turns){
        var d=GinsoInterior.rotate(new BlockPos(world.getStepX(),0,world.getStepZ()),-turns);
        byte c=course.cell(x+d.getX(),y,z+d.getZ());
        byte e=escape.cell(x+d.getX(),y,z+d.getZ());if(e!=0)c=e;
        return c==TallGinsoInterior.RAIL||c==GinsoInterior.WOOD||c==GinsoInterior.LIGHT||c==GinsoShape.SAP||c==TallGinsoInterior.CUSHION;
    }
}
