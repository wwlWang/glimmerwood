package cn.wwl.orimod.world;

import java.util.Map;
import net.minecraft.core.*;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Only ForestTerrainVersion=1. The approach owns its two outer columns; the
 * main skirt never writes them. Ordinary flanks use the same immutable plan as
 * the skirt, while untouched caves/buildings use physical collision heights. */
final class ForestApproachGuards {
    private final RuinPlacement door;
    private final int[] terrain;
    private volatile int[] sides;
    private final CreekTerrainGuards guards=new CreekTerrainGuards();
    ForestApproachGuards(RuinPlacement door,int[] terrain,int[] profile){
        if(profile.length!=0&&profile.length!=54)throw new IllegalArgumentException("Forest approach needs 18 flank triples");
        this.door=door;this.terrain=terrain.clone();sides=profile.clone();
    }
    void restore(long[] cells){guards.restore(cells);}
    long[] saved(){return guards.save();}
    int[] profile(){return sides.clone();}
    boolean ownsGuard(BlockPos p,BlockState s){return s.is(Blocks.MOSSY_COBBLESTONE_WALL)&&guards.contains(p.asLong());}
    boolean ownsSupport(BlockPos p,BlockState s){return s.is(Blocks.MOSSY_COBBLESTONE)&&guards.contains(p.above().asLong());}
    boolean ownsLight(BlockPos p,BlockState s){return s.is(Blocks.LIGHT)&&s.getValue(LightBlock.LEVEL)==7&&guards.contains(p.below().asLong());}
    boolean ownsAssembly(BlockPos p,BlockState s){return ownsGuard(p,s)||ownsSupport(p,s)||ownsLight(p,s);}
    private void ensure(WorldGenLevel level,ChunkGenerator generator){
        if(sides.length!=0)return;
        synchronized(this){if(sides.length!=0)return;
            // Compatibility for pre-release FTV1 starts/test constructors without
            // the new profile: pure noise only, never live neighbouring terrain.
            var layout=RuinKind.FOREST.layout(1);
            var p=new RuinPlacement(door.block(new BlockPos(-(int)layout.entry().x,-layout.approachY(),0)),door.rotation());
            var template=level.getLevel().getStructureManager().getOrCreate(RuinKind.FOREST.template(1));
            var contract=ForestTerrainContract.of(template).terrain;
            var samples=RuinTerrainKernel.sample(contract,p,(x,z)->generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,level.getLevel().getChunkSource().randomState()),"MISSING_FOREST_APPROACH_PROFILE",Map.of());
            var kernel=new ForestGradedTerrain(template,p.origin(),new StructurePlaceSettings().setRotation(p.rotation()),terrain);
            kernel.restoreSamples(samples);sides=kernel.approachSideProfile();
        }
    }
    private record Surface(double feet,boolean ungraded){}
    private Surface surface(WorldGenLevel l,BlockPos side,int natural,int planned,int floor,boolean graded){
        boolean protectedPlant=false,foreign=false;
        double feet=Double.NaN;
        // Neighbour columns may belong to another slice: use block states, not its mutable heightmap.
        int first=l.getMaxBuildHeight()-1;
        for(int y=first;y>=l.getMinBuildHeight();y--){var at=side.atY(y);var state=l.getBlockState(at);
            var category=CreekTerrainSurface.category(state,l,at);
            if(category==CreekTerrainSurface.Category.IGNORE_PROTECTED)protectedPlant=true;
            if(category==CreekTerrainSurface.Category.BUILDING)foreign=true;
            if(CreekTerrainSurface.standingCandidate(state,l,at)||category==CreekTerrainSurface.Category.IGNORE_SOFT
                    &&Double.isFinite(CreekTerrainSurface.standingTop(state,l,at))&&CreekTerrainSurface.standingTop(state,l,at)-y<1){
                feet=CreekTerrainSurface.standingTop(state,l,at);break;
            }
        }
        if(!Double.isFinite(feet))return new Surface(l.getMinBuildHeight(),true);
        int ground=(int)Math.ceil(feet)-1;
        boolean cave=CreekTerrainExceptions.cave(natural,planned,ground);
        boolean untouched=!graded||cave||foreign||ground>planned+6||protectedPlant&&ground>planned
                ||ForestFluidProtection.FOREIGN_ONLY.preservesColumn(l,side.atY(planned),ground);
        return new Surface(untouched?feet:planned+1,untouched);
    }
    void place(WorldGenLevel l,ChunkGenerator generator,BoundingBox clip){
        ensure(l,generator);int[] snapshot=sides;
        for(int side=0;side<2;side++)for(int z=-9;z<=-1;z++){
            int x=side==0?-2:2,ground=terrain[(x+2)*9];
            int floor=z==-9?ground-1:RuinSiteRules.stepFloor(ground,door.origin().getY(),z+8);
            var base=door.block(new BlockPos(x,0,z)).atY(floor);if(!clip.isInside(base))continue;
            var at=base.above();var existing=l.getBlockState(at);
            if(ownsGuard(at,existing)){light(l,clip,at);continue;}
            var support=l.getBlockState(base);double top=CreekTerrainSurface.standingTop(support,l,base);
            if(!Double.isFinite(top))continue;
            var outside=door.block(new BlockPos(side==0?-3:3,0,z));int i=(side*9+z+9)*3;
            var neighbour=surface(l,outside,snapshot[i],snapshot[i+1],floor,snapshot[i+2]==1);
            if(CreekTerrainSurface.dropHalfUnits(top,neighbour.feet())<=2)continue;
            // Never claim/overwrite foreign masonry, a hard block, or vegetation
            // protected by the shared classification. Support only the road top.
            if(!clip.isInside(at)||!CreekTerrainSurface.replaceable(existing,l,at))continue;
            if(!(support.is(Blocks.MOSSY_STONE_BRICKS)||support.is(Blocks.MOSSY_STONE_BRICK_STAIRS)||CreekTerrainSurface.natural(support))
                    ||!CreekTerrainSurface.mutable(support,l,base))continue;
            if(!ForestFluidProtection.FOREIGN_ONLY.allows(l,base,Blocks.MOSSY_COBBLESTONE.defaultBlockState())
                    ||!ForestFluidProtection.FOREIGN_ONLY.allows(l,at,wall()))continue;
            guards.plan(at.asLong());
            l.setBlock(base,Blocks.MOSSY_COBBLESTONE.defaultBlockState(),18);
            l.setBlock(at,wall(),18);light(l,clip,at);
        }
    }
    private void light(WorldGenLevel l,BoundingBox clip,BlockPos guard){
        var at=guard.above();if(clip.isInside(at)&&l.isEmptyBlock(at))l.setBlock(at,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,7),18);
    }
    private static BlockState wall(){return Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState().setValue(WallBlock.UP,true)
            .setValue(WallBlock.NORTH_WALL,WallSide.LOW).setValue(WallBlock.SOUTH_WALL,WallSide.LOW)
            .setValue(WallBlock.EAST_WALL,WallSide.LOW).setValue(WallBlock.WEST_WALL,WallSide.LOW);}
}
