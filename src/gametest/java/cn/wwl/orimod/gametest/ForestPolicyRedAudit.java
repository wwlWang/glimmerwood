package cn.wwl.orimod.gametest;

import cn.wwl.orimod.world.*;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Real-noise production rejection, separate from the flat placement fixtures. */
final class ForestPolicyRedAudit {
    static void started(ServerStartedEvent event){
        var server=event.getServer();var l=server.overworld();var out=new LinkedHashMap<String,Object>();
        try{
            if(l.getSeed()!=116118L)throw new IllegalStateException("Wrong fixture seed");
            var audit=new ForestSitingAudit(l);var cp=new ChunkPos(-90,-118);var c=audit.context(RuinKind.FOREST,cp);
            var rotation=Rotation.getRandom(c.random());var footprint=RuinPlacement.atMinimum(RuinKind.FOREST.layout(1),cp.getWorldPosition(),rotation);
            var heights=new ForestSitingAudit.Heights(c);var rules=ForestSiteRules.inspect((x,z)->heights.at(footprint.block(new BlockPos(x,0,z))));
            if(!rules.allowed()||rules.footprintMax()-rules.footprintMin()<=4)throw new IllegalStateException("Slope witness no longer qualifies "+rules);
            var current=audit.structure(RuinKind.FOREST).decision(audit.context(RuinKind.FOREST,cp),1);
            out.put("seed",l.getSeed());out.put("chunk",List.of(cp.x,cp.z));out.put("rotation",rotation.name());out.put("policy",rules);out.put("productionReason",current.reason());out.put("test","forest_policy.selected_outer_eight_slope_is_accepted");
            if(current.site()==null)throw new AssertionError("Approved core="+(rules.coreMax()-rules.coreMin())+" outer="+(rules.footprintMax()-rules.footprintMin())+" site rejected by production: "+current.reason());
            out.put("status","passed");
        }catch(AssertionError e){out.put("status","failed_assertion");out.put("failure",e.getMessage());System.out.println("FOREST_POLICY_RED "+e.getMessage());}
        catch(Exception e){out.put("status","fixture_error");out.put("error",e.toString());e.printStackTrace();}
        finally{try{Files.writeString(ReportFiles.output(Path.of("forest-policy-red.json")),new GsonBuilder().setPrettyPrinting().create().toJson(out));}catch(Exception e){throw new IllegalStateException(e);}finally{CreekFixtureChunks.settle(l,"forest-policy-red-stop");server.halt(false);}}
    }
}
