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

/** Surface ruins blend into the hillside (NeoForge beard hook, base at the deck); the cave altar stays buried. */
public final class RuinPiece extends TemplateStructurePiece implements net.neoforged.neoforge.common.world.PieceBeardifierModifier {
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, RuinPlacement placement) {
        super(OriWorldgen.PIECE.get(), 0, manager, template(kind), template(kind).toString(), settings(kind, placement.rotation()), placement.origin());
        this.kind = kind;
        this.secret = kind.layout().secret();
        this.bridges = kind.layout().bridges();
        this.beetles = kind.layout().beetles();
        this.mapStone = kind.layout().mapStone();
    }
    public RuinPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(OriWorldgen.PIECE.get(), tag, manager, name -> settings(kindOf(name.toString()), RuinPlacement.readRotation(tag.getString("Rot"))));
        this.kind = kindOf(templateName);
        this.mapStone=tag.contains("OriMapStone",net.minecraft.nbt.Tag.TAG_LONG)?BlockPos.of(tag.getLong("OriMapStone"))
                :kind==null?null:kind.layout().legacyMapStone();
        if (tag.contains("OriSecret", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var s = tag.getCompound("OriSecret");
            this.secret = new RuinKind.Secret(BlockPos.of(s.getLong("Entrance")), net.minecraft.core.Direction.byName(s.getString("Facing")), s.getString("Blocks"));
        } else this.secret = null; // Pre-R103 starts keep their actual old layout, with no invented secret.
        var list=new java.util.ArrayList<RuinKind.Bridge>();
        for(var value:tag.getList("OriBridges",net.minecraft.nbt.Tag.TAG_COMPOUND))list.add(RuinKind.Bridge.load((CompoundTag)value));
        this.bridges=java.util.List.copyOf(list); // Never infer bridges from updated templates for older pieces.
        this.beetles=java.util.Arrays.stream(tag.getLongArray("OriBeetles")).mapToObj(BlockPos::of).toList();
        for(long point:tag.getLongArray("OriBeetlesSpawned"))if(beetles.contains(BlockPos.of(point)))spawnedBeetles.add(point);
    }
    private final java.util.List<BlockPos> beetles;
    private final java.util.Set<Long> spawnedBeetles=new java.util.HashSet<>();
    public java.util.List<BlockPos> beetles(){return beetles;}
    private final java.util.List<RuinKind.Bridge> bridges;
    public java.util.List<RuinKind.Bridge> bridges() { return bridges; }
    private final RuinKind kind;
    private final RuinKind.Secret secret;
    private final BlockPos mapStone;
    public BlockPos mapStone(){return mapStone;}
    public RuinKind kind() { return kind; }
    public RuinKind.Secret secret() { return secret; }
    private static ResourceLocation template(RuinKind kind) { return ResourceLocation.fromNamespaceAndPath("orimod", "ruins/" + kind.id); }
    private static StructurePlaceSettings settings(RuinKind kind, Rotation rotation) {
        var settings = new StructurePlaceSettings().setIgnoreEntities(true).setRotation(rotation);
        return kind == RuinKind.CAVE ? settings.addProcessor(CaveEntranceProcessor.INSTANCE) : settings;
    }
    @Override public BoundingBox getBeardifierBox() { return boundingBox; }
    @Override public net.minecraft.world.level.levelgen.structure.TerrainAdjustment getTerrainAdjustment() {
        return kind == null || kind == RuinKind.CAVE ? net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE
                : net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN;
    }
    /** Beard base = the deck's walking surface, so the low side is filled and the high side eased, not cut. */
    @Override public int getGroundLevelDelta() { return kind == null ? 0 : kind.layout().surfaceY(); }
    private static RuinKind kindOf(String template) {
        for (var kind : RuinKind.values()) if (template.endsWith("ruins/" + kind.id)) return kind;
        return null;
    }
    public BlockPos origin() { return templatePosition; }
    public RuinPlacement placement() { return new RuinPlacement(templatePosition, getRotation()); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", getRotation().name());
        if(mapStone!=null)tag.putLong("OriMapStone",mapStone.asLong());
        if(!beetles.isEmpty()){
            tag.putLongArray("OriBeetles",beetles.stream().mapToLong(BlockPos::asLong).toArray());
            synchronized(spawnedBeetles){tag.putLongArray("OriBeetlesSpawned",spawnedBeetles.stream().mapToLong(Long::longValue).toArray());}
        }
        if(!bridges.isEmpty()) {
            var list=new net.minecraft.nbt.ListTag();bridges.forEach(b->list.add(b.save()));tag.put("OriBridges",list);
        }
        if (secret != null) {
            var s = new CompoundTag(); s.putLong("Entrance", secret.entrance().asLong());
            s.putString("Facing", secret.facing().getName()); s.putString("Blocks", secret.blocks()); tag.put("OriSecret",s);
        }
    }
    @Override public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                                      RandomSource random, BoundingBox chunkBounds, ChunkPos chunk, BlockPos pivot) {
        super.postProcess(level, manager, generator, random, chunkBounds, chunk, pivot);
        // The template clears its interior. Remove the crowns of trunks cut at
        // its top edge too, so the entrance does not leave floating log columns.
        for (int x = Math.max(boundingBox.minX(), chunkBounds.minX()); x <= Math.min(boundingBox.maxX(), chunkBounds.maxX()); x++)
            for (int z = Math.max(boundingBox.minZ(), chunkBounds.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBounds.maxZ()); z++)
                RuinApproachPiece.clearCanopy(level, chunkBounds, x, z, boundingBox.maxY() + 1, boundingBox.maxY() + 48);
        if (kind != null) placeCells(level, kind, placement(), chunkBounds,mapStone);
        spawnBeetles(level,chunkBounds);
    }

    /** Like monument guardians, use the generation accessor to publish into the correct protochunk.
     * Consume each point before spawning; canceled/peaceful spawns and killed guards never respawn. */
    private void spawnBeetles(ServerLevelAccessor level,BoundingBox bounds){
        synchronized(spawnedBeetles){
            for(var local:beetles){
                var pos=placement().block(local);
                if(!bounds.isInside(pos) || !spawnedBeetles.add(local.asLong()))continue;
                var beetle=cn.wwl.orimod.entity.OriMobs.GLOOM_BEETLE.get().create(level.getLevel());
                if(beetle==null)continue;
                beetle.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);beetle.setPersistenceRequired();
                net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(beetle,level,level.getCurrentDifficultyAt(pos),net.minecraft.world.entity.MobSpawnType.STRUCTURE,null);
                level.addFreshEntity(beetle);
            }
        }
    }

    /** R44: put the layout's cell pedestals into the ruin (only the parts inside the chunk being generated). */
    public static int placeCells(net.minecraft.world.level.LevelAccessor level, RuinKind kind, RuinPlacement placement, BoundingBox bounds) {
        return placeCells(level,kind,placement,bounds,kind.layout().mapStone());
    }
    private static int placeCells(net.minecraft.world.level.LevelAccessor level,RuinKind kind,RuinPlacement placement,BoundingBox bounds,BlockPos mapStone) {
        int placed = 0;
        if (kind.layout().spiritSand() != null) {   // R64: burrow tunnel through the solid wall, plus a lit pocket at its end
            var a = kind.layout().spiritSand()[0]; var b = kind.layout().spiritSand()[1];
            for (int x = a.getX(); x <= b.getX() + 2; x++) for (int y = a.getY(); y <= b.getY(); y++) for (int z = a.getZ(); z <= b.getZ(); z++) {
                var pos = placement.block(new BlockPos(x, y, z));
                if (!bounds.isInside(pos)) continue;
                level.setBlock(pos, x <= b.getX() ? cn.wwl.orimod.block.OriBlocks.SPIRIT_SAND.get().defaultBlockState()
                        : (y == a.getY() && z == a.getZ() ? net.minecraft.world.level.block.Blocks.SEA_LANTERN.defaultBlockState() : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()), 2);
                placed++;
            }
        }
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
        if (kind.layout().stormAltar() != null) {   // R70
            var pos = placement.block(kind.layout().stormAltar());
            if (bounds.isInside(pos)) { level.setBlock(pos, cn.wwl.orimod.block.OriBlocks.STORM_ALTAR.get().defaultBlockState(), 2); placed++; }
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
        if (mapStone != null) {   // Saved starts keep their old coordinate even when an adjacent chunk generates later.
            var pos = placement.block(mapStone);
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
