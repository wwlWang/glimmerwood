package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FeatureSiteReservationsTest {
    @Test void retryPositionsKeepLegacyOrderAndStayWithinOriginChunk(){
        var at=new BlockPos(-98,68,-1634);var retries=FeatureSitePiece.candidates(at);
        assertEquals(List.of(at,new BlockPos(-100,68,-1636),new BlockPos(-108,68,-1636)),retries);
        for(var p:retries){assertEquals(at.getX()>>4,p.getX()>>4);assertEquals(at.getZ()>>4,p.getZ()>>4);}
    }
    @Test void conservativeJigsawEnvelopeIncludesFeatureRadiusAndHasExactBoundary(){
        var cp=new net.minecraft.world.level.ChunkPos(0,0);
        assertTrue(FeatureSiteReservations.jigsawEnvelope(cp,new BlockPos(44,70,4)));
        assertTrue(FeatureSiteReservations.jigsawEnvelope(cp,new BlockPos(109,70,4)));
        assertFalse(FeatureSiteReservations.jigsawEnvelope(cp,new BlockPos(110,70,4)));
        assertTrue(FeatureSiteReservations.jigsawEnvelope(cp,new BlockPos(-94,70,4)));
        assertFalse(FeatureSiteReservations.jigsawEnvelope(cp,new BlockPos(-95,70,4)));
    }
    @Test void potentialTreeChunkAloneDoesNotReserveWholeBlightNeighbourhood(){
        var tree=new FeatureSitePlan("OK",new BlockPos(0,70,0),Map.of());
        var far=new FeatureSitePlan("OK",new BlockPos(28,70,0),Map.of());
        var near=new FeatureSitePlan("OK",tree.base().offset(SpiritTreeShape.HEART),Map.of());
        assertFalse(FeatureSiteReservations.conflicts(far,tree));assertTrue(FeatureSiteReservations.conflicts(near,tree));
    }
}
