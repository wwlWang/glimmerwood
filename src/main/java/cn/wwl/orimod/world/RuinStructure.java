package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

public final class RuinStructure extends Structure {
    public static final MapCodec<RuinStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.STRING.fieldOf("kind").forGetter(value -> value.kind.id)
    ).apply(instance, (settings, kind) -> new RuinStructure(settings, RuinKind.byId(kind))));
    public final RuinKind kind;
    private final CreekStructureReservations creekReservations=new CreekStructureReservations();
    private final ForestStructureReservations forestReservations=new ForestStructureReservations();
    public RuinStructure(StructureSettings settings, RuinKind kind) { super(settings); this.kind = kind; }

    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return findGenerationPoint(context,kind.currentVersion());
    }

    // Explicit version remains available to the frozen-layout audit.
    Optional<GenerationStub> findGenerationPoint(GenerationContext context,int version) {
        var result=kind==RuinKind.FOREST&&version>0?RuinSitingSites.owner(this,context,version):decision(context,version);
        if(result.site()==null)return Optional.empty();
        if((kind==RuinKind.CREEK||kind==RuinKind.FOREST)&&version>0&&RuinSitingSites.yield(context,result.site()).denied())return Optional.empty();
        return Optional.of(result.site().stub(context));
    }
    public record SiteResult(RuinDecision site,String reason) {}
    public SiteResult decision(GenerationContext context,int version){return RuinSitingSites.base(this,context,version);}
    SiteResult evaluate(GenerationContext context,int version) {
        return evaluateStages(context,version,false);
    }
    SiteResult evaluateOwner(GenerationContext context,int version){
        return evaluateStages(context,version,true);
    }
    private SiteResult evaluateStages(GenerationContext context,int version,boolean owner) {
        var trace=RuinSitingTrace.begin(kind,version,context.chunkPos().x,context.chunkPos().z);
        var layout = kind.layout(version);
        int reservation = version == 0 ? 56 : (int)Math.ceil(Math.hypot(layout.size().getX()-8,layout.size().getZ()-8));
        if((kind==RuinKind.FOREST||kind==RuinKind.CREEK)&&version>0)reservation+=12; // Maximum adaptive skirt, including its diagonal extent.
        if(GinsoStructure.reserved(context,reservation))return rejected(trace,"GINSO");
        int x = context.chunkPos().getMinBlockX(), z = context.chunkPos().getMinBlockZ();
        var rotation = Rotation.getRandom(context.random());
        var footprint = RuinPlacement.atMinimum(layout, new BlockPos(x, 0, z), rotation);
        var entryColumn = BlockPos.containing(footprint.point(layout.entry()));
        // Reject impossible biome columns before expensive noise sampling. Check every possible
        // final entry quart-height, so this cannot reject a start the vanilla biome check accepts.
        int entryDelta = (int)Math.floor(layout.entry().y) - layout.surfaceY();
        boolean possibleBiome = false;
        for (int y = QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE + entryDelta); y <= QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE + entryDelta); y++) {
            if (context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(entryColumn.getX()), y,
                    QuartPos.fromBlock(entryColumn.getZ()), context.randomState().sampler()))) { possibleBiome = true; break; }
        }
        if (!possibleBiome) return rejected(trace,"BIOME_COARSE");
        if(kind==RuinKind.CREEK&&version>0){
            String reason=creekReservations.rejection(context,footprint,layout);
            if(reason!=null)return rejected(trace,reason);
        }
        var samples = new TerrainSamples(context,(kind==RuinKind.CREEK||kind==RuinKind.FOREST)&&version>0,version>0,trace);
        int caveEntryLow=Integer.MAX_VALUE,caveApproachHigh=Integer.MIN_VALUE;
        // A new cave's five doorway columns have their own slope constraint.
        // Check that independent condition before its much larger roof footprint.
        // The old loop still merges these exact cached heights into the final min.
        if(kind==RuinKind.CAVE&&version>0){
            var door=RuinApproachPiece.doorway(kind,layout,footprint);
            int low=Integer.MAX_VALUE,high=Integer.MIN_VALUE;
            for(int dx=-2;dx<=2;dx++){
                int y=samples.height(door.block(new BlockPos(dx,0,-1)));
                low=Math.min(low,y);high=Math.max(high,y);
                if(!RuinSiteRules.footprintOk(low,high))return rejected(trace,"ENTRY_RELIEF");
            }
            if(!samples.dry())return rejected(trace,"WATER");
            caveEntryLow=low;
            // Any remaining footprint sample can only lower this doorway.
            // An approach already too high relative to this upper bound can
            // never pass the original MAX_APPROACH_DELTA test later.
            int highestDoor=RuinSiteRules.templateSurface(true,version,layout.surfaceY(),layout.size().getY(),low,high)
                    -layout.surfaceY()+layout.approachY();
            for(int dx=-2;dx<=2;dx++)for(int dz=-9;dz<=-1;dz++){
                int y=samples.height(door.block(new BlockPos(dx,0,dz)));
                caveApproachHigh=Math.max(caveApproachHigh,y);
                if(!RuinSiteRules.surfaceInRange(y)||y>highestDoor+RuinSiteRules.MAX_APPROACH_DELTA)return rejected(trace,"APPROACH");
            }
            if(!samples.dry())return rejected(trace,"WATER");
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        boolean creekV1=kind==RuinKind.CREEK&&version>0;
        boolean forestV1=kind==RuinKind.FOREST&&version>0;
        int forestAnchor=0;
        if(forestV1){
            var forestDoor=RuinApproachPiece.doorway(kind,layout,footprint);
            var result=ForestSiteRules.checkWithEntry((dx,dz)->samples.height(footprint.block(new BlockPos(dx,0,dz))),
                    dx->samples.height(forestDoor.block(new BlockPos(dx,0,-1))),
                    owner?()->RuinSitingSites.yieldBounds(context,RuinDecision.boxes(kind,version,footprint)).denied():null);
            if(!result.allowed())return rejected(trace,result.firstReason());
            min=result.coreMin();max=result.coreMax();forestAnchor=result.anchor();
        }else if(creekV1){
            var result=CreekSiteRules.check((dx,dz)->samples.height(footprint.block(new BlockPos(dx,0,dz))));
            if(!result.allowed())return rejected(trace,result.reason());
            // Anchor the deck to the playable core, not the highest outer hillside.
            min=result.coreMin();max=result.coreMax();
        }else{
        var footprintPoints = new LinkedHashSet<BlockPos>();
        int farX = layout.size().getX() - 1, farZ = layout.size().getZ() - 1;
        // Corners reject broad slopes early; retain every original four-block grid sample.
        for (int dx : new int[]{0, farX}) for (int dz : new int[]{0, farZ}) footprintPoints.add(footprint.block(new BlockPos(dx, 0, dz)));
        for (int sampleX = 0; sampleX <= (layout.size().getX() + 2) / 4; sampleX++)
            for (int sampleZ = 0; sampleZ <= (layout.size().getZ() + 2) / 4; sampleZ++)
                footprintPoints.add(footprint.block(new BlockPos(Math.min(sampleX * 4, farX), 0, Math.min(sampleZ * 4, farZ))));
        if(kind==RuinKind.CAVE&&version>0){
            // Keep the original point set and stable order within each group.
            // Boundary lows can prove a too-high approach before sampling the interior.
            var bounds=footprint.blocks(BlockPos.ZERO,new BlockPos(farX,0,farZ));
            var ordered=new java.util.ArrayList<>(footprintPoints);
            ordered.sort(java.util.Comparator.comparingInt((BlockPos p)->
                    p.getX()==bounds.minX()||p.getX()==bounds.maxX()||p.getZ()==bounds.minZ()||p.getZ()==bounds.maxZ()?0:1));
            footprintPoints=new LinkedHashSet<>(ordered);
        }
        for (var sample : footprintPoints) {
            int y = samples.height(sample);
            min = Math.min(min, y); max = Math.max(max, y);
            if (version == 0 || kind != RuinKind.CAVE ? !RuinSiteRules.footprintOk(min, max) : !RuinSiteRules.surfaceInRange(y)) return rejected(trace,"FOOTPRINT_RELIEF");
            if(kind==RuinKind.CAVE&&version>0){
                // Further roof samples can only lower the minimum and doorway.
                // Only the too-high direction is irreversible; a low approach may recover.
                int highestDoor=RuinSiteRules.templateSurface(true,version,layout.surfaceY(),layout.size().getY(),
                        Math.min(min,caveEntryLow),max)-layout.surfaceY()+layout.approachY();
                if(caveApproachHigh>highestDoor+RuinSiteRules.MAX_APPROACH_DELTA)return rejected(trace,"APPROACH");
            }
        }
        }
        // Include the ground immediately outside the doorway. Surface stairs cover
        // the entire accepted four-block drop; cave stairs descend from it to the buried doorway.
        var approach = RuinApproachPiece.doorway(kind, layout, footprint);
        int entryMin=Integer.MAX_VALUE,entryMax=Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            var sample = approach.block(new BlockPos(dx, 0, -1));
            int height = samples.height(sample);
            min = Math.min(min, height); max = Math.max(max, height);
            entryMin=Math.min(entryMin,height);entryMax=Math.max(entryMax,height);
            if (version == 0 || kind != RuinKind.CAVE ? !RuinSiteRules.footprintOk(min, max) : !RuinSiteRules.footprintOk(entryMin,entryMax)) return rejected(trace,"ENTRY_RELIEF");
        }
        // The cave altar's roof sits at its doorway level: bury it under the lowest sampled ground so no flat
        // deepslate plate shows; the approach stairs descend at most MAX_SLOPE + CAVE_COVER (< MAX_APPROACH_DELTA).
        int surface = RuinSiteRules.templateSurface(kind==RuinKind.CAVE,version,layout.surfaceY(),layout.size().getY(),min,max);
        if(creekV1)surface=(min+max+1)/2;
        if(forestV1)surface=forestAnchor;
        // A buried vault may sit below a hill: only the doorway needs a gentle slope.
        // Keep the highest template roof at least CAVE_COVER below the lowest terrain sample.
        var origin = footprint.origin().atY(surface - layout.surfaceY());
        var placement = new RuinPlacement(origin, rotation);
        var doorway = RuinApproachPiece.doorway(kind, layout, placement);
        int[] terrain = new int[RuinApproachPiece.WIDTH * RuinApproachPiece.DEPTH];
        for (int dx = -2; dx <= 2; dx++) for (int dz = -9; dz <= -1; dz++) {
            var sample = doorway.block(new BlockPos(dx, 0, dz));
            int height = samples.height(sample);
            if (!RuinSiteRules.approachOk(height, doorway.origin().getY())) return rejected(trace,"APPROACH");
            terrain[(dx + 2) * RuinApproachPiece.DEPTH + dz + 9] = height;
        }
        var entry = BlockPos.containing(placement.point(layout.entry()));
        if (!context.validBiome().test(context.chunkGenerator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(entry.getX()), QuartPos.fromBlock(entry.getY()),
                QuartPos.fromBlock(entry.getZ()), context.randomState().sampler()))) return rejected(trace,"BIOME_ENTRY");
        if (!samples.dry()) return rejected(trace,"WATER");
        if (!RuinSiteRules.fitsWorld(origin.getY(), layout.size().getY(),
                context.heightAccessor().getMinBuildHeight(), context.heightAccessor().getMaxBuildHeight())) return rejected(trace,"BUILD_HEIGHT");
        var site=new RuinDecision(kind,version,placement,java.util.Arrays.stream(terrain).boxed().toList(),samples.surfaces);
        if(forestV1){
            String reason=forestReservations.rejection(context,site);
            if(reason!=null)return rejected(trace,reason);
        }
        RuinSitingTrace.end(trace,"PASS");
        return new SiteResult(site,"PASS");
    }
    private static SiteResult rejected(RuinSitingTrace.Sample trace,String reason){
        RuinSitingTrace.end(trace,reason);return new SiteResult(null,reason);
    }
    private static final class TerrainSamples {
        private final RuinSitingTrace.Sample trace;
        private final GenerationContext context;
        private final boolean keepSurfaces;
        private final CreekSurfaceSample.Cells cells;
        private final LinkedHashMap<BlockPos, Integer> heights = new LinkedHashMap<>();
        private final LinkedHashMap<BlockPos, Integer> surfaces = new LinkedHashMap<>();
        private final LinkedHashMap<BlockPos, CreekSurfaceSample.Surface> partialColumns = new LinkedHashMap<>();
        TerrainSamples(GenerationContext context,boolean keepSurfaces,boolean exactCells,RuinSitingTrace.Sample trace) {
            this.trace=trace;this.context=context;this.keepSurfaces=keepSurfaces;
            cells=exactCells&&CreekSurfaceSample.supported(context.chunkGenerator())?new CreekSurfaceSample.Cells(context.chunkGenerator(),context.heightAccessor(),context.randomState()):null;
        }
        int height(BlockPos pos) {
            return heights.computeIfAbsent(pos.atY(0), key -> {
                if(trace!=null)trace.heightCalls++;
                if(cells!=null){
                    if(trace!=null)trace.partialColumnCalls++;
                    var sample=cells.at(key.getX(),key.getZ());
                    if(trace!=null)trace.noiseCells=cells.count();
                    partialColumns.put(key,sample);if(keepSurfaces)surfaces.put(key,sample.surface());return sample.floor();
                }
                return context.chunkGenerator().getBaseHeight(key.getX(), key.getZ(),Heightmap.Types.OCEAN_FLOOR_WG,context.heightAccessor(),context.randomState());
            });
        }
        boolean dry() {
            // New-layout vanilla-noise decisions retain the exact floor and dry result
            // from the height pass. Legacy and other generators keep their full-column contract.
            for (var entry : heights.entrySet()) {
                var pos = entry.getKey(); int y = entry.getValue();
                var cached=partialColumns.get(pos);
                if(cached!=null){if(!cached.dry())return false;continue;}
                if(trace!=null)trace.columnCalls++;
                var column = context.chunkGenerator().getBaseColumn(pos.getX(), pos.getZ(), context.heightAccessor(), context.randomState());
                if (!column.getBlock(y).getFluidState().isEmpty() || !column.getBlock(y - 1).getFluidState().isEmpty()
                        || column.getBlock(y - 1).isAir()) return false;
                // Reuse the actual noise column already required for siting.
                // This is WORLD_SURFACE_WG's predicate, not an assumption that
                // ocean-floor and world-surface heights must be equal.
                if(keepSurfaces){
                    int surface=context.heightAccessor().getMaxBuildHeight()-1;
                    while(surface>=context.heightAccessor().getMinBuildHeight()&&!Heightmap.Types.WORLD_SURFACE_WG.isOpaque().test(column.getBlock(surface)))surface--;
                    surfaces.put(pos,surface+1);
                }
            }
            return true;
        }
    }
    @Override public StructureType<?> type() { return OriWorldgen.RUIN.get(); }
}
