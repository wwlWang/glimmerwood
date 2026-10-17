package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class RuinPiece extends TemplateStructurePiece {
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, BlockPos origin) {
        super(OriWorldgen.PIECE.get(), 0, manager, template(kind), template(kind).toString(), settings(), origin);
    }
    public RuinPiece(StructureTemplateManager manager, CompoundTag tag) { super(OriWorldgen.PIECE.get(), tag, manager, ignored -> settings()); }
    private static ResourceLocation template(RuinKind kind) { return ResourceLocation.fromNamespaceAndPath("orimod", "ruins/" + kind.id); }
    private static StructurePlaceSettings settings() { return new StructurePlaceSettings().setIgnoreEntities(true); }
    public BlockPos origin() { return templatePosition; }
    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, manager, generator, random, chunkBounds, chunk, pivot);
        // The template clears its interior. Remove the crowns of trunks cut at
        // its top edge too, so the entrance does not leave floating log columns.
        for (int x = Math.max(boundingBox.minX(), chunkBounds.minX()); x <= Math.min(boundingBox.maxX(), chunkBounds.maxX()); x++)
            for (int z = Math.max(boundingBox.minZ(), chunkBounds.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBounds.maxZ()); z++)
                RuinApproachPiece.clearCanopy(level, chunkBounds, x, z, boundingBox.maxY() + 1, boundingBox.maxY() + 48);
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {}
}
