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
        this(manager, kind, placement, 0); // Existing callers/tests explicitly retain the legacy contract.
    }
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, RuinPlacement placement, int layoutVersion) {
        this(manager,kind,placement,layoutVersion,new int[0]);
    }
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, RuinPlacement placement, int layoutVersion, int[] approachTerrain) {
        this(manager,kind,placement,layoutVersion,approachTerrain,new int[0]);
    }
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, RuinPlacement placement, int layoutVersion, int[] approachTerrain,int[] naturalTerrain) {
        this(manager,kind,placement,layoutVersion,approachTerrain,naturalTerrain,0);
    }
    public RuinPiece(StructureTemplateManager manager, RuinKind kind, RuinPlacement placement, int layoutVersion, int[] approachTerrain,int[] naturalTerrain,int forestTerrainVersion) {
        super(OriWorldgen.PIECE.get(), 0, manager, kind.template(layoutVersion), kind.template(layoutVersion).toString(), settings(kind, placement.rotation(), layoutVersion), placement.origin());
        this.kind = kind;
        this.layoutVersion = layoutVersion;
        this.forestTerrainVersion=checkedForestVersion(kind,layoutVersion,forestTerrainVersion);
        this.secret = layout().secret(); this.bridges = layout().bridges();
        this.beetles = layout().beetles(); this.mapStone = layout().mapStone();
        this.forestApproach=approachTerrain.clone();
        this.forestTerrain = forestTerrain();
        this.creekTerrain = creekTerrain();
        this.gradedForest=forestTerrainVersion==1?new ForestGradedTerrain(template,templatePosition,placeSettings,forestApproach):null;
        if(creekTerrain!=null)creekTerrain.restoreSamples(naturalTerrain);
        if(gradedForest!=null)gradedForest.restoreSamples(naturalTerrain);
        if(creekTerrain!=null||gradedForest!=null)CreekSliceSettings.prepare(template);
        if(forestTerrain!=null||gradedForest!=null)boundingBox=ForestRuinTerrain.bounds(boundingBox);
        if(creekTerrain!=null)boundingBox=CreekRuinTerrain.bounds(boundingBox);
    }
    public RuinPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(OriWorldgen.PIECE.get(), tag, manager, name -> settings(kindOf(name.toString()), RuinPlacement.readRotation(tag.getString("Rot")), tag.getInt("LayoutVersion")));
        this.kind = kindOf(templateName);
        this.layoutVersion = tag.getInt("LayoutVersion");
        this.forestTerrainVersion=checkedForestVersion(kind,layoutVersion,tag.getInt("ForestTerrainVersion"));
        if (kind == null || !kind.template(layoutVersion).toString().equals(templateName)) throw new IllegalArgumentException("Ruin version/template mismatch: " + templateName);
        this.mapStone=tag.contains("OriMapStone",net.minecraft.nbt.Tag.TAG_LONG)?BlockPos.of(tag.getLong("OriMapStone"))
                :kind==null?null:layout().legacyMapStone();
        if (tag.contains("OriSecret", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            var s = tag.getCompound("OriSecret");
            this.secret = new RuinKind.Secret(BlockPos.of(s.getLong("Entrance")), net.minecraft.core.Direction.byName(s.getString("Facing")), s.getString("Blocks"));
        } else this.secret = null; // Pre-R103 starts keep their actual old layout, with no invented secret.
        var list=new java.util.ArrayList<RuinKind.Bridge>();
        for(var value:tag.getList("OriBridges",net.minecraft.nbt.Tag.TAG_COMPOUND))list.add(RuinKind.Bridge.load((CompoundTag)value));
        this.bridges=java.util.List.copyOf(list); // Never infer bridges from updated templates for older pieces.
        this.beetles=java.util.Arrays.stream(tag.getLongArray("OriBeetles")).mapToObj(BlockPos::of).toList();
        for(long point:tag.getLongArray("OriBeetlesSpawned"))if(beetles.contains(BlockPos.of(point)))spawnedBeetles.add(point);
        this.forestApproach=tag.getIntArray(kind==RuinKind.CREEK?"CreekApproachTerrain":"ForestApproachTerrain");
        this.forestTerrain = forestTerrain();
        this.creekTerrain = creekTerrain();
        this.gradedForest=forestTerrainVersion==1?new ForestGradedTerrain(template,templatePosition,placeSettings,forestApproach):null;
        if(creekTerrain!=null){creekTerrain.restoreSamples(tag.getIntArray("CreekNaturalTerrain"));creekTerrain.restoreGuards(tag.getLongArray("CreekGuardCells"));}
        if(gradedForest!=null){gradedForest.restoreSamples(tag.getIntArray("ForestNaturalTerrain"));gradedForest.restoreGuards(tag.getLongArray("ForestGuardCells"));}
        if(creekTerrain!=null||gradedForest!=null)CreekSliceSettings.prepare(template);
        if(forestTerrain!=null||gradedForest!=null)boundingBox=ForestRuinTerrain.bounds(boundingBox);
        if(creekTerrain!=null)boundingBox=CreekRuinTerrain.bounds(boundingBox);
    }
    private final int forestTerrainVersion;
    private final ForestGradedTerrain gradedForest;
    public int forestTerrainVersion(){return forestTerrainVersion;}
    private static int checkedForestVersion(RuinKind kind,int layout,int version){
        if(version<0||version>1||version!=0&&(kind!=RuinKind.FOREST||layout<1||layout>2))throw new IllegalArgumentException("Invalid forest terrain version "+version);
        return version;
    }
    private final ForestRuinTerrain forestTerrain;
    private final CreekRuinTerrain creekTerrain;
    private CreekRuinTerrain creekTerrain(){return kind==RuinKind.CREEK&&layoutVersion>0?new CreekRuinTerrain(template,templatePosition,placeSettings,forestApproach):null;}
    private final int[] forestApproach;
    private ForestRuinTerrain forestTerrain(){return kind==RuinKind.FOREST&&layoutVersion>0&&forestTerrainVersion==0?new ForestRuinTerrain(template,templatePosition,placeSettings,forestApproach):null;}
    private final java.util.List<BlockPos> beetles;
    private final java.util.Set<Long> spawnedBeetles=new java.util.HashSet<>();
    public java.util.List<BlockPos> beetles(){return beetles;}
    private final java.util.List<RuinKind.Bridge> bridges;
    public java.util.List<RuinKind.Bridge> bridges() { return bridges; }
    private final RuinKind kind;
    private final int layoutVersion;
    public int layoutVersion() { return layoutVersion; }
    public RuinKind.Layout layout() { return kind.layout(layoutVersion); }
    private final RuinKind.Secret secret;
    private final BlockPos mapStone;
    public BlockPos mapStone(){return mapStone;}
    public RuinKind kind() { return kind; }
    public RuinKind.Secret secret() { return secret; }
    private static StructurePlaceSettings settings(RuinKind kind, Rotation rotation, int version) {
        StructurePlaceSettings settings = new StructurePlaceSettings();
        settings.setIgnoreEntities(true).setRotation(rotation);
        if (version > 0) settings.setLiquidSettings(net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING);
        return kind == RuinKind.CAVE && version == 0 ? settings.addProcessor(CaveEntranceProcessor.INSTANCE) : settings;
    }
    @Override public BoundingBox getBeardifierBox() { return forestTerrain==null&&creekTerrain==null&&gradedForest==null?boundingBox:template.getBoundingBox(placeSettings,templatePosition); }
    @Override public net.minecraft.world.level.levelgen.structure.TerrainAdjustment getTerrainAdjustment() {
        return kind == null || kind == RuinKind.CAVE ? net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE
                : net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN;
    }
    /** Beard base = the deck's walking surface, so the low side is filled and the high side eased, not cut. */
    @Override public int getGroundLevelDelta() { return kind == null ? 0 : layout().surfaceY(); }
    private static RuinKind kindOf(String template) {
        for (var kind : RuinKind.values()) for (int v = 0; v <= kind.currentVersion(); v++) if (template.equals(kind.template(v).toString())) return kind;
        return null;
    }
    public BlockPos origin() { return templatePosition; }
    public boolean ownsCreekSupport(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return creekTerrain!=null&&creekTerrain.ownSupport(pos,state);}
    public boolean ownsCreekGuard(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return creekTerrain!=null&&creekTerrain.ownGuard(pos,state);}
    public boolean ownsTerrainSupport(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return ownsCreekSupport(pos,state)||gradedForest!=null&&gradedForest.ownSupport(pos,state);}
    public boolean ownsTerrainGuard(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return ownsCreekGuard(pos,state)||gradedForest!=null&&gradedForest.ownGuard(pos,state);}
    public int[] forestApproachSideProfile(){return gradedForest==null?new int[0]:gradedForest.approachSideProfile();}
    public RuinPlacement placement() { return new RuinPlacement(templatePosition, getRotation()); }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", getRotation().name());
        tag.putInt("LayoutVersion", layoutVersion);
        if((forestTerrain!=null||gradedForest!=null)&&forestApproach.length!=0)tag.putIntArray("ForestApproachTerrain",forestApproach);
        if(creekTerrain!=null&&forestApproach.length!=0)tag.putIntArray("CreekApproachTerrain",forestApproach);
        if(creekTerrain!=null){tag.putIntArray("CreekNaturalTerrain",creekTerrain.savedSamples());tag.putLongArray("CreekGuardCells",creekTerrain.savedGuards());}
        if(gradedForest!=null){tag.putInt("ForestTerrainVersion",forestTerrainVersion);tag.putIntArray("ForestNaturalTerrain",gradedForest.savedSamples());tag.putLongArray("ForestGuardCells",gradedForest.savedGuards());}
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
        long profileStart = Boolean.getBoolean("orimod.profileRuins") ? System.nanoTime() : 0;
        var cpuClock=profileStart==0?null:java.lang.management.ManagementFactory.getThreadMXBean();
        long cpuStart=cpuClock==null||!cpuClock.isCurrentThreadCpuTimeSupported()?0:cpuClock.getCurrentThreadCpuTime();
        if(creekTerrain!=null)creekTerrain.ensureSamples(level,generator);
        if(gradedForest!=null)gradedForest.ensureSamples(level,generator);
        long captureDone=profileStart==0?0:System.nanoTime();
        // Expanded terrain skirts include chunks containing no authored template.
        // Avoid transforming every template cell in those chunks. Legacy and the
        // other ruins retain their original placement path.
        if(creekTerrain==null&&gradedForest==null)super.postProcess(level, manager, generator, random, chunkBounds, chunk, pivot);
        else {
            // TemplateStructurePiece.postProcess mutates its shared settings AND
            // boundingBox. Adjacent worldgen workers must not enter that path.
            // These options are read-only after construction; clip and RNG belong
            // to this invocation, and the expanded piece box remains unchanged.
            var local=new CreekSliceSettings();
            local.setRotation(placeSettings.getRotation()).setMirror(placeSettings.getMirror())
                    .setRotationPivot(placeSettings.getRotationPivot()).setIgnoreEntities(true)
                    // New forest consumes the authored connections/attachments.
                    // Neighbour slices may not yet exist: recomputing a lichen or
                    // wall here would make first placement differ from a reload.
                    .setKnownShape(gradedForest!=null||placeSettings.getKnownShape()).setFinalizeEntities(placeSettings.shouldFinalizeEntities())
                    .setLiquidSettings(net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings.IGNORE_WATERLOGGING)
                    .setBoundingBox(chunkBounds);
            placeSettings.getProcessors().forEach(local::addProcessor);
            if(creekTerrain!=null)local.addProcessor(CreekRimDecorationProcessor.INSTANCE);
            else local.addProcessor(new ForestTerrainProcessor(ForestTerrainContract.of(template),gradedForest.fluids));
            if(template.getBoundingBox(local,templatePosition).intersects(chunkBounds))
                placeCreekTemplate(level,random,chunkBounds,pivot,local);
        }
        long templateDone=profileStart==0?0:System.nanoTime();
        if (forestTerrain != null) {
            supportForestFoundation(level, chunkBounds);
            forestTerrain.place(level,chunkBounds);
            // TemplateStructurePiece resets its box during postProcess. Restore the
            // skirt envelope so subsequent chunk slices and saved starts include it.
            boundingBox=ForestRuinTerrain.bounds(template.getBoundingBox(placeSettings,templatePosition));
        }
        if(gradedForest!=null)clearForestSupplements(level,chunkBounds);
        CreekRuinTerrain.Stats terrainStats=creekTerrain!=null?creekTerrain.place(level,chunkBounds):gradedForest!=null?gradedForest.place(level,chunkBounds):null;
        long terrainDone=profileStart==0?0:System.nanoTime();
        // The template clears its interior. Remove the crowns of trunks cut at
        // its top edge too, so the entrance does not leave floating log columns.
        if (layoutVersion == 0) for (int x = Math.max(boundingBox.minX(), chunkBounds.minX()); x <= Math.min(boundingBox.maxX(), chunkBounds.maxX()); x++)
            for (int z = Math.max(boundingBox.minZ(), chunkBounds.minZ()); z <= Math.min(boundingBox.maxZ(), chunkBounds.maxZ()); z++)
                RuinApproachPiece.clearCanopy(level, chunkBounds, x, z, boundingBox.maxY() + 1, boundingBox.maxY() + 48);
        if (kind != null && layoutVersion == 0) placeCells(level, kind, placement(), chunkBounds,mapStone);
        spawnBeetles(level,chunkBounds);
        if (profileStart != 0) {
            long end=System.nanoTime();
            long cpuEnd=cpuStart==0?0:cpuClock.getCurrentThreadCpuTime();
            if(terrainStats!=null){
                var t=terrainStats;
                cn.wwl.orimod.OriMod.LOGGER.info("CREEK_PHASE chunk={} samplePlanMs={} foundationMs={} bankWriteMs={} samples={} writes={} filled={} cut={} deepExceptions={} treeExceptions={} unsupported={} caveExceptions={} buildingExceptions={} maxDesignFill={}",chunk,t.planNanos()/1e6,t.foundationNanos()/1e6,t.bankNanos()/1e6,t.naturalSamples(),t.writtenBlocks(),t.filledColumns(),t.cutColumns(),t.deepExceptions(),t.treeExceptions(),t.unsupportedColumns(),t.caveExceptions(),t.buildingExceptions(),t.maximumFill());
                cn.wwl.orimod.OriMod.LOGGER.info("CREEK_TERRAIN chunk={} stats={}",chunk,t);
            }
            cn.wwl.orimod.OriMod.LOGGER.info("RUIN_WRITE kind={} version={} chunk={} ms={} captureMs={} templateMs={} terrainMs={} postMs={} cpuMs={}",kind.id,layoutVersion,chunk,(end-profileStart)/1e6,(captureDone-profileStart)/1e6,(templateDone-captureDone)/1e6,(terrainDone-templateDone)/1e6,(end-terrainDone)/1e6,(cpuEnd-cpuStart)/1e6);
            cn.wwl.orimod.OriMod.LOGGER.info("RUIN_PROFILE_OVERHEAD kind={} chunk={} logMs={}",kind.id,chunk,(System.nanoTime()-end)/1e6);
        }
    }

    /** Only missing cells in the eight registered late foot columns; template stays byte-identical. */
    private void clearForestSupplements(WorldGenLevel level,BoundingBox clip){
        for(var raw:ForestTerrainContract.of(template).clearance){var at=placement().block(raw);
            if(clip.isInside(at)&&CreekTerrainSurface.mutable(level.getBlockState(at),level,at)&&level.getBlockEntity(at)==null
                    &&gradedForest.fluids.allows(level,at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()))
                level.setBlock(at,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS|net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
        }
    }

    /** Vanilla template placement and marker handling with invocation-local settings.
     * Only the pre-existing, synchronized beetle receipt is mutable per piece. */
    private void placeCreekTemplate(WorldGenLevel level,RandomSource random,BoundingBox clip,BlockPos pivot,StructurePlaceSettings local){
        var trace=CreekPlacementTrace.begin(clip.minX()>>4,clip.minZ()>>4);
        boolean placed;
        try{
            placed=template.placeInWorld(level,templatePosition,pivot,local,random,
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS|(gradedForest==null?0:net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE));
        }
        finally{CreekPlacementTrace.finish(trace);}
        if(!placed)return;
        for(var info:template.filterBlocks(templatePosition,local,net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK)){
            if(info.nbt()!=null&&"DATA".equals(info.nbt().getString("mode")))
                handleDataMarker(info.nbt().getString("metadata"),info.pos(),level,random,clip);
        }
        for(var info:template.filterBlocks(templatePosition,local,net.minecraft.world.level.block.Blocks.JIGSAW)){
            if(info.nbt()==null)continue;
            var state=net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            try{
                state=net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                        level.holderLookup(net.minecraft.core.registries.Registries.BLOCK),info.nbt().getString("final_state"),true).blockState();
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){
                cn.wwl.orimod.OriMod.LOGGER.error("Error while parsing jigsaw blockstate {} at {}",info.nbt().getString("final_state"),info.pos());
            }
            if(gradedForest==null||CreekTerrainSurface.mutable(level.getBlockState(info.pos()),level,info.pos())
                    &&level.getBlockEntity(info.pos())==null&&gradedForest.fluids.allows(level,info.pos(),state))level.setBlock(info.pos(),state,3);
        }
    }

    /** Noise preflight cannot see carver pockets. Extend only authored bottom dirt into
     * small holes, within this chunk; never pave untouched columns or alter legacy pieces. */
    private void supportForestFoundation(WorldGenLevel level, BoundingBox clip) {
        for (var cell : template.filterBlocks(templatePosition, placeSettings, net.minecraft.world.level.block.Blocks.DIRT)) {
            var base = cell.pos();
            if (base.getY() != templatePosition.getY() || !clip.isInside(base)) continue;
            int depth = 1;
            while (depth <= 8 && level.isEmptyBlock(base.below(depth))) depth++;
            if (depth <= 1 || depth > 8 || !level.getBlockState(base.below(depth)).isFaceSturdy(level, base.below(depth), net.minecraft.core.Direction.UP)) continue;
            for (int d = 1; d < depth; d++) {
                var at = base.below(d);
                if (clip.isInside(at)) level.setBlock(at, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }
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
