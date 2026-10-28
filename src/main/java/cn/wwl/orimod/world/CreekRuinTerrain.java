package cn.wwl.orimod.world;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** New creek only: each authored bank meets the natural ground at its own height. */
final class CreekRuinTerrain {
    static final int RADIUS=CreekTerrainRules.RADIUS, REACH=CreekTerrainRules.REACH, DEPTH=24, MAX_CUT=6;
    record Stats(int columns,int boundaryColumns,int guardedColumns,int rearColumns,
                 int rearBoundaryColumns,int rearGuardedColumns,int writtenBlocks,int naturalSamples,
                 long planNanos,long foundationNanos,long bankNanos,
                 int filledColumns,int cutColumns,int deepExceptions,int treeExceptions,int unsupportedColumns,
                 int caveExceptions,int buildingExceptions,int maximumFill) {}
    private record Surface(int y,BlockState state,boolean log,boolean building,double standingTop) {}
    private record Observation(CreekTerrainRules.Column column,Surface surface,boolean cave) {
        boolean building(){return surface!=null&&surface.building();}
        boolean untouched(){return cave||building();}
    }
    private record Contract(int sizeX,int sizeY,int sizeZ,List<CreekTerrainRules.Ground> columns,
                            int entranceX,int entranceHalfWidth,int entranceFloor,int rearFromZ,
                            int minGround,int maxGround,CreekTerrainRules rules) {}
    private static final class SavedContract { private static final Contract VALUE=readContract(); }

    /** Resolve the packaged contract before player-driven chunk reads can construct a piece. */
    static void prepare(){java.util.Objects.requireNonNull(SavedContract.VALUE);}

    private final Contract contract;
    private final BlockPos origin;
    private final Rotation rotation;
    private final int[] approach;
    // Immutable dense noise snapshot. New starts compute it in STRUCTURE_STARTS;
    // the fallback for a pre-release v1 NBT is the SAME pure noise API, never
    // current WorldGenRegion blocks (which may not even exist outside 3x3).
    private volatile int[] naturalHeights=new int[0];
    private final CreekTerrainGuards guards=new CreekTerrainGuards();
    void restoreGuards(long[] saved){guards.restore(saved);}
    long[] savedGuards(){return guards.save();}
    boolean ownGuard(BlockPos pos,BlockState state){return state.is(Blocks.MOSSY_COBBLESTONE_WALL)&&guards.contains(pos.asLong());}
    boolean ownSupport(BlockPos pos,BlockState state){return state.is(Blocks.MOSSY_COBBLESTONE)&&guards.contains(pos.above().asLong());}
    private boolean ownAssembly(BlockPos pos,BlockState state){return ownGuard(pos,state)||ownSupport(pos,state);}
    private boolean gradeable(BlockState state,WorldGenLevel level,BlockPos pos){
        return !ownAssembly(pos,state)&&CreekTerrainSurface.mutable(state,level,pos)
                &&(CreekTerrainSurface.replaceable(state,level,pos)||CreekTerrainSurface.natural(state)&&terrain(state,level,pos));
    }
    private int writeGuard(WorldGenLevel level,BlockPos pos){
        int written=0;
        guards.plan(pos.asLong());
        var base=pos.below();var state=level.getBlockState(base);
        // Coordinate-owned mossy cobble is neither flower soil nor moss-
        // replaceable. Foreign cobble is still a protected building.
        if(!state.is(Blocks.MOSSY_COBBLESTONE)&&gradeable(state,level,base)){
            level.setBlock(base,Blocks.MOSSY_COBBLESTONE.defaultBlockState(),Block.UPDATE_CLIENTS);written++;
        }
        if(level.getBlockState(pos)!=guard()){level.setBlock(pos,guard(),Block.UPDATE_CLIENTS);written++;}
        return written;
    }
    private static final int PAD=RADIUS+REACH;
    void restoreSamples(int[] values){
        if(values.length!=0&&values.length!=(contract.sizeX+2*PAD)*(contract.sizeZ+2*PAD))
            throw new IllegalArgumentException("Invalid creek natural-height snapshot length "+values.length);
        naturalHeights=values.clone();
    }
    int[] savedSamples(){return naturalHeights.clone();}
    static int[] sample(net.minecraft.world.level.levelgen.structure.Structure.GenerationContext context,RuinPlacement placement,
                        java.util.Map<BlockPos,Integer> preparedSurfaces){
        return sample(SavedContract.VALUE,placement,(x,z)->context.chunkGenerator().getBaseHeight(x,z,
                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,context.heightAccessor(),context.randomState()),"STRUCTURE_STARTS",preparedSurfaces);
    }
    private static int[] sample(Contract c,RuinPlacement p,java.util.function.IntBinaryOperator height,String cause,java.util.Map<BlockPos,Integer> preparedSurfaces){
        var event=CreekSamplingEvent.begin(cause);
        if(event!=null){event.originX=p.origin().getX();event.originZ=p.origin().getZ();}
        var cpu=event==null?null:java.lang.management.ManagementFactory.getThreadMXBean();
        long before=cpu==null?0:cpu.getCurrentThreadCpuTime();
        int width=c.sizeX+2*PAD;var result=new int[width*(c.sizeZ+2*PAD)];
        int[] calls={0},reused={0};
        var grid=new CreekHeightGrid((x,z)->{var at=p.block(new BlockPos(x,0,z));
            Integer known=preparedSurfaces.get(at.atY(0));
            if(known!=null){reused[0]++;return known-1-p.origin().getY();}
            calls[0]++;
            return height.applyAsInt(at.getX(),at.getZ())-1-p.origin().getY();});
        for(int z=-PAD;z<c.sizeZ+PAD;z++)for(int x=-PAD;x<c.sizeX+PAD;x++){
            int authored=c.rules.ground(x,z);
            result[(z+PAD)*width+x+PAD]=authored!=CreekTerrainRules.NONE?authored:
                    c.rules.needsNatural(x,z)?grid.at(x,z):CreekTerrainRules.NONE;
        }
        if(event!=null){event.end();event.calls=calls[0];event.reused=reused[0];event.gridCorners=grid.samples();event.cpuNanos=cpu.getCurrentThreadCpuTime()-before;event.commit();}
        return result;
    }
    void ensureSamples(WorldGenLevel level,net.minecraft.world.level.chunk.ChunkGenerator generator){
        if(naturalHeights.length!=0)return;
        synchronized(this){
            if(naturalHeights.length==0)naturalHeights=sample(contract,new RuinPlacement(origin,rotation),
                    (x,z)->generator.getBaseHeight(x,z,net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG,
                            level,level.getLevel().getChunkSource().randomState()),"MISSING_V1_SNAPSHOT",java.util.Map.of());
        }
    }
    private int natural(int x,int z){return naturalHeights[(z+PAD)*(contract.sizeX+2*PAD)+x+PAD];}
    CreekRuinTerrain(StructureTemplate template,BlockPos origin,StructurePlaceSettings settings,int[] approachTerrain){
        contract=SavedContract.VALUE;this.origin=origin.immutable();rotation=settings.getRotation();
        if(template.getSize().getX()!=contract.sizeX||template.getSize().getY()!=contract.sizeY||template.getSize().getZ()!=contract.sizeZ)
            throw new IllegalArgumentException("Creek terrain/template dimensions differ");
        if(approachTerrain.length!=0&&approachTerrain.length!=RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH)
            throw new IllegalArgumentException("Creek approach needs 45 samples");
        approach=approachTerrain.clone();
    }
    static BoundingBox bounds(BoundingBox b){return new BoundingBox(b.minX()-RADIUS,b.minY()-DEPTH,b.minZ()-RADIUS,b.maxX()+RADIUS,b.maxY(),b.maxZ()+RADIUS);}

    private int[] localBounds(BoundingBox clip){
        int minX=Integer.MAX_VALUE,minZ=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,maxZ=Integer.MIN_VALUE;
        for(int x:new int[]{clip.minX(),clip.maxX()})for(int z:new int[]{clip.minZ(),clip.maxZ()}){
            var p=local(x,z);minX=Math.min(minX,p.getX());maxX=Math.max(maxX,p.getX());minZ=Math.min(minZ,p.getZ());maxZ=Math.max(maxZ,p.getZ());
        }
        return new int[]{minX,minZ,maxX,maxZ};
    }
    static boolean rimColumn(int x,int z){return SavedContract.VALUE.rules.rim(x,z);}

    Stats place(WorldGenLevel level,BoundingBox clip){
        long profileStart=Boolean.getBoolean("orimod.profileRuins")?System.nanoTime():0;
        var bounds=localBounds(clip);int minX=bounds[0],minZ=bounds[1],maxX=bounds[2],maxZ=bounds[3];
        var cursor=new BlockPos.MutableBlockPos();int[] samples={0};
        var plan=contract.rules.plan(minX-1,minZ-1,maxX+1,maxZ+1,(x,z)->{samples[0]++;return natural(x,z);},this::approachFloor);
        int ow=maxX-minX+3;
        var observed=new Observation[ow*(maxZ-minZ+3)];
        for(var c:plan){
            var top=world(c.x(),c.top(),c.z());
            int threshold=origin.getY()+CreekTerrainExceptions.caveThreshold(c.natural(),c.top());
            boolean owned=c.x()>=minX&&c.x()<=maxX&&c.z()>=minZ&&c.z()<=maxZ;
            var surface=surface(level,top,threshold,owned);
            boolean cave=surface==null||CreekTerrainExceptions.cave(c.natural(),c.top(),surface.y()-origin.getY());
            observed[(c.z()-minZ+1)*ow+c.x()-minX+1]=new Observation(c,surface,cave);
        }
        long planDone=profileStart==0?0:System.nanoTime();
        int boundary=0,guards=0,rear=0,rearBoundary=0,rearGuards=0,written=supportFoundations(level,clip,minX,minZ,maxX,maxZ);
        long foundationDone=profileStart==0?0:System.nanoTime();
        int filled=0,cut=0,deepExceptions=0,treeExceptions=0,unsupported=0,caves=0,buildings=0,maximumFill=0,columns=0;
        for(var c:plan){
            if(c.x()<minX||c.x()>maxX||c.z()<minZ||c.z()>maxZ)continue;
            columns++;
            var observation=observed[(c.z()-minZ+1)*ow+c.x()-minX+1];
            boolean edge=contract.rules.outerBoundary(c.x(),c.z());
            if(edge&&!c.entrance())c=protectActualSeam(level,c);
            if(edge)boundary++;
            if(c.rear()){rear++;if(edge)rearBoundary++;}
            // The saved approach piece owns its five-wide route and clearance.
            // Its floor contributes to the grade without a soil lip across it.
            if(c.entrance())continue;
            var top=world(c.x(),c.top(),c.z());
            // The interpolation plans the grade, never decides whether real soil
            // already reaches it. Only this slice owns and reads this column.
            // No live neighbour height is fed back into the immutable plan.
            var surface=observation.surface();
            if(observation.building()){buildings++;continue;}
            if(observation.cave()){caves++;if(surface==null)unsupported++;continue;}
            if(surface==null||surface.y()<top.getY()-DEPTH){unsupported++;continue;}
            if(surface.building()){buildings++;continue;}
            boolean tooDeep=surface.y()>top.getY()+MAX_CUT;
            boolean tree=surface.log()&&surface.y()>top.getY();
            if(tooDeep||tree){
                if(tooDeep)deepExceptions++;
                if(tree)treeExceptions++;
                // Preserve the tree's soil and deep natural outcrops. A full
                // trunk already blocks entry; a bare cap gets a real root lip.
                var lip=top.atY(surface.y()+1);
                if(!level.getBlockState(lip).is(Blocks.MOSSY_COBBLESTONE_WALL)
                        &&!guardSpace(level,lip)&&guardSpace(level,lip.above()))lip=lip.above();
                if(clip.isInside(lip)&&(guardSpace(level,lip)||ownGuard(lip,level.getBlockState(lip)))){
                    written+=writeGuard(level,lip);guards++;if(c.rear())rearGuards++;
                }
                continue;
            }
            if(surface.y()>top.getY()){
                cut++;
                for(int y=surface.y();y>top.getY();y--){
                    cursor.set(top.getX(),y,top.getZ());var state=level.getBlockState(cursor);
                    if(clip.isInside(cursor)&&gradeable(state,level,cursor)){level.setBlock(cursor,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);written++;}
                }
                var oldPlant=top.atY(surface.y()+1);
                if(clip.isInside(oldPlant)&&CreekTerrainSurface.mutable(level.getBlockState(oldPlant),level,oldPlant)&&replaceable(level,oldPlant)&&!level.isEmptyBlock(oldPlant)){
                    level.setBlock(oldPlant,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);written++;
                }
            }else if(surface.y()<top.getY()){filled++;maximumFill=Math.max(maximumFill,top.getY()-surface.y());}
            int bottom=surface.y()<top.getY()?surface.y()+1:top.getY();
            if(c.retaining())bottom=Math.min(bottom,origin.getY()+c.retainingBottom());
            bottom=Math.max(bottom,Math.max(level.getMinBuildHeight(),top.getY()-DEPTH));
            for(int y=top.getY();y>=bottom;y--){
                cursor.set(top.getX(),y,top.getZ());var state=level.getBlockState(cursor);
                boolean rock=c.retaining()&&y>=origin.getY()+c.retainingBottom();
                // Authored guard assemblies are never soil: use the same ownership
                // contract in classification, cutting AND filling, including NBT reload.
                if(ownAssembly(cursor,state))continue;
                if(!gradeable(state,level,cursor))break;
                if(!clip.isInside(cursor))continue;
                BlockState fill;
                if(rock){
                    // An exposed high rear bank is a weathered rock face, not
                    // a uniform brick curtain around the entire structure.
                    int variation=Math.floorMod(top.getX()*13+top.getZ()*7+y,9);
                    fill=(y==top.getY()?Blocks.STONE:variation<5?Blocks.ANDESITE:Blocks.STONE).defaultBlockState();
                }else if(y==top.getY()&&c.rear()&&rearTrail(c.x(),c.z()))
                    fill=Blocks.GRAVEL.defaultBlockState();
                else if(y==top.getY()&&c.rear()&&rockPatch(c.x(),c.z()))
                    fill=(Math.sin(c.x()*.25+c.z()*.31)>.45?Blocks.MOSS_BLOCK:
                            Math.cos(c.x()*.21-c.z()*.43)>.2?Blocks.COARSE_DIRT:Blocks.ANDESITE).defaultBlockState();
                else fill=y==top.getY()?surfaceMaterial(surface.state()):Blocks.DIRT.defaultBlockState();
                if(state!=fill){level.setBlock(cursor,fill,Block.UPDATE_CLIENTS);written++;}
            }
            if(c.retaining()){
                var lip=top.above();
                if(clip.isInside(lip)&&(guardSpace(level,lip)||ownGuard(lip,level.getBlockState(lip)))){
                    if(clip.isInside(lip.above())&&level.getBlockState(lip.above()).is(BlockTags.FLOWERS)){
                        level.setBlock(lip.above(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);written++;
                    }
                    // Real 1.5-block collision only at the remaining cliff;
                    // normal banks and the entrance have no enclosing rail.
                    written+=writeGuard(level,lip);
                }
                if(clip.isInside(lip)&&level.getBlockState(lip).is(Blocks.MOSSY_COBBLESTONE_WALL)){
                    guards++;if(c.rear())rearGuards++;
                }
            }
            // Exception columns themselves are never touched. Their classification
            // is invariant before/after neighbouring slices: ordinary grades remain
            // ordinary and cave/building columns remain exactly as encountered.
            // Read only that invariant classification, never a neighbour's new top.
            int guardTop=exceptionGuardTop(level,c.x(),c.z(),top.getY(),observed,minX,minZ,ow);
            if(guardTop!=Integer.MIN_VALUE){
                written+=edgeGuard(level,clip,top,guardTop);
                if(!c.retaining()){guards++;if(c.rear())rearGuards++;}
            }
            var moss=top.above();
            if(Math.floorMod(top.getX()+top.getZ(),3)==0&&clip.isInside(moss)&&level.isEmptyBlock(moss)){
                level.setBlock(moss,Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN),true),Block.UPDATE_CLIENTS);written++;
            }else if(c.rear()&&!c.retaining()&&Math.floorMod(c.x()*19+c.z()*31,7)==0
                    &&clip.isInside(moss)&&level.isEmptyBlock(moss)&&level.getBlockState(top).is(Blocks.GRASS_BLOCK)){
                level.setBlock(moss,Blocks.FERN.defaultBlockState(),Block.UPDATE_CLIENTS);written++;
            }
        }
        // A cave may touch the immutable template boundary itself. Protect its
        // existing edge rather than span/seal the opening with an unsupported lip.
        for(var c:contract.columns){
            if(c.x()<minX||c.x()>maxX||c.z()<minZ||c.z()>maxZ)continue;
            var top=world(c.x(),c.y(),c.z());
            int guardTop=exceptionGuardTop(level,c.x(),c.z(),top.getY(),observed,minX,minZ,ow);
            if(guardTop!=Integer.MIN_VALUE)written+=edgeGuard(level,clip,top,guardTop);
        }
        // Do not write logs inside the measured call: synchronous appenders can
        // stall a worker, making an empty slice look like expensive terrain work.
        long end=profileStart==0?0:System.nanoTime();
        return new Stats(columns,boundary,guards,rear,rearBoundary,rearGuards,written,samples[0],
                planDone-profileStart,foundationDone-planDone,end-foundationDone,filled,cut,deepExceptions,treeExceptions,unsupported,caves,buildings,maximumFill);
    }

    private int exceptionGuardTop(WorldGenLevel level,int x,int z,int y,Observation[] observed,int minX,int minZ,int width){
        int result=Integer.MIN_VALUE;
        var at=world(x,y-origin.getY(),z);
        double ourTop=CreekTerrainSurface.standingTop(level.getBlockState(at),level,at);
        if(!Double.isFinite(ourTop))return result;
        for(var d:Direction.Plane.HORIZONTAL){
            int nx=x+d.getStepX()-minX+1,nz=z+d.getStepZ()-minZ+1;
            if(nx<0||nx>=width||nz<0||nz*width>=observed.length)continue;
            var other=observed[nz*width+nx];if(other==null||!other.untouched()||other.column.entrance())continue;
            double otherTop=other.surface==null?level.getMinBuildHeight():other.surface.standingTop;
            if(CreekTerrainSurface.dropHalfUnits(Math.max(ourTop,otherTop),Math.min(ourTop,otherTop))>2)
                result=Math.max(result,(int)Math.ceil(Math.max(ourTop,otherTop)));
        }
        return result;
    }
    private int edgeGuard(WorldGenLevel level,BoundingBox clip,BlockPos top,int guardTop){
        if(!level.getBlockState(top).isFaceSturdy(level,top,Direction.UP))return 0;
        int writes=0;
        for(int y=top.getY()+1;y<=guardTop;y++){
            var p=top.atY(y);var state=level.getBlockState(p);
            if(!clip.isInside(p))continue;
            if(ownGuard(p,state)){writes+=writeGuard(level,p);continue;}
            if(state.is(Blocks.MOSSY_COBBLESTONE_WALL))continue;
            if(!guardSpace(level,p))break;
            writes+=writeGuard(level,p);
        }
        return writes;
    }

    private static BlockState guard(){return Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.UP,true)
            .setValue(WallBlock.NORTH_WALL,WallSide.LOW).setValue(WallBlock.SOUTH_WALL,WallSide.LOW)
            .setValue(WallBlock.EAST_WALL,WallSide.LOW).setValue(WallBlock.WEST_WALL,WallSide.LOW);}
    private static BlockState surfaceMaterial(BlockState original){
        if(original.is(BlockTags.SAND)||original.is(Blocks.PODZOL)||original.is(Blocks.MYCELIUM)||original.is(Blocks.MUD)||original.is(Blocks.MOSS_BLOCK))return original;
        return Blocks.GRASS_BLOCK.defaultBlockState();
    }
    private Surface surface(WorldGenLevel level,BlockPos top,int threshold,boolean owned){
        // Only owned columns may use their live heightmap. A one-column halo is
        // inspected through block states, and contributes ONLY invariant exception
        // classification (plus the unchanged height of a preserved building).
        // No observation cache or FEATURES-order assumption is required.
        int first=owned?Math.max(top.getY(),level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,top.getX(),top.getZ())-1):level.getMaxBuildHeight()-1;
        int min=Math.max(level.getMinBuildHeight(),Math.min(top.getY()-DEPTH,threshold));boolean log=false,building=false;
        Surface highest=null;
        var at=new BlockPos.MutableBlockPos(top.getX(),first,top.getZ());
        for(int y=first;y>=min;y--){at.setY(y);var state=level.getBlockState(at);
            if(CreekTerrainSurface.category(state,level,at)==CreekTerrainSurface.Category.IGNORE_PROTECTED)log=true;
            boolean own=ownGuard(at,state);
            boolean foreign=CreekTerrainSurface.building(state,level,at,own||ownSupport(at,state));
            building|=foreign;
            if(highest==null&&!own&&CreekTerrainSurface.standingCandidate(state,level,at))
                highest=new Surface(y,state,log,false,CreekTerrainSurface.standingTop(state,level,at));
            // Continue through the complete possible cut/fill interval. A natural
            // cap over masonry must not hide a foreign building until we cut it.
        }
        return highest==null?null:new Surface(highest.y(),highest.state(),log,building,highest.standingTop());
    }

    private CreekTerrainRules.Column protectActualSeam(WorldGenLevel level,CreekTerrainRules.Column c){
        int bottom=CreekTerrainRules.NONE;
        for(var direction:Direction.Plane.HORIZONTAL){
            int x=c.x()+direction.getStepX(),z=c.z()+direction.getStepZ();
            if(!contract.rules.readOnlySeam(x,z))continue;
            var at=world(x,0,z);
            // At most one column outside this write slice, within WorldGenRegion.
            // Ignore leaves, trunks, vegetation, snow layers and fluids. Never
            // feed a mutable height back into the planned inner 1..8 columns.
            int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,at.getX(),at.getZ())-1;
            var cursor=new BlockPos.MutableBlockPos(at.getX(),y,at.getZ());
            while(y>level.getMinBuildHeight()&&!CreekTerrainSurface.standingCandidate(level.getBlockState(cursor),level,cursor)){cursor.setY(--y);}
            double actual=CreekTerrainSurface.standingTop(level.getBlockState(cursor),level,cursor)-origin.getY();
            if(CreekTerrainSurface.dropHalfUnits(c.top()+1,actual)>2){
                int support=(int)Math.floor(actual);
                bottom=bottom==CreekTerrainRules.NONE?support:Math.min(bottom,support);
            }
        }
        return bottom==c.retainingBottom()?c:new CreekTerrainRules.Column(c.x(),c.z(),c.natural(),c.top(),c.distance(),bottom,c.entrance(),c.rear());
    }

    // A winding surface-only trail; no height overrides or new landing cliffs.
    static boolean rearTrail(int x,int z){
        int centre=35+(int)Math.round(2.5*Math.sin((z-54)*.27));
        int half=(z>=60&&z<=62)||(z>=68&&z<=69)?2:1;
        return z>=56&&Math.abs(x-centre)<=half;
    }
    static boolean rockPatch(int x,int z){return Math.sin(x*.42+z*.21)+.55*Math.cos(x*.19-z*.37)>.65;}

    private int approachFloor(int x,int z){
        if(approach.length==0||z>=0||z < -RuinApproachPiece.DEPTH||Math.abs(x-contract.entranceX)>contract.entranceHalfWidth)return CreekTerrainRules.NONE;
        int start=approach[(x-contract.entranceX+contract.entranceHalfWidth)*RuinApproachPiece.DEPTH];
        if(z == -RuinApproachPiece.DEPTH)return start-1-origin.getY();
        int step=z+8,door=origin.getY()+contract.entranceFloor+1;
        int floor=RuinSiteRules.stepFloor(start,door,step);
        int previous=step==0?start-1:RuinSiteRules.stepFloor(start,door,step-1);
        int next=step==7?door-1:RuinSiteRules.stepFloor(start,door,step+1);
        // Anchor the shoulder to the same collision TOP used by the edge scan.
        // Using the support below a bottom stair hid a two-block drop from its
        // high half. Shape orientation changes the step direction, not its max Y.
        var state=(floor>previous||floor>next?Blocks.MOSSY_STONE_BRICK_STAIRS:Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
        double top=CreekTerrainSurface.standingTop(state,net.minecraft.world.level.EmptyBlockGetter.INSTANCE,new BlockPos(x,floor,z));
        return (int)Math.floor(top)-1-origin.getY();
    }
    private int supportFoundations(WorldGenLevel level,BoundingBox clip,int minX,int minZ,int maxX,int maxZ){
        int written=0;
        for(var c:contract.columns){
            if(c.x()<minX||c.x()>maxX||c.z()<minZ||c.z()>maxZ)continue;
            var base=world(c.x(),0,c.z());if(!clip.isInside(base))continue;
            int depth=1;
            while(depth<=DEPTH&&base.getY()-depth>=level.getMinBuildHeight()&&replaceable(level,base.below(depth)))depth++;
            if(depth<=1||depth>DEPTH||!level.getBlockState(base.below(depth)).isFaceSturdy(level,base.below(depth),Direction.UP))continue;
            for(int d=1;d<depth;d++){
                var at=base.below(d);if(clip.isInside(at)){level.setBlock(at,Blocks.DIRT.defaultBlockState(),Block.UPDATE_CLIENTS);written++;}
            }
        }
        return written;
    }
    private BlockPos world(int x,int y,int z){return new BlockPos(origin.getX()+rotatedX(x,z),origin.getY()+y,origin.getZ()+rotatedZ(x,z));}
    private int rotatedX(int x,int z){return switch(rotation){case NONE->x;case CLOCKWISE_90->-z;case CLOCKWISE_180->-x;case COUNTERCLOCKWISE_90->z;};}
    private int rotatedZ(int x,int z){return switch(rotation){case NONE->z;case CLOCKWISE_90->x;case CLOCKWISE_180->-z;case COUNTERCLOCKWISE_90->-x;};}
    private BlockPos local(int worldX,int worldZ){
        int x=worldX-origin.getX(),z=worldZ-origin.getZ();
        return switch(rotation){case NONE->new BlockPos(x,0,z);case CLOCKWISE_90->new BlockPos(z,0,-x);
            case CLOCKWISE_180->new BlockPos(-x,0,-z);case COUNTERCLOCKWISE_90->new BlockPos(-z,0,x);};
    }
    private static boolean terrain(BlockState s,WorldGenLevel level,BlockPos pos){return CreekTerrainSurface.ground(s,level,pos);}
    /** A lip needs free walking space; a solid ignored trunk already blocks the
     * edge. Fill classification must not authorize replacing that live barrier. */
    private static boolean guardSpace(WorldGenLevel level,BlockPos pos){
        return guardSpace(level.getBlockState(pos),level,pos);
    }
    private static boolean guardSpace(BlockState state,WorldGenLevel level,BlockPos pos){
        return CreekTerrainSurface.replaceable(state,level,pos)&&state.getCollisionShape(level,pos).isEmpty();
    }
    private static boolean replaceable(WorldGenLevel level,BlockPos pos){return CreekTerrainSurface.replaceable(level.getBlockState(pos),level,pos);}

    private static Contract readContract(){
        String path="/orimod/ruin_layout/creek_ruins_terrain_v1.json";
        try(var stream=CreekRuinTerrain.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing creek terrain "+path);
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var size=root.getAsJsonArray("size");int sx=size.get(0).getAsInt(),sy=size.get(1).getAsInt(),sz=size.get(2).getAsInt();
            var columns=new ArrayList<CreekTerrainRules.Ground>();int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(var value:root.getAsJsonArray("columns")){
                var a=value.getAsJsonArray();int y=a.get(2).getAsInt();
                if(y<0||y>=sy)throw new IllegalArgumentException("Creek bank outside template height: "+a);
                columns.add(new CreekTerrainRules.Ground(a.get(0).getAsInt(),a.get(1).getAsInt(),y));min=Math.min(min,y);max=Math.max(max,y);
            }
            var entry=root.getAsJsonObject("entrance");int x=entry.get("x").getAsInt(),half=entry.get("half_width").getAsInt(),floor=entry.get("floor_y").getAsInt(),rear=root.get("rear_from_z").getAsInt();
            if(half!=2)throw new IllegalArgumentException("Creek saved approach must remain five columns wide");
            var immutable=List.copyOf(columns);var rules=new CreekTerrainRules(sx,sz,immutable,x,half,rear);
            return new Contract(sx,sy,sz,immutable,x,half,floor,rear,min,max,rules);
        }catch(Exception e){throw new IllegalStateException("Invalid creek terrain "+path,e);}
    }
}
