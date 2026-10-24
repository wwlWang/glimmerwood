package cn.wwl.orimod.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TallGinsoTest {
    @Test void sixtyFourSeedsKeepScaleHollowShaftBrokenForksAndSupportedDrips(){
        for(int seed=0;seed<64;seed++){
            var s=new GinsoShape(seed,3);int top=0,wood=0,leaf=0,sap=0,fungus=0,lowX=99,highX=-99,lowZ=99,highZ=-99;
            double minU=99,maxU=-99,sumU=0;
            int centralTip=0,leaningTip=0,rootGaps=0,longestVine=0,onCrownSide=0,leafMinX=99,leafMaxX=-99,leafMinZ=99,leafMaxZ=-99;
            var random=new java.util.Random(seed);random.nextInt(11);double phase=random.nextDouble()*.4,angle=phase-.35;
            for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++)for(int y=-20;y<=124;y++){
                byte c=s.cell(x,y,z);
                if(Math.abs(x)==35||Math.abs(z)==35||y==124)assertEquals(0,c,"Clipped seed "+seed);
                if(c!=0&&c!=4){lowX=Math.min(lowX,x);highX=Math.max(highX,x);lowZ=Math.min(lowZ,z);highZ=Math.max(highZ,z);}
                if(GinsoShape.timber(c)){wood++;top=Math.max(top,y);if(y>s.height-7){if(Math.hypot(x,z)<11)centralTip++;else leaningTip++;}}
                if(c==2){leaf++;double u=x*Math.cos(angle)+z*Math.sin(angle);minU=Math.min(minU,u);maxU=Math.max(maxU,u);sumU+=u;if(u>0)onCrownSide++;
                    leafMinX=Math.min(leafMinX,x);leafMaxX=Math.max(leafMaxX,x);leafMinZ=Math.min(leafMinZ,z);leafMaxZ=Math.max(leafMaxZ,z);
                    assertTrue(y<=s.height-17,"Moss must not hide the three broken tips");}
                if(c==5)sap++;if(c==6)fungus++;
                if(c==3)assertTrue(s.cell(x,y+1,z)==3||s.cell(x,y+1,z)==2||GinsoShape.timber(s.cell(x,y+1,z)),"Unsupported drip");
                if(c==3&&s.cell(x,y-1,z)!=3){int length=1;while(s.cell(x,y+length,z)==3)length++;longestVine=Math.max(longestVine,length);}
            }
            assertTrue(top>=108&&top<=120,"Height="+top);assertTrue(highX-lowX+1<=70&&highZ-lowZ+1<=70);
            assertTrue(wood>15000&&leaf>3000&&leaf<12000,"Wood/leaf "+wood+"/"+leaf);
            assertTrue(onCrownSide>leaf*.8,"One-sided moss drapes");
            assertTrue(minU>=-10.5&&maxU>=18&&maxU<=25.5&&sumU/leaf>8,"Crown must lean with the main fork: "+minU+".."+maxU);
            assertTrue(Math.max(leafMaxX-leafMinX+1,leafMaxZ-leafMinZ+1)>=35,"Crown too small for tower");
            assertTrue(longestVine>=20&&longestVine<=25,"Long finite curtain: "+longestVine);
            assertTrue(sap>300&&sap<wood/8&&fungus>150,"Dim cracks and shelves");
            assertEquals(0,centralTip,"No flat central chimney");assertTrue(leaningTip>20);
            for(int y=1;y<=s.height;y++)for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)assertEquals(4,s.cell(x,y,z),"Hollow shaft");
            for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++)if(Math.hypot(x,z)>14&&clear(s.cell(x,1,z)))
                for(int y=3;y<=12;y++)if(GinsoShape.timber(s.cell(x,y,z))){rootGaps++;break;}
            assertTrue(rootGaps>100,"Stilt gaps="+rootGaps);
        }
    }
    @Test void opposingRootArchesHaveWalkThroughClearanceBelowTheirRoofs(){
        for(int seed=0;seed<64;seed++){
            var s=new GinsoShape(seed,3);var random=new java.util.Random(seed);random.nextInt(11);double phase=random.nextDouble()*.4;
            for(int i=0;i<8;i+=2){
                double a=i*Math.PI/4+.28+phase+.18;int cx=(int)Math.round(Math.cos(a)*16),cz=(int)Math.round(Math.sin(a)*16);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=1;y<=6;y++)
                    assertTrue(clear(s.cell(cx+dx,y,cz+dz)),"Closed root arch seed="+seed+" arch="+i);
                boolean roof=false;for(int y=7;y<=17;y++)roof|=GinsoShape.timber(s.cell(cx,y,cz));
                assertTrue(roof,"Opening must be beneath a root bridge, not beside it");
            }
        }
    }
    @Test void carvingRootArchesNeverDisconnectsTheirUnchangedFeet(){
        int[][] directions={{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        for(int seed=0;seed<64;seed++){
            var s=new GinsoShape(seed,3);boolean[] seen=new boolean[71*71*41];
            var pending=new java.util.ArrayDeque<int[]>();pending.add(new int[]{0,0,0});seen[rootKey(0,0,0)]=true;
            while(!pending.isEmpty()){
                var p=pending.remove();
                for(var d:directions){
                    int x=p[0]+d[0],y=p[1]+d[1],z=p[2]+d[2];
                    if(Math.abs(x)>35||Math.abs(z)>35||y< -20||y>20)continue;
                    int key=rootKey(x,y,z);
                    if(!seen[key]&&GinsoShape.timber(s.cell(x,y,z))){seen[key]=true;pending.add(new int[]{x,y,z});}
                }
            }
            for(var foot:GinsoSiteRules.rootFeet(seed)){
                boolean connected=false;
                for(int x=foot[0]-1;x<=foot[0]+1;x++)for(int z=foot[1]-1;z<=foot[1]+1;z++)for(int y=-5;y<=0;y++)
                    connected|=seen[rootKey(x,y,z)]&&GinsoShape.timber(s.cell(x,y,z));
                assertTrue(connected,"Detached root toe seed="+seed+" at "+java.util.Arrays.toString(foot));
            }
        }
    }
    private static int rootKey(int x,int y,int z){return ((x+35)*71+z+35)*41+y+20;}
    private static boolean clear(byte cell){return cell==GinsoShape.EMPTY||cell==GinsoShape.AIR;}
    @Test void feetKeepTheirShallowWaterProbeAndLandmarkClearanceInsideBounds(){
        for(int seed=0;seed<64;seed++)for(var foot:GinsoSiteRules.rootFeet(seed)){
            double r=Math.hypot(foot[0],foot[1]);assertTrue(r>=21&&r<=25);assertTrue(Math.abs(foot[0])<35&&Math.abs(foot[1])<35);
        }
        assertTrue(GinsoSiteRules.MIN_LANDMARK_DISTANCE-GreatTreeShape.radius(2)-GinsoShape.RADIUS>=180);
        assertEquals(71,GinsoPiece.bounds(net.minecraft.core.BlockPos.ZERO,3).getXSpan());
        assertEquals(145,GinsoPiece.bounds(net.minecraft.core.BlockPos.ZERO,3).getYSpan());
    }
    @Test void rootFeetAcceptSmoothSlopesButRejectDeepChannelsAndCliffs(){
        assertEquals(GinsoSiteRules.Reason.OK,GinsoSiteRules.rootSupport(70,58,58,56,60));
        assertEquals(GinsoSiteRules.Reason.OK,GinsoSiteRules.rootSupport(70,82,82,80,84));
        assertEquals(GinsoSiteRules.Reason.OK,GinsoSiteRules.rootSupport(70,68,70,67,71));
        assertEquals(GinsoSiteRules.Reason.ROOT_DEEP_WATER,GinsoSiteRules.rootSupport(70,67,70,66,70));
        assertEquals(GinsoSiteRules.Reason.ROOT_CLIFF,GinsoSiteRules.rootSupport(70,60,60,51,61));
        assertEquals(GinsoSiteRules.Reason.ROOT_CLIFF,GinsoSiteRules.rootSupport(70,53,53,52,54));
    }
    @Test void arbitrarySliceVisitOrderMatchesTheSingleField(){
        var a=new GinsoShape(115118,3);var b=new GinsoShape(115118,3);long full=0,sliced=0;
        for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++)for(int y=-20;y<=124;y++)full+=a.cell(x,y,z);
        for(int cx=3;cx>=-3;cx--)for(int cz=3;cz>=-3;cz--)
            for(int x=Math.max(-35,cx*16-8);x<=Math.min(35,cx*16+7);x++)for(int z=Math.max(-35,cz*16-8);z<=Math.min(35,cz*16+7);z++)
                for(int y=-20;y<=124;y++){assertEquals(a.cell(x,y,z),b.cell(x,y,z));sliced+=b.cell(x,y,z);}
        assertEquals(full,sliced);
    }
}
