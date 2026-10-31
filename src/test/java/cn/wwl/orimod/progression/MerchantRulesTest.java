package cn.wwl.orimod.progression;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MerchantRulesTest {
    @ParameterizedTest @EnumSource(Shard.class) void pricesAndExactPayment(Shard shard){
        int price=MerchantRules.price(shard.id());assertTrue(price>=80 && price<=200);
        assertEquals("",MerchantRules.refusal(true,36,1,1,true,price,shard.id(),0));
        assertEquals("funds",MerchantRules.refusal(true,36,1,1,true,price-1,shard.id(),0));
        assertEquals("owned",MerchantRules.refusal(true,0,1,1,true,1000,shard.id(),shard.bit));
    }
    @ParameterizedTest @EnumSource(Shard.class) void panelFallbackCostsExactlyOneAndAHalfTimes(Shard shard){
        assertEquals(MerchantRules.price(shard.id())*3,MerchantRules.panelPrice(shard)*2);
    }
    @Test void pricesMatchStrengthTable(){
        assertEquals(List.of(100,100,80,100,120,200,120,120,160,140,140,160,100,120,140,140,120),Arrays.stream(Shard.values()).map(s->MerchantRules.price(s.id())).toList());
        assertEquals(-1,MerchantRules.price("ruin"));assertEquals(60,MerchantRules.price("tree"));assertEquals(50,MerchantRules.price("blight"));assertEquals(-1,MerchantRules.price("unknown"));
    }
    @Test void stockIsStableUniqueAndUnowned(){for(int owned=0;owned<=Shard.MASK;owned++){
        var stock=MerchantRules.stock(107,owned);assertEquals(stock,MerchantRules.stock(107,owned));assertEquals(stock.size(),new HashSet<>(stock).size());assertEquals(Math.min(3,Shard.values().length-Integer.bitCount(owned)),stock.size());
        for(var id:stock)assertEquals(0,owned&Shard.byId(id).bit);
    }}
    @Test void daysChangeSelectionWithoutRandomWallClock(){assertNotEquals(MerchantRules.stock(107,0),MerchantRules.stock(108,0));assertEquals(0,MerchantRules.day(23999));assertEquals(1,MerchantRules.day(24000));assertEquals(-1,MerchantRules.day(-1));}
    @Test void forgedOrStaleOffersCannotPay(){
        assertEquals("stale",MerchantRules.refusal(true,0,0,1,true,500,"tree",0));
        assertEquals("stale",MerchantRules.refusal(true,0,1,1,false,500,"tree",0));
        assertEquals("stale",MerchantRules.refusal(true,0,1,1,true,500,"unknown",0));
        for(double d:new double[]{36.01,Double.NaN,Double.POSITIVE_INFINITY})assertEquals("distance",MerchantRules.refusal(true,d,0,0,true,500,"tree",0));
        assertEquals("distance",MerchantRules.refusal(false,0,0,0,true,500,"tree",0));
    }
}
