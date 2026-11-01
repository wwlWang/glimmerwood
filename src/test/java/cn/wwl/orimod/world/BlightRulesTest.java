package cn.wwl.orimod.world;
import cn.wwl.orimod.world.BlightRules.Source;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class BlightRulesTest {
    @ParameterizedTest @EnumSource(Source.class) void damageMatrix(Source source){
        var expected=Map.of(Source.CHARGE_FLAME,12,Source.SMASH,10,Source.LIGHT_BURST,8,Source.SPEAR,6,Source.FLASH,4,Source.ARROW,4,Source.BLADE,3);
        assertEquals(expected.getOrDefault(source,0),BlightRules.damage(source));
    }
    @ParameterizedTest @EnumSource(Source.class) void thornsRequireAnExplicitPlayerAttack(Source source){
        var allowed=EnumSet.of(Source.BLADE,Source.ARROW,Source.CHARGE_FLAME,Source.LIGHT_BURST,Source.SMASH,Source.SPEAR,Source.STOMP);
        assertEquals(allowed.contains(source),BlightRules.clearsThorns(source));
    }
    @Test void exactHealthHasMonotonicVisualCracks(){
        assertEquals(0,BlightRules.crack(40));assertEquals(0,BlightRules.crack(34));assertEquals(1,BlightRules.crack(32));
        for(int hp=0;hp<=40;hp++)assertTrue(BlightRules.crack(hp)>=0 && BlightRules.crack(hp)<=4);
        assertEquals(4,BlightRules.crack(1));
    }
    @Test void radialDeadlinesCoverThirtySeconds(){
        assertEquals(1,BlightRules.due(0,13));assertEquals(300,BlightRules.due(6.5,13));assertEquals(600,BlightRules.due(13,13));
        int last=0;for(int i=0;i<=130;i++){int due=BlightRules.due(i*.1,13);assertTrue(due>=last);last=due;}
    }
    @Test void sanctuaryIsInclusiveSphereAndDefendersAreBounded(){
        assertTrue(BlightRules.protects(1024));assertFalse(BlightRules.protects(1024.01));
        assertTrue(BlightRules.defenderReady(100,100,2));assertFalse(BlightRules.defenderReady(99,100,0));assertFalse(BlightRules.defenderReady(100,100,3));
    }
    @Test void sixtyFourSeedsHaveContentBoundsAndAnApproach(){
        Set<Integer> shapes=new HashSet<>(),radii=new HashSet<>();int minSoil=999,maxSoil=0;
        for(int seed=0;seed<64;seed++){
            var p=BlightedGroundsShape.plan(seed);assertEquals(p,BlightedGroundsShape.plan(seed));shapes.add(p.hashCode());radii.add(p.radius());
            assertTrue(p.radius()>=10 && p.radius()<=13);assertTrue(p.pools().size()>=3 && p.pools().size()<=9);
            assertTrue(p.thorns().size()>=8 && p.thorns().size()<=20);assertTrue(p.trees().size()>=2 && p.trees().size()<=4);
            assertTrue(p.trees().values().stream().allMatch(h->h>=3 && h<=6));
            for(var cell:p.soil())assertTrue(Math.abs(cell.getX())<=14 && Math.abs(cell.getZ())<=14);
            assertTrue(p.soil().containsAll(p.pools()));assertTrue(p.soil().containsAll(p.thorns()));
            // A four-neighbor path from the north perimeter reaches the core without entering hazards or trunks.
            var start=p.soil().stream().filter(c->c.getX()==0).min(Comparator.comparingInt(BlockPos::getZ)).orElseThrow();
            var seen=new HashSet<BlockPos>();var q=new ArrayDeque<BlockPos>();q.add(start);seen.add(start);
            while(!q.isEmpty()){var at=q.remove();for(var dir:List.of(Direction.NORTH,Direction.SOUTH,Direction.EAST,Direction.WEST)){
                var next=at.relative(dir);if(p.soil().contains(next) && !p.pools().contains(next) && !p.thorns().contains(next) && !p.trees().containsKey(next) && seen.add(next))q.add(next);
            }}
            assertTrue(seen.contains(BlockPos.ZERO),"core inaccessible, seed="+seed);
            minSoil=Math.min(minSoil,p.soil().size());maxSoil=Math.max(maxSoil,p.soil().size());
        }
        assertEquals(64,shapes.size());assertEquals(Set.of(10,11,12,13),radii);System.out.println("R106 64 seeds: soil="+minSoil+".."+maxSoil+", radius=10..13, pools=1..3, thorns=8..20, trees=2..4 (height 3..6)");
    }
    @Test void completedSaveIsCompactAndCannotBeRegisteredAgain(){
        var tag=new CompoundTag();tag.putLongArray("completed",new long[]{BlockPos.ZERO.asLong()});
        var data=BlightData.load(tag,null);data.register(BlockPos.ZERO,new long[]{new BlockPos(1,0,0).asLong()});
        var saved=data.save(new CompoundTag(),null);assertTrue(saved.getList("regions",Tag.TAG_COMPOUND).isEmpty());
        assertEquals(Set.of("regions","completed","pending"),saved.getAllKeys());
        assertTrue(data.protects(new Vec3(32.5,.5,.5)));assertFalse(data.protects(new Vec3(32.51,.5,.5)));
    }
    @Test void sanctuaryIndexSearchesTheWholeNewRadiusAcrossChunkEdges(){
        // At these edges, retaining the old 24-block index window misses centres in the outer chunk.
        for(var center:List.of(new BlockPos(15,64,15),new BlockPos(-16,64,-16))){
            var tag=new CompoundTag();tag.putLongArray("completed",new long[]{center.asLong()});
            var data=BlightData.load(tag,null);var at=Vec3.atCenterOf(center);
            for(var dir:Direction.values()){
                var axis=new Vec3(dir.getStepX(),dir.getStepY(),dir.getStepZ());
                assertTrue(data.protects(at.add(axis.scale(32))),"Missing inclusive edge "+center+" "+dir);
                assertFalse(data.protects(at.add(axis.scale(32.01))),"Outside sanctuary "+center+" "+dir);
            }
        }
    }
    @Test void regionCursorsAndEffectiveTimeRoundTripWithoutRewinding(){
        var data=new BlightData();var center=new BlockPos(15,64,15);
        data.register(center,new long[]{center.below().asLong(),center.offset(4,0,0).asLong()});
        var tag=data.save(new CompoundTag(),null);var region=tag.getList("regions",Tag.TAG_COMPOUND).getCompound(0);
        region.putBoolean("purifying",true);region.putInt("elapsed",303);region.getList("chunks",Tag.TAG_COMPOUND).getCompound(0).putInt("cursor",1);
        var reloaded=BlightData.load(tag,null);assertEquals(tag,reloaded.save(new CompoundTag(),null));
        assertEquals(303,reloaded.status(Vec3.atCenterOf(center)).get("elapsed"));assertEquals(1,reloaded.status(Vec3.atCenterOf(center)).get("processed"));
    }
    @Test void malformedRecipientListsAreCappedAndCompletedOverridesOldActiveEntry(){
        var data=new BlightData();data.register(BlockPos.ZERO,new long[]{BlockPos.ZERO.below().asLong()});var tag=data.save(new CompoundTag(),null);
        var people=new ListTag();for(int i=0;i<25;i++)people.add(NbtUtils.createUUID(UUID.randomUUID()));
        tag.getList("regions",Tag.TAG_COMPOUND).getCompound(0).put("participants",people);
        var loaded=BlightData.load(tag,null);assertEquals(16,loaded.status(Vec3.ZERO).get("participants"));
        tag.putLongArray("completed",new long[]{0});assertTrue(BlightData.load(tag,null).save(new CompoundTag(),null).getList("regions",Tag.TAG_COMPOUND).isEmpty());
    }
    @Test void worldgenUsesEarlyStageAndAgreedRarity() throws Exception{
        var root=java.nio.file.Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources/data/orimod");
        // R114: a structure start (so /locate finds it); the old 1/160 chunk rarity becomes a 13-chunk spread.
        var structure=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("worldgen/structure/blighted_grounds.json"))).getAsJsonObject();
        assertEquals("local_modifications",structure.get("step").getAsString());assertEquals("blighted_grounds",structure.get("site").getAsString());
        var placement=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("worldgen/structure_set/blighted_grounds.json"))).getAsJsonObject().getAsJsonObject("placement");
        assertEquals(13,placement.get("spacing").getAsInt());
        assertFalse(java.nio.file.Files.exists(root.resolve("neoforge/biome_modifier/blighted_grounds.json")));
    }
    @Test void poolsOnlyWhereNoNeighbourIsLower(){
        assertTrue(BlightRules.poolContained(118,118));
        assertTrue(BlightRules.poolContained(118,121));
        assertFalse(BlightRules.poolContained(118,117),"a lower neighbour would let gloom water flow out");
    }
}
