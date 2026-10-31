package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerLevel;
import java.util.HashMap;
import java.util.Map;

/** The same feature preflight reads either real blocks or cached, undecorated noise columns. */
interface FeatureTerrain extends BlockGetter {
    int getHeight(Heightmap.Types type, int x, int z);
    boolean ensureCanWrite(BlockPos pos);
    boolean available(BlockPos origin, int radius);
    boolean ruin(BlockPos pos, int margin);
    boolean near(BlockPos pos, int radius, Block block);
    @Override default FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

    static FeatureTerrain world(WorldGenLevel world) {
        return new FeatureTerrain() {
            public BlockState getBlockState(BlockPos pos) { return world.getBlockState(pos); }
            public BlockEntity getBlockEntity(BlockPos pos) { return world.getBlockEntity(pos); }
            public int getHeight() { return world.getHeight(); }
            public int getMinBuildHeight() { return world.getMinBuildHeight(); }
            public int getHeight(Heightmap.Types type,int x,int z) { return world.getHeight(type,x,z); }
            public boolean ensureCanWrite(BlockPos pos) { return world.ensureCanWrite(pos); }
            public boolean available(BlockPos pos,int radius) {
                for(int x=(pos.getX()-radius)>>4;x<=(pos.getX()+radius)>>4;x++)
                    for(int z=(pos.getZ()-radius)>>4;z<=(pos.getZ()+radius)>>4;z++)
                        if(world instanceof ServerLevel server ? server.getChunkSource().getChunkNow(x,z)==null : !world.hasChunk(x,z)) return false;
                return true;
            }
            public boolean ruin(BlockPos pos,int margin) { return RuinVegetationGuard.blocksTree(world,pos,margin); }
            public boolean near(BlockPos pos,int radius,Block block) { return BlightedGroundsFeature.nearBlock(world,pos,radius,block); }
        };
    }

    /** Vanilla cells share exact column states down through root depth; custom generators retain getBaseColumn. */
    final class Noise implements FeatureTerrain {
        private record Column(NoiseColumn blocks,int floor,int surface,int minY) {}
        private final ChunkGenerator generator;
        private final RandomState random;
        private final LevelHeightAccessor height;
        private final ChunkPos chunk;
        private final FeatureNoiseColumns cells;
        private final Map<Long,Column> columns=new HashMap<>();
        Noise(ChunkGenerator generator,RandomState random,LevelHeightAccessor height,BlockPos origin) {
            this.generator=generator;this.random=random;this.height=height;this.chunk=new ChunkPos(origin);
            cells=generator.getClass()==NoiseBasedChunkGenerator.class&&!Boolean.getBoolean("orimod.worldgen.site.fullColumns")?new FeatureNoiseColumns((NoiseBasedChunkGenerator)generator,random,height):null;
        }
        private Column column(int x,int z) {
            return columns.computeIfAbsent(ChunkPos.asLong(x,z),ignored->{
                if(cells!=null){var c=cells.at(x,z);return new Column(c.blocks(),c.floor(),c.surface(),c.minY());}
                var blocks=generator.getBaseColumn(x,z,height,random);
                int floor=getMinBuildHeight(),surface=floor;
                boolean top=false;
                for(int y=getMaxBuildHeight()-1;y>=getMinBuildHeight();y--) {
                    var state=blocks.getBlock(y);
                    if(!top && Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(state)){surface=y+1;top=true;}
                    if(Heightmap.Types.OCEAN_FLOOR_WG.isOpaque().test(state)){floor=y+1;break;}
                }
                return new Column(blocks,floor,surface,getMinBuildHeight());
            });
        }
        public BlockState getBlockState(BlockPos pos) {
            var c=column(pos.getX(),pos.getZ());
            if(pos.getY()<c.minY){
                c=new Column(generator.getBaseColumn(pos.getX(),pos.getZ(),height,random),c.floor,c.surface,getMinBuildHeight());
                columns.put(ChunkPos.asLong(pos.getX(),pos.getZ()),c);
            }
            return c.blocks.getBlock(pos.getY());
        }
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        public int getHeight() { return height.getHeight(); }
        public int getMinBuildHeight() { return height.getMinBuildHeight(); }
        public int getHeight(Heightmap.Types type,int x,int z) {
            var column=column(x,z);
            return type==Heightmap.Types.OCEAN_FLOOR_WG || type==Heightmap.Types.OCEAN_FLOOR ? column.floor : column.surface;
        }
        public boolean ensureCanWrite(BlockPos pos) { return !isOutsideBuildHeight(pos) && Math.abs((pos.getX()>>4)-chunk.x)<=1 && Math.abs((pos.getZ()>>4)-chunk.z)<=1; }
        public boolean available(BlockPos pos,int radius) { return true; }
        // Structure reservations are decided separately from placement grids, never from generated chunks.
        public boolean ruin(BlockPos pos,int margin) { return false; }
        public boolean near(BlockPos pos,int radius,Block block) { return false; }
        int sampledColumns() { return columns.size(); }
    }
}
