package cn.wwl.orimod.world;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GladesOutpostTest {
    @ParameterizedTest @EnumSource(Rotation.class) void rotationKeepsContractIn31Square(Rotation rotation){var at=GladesOutpostStructure.placement(new BlockPos(-200,64,400),rotation);for(var p:List.of(BlockPos.ZERO,GladesOutpostPiece.SIZE.offset(-1,-1,-1),GladesOutpostPiece.ENTRY,GladesOutpostPiece.WELL,GladesOutpostPiece.MERCHANTS.getFirst(),GladesOutpostPiece.MERCHANTS.getLast())){var q=at.block(p);assertTrue(q.getX()>=-200 && q.getX()<=-170);assertTrue(q.getZ()>=400 && q.getZ()<=430);assertEquals(p,BlockPos.containing(at.local(net.minecraft.world.phys.Vec3.atCenterOf(q))));}}
    @Test void generatedLayoutMatchesCodeAndPlacementDensity()throws Exception{
        var layout=read("orimod/glades_outpost.json");assertEquals(List.of(31,18,31),ints(layout.getAsJsonArray("size")));assertEquals(List.of(15,3,0),ints(layout.getAsJsonArray("entry")));assertEquals(List.of(15,3,19),ints(layout.getAsJsonArray("well")));
        var p=read("data/orimod/worldgen/structure_set/glades_outpost.json").getAsJsonObject("placement");assertEquals(24,p.get("spacing").getAsInt());assertEquals(10,p.get("separation").getAsInt());
        var structure=read("data/orimod/worldgen/structure/glades_outpost.json");assertEquals("#orimod:has_glades_outpost",structure.get("biomes").getAsString());assertEquals("top_layer_modification",structure.get("step").getAsString());
    }
    @Test void terraceAcceptsOrdinarySlopesButRejectsCliffsAndUnderseaGround(){
        assertTrue(GladesOutpostStructure.terrainFits(65,75));assertFalse(GladesOutpostStructure.terrainFits(65,76));
        assertFalse(GladesOutpostStructure.terrainFits(62,66));assertFalse(GladesOutpostStructure.terrainFits(140,146));
        for(int min=63;min<=135;min++)for(int relief=0;relief<=10;relief++){
            int floor=GladesOutpostStructure.floorHeight(min,min+relief);
            assertTrue(floor-min<=5 && min+relief-floor<=5,"Terrace cuts or fills more than five sampled blocks");
        }
    }
    @Test void entranceStairsCoverSevenBlockDropInBothDirections(){
        for(int delta=-7;delta<=7;delta++){
            int previous=80+delta-1;
            for(int step=0;step<=7;step++){int next=RuinSiteRules.stepFloor(80+delta,80,step);assertTrue(Math.abs(next-previous)<=1);previous=next;}
            assertEquals(79,previous);assertTrue(RuinSiteRules.approachOk(80+delta,80));
        }
        assertFalse(RuinSiteRules.approachOk(88,80));assertFalse(RuinSiteRules.approachOk(72,80));
    }
    @Test void purchaseTranslationsAndEffectDescriptionsExistInBothLanguages()throws Exception{
        for(String locale:List.of("zh_cn","en_us")){
            var language=read("assets/orimod/lang/"+locale+".json");
            assertEquals(locale.equals("zh_cn")?"购买 · %s 灵光":"Buy · %s",language.get("merchant.orimod.buy").getAsString());
            assertNotEquals(language.get("merchant.orimod.daily"),language.get("merchant.orimod.cartography"));
            for(var shard:cn.wwl.orimod.progression.Shard.values())assertFalse(language.get("shard.orimod."+shard.id()+".description").getAsString().isBlank());
        }
    }
    @Test void emptyOldIndexAndDurableRoundTrip(){var old=FeatureSites.load(new CompoundTag(),RegistryAccess.EMPTY);assertEquals(0,old.count(FeatureSites.Kind.TREE));old.add(FeatureSites.Kind.TREE,new BlockPos(-1,70,200));old.add(FeatureSites.Kind.TREE,new BlockPos(-1,70,200));old.add(FeatureSites.Kind.BLIGHT,new BlockPos(20,64,-100));var tag=old.save(new CompoundTag(),RegistryAccess.EMPTY);var loaded=FeatureSites.load(tag,RegistryAccess.EMPTY);assertEquals(1,loaded.count(FeatureSites.Kind.TREE));assertEquals(1,loaded.count(FeatureSites.Kind.BLIGHT));assertEquals(tag,loaded.save(new CompoundTag(),RegistryAccess.EMPTY));}
    static List<Integer> ints(JsonArray array){var r=new ArrayList<Integer>();array.forEach(v->r.add(v.getAsInt()));return r;}
    static JsonObject read(String path)throws IOException{try(var input=GladesOutpostTest.class.getClassLoader().getResourceAsStream(path)){assertNotNull(input,path);return JsonParser.parseReader(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();}}
}
