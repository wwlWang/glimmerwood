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
        // R57: the keystone vault shell and door first, so the cell inside is placed into its hollow.
        var vault = kind.layout().vault();
        if (vault != null) {
            for (int x = vault.from().getX(); x <= vault.to().getX(); x++)
                for (int y = vault.from().getY(); y <= vault.to().getY(); y++)
                    for (int z = vault.from().getZ(); z <= vault.to().getZ(); z++) {
                        boolean shell = x == vault.from().getX() || x == vault.to().getX() || y == vault.to().getY() || z == vault.from().getZ() || z == vault.to().getZ();
                        var pos = placement.block(new BlockPos(x, y, z));
                        if (bounds.isInside(pos)) level.setBlock(pos, (shell ? net.minecraft.world.level.block.Blocks.DEEPSLATE_TILES : net.minecraft.world.level.block.Blocks.AIR).defaultBlockState(), 2);
                    }
            for (var local : vault.door()) {
                var pos = placement.block(local);
                if (bounds.isInside(pos)) { level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(), 2); placed++; }
            }
        }
        for (var local : kind.layout().keystones()) {
            var pos = placement.block(local);
            if (bounds.isInside(pos)) { level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.KEYSTONE_PEDESTAL.get().defaultBlockState(), 2); placed++; }
        }
        for (var cell : kind.layout().cells()) {
            var pos = placement.block(cell.position());
            if (!bounds.isInside(pos)) continue;
            var block = cell.kind() == cn.wwl.orimod.progression.SpiritCells.Kind.LIFE ? cn.wwl.orimod.block.OriBlocks.LIFE_CELL_PEDESTAL : cn.wwl.orimod.block.OriBlocks.ENERGY_CELL_PEDESTAL;
            level.setBlock(pos, block.get().defaultBlockState(), 2);
            placed++;
        }
        // R50: the boss altar replaces one floor block of the arena hall.
        if (kind.layout().bossAltar() != null) {
            var pos = placement.block(kind.layout().bossAltar());
            if (bounds.isInside(pos)) { level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.GLOOM_ALTAR.get().defaultBlockState(), 2); placed++; }
        }
        // R61: the creek under the glide route is corrupted (water only; cleansed by Charge Flame / Light Burst).
        var gloom = kind.layout().gloomWater();
        if (gloom != null)
            for (var local : BlockPos.betweenClosed(gloom[0], gloom[1])) {
                var pos = placement.block(local);
                if (bounds.isInside(pos) && level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WATER)) {
                    level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.GLOOM_WATER.get().defaultBlockState(), 2); placed++; }
            }
        if (kind.layout().mapStone() != null) {   // R62
            var pos = placement.block(kind.layout().mapStone());
            if (bounds.isInside(pos)) { level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.MAP_STONE.get().defaultBlockState(), 2); placed++; }
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
