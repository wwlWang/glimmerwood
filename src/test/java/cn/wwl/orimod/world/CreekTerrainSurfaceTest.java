package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import com.google.gson.JsonParser;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CreekTerrainSurfaceTest {
    @Test void collisionDropsRoundUpToHalfBlocksWithoutHidingSlabEdges(){
        assertEquals(0,CreekTerrainSurface.dropHalfUnits(10,10));
        assertEquals(1,CreekTerrainSurface.dropHalfUnits(10,9.5));
        assertEquals(2,CreekTerrainSurface.dropHalfUnits(10,9));
        assertEquals(3,CreekTerrainSurface.dropHalfUnits(10,8.875));
        assertEquals(3,CreekTerrainSurface.dropHalfUnits(10,8.5));
        assertEquals(0,CreekTerrainSurface.dropHalfUnits(9,10));
    }
    private static Set<String> tag(String name) throws Exception {
        var path=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/data/orimod/tags/block/"+name+".json");
        var values=new HashSet<String>();
        JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("values").forEach(v->values.add(v.getAsString()));
        return values;
    }
    @Test void ignoredGrowthHasSharedSoftAndProtectedSubclasses() throws Exception {
        var protectedGrowth=tag("terrain_scan_protected");var ignored=tag("terrain_scan_ignore");
        assertTrue(ignored.contains("#orimod:terrain_scan_protected"));
        for(var id:List.of("#minecraft:logs","minecraft:mushroom_stem","minecraft:brown_mushroom_block","minecraft:red_mushroom_block", "minecraft:mangrove_roots","minecraft:muddy_mangrove_roots","minecraft:bee_nest","minecraft:bamboo","minecraft:cactus","minecraft:pumpkin","minecraft:melon"))assertTrue(protectedGrowth.contains(id),id);
        for(var id:List.of("minecraft:brown_mushroom","minecraft:red_mushroom","#minecraft:leaves","#minecraft:flowers","minecraft:snow","minecraft:moss_carpet","minecraft:pointed_dripstone"))assertFalse(protectedGrowth.contains(id),id);
    }
    @Test void mountainInfestedRockIsNaturalButStrongholdMasonryIsNot() throws Exception {
        var natural=tag("terrain_grading_natural");
        for(var id:List.of("infested_stone","infested_deepslate","bone_block","raw_iron_block","raw_copper_block"))
            assertTrue(natural.contains("minecraft:"+id),id);
        for(var id:List.of("infested_cobblestone","infested_stone_bricks","infested_mossy_stone_bricks"))
            assertFalse(natural.contains("minecraft:"+id),id);
    }
    @Test void beeNestAndCollidingPlantsAreIgnoredButStillKeepBlockEntityProtection() throws Exception {
        var ignored=tag("terrain_scan_ignore");
        for(var id:List.of("bee_nest","bamboo","big_dripleaf","big_dripleaf_stem","moss_carpet","lily_pad","sea_pickle","cocoa","chorus_plant","chorus_flower","pitcher_crop","pitcher_plant","small_dripleaf","pink_petals"))
            assertTrue(ignored.contains("minecraft:"+id),id);
        assertFalse(ignored.contains("#minecraft:climbable"),"Do not inherit scaffolding/ladder exemptions");
        assertFalse(ignored.contains("minecraft:scaffolding"));
        assertFalse(CreekTerrainSurface.mutable(.3f,true),"Bee nest remains immutable despite its scan category");
    }
    @Test void everyRegisteredOriBlockHasAnExplicitTerrainClassification() throws Exception {
        var path=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/data/orimod/tags/block/terrain_scan_ignore.json");
        assertTrue(Files.exists(path),"Missing shared terrain-scan exclusion tag");
        var ignored=new HashSet<String>();
        JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("values").forEach(v->ignored.add(v.getAsString()));
        var exceptions=Map.of("orimod:blighted_soil","Natural solid soil replacing the blight site's ground disc, not a decoration or structure core");
        var field=OriBlocks.class.getDeclaredField("BLOCKS");field.setAccessible(true);
        var register=(DeferredRegister<?>)field.get(null);var registered=new HashSet<String>();
        for(var holder:register.getEntries()){
            String id=holder.getId().toString();registered.add(id);
            assertTrue(ignored.contains(id)||exceptions.containsKey(id),"Unclassified OriBlocks entry: "+id);
            assertFalse(ignored.contains(id)&&exceptions.containsKey(id),"Ambiguous terrain classification: "+id);
        }
        assertTrue(registered.size()>35,"Did not inspect the complete block register");
        for(var id:ignored)if(id.startsWith("orimod:"))assertTrue(registered.contains(id),"Stale terrain-scan tag entry: "+id);
        assertTrue(registered.containsAll(exceptions.keySet()));
        for(var name:List.of("logs","leaves","flowers","saplings","replaceable"))
            assertTrue(ignored.contains("#minecraft:"+name),"Missing vanilla exclusion family: "+name);
        for(var name:List.of("#minecraft:walls","#minecraft:fences","#minecraft:fence_gates","minecraft:mossy_cobblestone_wall"))
            assertFalse(ignored.contains(name),"Foreign walls must not share our coordinate-owned exemption: "+name);
    }
    @Test void naturalTagIncludesDeepAndSoftTerrainButNotCraftedBuildings() throws Exception {
        var path=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/data/orimod/tags/block/terrain_grading_natural.json");
        var natural=new HashSet<String>();
        JsonParser.parseString(Files.readString(path)).getAsJsonObject().getAsJsonArray("values").forEach(v->natural.add(v.getAsString()));
        for(var id:List.of("#minecraft:base_stone_overworld","#minecraft:dirt","#minecraft:sand","minecraft:powder_snow","#minecraft:terracotta",
                "minecraft:bedrock","minecraft:calcite","minecraft:amethyst_block","minecraft:gravel","minecraft:smooth_basalt"))assertTrue(natural.contains(id),id);
        for(var id:List.of("minecraft:birch_planks","minecraft:cobblestone_wall","minecraft:mossy_cobblestone_wall"))assertFalse(natural.contains(id),id);
    }
    @Test void immutableBlocksAreNeverCutRegardlessOfNaturalClassification(){
        assertFalse(CreekTerrainSurface.mutable(-1,false),"Bedrock/barrier");
        assertFalse(CreekTerrainSurface.mutable(1,true),"Natural BE is still protected");
        assertTrue(CreekTerrainSurface.mutable(1,false));
    }
    @Test void guardOwnershipIsPerPiecePerCoordinateAndSurvivesReload(){
        var a=new CreekTerrainGuards();var foreign=new CreekTerrainGuards();
        a.plan(42);a.plan(42);
        assertFalse(a.contains(43));assertFalse(foreign.contains(42));
        var restored=new CreekTerrainGuards();restored.restore(a.save());
        assertArrayEquals(new long[]{42},restored.save());
    }
}
