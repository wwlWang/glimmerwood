package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.*;

/** Additive grass transition, extending to eight blocks only where the natural floor needs it. */
final class ForestRuinTerrain {
    static final int RADIUS=8, DEPTH=16, REACH=12;
    private final Set<BlockPos> land;
    private final Set<BlockPos> entrance;
    private final Map<BlockPos,Integer> approachFloor;
    private final Map<BlockPos,Integer> ring;
    private final int deck;
    ForestRuinTerrain(StructureTemplate template,BlockPos origin,StructurePlaceSettings settings,int[] terrain){
        deck=origin.getY()+5;var ground=new HashSet<BlockPos>();
        // Independent settings: the per-chunk clipping box must not truncate the authored footprint.
        var whole=new StructurePlaceSettings().setRotation(settings.getRotation());
        var approach=new HashSet<BlockPos>();var floors=new HashMap<BlockPos,Integer>();int center=template.getSize().getX()/2;
        if(terrain.length!=0&&terrain.length!=RuinApproachPiece.WIDTH*RuinApproachPiece.DEPTH)throw new IllegalArgumentException("Forest approach needs 45 samples");
        for(int x=center-2;x<=center+2;x++)for(int z=-RuinApproachPiece.DEPTH;z<0;z++){
            var at=StructureTemplate.calculateRelativePosition(whole,new BlockPos(x,0,z)).offset(origin).atY(0);approach.add(at);
            if(terrain.length!=0){int approachY=terrain[(x-center+2)*RuinApproachPiece.DEPTH];
                floors.put(at,z==-9?approachY-1:RuinSiteRules.stepFloor(approachY,deck+1,z+8));}
        }
        entrance=Set.copyOf(approach);approachFloor=Map.copyOf(floors);
        // The generator includes every grounded pier and root foot in this
        // foundation. Never use an elevated architectural face as terrain height.
        for(var cell:template.filterBlocks(origin,whole,Blocks.DIRT))if(cell.pos().getY()==origin.getY())ground.add(cell.pos().atY(0));
        land=Collections.unmodifiableSet(ground);var distance=new HashMap<BlockPos,Integer>();
        for(var p:land)for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dz=-RADIUS;dz<=RADIUS;dz++){
            int d=Math.abs(dx)+Math.abs(dz);if(d==0||d>RADIUS)continue;
            var q=p.offset(dx,0,dz);if(!land.contains(q))distance.merge(q,d,Math::min);
        }
        var ordered=new TreeMap<BlockPos,Integer>();ordered.putAll(distance);ring=Collections.unmodifiableMap(ordered);
    }
    static BoundingBox bounds(BoundingBox b){return new BoundingBox(b.minX()-RADIUS,b.minY()-DEPTH,b.minZ()-RADIUS,b.maxX()+RADIUS,b.maxY(),b.maxZ()+RADIUS);}
    void place(WorldGenLevel level,BoundingBox clip){
        var heights=new HashMap<BlockPos,Integer>();var targets=new LinkedHashMap<BlockPos,Integer>();
        // Fit both the deck and the surrounding natural ground, with a maximum
        // one-block descent per column. The discrete slope envelope joins narrow
        // valleys from both banks; equal/higher natural columns stay untouched
        // except for the explicit cliff-retaining face below.
        // Extend beyond the mandatory four columns only while the ground is lower
        // than that envelope, capped at eight. Read sources before writing this slice.
        for(var e:ring.entrySet()){
            var p=e.getKey();if(!clip.isInside(p.atY(deck)))continue;
            int target=deck-e.getValue();
            for(int dx=-REACH;dx<=REACH;dx++)for(int dz=-REACH;dz<=REACH;dz++){
                int d=Math.abs(dx)+Math.abs(dz);if(d>REACH)continue;
                var q=p.offset(dx,0,dz);int h=heights.computeIfAbsent(q,key->land.contains(key)?deck:height(level,key));
                target=Math.max(target,Math.max(h,approachFloor.getOrDefault(q,h))-d);
            }
            targets.put(p,target);
        }
        var retaining=new HashMap<BlockPos,Integer>();
        for(var e:targets.entrySet()){
            var p=e.getKey();int top=e.getValue();
            // The saved approach piece owns the walk-in corridor. Never put a
            // retaining lip across it, even if its stairs have not been placed yet.
            if(ring.get(p)!=RADIUS||entrance.contains(p))continue;
            for(var d:Direction.Plane.HORIZONTAL){
                var q=p.relative(d);if(land.contains(q)||ring.containsKey(q))continue;
                int outside=heights.computeIfAbsent(q,key->height(level,key));
                if(top-outside>1)retaining.merge(p,outside+1,Math::min);
            }
        }
        for(var e:targets.entrySet()){
            var p=e.getKey();int top=e.getValue(),old=heights.get(p);boolean wall=retaining.containsKey(p);
            if(old>=top&&!wall)continue;
            var surface=p.atY(top);var state=level.getBlockState(surface);
            if(!replaceable(state)&&!(wall&&terrain(state)))continue; // Preserve other pieces and trees.
            for(int y=top;y>=Math.max(level.getMinBuildHeight(),top-DEPTH);y--){
                var at=p.atY(y);var before=level.getBlockState(at);
                if(y<top&&!replaceable(before)&&!(wall&&y>=retaining.get(p)&&terrain(before)))break;
                // Masonry below a lip also prevents later flower patches from
                // considering that column plantable and replacing the guard.
                if(clip.isInside(at))level.setBlock(at,(wall&&y>=retaining.get(p)?Blocks.MOSSY_STONE_BRICKS:y==top?Blocks.GRASS_BLOCK:Blocks.DIRT).defaultBlockState(),Block.UPDATE_CLIENTS);
            }
            if(wall){
                // A surviving cliff outside the eight-column envelope gets a
                // masonry face and a real 1.5-high lip, never an unguarded dirt cut.
                var lip=surface.above();if(clip.isInside(lip)&&replaceable(level.getBlockState(lip))){
                    if(clip.isInside(lip.above())&&level.getBlockState(lip.above()).is(BlockTags.FLOWERS))level.setBlock(lip.above(),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS);
                    level.setBlock(lip,Blocks.MOSSY_STONE_BRICK_WALL.defaultBlockState()
                            .setValue(WallBlock.UP,true).setValue(WallBlock.NORTH_WALL,WallSide.LOW)
                            .setValue(WallBlock.SOUTH_WALL,WallSide.LOW).setValue(WallBlock.EAST_WALL,WallSide.LOW)
                            .setValue(WallBlock.WEST_WALL,WallSide.LOW),Block.UPDATE_CLIENTS);
                }
            }
            // Sparse, surface-attached moss light. Same column support survives
            // reverse chunk order; unlike a wall patch it never waits on a neighbour.
            var moss=surface.above();if(Math.floorMod(p.getX()+p.getZ(),3)==0&&clip.isInside(moss)&&level.isEmptyBlock(moss))
                level.setBlock(moss,Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.DOWN),true),Block.UPDATE_CLIENTS);
        }
    }
    private int height(WorldGenLevel level,BlockPos p){
        var mutable=new BlockPos.MutableBlockPos(p.getX(),deck+4,p.getZ());
        for(int y=deck+4;y>=Math.max(level.getMinBuildHeight(),deck-DEPTH);y--){
            mutable.setY(y);var state=level.getBlockState(mutable);
            // Retaining/approach masonry placed in an earlier chunk slice is
            // ground too; excluding it would make results depend on slice order.
            if(terrain(state)||state.is(Blocks.MOSSY_STONE_BRICKS)||state.is(Blocks.MOSSY_STONE_BRICK_STAIRS))return y;
        }
        return deck-DEPTH;
    }
    private static boolean terrain(BlockState s){return s.is(BlockTags.DIRT)||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(BlockTags.SAND)||s.is(Blocks.GRAVEL)||s.is(Blocks.CLAY)||s.is(Blocks.SNOW_BLOCK);}
    private static boolean replaceable(BlockState s){return s.isAir()||s.canBeReplaced()||s.is(BlockTags.FLOWERS)||s.is(Blocks.WATER)||s.is(Blocks.LAVA)||s.is(Blocks.GLOW_LICHEN);}
}
