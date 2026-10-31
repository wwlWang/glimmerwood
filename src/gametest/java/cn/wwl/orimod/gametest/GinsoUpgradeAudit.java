package cn.wwl.orimod.gametest;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Opt-in real-world upgrade audit. This harness also compiles against the frozen 42a50ec sources. */
final class GinsoUpgradeAudit {
    private static final BlockPos OLD_TREE=new BlockPos(2536,75,2600);
    private static final BlockPos EDGE_CANDIDATE=new BlockPos(2088,70,3544);
    static void started(ServerStartedEvent event){
        var server=event.getServer();var level=server.overworld();
        boolean baseline=System.getProperty("orimod.worldgen.audit.mode").equals("ginso_old");
        var report=new LinkedHashMap<String,Object>();report.put("mode",baseline?"42a50ec-old":"upgraded");report.put("seed",level.getSeed());
        Path output=Path.of(baseline?"ginso-old.json":"ginso-upgrade.json");long started=System.nanoTime();
        try{
            level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
            var old=baseline?locate(level,OLD_TREE):existing(level,OLD_TREE);generate(level,old);
            report.put("oldTree",metadata(level,old));String hash=hash(level,old);report.put("oldVolumeSha256",hash);
            if(baseline){
                // A genuinely explored 64 x 80 strip. Its eastern edge is just 25 blocks
                // from a terrain-valid candidate on the NEW 52/20 grid, whose radius is 35.
                for(int x=125;x<=128;x++)for(int z=219;z<=223;z++)level.getChunk(x,z);
                report.put("exploredFullChunks",List.of(125,219,128,223));
                report.put("edgeCandidate",xyz(EDGE_CANDIDATE));
                report.put("oldVolumeSha256AfterExploration",hash(level,old));
            }else{
                var previous=com.google.gson.JsonParser.parseString(Files.readString(Path.of("ginso-old.json"))).getAsJsonObject();
                require(hash.equals(previous.get("oldVolumeSha256").getAsString()),"Old v2 volume changed on upgrade");
                require(tag(level,old).getInt("ShapeVersion")==2,"Old shape was upgraded");
                require(tag(level,old).getInt("CourseVersion")==1,"Old course was upgraded");
                // Invoke only the new guard through reflection, keeping this file buildable on HEAD.
                var guard=Class.forName("cn.wwl.orimod.world.GinsoGenerationGuard");
                Object result=guard.getMethod("check",net.minecraft.world.level.levelgen.RandomState.class,BlockPos.class,int.class)
                    .invoke(null,level.getChunkSource().randomState(),EDGE_CANDIDATE,3);
                report.put("edgeGuard",result.toString());require(!(boolean)result.getClass().getMethod("allowed").invoke(result),"Old edge footprint was accepted");
                // Walk generation out through the old border, rather than /place a template.
                for(int x=129;x<=133;x++)for(int z=219;z<=223;z++)level.getChunk(x,z);
                var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
                var edge=level.getChunk(EDGE_CANDIDATE.getX()>>4,EDGE_CANDIDATE.getZ()>>4).getStartForStructure(structure);
                require(edge==null||!edge.isValid(),"New tree overlaps the old explored strip");report.put("edgeHasNewStart",false);
                var rows=new ArrayList<Map<String,Object>>();report.put("newTrees",rows);
                for(var query:List.of(EDGE_CANDIDATE,new BlockPos(5048,71,4312),new BlockPos(1096,71,-1304))){
                    var tree=locate(level,query);generate(level,tree);var row=metadata(level,tree);row.put("query",xyz(query));
                    require(tag(level,tree).getInt("ShapeVersion")==3,"Locate did not find new v3 tree");
                    require(!tree.getBoundingBox().intersects(new net.minecraft.world.level.levelgen.structure.BoundingBox(2000,-64,3504,2063,319,3583)),
                        "Locate selected a new tree overlapping the old explored strip");
                    row.put("voxels",verify(level,tree));rows.add(row);
                }
                require(rows.stream().allMatch(row->((Number)((Map<?,?>)row.get("voxels")).get("missing")).intValue()==0),
                    "Incomplete new tree(s): "+rows);
                require(hash(level,old).equals(hash),"Old tree changed while exploring beyond border");
                report.put("oldVolumeSha256AfterExploration",hash(level,old));
            }
            report.put("status","passed");
        }catch(Throwable error){report.put("status","failed");report.put("error",error.toString());OriMod.LOGGER.error("Ginso upgrade audit failed",error);}
        finally{
            report.put("elapsedSeconds",(System.nanoTime()-started)/1e9);
            try{Files.writeString(ReportFiles.output(output),new GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception error){throw new RuntimeException(error);}
            server.halt(false);
        }
    }
    private static GinsoPiece locate(ServerLevel level,BlockPos at){
        var holder=level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(
            net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree")));
        var found=level.getChunkSource().getGenerator().findNearestMapStructure(level,HolderSet.direct(holder),at,100,false);
        require(found!=null,"Locate failed at "+at);var cp=new ChunkPos(found.getFirst());
        var start=level.getChunk(cp.x,cp.z,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(holder.value());
        require(start!=null&&start.isValid(),"Locate returned invalid start");
        return start.getPieces().stream().filter(GinsoPiece.class::isInstance).map(GinsoPiece.class::cast).findFirst().orElseThrow();
    }
    private static GinsoPiece existing(ServerLevel level,BlockPos at){
        var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ResourceLocation.fromNamespaceAndPath("orimod","ginso_tree"));
        var start=level.getChunk(at.getX()>>4,at.getZ()>>4,ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure);
        require(start!=null&&start.isValid(),"Saved old start disappeared at "+at);
        return start.getPieces().stream().filter(GinsoPiece.class::isInstance).map(GinsoPiece.class::cast).findFirst().orElseThrow();
    }
    private static void generate(ServerLevel level,GinsoPiece piece){
        var b=piece.getBoundingBox();for(int x=(b.minX()>>4)-1;x<=(b.maxX()>>4)+1;x++)
            for(int z=(b.minZ()>>4)-1;z<=(b.maxZ()>>4)+1;z++)level.getChunk(x,z);
    }
    private static net.minecraft.nbt.CompoundTag tag(ServerLevel l,GinsoPiece p){return p.createTag(StructurePieceSerializationContext.fromLevel(l));}
    private static LinkedHashMap<String,Object> metadata(ServerLevel l,GinsoPiece p){
        var r=new LinkedHashMap<String,Object>();var t=tag(l,p);r.put("origin",xyz(p.origin()));r.put("shapeVersion",t.getInt("ShapeVersion"));
        r.put("courseVersion",t.getInt("CourseVersion"));r.put("bounds",p.getBoundingBox().toString());r.put("seed",p.shapeSeed());return r;
    }
    private static String hash(ServerLevel level,GinsoPiece piece)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");var bytes=new HashMap<BlockState,byte[]>();var b=piece.getBoundingBox();
        for(var p:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()))
            digest.update(bytes.computeIfAbsent(level.getBlockState(p),s->(s+"\n").getBytes(StandardCharsets.UTF_8)));
        return HexFormat.of().formatHex(digest.digest());
    }
    private static Map<String,Object> verify(ServerLevel level,GinsoPiece piece)throws Exception{
        var shape=GinsoShape.class.getConstructor(long.class,int.class).newInstance(piece.shapeSeed(),3);
        var origin=piece.origin();var b=piece.getBoundingBox();int checked=0,missing=0;
        var byChunk=new TreeMap<String,Integer>();var examples=new ArrayList<String>();
        var laterVegetation=new TreeMap<String,Integer>();
        for(var at:BlockPos.betweenClosed(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())){
            int x=at.getX()-origin.getX(),y=at.getY()-origin.getY(),z=at.getZ()-origin.getZ();var local=piece.local(at);
            byte cell=shape.cell(x,y,z),inside=piece.course().cell(local.getX(),y,local.getZ());
            if(inside!=0)cell=inside;
            else if(piece.entrance()!=0&&Math.abs(x)<=2&&z>=-9&&z<=-6&&y>=1&&y<=5)cell=GinsoShape.WOOD;
            int floor=(int)Math.round(Math.clamp((-local.getZ()-8)/19.0,0,1)*piece.approach());
            if(Math.abs(local.getX())<=3&&local.getZ()>=-27&&local.getZ()<=-5&&y<=floor+6){
                if(y==floor)cell=GinsoShape.WOOD;else if(y>floor)cell=GinsoShape.AIR;else continue;
            }
            if(piece.courseVersion()==2&&inside!=0)cell=inside;
            if(cell==GinsoShape.EMPTY)continue;checked++;
            Block expected=switch(cell){
                case GinsoShape.WOOD,GinsoShape.SAP->OriBlocks.GINSO_WOOD.get();
                case GinsoShape.LEAF->piece.leafAt(x,y,z);case GinsoShape.VINE->OriBlocks.GINSO_VINE.get();
                case GinsoShape.FUNGUS->OriBlocks.GINSO_FUNGUS.get();case GinsoInterior.LIGHT->OriBlocks.GREAT_SPIRIT_ROOT.get();
                case GinsoInterior.LAMP->OriBlocks.BASH_ANCHOR.get();case GinsoInterior.FLOWER->OriBlocks.GRAPPLE_FLOWER.get();
                case GinsoInterior.WIND->OriBlocks.UPDRAFT.get();case GinsoInterior.WELL->OriBlocks.GLADES_WELL.get();
                case TallGinsoInterior.CUSHION->OriBlocks.GINSO_LANDING.get();case TallGinsoInterior.RAIL->Blocks.DARK_OAK_FENCE;
                case TallGinsoInterior.DOOR,TallGinsoInterior.DOOR_TOP->Blocks.DARK_OAK_DOOR;case TallGinsoInterior.WINDOW->Blocks.CYAN_STAINED_GLASS;
                case TallGinsoInterior.STEP->Blocks.DARK_OAK_SLAB;
                case GinsoInterior.STAIR,GinsoInterior.STAIR_SOUTH->Blocks.DARK_OAK_STAIRS;default->Blocks.AIR;
            };
            var actual=level.getBlockState(at);byChunk.merge(new ChunkPos(at).toString(),1,Integer::sum);
            // The structure precedes VEGETAL_DECORATION/TOP_LAYER_MODIFICATION: plants or one snow layer may
            // occupy its air afterward. Report those separately; every solid template
            // voxel must still match, and air may not become terrain, fluid or collision.
            if(expected==Blocks.AIR && (actual.is(Blocks.SHORT_GRASS)||actual.is(Blocks.TALL_GRASS)
                    ||actual.is(Blocks.FERN)||actual.is(Blocks.LARGE_FERN)||actual.is(Blocks.VINE)
                    ||actual.is(Blocks.BROWN_MUSHROOM)||actual.is(Blocks.RED_MUSHROOM)
                    ||actual.is(net.minecraft.tags.BlockTags.FLOWERS)
                    ||actual.is(Blocks.SNOW)&&actual.getValue(SnowLayerBlock.LAYERS)==1)
                    && actual.getCollisionShape(level,at).isEmpty() && actual.getFluidState().isEmpty()){
                laterVegetation.merge(actual.getBlock().toString(),1,Integer::sum);continue;
            }
            if(!actual.is(expected)){missing++;if(examples.size()<20)examples.add(at.toShortString()+" expected "+expected+" got "+actual);}
        }
        return Map.of("checked",checked,"missing",missing,"byChunk",byChunk,"laterVegetationInAir",laterVegetation,"mismatchExamples",examples);
    }
    private static List<Integer> xyz(BlockPos p){return List.of(p.getX(),p.getY(),p.getZ());}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
