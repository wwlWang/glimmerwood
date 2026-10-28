package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RuinDecisionCacheTest {
    @Test void concurrentRequestComputesOnceAndPublishesCompletedValue()throws Exception{
        var cache=new RuinDecisionCache<String,String>(2);var calls=new AtomicInteger();
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(8)){
            var futures=new ArrayList<Future<String>>();
            for(int i=0;i<8;i++)futures.add(pool.submit(()->cache.get("same",()->{
                calls.incrementAndGet();entered.countDown();
                try{if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("Release timed out");}
                catch(InterruptedException e){throw new RuntimeException(e);}return "complete";
            })));
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(1,calls.get());release.countDown();
            for(var f:futures)assertEquals("complete",f.get(5,TimeUnit.SECONDS));
            assertEquals(1,calls.get());assertEquals(1,cache.size());
        }
    }
    @Test void boundedEvictionRecomputesWithoutChangingResults(){
        var cache=new RuinDecisionCache<Integer,Integer>(2);var calls=new AtomicInteger();
        for(int round=0;round<3;round++)for(int i=0;i<9;i++){int key=i;assertEquals(i*i,cache.get(i,()->{calls.incrementAndGet();return key*key;}));assertTrue(cache.size()<=2);}
        assertEquals(27,calls.get());
    }
    @Test void rememberedImmutableResultIsReusedByTheOwner(){
        var cache=new RuinDecisionCache<Integer,String>(2);
        assertNull(cache.completed(3));cache.remember(3,"decision");
        assertEquals("decision",cache.get(3,()->{throw new AssertionError("Owner recomputed cached decision");}));
        cache.remember(3,"replacement");assertEquals("decision",cache.completed(3));
    }
    @Test void failuresAreNotPermanentlyCached(){
        var cache=new RuinDecisionCache<Integer,String>(2);
        assertThrows(CompletionException.class,()->cache.get(1,()->{throw new IllegalStateException("expected");}));
        assertEquals("retry",cache.get(1,()->"retry"));
    }
    @Test void saturatedInFlightCacheDoesNotDeadlockNestedDifferentKey(){
        var cache=new RuinDecisionCache<Integer,String>(1);
        assertEquals("inner",cache.get(1,()->cache.get(2,()->"inner")));assertEquals(1,cache.size());
    }
}
