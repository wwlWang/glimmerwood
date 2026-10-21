package cn.wwl.orimod.progression;
import java.util.*;

/** Prices and one immutable daily selection; ownership is sampled when this day's stock is first opened. */
public final class MerchantRules {
    public static final int RANGE=6;
    public static long day(long dayTime){return Math.floorDiv(dayTime,24000);}
    public static int price(String id){
        var shard=Shard.byId(id);
        if(shard!=null)return switch(shard){
            case GATHERING->80;case CAPACITY,RECOVERY,RESILIENCE->100;case AIR_CONTROL,LIFE_HARVEST,ENERGY_HARVEST->120;
            case THORN,STICKY->140;case ULTRA_BASH->160;case TRIPLE_JUMP->200;
        };
        return switch(id){case "ruin"->40;case "tree"->60;case "blight"->50;case "shrine"->55;default->-1;};
    }
    /** Progression fallback when no accessible outpost exists; merchant remains one third cheaper. */
    public static int panelPrice(Shard shard){return (price(shard.id())*3+1)/2;}
    public static List<String> stock(long seed,int owned){
        var values=new ArrayList<>(Arrays.stream(Shard.values()).filter(s->(s.bit&owned)==0).toList());Collections.shuffle(values,new Random(seed));
        return values.stream().limit(3).map(Shard::id).toList();
    }
    public static String refusal(boolean valid,double distance,long quoteDay,long day,boolean current,int balance,String product,int owned){
        if(!valid || !Double.isFinite(distance) || distance>RANGE*RANGE)return "distance";
        if(quoteDay!=day || !current || price(product)<0)return "stale";
        var shard=Shard.byId(product);if(shard!=null && (shard.bit&owned)!=0)return "owned";
        return balance<price(product)?"funds":"";
    }
    private MerchantRules(){}
}
