package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public final class RuinPiece extends TemplateStructurePiece {
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, RuinPlacement placement) {
        super(OriWorldgen.PIECE.get(), 0, manager, template(kind), template(kind).toString(), settings(placement.rotation()), placement.origin());
        this.kind = kind;
    }
    public RuinPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(OriWorldgen.PIECE.get(), tag, manager, ignored -> settings(RuinPlacement.readRotation(tag.getString("Rot"))));
        this.kind = kindOf(templateName);
    }
    private final RuinKind kind;
    private static ResourceLocation template(RuinKind kind) { return ResourceLocation.fromNamespaceAndPath("orimod", "ruins/" + kind.id); }
    private static StructurePlaceSettings settings(Rotation rotation) { return new StructurePlaceSettings().setIgnoreEntities(true).setRotation(rotation); }
    private static RuinKind kindOf(String template) {
        for (var kind : RuinKind.values()) if (template.endsWith("ruins/" + kind.id)) return kind;
        return null;
    }
    public BlockPos origin() { return templatePosition; }
    public RuinPlacement placement() { return new RuinPlacement(templatePosition, getRotation()); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", getRotation().name());
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, manager, generator, random, chunkBounds, chunk, pivot);
        // The template clears its interior. Remove the crowns of trunks cut at
        // its top edge too, so the entrance does not leave floating log columns.
        for (int x = Math.max(boundingBox.minX(), chunkBounds.minX()); x <= Math.min(boundingBox.maxX(), chunkBounds.maxX()); x++)
            for (int z = Math.max(boundingBox.minZ(), chunkBounds.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBounds.maxZ()); z++)
                RuinApproachPiece.clearCanopy(level, chunkBounds, x, z, boundingBox.maxY() + 1, boundingBox.maxY() + 48);
        if (kind != null) placeCells(level, kind, placement(), chunkBounds);
    }

    /** R44: put the layout's cell pedestals into the ruin (only the parts inside the chunk being generated). */
    public static int placeCells(net.minecraft.world.level.LevelAccessor level, RuinKind kind, RuinPlacement placement, BoundingBox bounds) {
        int placed = 0;
        for (var cell : kind.layout().cells()) {
            var pos = placement.block(cell.position());
            if (!bounds.isInside(pos)) continue;
            var block = cell.kind() == cn.wwl.orimod.progression.SpiritCells.Kind.LIFE ? cn.wwl.orimod.block.OriBlocks.LIFE_CELL_PEDESTAL : cn.wwl.orimod.block.OriBlocks.ENERGY_CELL_PEDESTAL;
            level.setBlock(pos, block.get().defaultBlockState(), 2);
            placed++;
        }
        // R46: updraft vents over the creek gaps, so the glide route can climb.
        for (var local : kind.layout().updrafts()) {
            var pos = placement.block(local);
            if (bounds.isInside(pos)) { level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.UPDRAFT.get().defaultBlockState(), 2); placed++; }
        }
        return placed;
    }
    @Override protected void handleDataMarker(String marker, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox bounds) {}
}
