package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.block.SpiritVineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/** One saved sculpture, clipped strictly to the decorating chunk. No write reaches a neighbour. */
public final class GreatTreePiece extends StructurePiece {
    private final BlockPos origin;
    private final long seed;
    private final int version;
    public GreatTreePiece(GreatTreeSite.Site site){
        super(OriWorldgen.GREAT_TREE_PIECE.get(),0,bounds(site.origin(),site.shapeVersion()));
        origin=site.origin();seed=site.shapeSeed();version=site.shapeVersion();
    }
    public GreatTreePiece(CompoundTag tag){super(OriWorldgen.GREAT_TREE_PIECE.get(),tag);origin=BlockPos.of(tag.getLong("Origin"));seed=tag.getLong("ShapeSeed");version=tag.getInt("ShapeVersion");}
    private static BoundingBox bounds(BlockPos p,int version){int r=GreatTreeShape.radius(version);return new BoundingBox(p.getX()-r,p.getY()+GreatTreeShape.minY(version),p.getZ()-r,p.getX()+r,p.getY()+GreatTreeShape.maxY(version),p.getZ()+r);}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag){tag.putLong("Origin",origin.asLong());tag.putLong("ShapeSeed",seed);tag.putInt("ShapeVersion",version);}

    @Override public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator generator,RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot){
        long start=System.nanoTime();var shape=GreatTreeSites.shape(seed,version);long shaped=System.nanoTime();int writes=0;
        var log=OriBlocks.GREAT_SPIRIT_WOOD.get().defaultBlockState();var leaf=OriBlocks.SPIRIT_LEAVES.get().defaultBlockState().setValue(cn.wwl.orimod.block.SpiritLeavesBlock.GREAT_TREE,version>=2);
        var vine=OriBlocks.SPIRIT_VINE.get().defaultBlockState();
        var p=new BlockPos.MutableBlockPos();
        for(int x=Math.max(clip.minX(),boundingBox.minX());x<=Math.min(clip.maxX(),boundingBox.maxX());x++)
            for(int z=Math.max(clip.minZ(),boundingBox.minZ());z<=Math.min(clip.maxZ(),boundingBox.maxZ());z++){
                int dx=x-origin.getX(),dz=z-origin.getZ();
                // Read each column before writing it. Smooth additive mound, never a rectangular terrain cut.
                int ground=level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,x,z)-1;
                double distance=Math.hypot(dx,dz),blend=Math.max(0,1-distance/(version==1?23:43));
                int top=Math.max(ground,(int)Math.floor(ground+(origin.getY()-1-ground)*blend*blend));
                if(distance<(version==1?7:16))top=Math.max(top,origin.getY()-1);
                // A raised, softly shouldered approach meets the arch floor. Never cut a trench between roots.
                int approach=version==1?24:38,shoulder=version==1?8:11;
                if(dz>=-approach && dz<=-4 && Math.abs(dx)<shoulder){
                    double along=Math.clamp((dz+approach)/14.0,0,1),across=Math.clamp((shoulder-Math.abs(dx))/5.0,0,1);
                    top=Math.max(top,(int)Math.floor(ground+(origin.getY()-1-ground)*along*across));
                }
                for(int y=Math.max(ground+1,boundingBox.minY());y<=top;y++){
                    p.set(x,y,z);if(!clip.isInside(p))continue;
                    level.setBlock(p,y==top?Blocks.GRASS_BLOCK.defaultBlockState():Blocks.DIRT.defaultBlockState(),Block.UPDATE_CLIENTS);writes++;
                }
                // Bury root undersides to the local ground, rather than hovering on a downhill column.
                int lowestRoot=Integer.MAX_VALUE;
                for(int y=GreatTreeShape.minY(version);y<=(version==1?12:34);y++)if(shape.cell(dx,y,dz)==GreatTreeShape.WOOD || shape.cell(dx,y,dz)==GreatTreeShape.VEIN){lowestRoot=y;break;}
                if(lowestRoot!=Integer.MAX_VALUE && distance>6 && distance<(version==1?32:42)){
                    int bottom=origin.getY()+lowestRoot;
                    for(int y=Math.max(top+1,boundingBox.minY());y<bottom;y++){
                        p.set(x,y,z);if(!clip.isInside(p))continue;level.setBlock(p,log,Block.UPDATE_CLIENTS);writes++;
                    }
                }
                for(int y=Math.max(GreatTreeShape.minY(version),clip.minY()-origin.getY());y<=Math.min(GreatTreeShape.maxY(version),clip.maxY()-origin.getY());y++){
                    byte interior=version==1?GreatTreeInterior.layout().cell(dx,y,dz):GreatTreeScaleInterior.layout().cell(dx,y,dz);
                    byte cell=interior==0?shape.cell(dx,y,dz):interior;if(cell==GreatTreeShape.EMPTY)continue;
                    p.set(x,origin.getY()+y,z);if(!clip.isInside(p) || level.isOutsideBuildHeight(p))continue;
                    var state=switch(cell){
                        case GreatTreeShape.WOOD->log;
                        case GreatTreeShape.VEIN->log.setValue(cn.wwl.orimod.block.GreatSpiritWoodBlock.VEIN,true);
                        case GreatTreeShape.LEAF->leaf;
                        case GreatTreeShape.CORE->OriBlocks.GREAT_TREE_LEAF_CORE.get().defaultBlockState();
                        case GreatTreeShape.VINE->vine.setValue(SpiritVineBlock.TIP,shape.cell(dx,y-1,dz)!=GreatTreeShape.VINE);
                        case GreatTreeInterior.INNER,GreatTreeInterior.FINE_VEIN,GreatTreeInterior.KNOT->OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(cn.wwl.orimod.block.GreatSpiritRootBlock.LIGHT,
                            cell==GreatTreeInterior.KNOT?cn.wwl.orimod.block.GreatSpiritRootBlock.Light.KNOT:cell==GreatTreeInterior.FINE_VEIN?cn.wwl.orimod.block.GreatSpiritRootBlock.Light.VEIN:cn.wwl.orimod.block.GreatSpiritRootBlock.Light.NONE);
                        case GreatTreeInterior.HEART->OriBlocks.GREAT_TREE_HEART.get().defaultBlockState();
                        case GreatTreeInterior.REWARD->OriBlocks.ENERGY_CELL_PEDESTAL.get().defaultBlockState();
                        case GreatTreeScaleInterior.WIND->OriBlocks.GREAT_TREE_WIND.get().defaultBlockState();
                        case GreatTreeScaleInterior.VENT->OriBlocks.GREAT_TREE_LANDING.get().defaultBlockState();
                        case GreatTreeScaleInterior.OCULUS->Blocks.GLASS.defaultBlockState();
                        case GreatTreeScaleInterior.RAIL->Blocks.DARK_OAK_FENCE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.FenceBlock.NORTH,rail(dx,y,dz-1))
                                .setValue(net.minecraft.world.level.block.FenceBlock.SOUTH,rail(dx,y,dz+1))
                                .setValue(net.minecraft.world.level.block.FenceBlock.WEST,rail(dx-1,y,dz))
                                .setValue(net.minecraft.world.level.block.FenceBlock.EAST,rail(dx+1,y,dz));
                        case GreatTreeInterior.NORTH,GreatTreeInterior.EAST,GreatTreeInterior.SOUTH,GreatTreeInterior.WEST->
                                Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,
                                        switch(cell){case GreatTreeInterior.NORTH->net.minecraft.core.Direction.NORTH;case GreatTreeInterior.EAST->net.minecraft.core.Direction.EAST;case GreatTreeInterior.SOUTH->net.minecraft.core.Direction.SOUTH;default->net.minecraft.core.Direction.WEST;});
                        default->Blocks.AIR.defaultBlockState();
                    };
                    if(cell==GreatTreeShape.AIR && p.getY()<origin.getY() && interior==0)continue;
                    level.setBlock(p,state,Block.UPDATE_CLIENTS);writes++;
                }
                if(version>=2 && GreatTreeSiteRules.groundKnot(dx,dz)){
                    // Embed individual bioluminescent root knots in the terrain; no raised paving or collision grid.
                    p.set(x,top,z);
                    if(clip.isInside(p)&&level.getBlockState(p).isFaceSturdy(level,p,net.minecraft.core.Direction.UP)
                            && level.getBlockState(p.above()).canBeReplaced()){
                        level.setBlock(p,OriBlocks.GREAT_SPIRIT_ROOT.get().defaultBlockState().setValue(
                                cn.wwl.orimod.block.GreatSpiritRootBlock.LIGHT,cn.wwl.orimod.block.GreatSpiritRootBlock.Light.KNOT),Block.UPDATE_CLIENTS);writes++;
                    }
                }
            }
        GreatTreeSites.record(level.getLevel().getChunkSource().randomState(),chunk,writes,shaped-start,System.nanoTime()-shaped);
    }
    private static boolean rail(int x,int y,int z){
        var s=GreatTreeScaleInterior.layout();
        return s.cell(x,y,z)==GreatTreeScaleInterior.RAIL||s.cell(x,y-1,z)==GreatTreeScaleInterior.RAIL||s.cell(x,y+1,z)==GreatTreeScaleInterior.RAIL;
    }
}
