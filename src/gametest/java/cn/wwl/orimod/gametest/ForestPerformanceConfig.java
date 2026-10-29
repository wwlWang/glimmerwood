package cn.wwl.orimod.gametest;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Test-only startup assertion, outside all measurement intervals. */
final class ForestPerformanceConfig {
    static Map<String,Object> verify(String mode){
        try{
            var allowed=switch(mode){
                case "warm"->Set.of("RuinOuterTimerMixin");
                case "system"->Set.of("ForestNoiseThreadMixin","ForestNoiseCellThreadMixin");
                case "breakdown"->Set.of("ForestNoiseThreadMixin","ForestNoiseCellThreadMixin",
                        "ForestStartTerrainTimingMixin","ForestStartExternalTimingMixin","ForestStartR2TimingMixin");
                default->throw new IllegalArgumentException("Unknown measurement mode "+mode);
            };
            var loader=ForestPerformanceConfig.class.getClassLoader();var resource=loader.getResource("orimod_gametest.mixins.json");
            if(resource==null)throw new IllegalStateException("Missing active test mixin configuration");
            byte[] bytes;try(var input=resource.openStream()){bytes=input.readAllBytes();}
            var config=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();var actual=new TreeSet<String>();
            config.getAsJsonArray("mixins").forEach(e->actual.add(e.getAsString()));
            if(!actual.equals(allowed))throw new IllegalStateException("Measurement observer mixins present: "+actual);
            var flags=new TreeMap<String,Boolean>();
            for(String flag:List.of("orimod.forestLavaEvidence","orimod.forestWaterEvidence","orimod.profileRuins","orimod.profileRuinSiting","orimod.creekGuardEvidence","orimod.creekForeignTerrainTrace")){
                boolean active=Boolean.getBoolean(flag);flags.put(flag,active);if(active)throw new IllegalStateException("Measurement observation flag active "+flag);}
            if(ForestLavaEvidence.ENABLED||ForestWaterEvidence.ENABLED)throw new IllegalStateException("Fluid observer active during timing");
            return Map.of("mode",mode,"sourceSet","gametest; isolated resources","configuration",resource.toString(),"configuredMixins",actual,
                    "sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),"diagnosticFlags",flags,"fluidObserverMixinsConfigured",false);
        }catch(Exception e){throw new IllegalStateException("Invalid performance isolation",e);}
    }
    private ForestPerformanceConfig(){}
}
