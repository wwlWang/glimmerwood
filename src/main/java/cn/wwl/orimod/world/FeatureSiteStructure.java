package cn.wwl.orimod.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * R114: spirit trees and blighted grounds were biome-modifier features, so {@code /locate structure} could never find
 * them. They are now structure starts (one per random_spread cell) whose single piece runs the unchanged feature in its
 * origin chunk. The start owns a complete noise-column plan; placement only adapts later natural decoration.
 */
public final class FeatureSiteStructure extends Structure {
    public enum Site {
        // Mirrors each feature's own terrain rule: tree = 5x5 relief <= 3; blight = soil disc up to r13, every column
        // within 3 of the centre and relief <= 6 (an r6 prefilter left phantom blight starts on forest slopes).
        SPIRIT_TREE(OriWorldgen.SPIRIT_TREE::get, 2, SpiritTreeFeature.MAX_RELIEF, SpiritTreeFeature.MAX_RELIEF),
        BLIGHTED_GROUNDS(OriWorldgen.BLIGHTED_GROUNDS::get, 12, 6, 3);
        /** Both features keep every write within this horizontal radius of their origin. */
        public static final int RADIUS = 14;
        private final Supplier<? extends Feature<NoneFeatureConfiguration>> feature;
        /** Radius of the noise sample disc, largest relief across it and largest deviation from the centre column. */
        public final int sampleRadius, maxRelief, maxDeviation;
        Site(Supplier<? extends Feature<NoneFeatureConfiguration>> feature, int sampleRadius, int maxRelief, int maxDeviation) {
            this.feature = feature; this.sampleRadius = sampleRadius; this.maxRelief = maxRelief; this.maxDeviation = maxDeviation;
        }
        public String id() { return name().toLowerCase(Locale.ROOT); }
        /** The feature's own preflight verdict at {@code at}, without writing (for retries and the decline log). */
        public String declineReason(net.minecraft.world.level.WorldGenLevel level, net.minecraft.core.BlockPos at, long seed) {
            if (this == SPIRIT_TREE) {
                var check = SpiritTreeFeature.preflight(level, at);
                return check.reason().name() + (check.rejectedAt() == null ? "" : " at " + check.rejectedAt().toShortString());
            }
            var check = BlightedGroundsFeature.preflight(level, at, seed);
            return check.reason().name() + (check.detail().isEmpty() ? "" : " (" + check.detail() + ")");
        }
        public Feature<NoneFeatureConfiguration> feature() { return feature.get(); }
        public static Site byId(String id) {
            for (var site : values()) if (site.id().equals(id)) return site;
            throw new IllegalArgumentException("Unknown feature site " + id);
        }
    }

    public static final MapCodec<FeatureSiteStructure> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            settingsCodec(instance), Codec.STRING.fieldOf("site").forGetter(value -> value.site.id())
    ).apply(instance, (settings, site) -> new FeatureSiteStructure(settings, Site.byId(site))));
    private final FeatureSiteReservations reservations = new FeatureSiteReservations();
    public final Site site;
    public FeatureSiteStructure(StructureSettings settings, Site site) { super(settings); this.site = site; }

    record Accepted(FeatureSitePlan plan,long seed) {}
    private record ChoiceKey(long chunk,GreatTreeSite.Site greatTree) {}
    private final java.util.Map<net.minecraft.world.level.levelgen.RandomState,RuinDecisionCache<ChoiceKey,Optional<Accepted>>> choices=
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    @Override protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        long started=System.nanoTime();var accepted=choose(context);
        if(Boolean.getBoolean("orimod.worldgen.site.profile"))cn.wwl.orimod.OriMod.LOGGER.info(
                "FEATURE_PREFLIGHT seed={} kind={} chunk={},{} accepted={} ms={}",context.seed(),site.id(),context.chunkPos().x,context.chunkPos().z,accepted.isPresent(),(System.nanoTime()-started)/1e6);
        return accepted.map(a->new GenerationStub(a.plan.base(),builder->builder.addPiece(new FeatureSitePiece(site,a.plan.base(),a.seed,a.plan))));
    }
    Optional<Accepted> choose(GenerationContext context) {
        RuinDecisionCache<ChoiceKey,Optional<Accepted>> cache;
        synchronized(choices){cache=choices.computeIfAbsent(context.randomState(),key->new RuinDecisionCache<>(256));}
        return cache.get(new ChoiceKey(context.chunkPos().toLong(),GreatTreeSites.site(context.randomState())),()->compute(context));
    }
    private Optional<Accepted> compute(GenerationContext context) {
        if(GreatTreeSites.reserved(context.randomState(),context.chunkPos().getMiddleBlockX(),context.chunkPos().getMiddleBlockZ(),Site.RADIUS+8))return Optional.empty();
        if(GinsoStructure.reserved(context,22))return Optional.empty();
        var random=context.random();
        var initial=new BlockPos(context.chunkPos().getMinBlockX()+random.nextInt(16),0,context.chunkPos().getMinBlockZ()+random.nextInt(16));
        var terrain=new FeatureTerrain.Noise(context.chunkGenerator(),context.randomState(),context.heightAccessor(),initial);
        // Preserve the legacy same-chunk retry positions. One column cache serves all three attempts.
        for(var candidate:FeatureSitePiece.candidates(initial)){
            long seed=random.nextLong();int x=candidate.getX(),z=candidate.getZ();
            boolean possibleBiome=false;
            for(int qy=QuartPos.fromBlock(RuinSiteRules.MIN_SURFACE);qy<=QuartPos.fromBlock(RuinSiteRules.MAX_SURFACE);qy++)
                if(context.validBiome().test(context.biomeSource().getNoiseBiome(x>>2,qy,z>>2,context.randomState().sampler()))){possibleBiome=true;break;}
            if(!possibleBiome)continue;
            String quick=reservations.quickRejection(context,candidate);
            if(quick!=null){
                if(Boolean.getBoolean("orimod.worldgen.site.profile"))cn.wwl.orimod.OriMod.LOGGER.info("FEATURE_CHEAP_REJECT seed={} kind={} chunk={},{} at={} reason={}",context.seed(),site.id(),context.chunkPos().x,context.chunkPos().z,candidate.toShortString(),quick);
                continue;
            }
            int y=terrain.getHeight(Heightmap.Types.WORLD_SURFACE_WG,x,z);
            if(!RuinSiteRules.surfaceInRange(y)||y<context.chunkGenerator().getSeaLevel()+2)continue;
            if(!context.validBiome().test(context.biomeSource().getNoiseBiome(x>>2,y>>2,z>>2,context.randomState().sampler())))continue;
            if(!coarse(terrain,new BlockPos(x,y,z),seed))continue;
            long started=System.nanoTime();
            var plan=FeatureSitePlan.check(site,terrain,new BlockPos(x,y,z),seed);long checked=System.nanoTime();
            String refusal=plan.allowed()?reservations.rejection(context,plan,site):plan.reason();
            if(Boolean.getBoolean("orimod.worldgen.site.profile"))cn.wwl.orimod.OriMod.LOGGER.info(
                    "FEATURE_DETAIL kind={} chunk={},{} at={} terrainMs={} reservationMs={} reason={}",site.id(),context.chunkPos().x,context.chunkPos().z,candidate.toShortString(),(checked-started)/1e6,(System.nanoTime()-checked)/1e6,refusal);
            if(plan.allowed()&&refusal==null)return Optional.of(new Accepted(plan,seed));
        }
        return Optional.empty();
    }

    /** A subset of the final predicates, not a new placement rule. Reject steep/wet sites before crown/disc reads. */
    private boolean coarse(FeatureTerrain terrain,BlockPos origin,long seed){
        var points=new java.util.LinkedHashSet<BlockPos>();points.add(BlockPos.ZERO);
        if(site==Site.SPIRIT_TREE){
            for(int x:new int[]{-2,2})for(int z:new int[]{-2,2})points.add(new BlockPos(x,0,z));
        }else{
            var soil=BlightedGroundsShape.plan(net.minecraft.util.RandomSource.create(seed).nextLong()).soil();
            points.add(soil.stream().min(java.util.Comparator.comparingInt(BlockPos::getX)).orElseThrow());
            points.add(soil.stream().max(java.util.Comparator.comparingInt(BlockPos::getX)).orElseThrow());
            points.add(soil.stream().min(java.util.Comparator.comparingInt(BlockPos::getZ)).orElseThrow());
            points.add(soil.stream().max(java.util.Comparator.comparingInt(BlockPos::getZ)).orElseThrow());
        }
        int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
        for(var point:points){
            var at=origin.offset(point);int surface=terrain.getHeight(Heightmap.Types.WORLD_SURFACE_WG,at.getX(),at.getZ());
            if(surface!=terrain.getHeight(Heightmap.Types.OCEAN_FLOOR_WG,at.getX(),at.getZ()))return false;
            min=Math.min(min,surface);max=Math.max(max,surface);
            if(max-min>(site==Site.SPIRIT_TREE?SpiritTreeFeature.MAX_RELIEF:6))return false;
        }
        return true;
    }

    @Override public StructureType<?> type() { return OriWorldgen.FEATURE_SITE.get(); }
}
