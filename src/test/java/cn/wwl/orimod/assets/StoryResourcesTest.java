package cn.wwl.orimod.assets;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StoryResourcesTest {
    private final Path root=Path.of(System.getProperty("orimod.projectDir","."),"src/main/resources");
    private JsonObject read(String path)throws Exception{return JsonParser.parseString(Files.readString(root.resolve(path))).getAsJsonObject();}
    @Test void bothLanguagesCoverTheSameKeysAndParameters()throws Exception{
        var zh=read("assets/orimod/lang/zh_cn.json");var en=read("assets/orimod/lang/en_us.json");assertEquals(zh.keySet(),en.keySet());
        for(var key:zh.keySet())assertEquals(zh.get(key).getAsString().chars().filter(c->c=='%').count(),en.get(key).getAsString().chars().filter(c->c=='%').count(),key);
    }
    @Test void storyParentsDescriptionsAndEventCriteriaExist()throws Exception{
        var lang=read("assets/orimod/lang/en_us.json");var paths=new ArrayList<Path>();
        try(var files=Files.list(root.resolve("data/orimod/advancement/story"))){files.forEach(paths::add);}
        paths.add(root.resolve("data/orimod/advancement/restoration.json"));assertEquals(12,paths.size());
        for(var path:paths){var node=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var display=node.getAsJsonObject("display");for(var field:List.of("title","description"))assertTrue(lang.has(display.getAsJsonObject(field).get("translate").getAsString()),path.toString());
            if(node.has("parent"))assertTrue(Files.isRegularFile(root.resolve("data/orimod/advancement/"+node.get("parent").getAsString().substring(7)+".json")),path.toString());
            for(var entry:node.getAsJsonObject("criteria").entrySet())assertNotEquals("minecraft:tick",entry.getValue().getAsJsonObject().get("trigger").getAsString());
        }
    }
}
