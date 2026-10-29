package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.*;

/** All structure writes since server construction, not a coordinate/type exception list.
 * Test source only. Natural scans opt in by audit mode; performance modes never record.
 * Aggregation retains unchanged attempts and a stack for each distinct call path. */
public final class ForestStructureEvidence {
    public static final boolean ENABLED="forest_natural".equals(System.getProperty("orimod.worldgen.audit.mode"));
    private record Actor(String key,String start,StructurePiece piece) {}
    private static final ThreadLocal<Deque<Actor>> ACTORS=ThreadLocal.withInitial(ArrayDeque::new);
    private record Key(String actor,long position,String owner,int stack,String before,String requested,String after) {}
    private static final ConcurrentMap<Key,Map<String,Object>> WRITES=new ConcurrentHashMap<>();
    private static final ConcurrentMap<List<String>,Integer> STACKS=new ConcurrentHashMap<>();
    private static final ConcurrentMap<BlockState,String> STATE_TEXT=new ConcurrentHashMap<>();
    private static String text(BlockState s){return STATE_TEXT.computeIfAbsent(s,BlockState::toString);}
    private static final AtomicLong SEQUENCE=new AtomicLong();
    private static final java.util.concurrent.atomic.AtomicInteger STACK_ID=new java.util.concurrent.atomic.AtomicInteger();
    private static volatile boolean hooked;
    private static final ThreadLocal<Deque<Long>> WRITE_POSITIONS=ThreadLocal.withInitial(ArrayDeque::new);
    private static final Queue<Map<String,Object>> COVERAGE_ERRORS=new ConcurrentLinkedQueue<>();
    private static final ConcurrentMap<net.minecraft.world.level.chunk.LevelChunkSection,BlockPos> SECTION_ORIGINS=new com.google.common.collect.MapMaker().weakKeys().makeMap();
    public static void enterWrite(BlockPos p){WRITE_POSITIONS.get().push(p.asLong());}
    public static void exitWrite(){WRITE_POSITIONS.get().pop();}
    public static boolean nestedWrite(BlockPos p){return WRITE_POSITIONS.get().contains(p.asLong());}
    public static void unavailable(BlockPos p,String reason){if(recording())COVERAGE_ERRORS.add(Map.of("actor",ACTORS.get().peek().key,"position",p==null?List.of():List.of(p.getX(),p.getY(),p.getZ()),"reason",reason));}
    public static void registerSections(ChunkPos pos,int minSection,net.minecraft.world.level.chunk.LevelChunkSection[] sections){if(!ENABLED)return;
        for(int i=0;i<sections.length;i++)SECTION_ORIGINS.put(sections[i],new BlockPos(pos.getMinBlockX(),(minSection+i)<<4,pos.getMinBlockZ()));}
    public static BlockPos sectionPosition(net.minecraft.world.level.chunk.LevelChunkSection section,int x,int y,int z){
        var origin=SECTION_ORIGINS.get(section);if(origin==null){unavailable(null,"Unmapped structure section write");return null;}return origin.offset(x,y,z);
    }
    public static String startKey(WorldGenLevel level,StructureStart start){return level.getLevel().dimension().location()+"/"+level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure())+"@"+start.getChunkPos().x+","+start.getChunkPos().z;}
    public static void begin(WorldGenLevel level,StructureStart start,StructurePiece piece){if(!ENABLED)return;hooked=true;
        var key=startKey(level,start);int index=piece==null?-1:start.getPieces().indexOf(piece);
        ACTORS.get().push(new Actor(key+"/"+index,key,piece));}
    public static void end(){if(ENABLED)ACTORS.get().pop();}
    public static String currentActorKey(){return recording()?ACTORS.get().peek().key:null;}
    public static boolean recording(){return ENABLED&&!ACTORS.get().isEmpty();}
    public static void write(BlockPos pos,BlockState before,BlockState requested,BlockState after){if(!recording())return;
        var actor=ACTORS.get().peek();long seq=SEQUENCE.incrementAndGet();
        var stack=Arrays.stream(Thread.currentThread().getStackTrace()).map(Object::toString).toList();
        int id=STACKS.computeIfAbsent(stack,k->STACK_ID.getAndIncrement());String owner="foreign_structure";
        if(actor.piece instanceof RuinApproachPiece||actor.piece instanceof RuinPiece){
            if(stack.stream().anyMatch(v->v.contains("ForestApproachGuards.place")||v.contains("RuinTerrainKernel.writeGuard")||v.contains("RuinTerrainKernel.edgeGuard")))owner="guard";
            else if(requested.is(Blocks.LIGHT))owner="lighting";
            else if(actor.piece instanceof RuinApproachPiece)owner="approach";
            else if(stack.stream().anyMatch(v->v.contains("RuinPiece.clearForestSupplements")))owner="extra_clearance";
            else if(stack.stream().anyMatch(v->v.contains("RuinTerrainKernel")))owner="rim";
            else owner="main_template";
        }
        var key=new Key(actor.key,pos.asLong(),owner,id,text(before),text(requested),text(after));final String source=owner;
        WRITES.compute(key,(k,old)->{
            var row=old==null?new LinkedHashMap<String,Object>():new LinkedHashMap<>(old);
            if(old==null){row.put("piece",actor.key);row.put("start",actor.start);row.put("position",List.of(pos.getX(),pos.getY(),pos.getZ()));
                row.put("owner",source);row.put("stackId",id);row.put("sequence",seq);row.put("before",text(before));row.put("attempts",0L);row.put("unchangedAttempts",0L);}
            row.put("sequence",Math.min(((Number)row.get("sequence")).longValue(),seq));row.put("lastSequence",Math.max(((Number)row.getOrDefault("lastSequence",seq)).longValue(),seq));row.put("requested",text(requested));row.put("after",text(after));
            row.put("attempts",((Number)row.get("attempts")).longValue()+1);
            if(before==after)row.put("unchangedAttempts",((Number)row.get("unchangedAttempts")).longValue()+1);
            return Collections.unmodifiableMap(row);
        });
    }
    private static ForestStructureConflictProof.Box box(BoundingBox b){return new ForestStructureConflictProof.Box(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
    static Map<String,Object> inspect(ServerLevel level,StructureStart ownStart,RuinPiece main,List<RuinApproachPiece> approaches,int index){
        var ownBoxes=new ArrayList<ForestStructureConflictProof.Box>();ownBoxes.add(box(main.getBoundingBox()));for(var p:approaches)ownBoxes.add(box(p.getBoundingBox()));
        var references=new TreeMap<String,List<String>>();var starts=new TreeMap<String,List<ForestStructureConflictProof.Piece>>();var ownKeys=new TreeSet<String>();
        String ownStartKey=level.dimension().location()+"/"+level.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(ownStart.getStructure())+"@"+ownStart.getChunkPos().x+","+ownStart.getChunkPos().z;
        for(int i=0;i<ownStart.getPieces().size();i++){var p=ownStart.getPieces().get(i);if(p==main||approaches.contains(p))ownKeys.add(ownStartKey+"/"+i);}
        var featureProofs=new TreeMap<String,ForestStructureConflictProof.Yielded>();
        var featureKeys=new TreeSet<String>();
        var archaeology=new LinkedHashMap<BlockPos,Map<String,Object>>();var registry=level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for(var key:ForestStructureConflictProof.chunks(ownBoxes)){
            var xy=key.split(",");int x=Integer.parseInt(xy[0]),z=Integer.parseInt(xy[1]);
            var chunk=level.getChunk(x,z,ChunkStatus.STRUCTURE_REFERENCES,false);if(chunk==null)continue;
            var refs=new TreeSet<String>();
            for(var entry:chunk.getAllReferences().entrySet())for(long packed:entry.getValue()){
                var cp=new ChunkPos(packed);var id=level.dimension().location()+"/"+registry.getKey(entry.getKey())+"@"+cp.x+","+cp.z;refs.add(id);if(starts.containsKey(id))continue;
                // Resolve via the referenced start chunk, never assume it has reached FULL.
                var startChunk=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS,false);if(startChunk==null)continue;
                var start=level.structureManager().getStartForStructure(SectionPos.of(cp,level.getMinSection()),entry.getKey(),startChunk);
                if(start==null||!start.isValid())continue;var pieces=new ArrayList<ForestStructureConflictProof.Piece>();
                for(int i=0;i<start.getPieces().size();i++){
                    var p=start.getPieces().get(i);String pk=id+"/"+i;pieces.add(new ForestStructureConflictProof.Piece(pk,id,box(p.getBoundingBox())));
                    if(start.getStructure() instanceof FeatureSiteStructure&&p instanceof FeatureSitePiece feature){
                        featureKeys.add(pk);var proof=ForestFeatureSiteEvidence.proof(pk,start.getStructure().getClass().getName(),feature);if(proof!=null)featureProofs.put(pk,proof);
                    }
                }starts.put(id,List.copyOf(pieces));
            }references.put(key,List.copyOf(refs));
        }
        var all=WRITES.values().stream().filter(w->w.get("start").toString().startsWith(level.dimension().location()+"/")).filter(w->ownKeys.contains(w.get("piece"))||featureKeys.contains(w.get("piece"))||ownBoxes.stream().anyMatch(b->b.contains(ForestStructureConflictProof.position(w)))).toList();
        var errors=COVERAGE_ERRORS.stream().filter(e->ownKeys.contains(e.get("actor"))||featureKeys.contains(e.get("actor"))||((List<?>)e.get("position")).isEmpty()||ownBoxes.stream().anyMatch(b->b.contains(ForestStructureConflictProof.position(e)))).toList();
        var input=new ForestStructureConflictProof.Input(ownBoxes,ownKeys,references,starts,all,ENABLED&&hooked&&errors.isEmpty(),ENABLED&&hooked&&errors.isEmpty(),featureProofs);
        var result=ForestStructureConflictProof.inspect(input);result.put("coverageErrors",errors);
        for(var pieces:starts.values())for(var p:pieces)if(!ownKeys.contains(p.key()))for(var b:ownBoxes)if(b.intersects(p.box())){
            var f=p.box();for(var at:BlockPos.betweenClosed(Math.max(b.minX(),f.minX()),Math.max(b.minY(),f.minY()),Math.max(b.minZ(),f.minZ()),Math.min(b.maxX(),f.maxX()),Math.min(b.maxY(),f.maxY()),Math.min(b.maxZ(),f.maxZ()))){
                var s=level.getBlockState(at);if(s.is(Blocks.SUSPICIOUS_GRAVEL)||s.is(Blocks.SUSPICIOUS_SAND)||s.is(Blocks.DECORATED_POT)){
                    var be=level.getBlockEntity(at);archaeology.put(at.immutable(),Map.of("position",List.of(at.getX(),at.getY(),at.getZ()),"state",s.toString(),"be",be==null?"":be.saveWithFullMetadata(level.registryAccess()).toString()));}
            }
        }
        var stacks=new TreeMap<Integer,List<String>>();STACKS.forEach((s,id)->stacks.put(id,s));
        try{Files.writeString(Path.of("forest-structures-"+index+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("input",input,"stacks",stacks,"archaeology",archaeology.values(),"result",result)));}
        catch(Exception e){throw new IllegalStateException(e);}
        result.put("evidenceFile","forest-structures-"+index+".json");result.put("archaeology",List.copyOf(archaeology.values()));return result;
    }
}
