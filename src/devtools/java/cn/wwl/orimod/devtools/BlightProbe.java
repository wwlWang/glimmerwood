package cn.wwl.orimod.devtools;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Read-only diagnosis of the real initial footprint's immediate outer ring. No chunk acquisition. */
public final class BlightProbe {
    /** Read-only boundary diagnosis: the same protection call as growth, plus loaded structure boxes. */
    public static Map<String,Object> boundary(ServerLevel level,BlockPos center){
        var journal=BlightData.get(level).ecology(center);
        if(journal==null)return Map.of("error","no ecology journal");
        var ring=new LinkedHashMap<Long,BlockPos>();
        for(var floor:journal.columns())for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
            var at=floor.relative(d);if(!journal.contains(at))ring.putIfAbsent(BlightRegion.column(at),at);
        }
        var rows=new ArrayList<Map<String,Object>>();var boxes=new LinkedHashMap<String,Object>();
        var counts=new TreeMap<String,Integer>();
        for(var at:ring.values()){
            if(!loaded(level,at))continue;
            BlockPos floor=null;for(int dy=4;dy>=-4;dy--){var p=at.offset(0,dy,0);if(BlightProtection.soil(level.getBlockState(p))){floor=p;break;}}
            if(floor==null)continue;
            BlightProtection.Check check=null;
            for(int i=0;i<32;i++){check=BlightProtection.inspect(level,floor,center,new BlightEcologyRules.Budget());if(check.result()!=BlightProtection.Result.DEFER)break;}
            var row=new LinkedHashMap<String,Object>();row.put("floor",point(floor));row.put("result",check.result());row.put("reason",check.reason());row.put("rememberedProtected",journal.protectedAt(floor));
            row.put("exposed",BlightConversion.exposed(level.getBlockState(floor.above())));
            counts.merge(check.result()+"/"+check.reason(),1,Integer::sum);
            var starts=new LinkedHashSet<net.minecraft.world.level.levelgen.structure.StructureStart>();var missing=new LinkedHashSet<String>();
            for(int cx=(floor.getX()-2)>>4;cx<=(floor.getX()+2)>>4;cx++)for(int cz=(floor.getZ()-2)>>4;cz<=(floor.getZ()+2)>>4;cz++){
                var chunk=level.getChunkSource().getChunkNow(cx,cz);if(chunk==null){missing.add(cx+","+cz);continue;}
                starts.addAll(chunk.getAllStarts().values());
                for(var refs:chunk.getAllReferences().entrySet())for(long packed:refs.getValue()){
                    var origin=level.getChunkSource().getChunkNow(net.minecraft.world.level.ChunkPos.getX(packed),net.minecraft.world.level.ChunkPos.getZ(packed));
                    if(origin==null){missing.add("start:"+net.minecraft.world.level.ChunkPos.getX(packed)+","+net.minecraft.world.level.ChunkPos.getZ(packed));continue;}
                    var start=origin.getStartForStructure(refs.getKey());if(start!=null)starts.add(start);
                }
            }
            var hits=new ArrayList<String>();
            for(var start:starts){if(!start.isValid())continue;
                var id=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(start.getStructure());
                String key=id+"@"+start.getChunkPos();var bb=start.getBoundingBox();
                if(BlightProtection.protects(bb,floor))hits.add(key);
                if(!boxes.containsKey(key)){var pieces=new ArrayList<Map<String,Object>>();for(var piece:start.getPieces())pieces.add(Map.of("type",piece.getClass().getSimpleName(),"box",box(piece.getBoundingBox())));
                    boxes.put(key,Map.of("structure",String.valueOf(id),"box",box(bb),"pieces",pieces));}
            }
            row.put("matchingStartBoxes",hits);row.put("missingChunks",missing);rows.add(row);
        }
        var site=GreatTreeSites.site(level.getChunkSource().randomState());
        return Map.of("center",point(center),"columns",journal.columns().stream().map(BlightProbe::point).toList(),"ring",rows,"counts",counts,"structureBoxes",boxes,"greatTreeSite",String.valueOf(site));
    }
    private static List<Integer> point(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private static List<Integer> box(net.minecraft.world.level.levelgen.structure.BoundingBox b){return List.of(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ());}
    public static Map<String,Object> scan(ServerLevel level,BlockPos center){
        if(level.getChunkSource().getChunkNow(center.getX()>>4,center.getZ()>>4)==null)return Map.of("error","core chunk unloaded");
        if(!(level.getBlockEntity(center) instanceof GloomCoreBlockEntity core))return Map.of("error","no core");
        var floors=new LinkedHashMap<Long,BlockPos>();
        for(long packed:core.saveWithoutMetadata(level.registryAccess()).getLongArray("terrain")){
            var p=BlockPos.of(packed);if(!loaded(level,p))continue;var s=level.getBlockState(p);
            if(s.is(OriBlocks.BLIGHTED_SOIL)||s.is(OriBlocks.GLOOM_WATER))floors.merge(BlightRegion.column(p),p,(a,b)->a.getY()>b.getY()?a:b);
        }
        var ring=new LinkedHashMap<Long,BlockPos>();
        for(var floor:floors.values())for(var d:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
            var p=floor.relative(d);long key=BlightRegion.column(p);if(!floors.containsKey(key))ring.putIfAbsent(key,p);
        }
        var counts=new TreeMap<String,Integer>();var examples=new ArrayList<Map<String,Object>>();
        for(var p:ring.values()){
            if(!loaded(level,p)){counts.merge("UNLOADED",1,Integer::sum);continue;}
            BlockPos floor=null;for(int y=4;y>=-4;y--){var candidate=p.offset(0,y,0);if(BlightProtection.soil(level.getBlockState(candidate))){floor=candidate;break;}}
            if(floor==null){counts.merge("NO_NATURAL_SOIL",1,Integer::sum);continue;}
            if(!BlightConversion.exposed(level.getBlockState(floor.above()))){counts.merge("NOT_EXPOSED",1,Integer::sum);continue;}
            BlightProtection.Check check=null;
            for(int attempt=0;attempt<32;attempt++){
                check=BlightProtection.inspect(level,floor,center,new BlightEcologyRules.Budget());
                if(check.result()!=BlightProtection.Result.DEFER)break;
            }
            String reason=check.result()==BlightProtection.Result.ALLOW?"ALLOW":check.result()==BlightProtection.Result.DEFER?"DEFER":check.reason().name();counts.merge(reason,1,Integer::sum);
            if(!reason.equals("ALLOW")&&examples.size()<12){var e=new LinkedHashMap<String,Object>();e.put("floor",List.of(floor.getX(),floor.getY(),floor.getZ()));e.put("reason",reason);
                if(reason.equals("ARTIFICIAL"))e.put("blocks",artificial(level,floor,center));examples.add(e);}
        }
        return Map.of("center",List.of(center.getX(),center.getY(),center.getZ()),"initialColumns",floors.size(),"ringColumns",ring.size(),"counts",counts,"examples",examples,"ecology",BlightData.get(level).status(net.minecraft.world.phys.Vec3.atCenterOf(center)),"budget",BlightEcology.metrics());
    }
    public static Map<String,Object> animals(ServerLevel level,BlockPos center){
        var animals=level.getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,new net.minecraft.world.phys.AABB(center).inflate(40),cn.wwl.orimod.entity.CorruptedAnimals::supported);
        var rows=new ArrayList<Map<String,Object>>();for(var a:animals){var state=a.getData(cn.wwl.orimod.entity.CorruptedAnimals.STATE);
            rows.add(Map.of("type",BuiltInRegistries.ENTITY_TYPE.getKey(a.getType()).toString(),"id",a.getId(),"uuid",a.getUUID().toString(),"name",a.getName().getString(),"pos",List.of(a.getX(),a.getY(),a.getZ()),"infected",state.infected(),"exposure",state.exposure(),"age",a.getAge(),"noAI",a.isNoAi()));}
        var r=BlightData.get(level).ecology(center);var region=new LinkedHashMap<String,Object>();
        if(r!=null){region.put("columns",r.columns().size());region.put("newColumns",r.grownColumns());region.put("loadedTicks",r.loadedTicks());region.put("radius",r.radius());region.put("nearFloors",r.columns().stream().filter(p->p.distSqr(center)<64).map(p->List.of(p.getX(),p.getY(),p.getZ())).toList());region.put("leaves",r.changes().stream().filter(c->c.expected().is(OriBlocks.BLIGHT_LEAVES)).count());}
        if(r!=null){region.put("protectedColumns",r.protectedColumns().size());region.put("outerFloors",r.columns().stream().sorted(Comparator.comparingDouble((BlockPos p)->-p.distSqr(center))).limit(32).map(p->List.of(p.getX(),p.getY(),p.getZ())).toList());}
        var result=new LinkedHashMap<String,Object>();result.put("serverTick",level.getServer().getTickCount());result.put("gameTime",level.getGameTime());result.put("dayTime",level.getDayTime());result.put("raining",level.isRaining());
        if(r!=null){
            int initialSnow=0,grownSnow=0,initialSoil=0;var covers=new TreeMap<String,Integer>();var snowy=new ArrayList<Map<String,Object>>();
            for(int i=0;i<r.columns().size();i++){
                var floor=r.columnAt(i);if(!loaded(level,floor)||!level.getBlockState(floor).is(OriBlocks.BLIGHTED_SOIL))continue;
                boolean initial=i<r.initialColumns();if(initial)initialSoil++;
                var cover=level.getBlockState(floor.above());if(initial)covers.merge(BuiltInRegistries.BLOCK.getKey(cover.getBlock()).toString(),1,Integer::sum);
                if(cover.is(net.minecraft.world.level.block.Blocks.SNOW)){
                    if(initial)initialSnow++;else grownSnow++;
                    if(snowy.size()<8){var check=BlightProtection.inspect(level,floor,center,new BlightEcologyRules.Budget());snowy.add(Map.of("floor",point(floor),"initial",initial,"cover",cover.toString(),"protected",r.protectedAt(floor),"inspect",check.toString()));}
                }
            }
            result.put("snow",Map.of("initialColumns",r.initialColumns(),"initialSoil",initialSoil,"initialSnow",initialSnow,"grownSnow",grownSnow,"initialCovers",covers,"examples",snowy,"plantFloor",String.valueOf(r.plantFloor())));
        }
        result.put("animals",rows);result.put("region",region);result.put("status",BlightData.get(level).status(net.minecraft.world.phys.Vec3.atCenterOf(center)));
        result.put("actorsBudget",cn.wwl.orimod.entity.CorruptedAnimals.metrics());result.put("terrainBudget",BlightEcology.metrics());result.put("atmosphereNetwork",BlightAtmosphereSync.metrics());
        var surfaces=new ArrayList<List<Integer>>();for(int x=-40;x<=40;x+=8)for(int z=-40;z<=40;z+=8){var pos=center.offset(x,0,z);var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk!=null)surfaces.add(List.of(pos.getX(),chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX()&15,pos.getZ()&15),pos.getZ()));}
        result.put("nearbySurfaceHeights",surfaces);return result;
    }
    private static Set<String> artificial(ServerLevel l,BlockPos floor,BlockPos core){
        var result=new TreeSet<String>();for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)for(int y=-2;y<=6;y++){
            var p=floor.offset(x,y,z);if(!loaded(l,p))continue;var s=l.getBlockState(p);
            if(!(p.equals(core)&&s.is(OriBlocks.GLOOM_CORE))&&BlightProtection.artificial(s))result.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        }return result;
    }
    private static boolean loaded(ServerLevel l,BlockPos p){return l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;}
    private BlightProbe(){}
}
