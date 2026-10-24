package cn.wwl.orimod.world;

import java.util.Random;
import static cn.wwl.orimod.world.GinsoShape.*;

/** v3: tall twisted wet cypress, three broken forks and an asymmetric hanging crown. */
final class TallGinsoShape {
    private static final double[] DRAPE_U={-.42,-.13,.23,.5,.05},DRAPE_V={-.35,.35,-.1,.2,-.6};
    private final byte[] cells=new byte[WIDTH*WIDTH*(MAX_Y-MIN_Y+1)];
    final int height;
    TallGinsoShape(long seed){
        var random=new Random(seed);height=108+random.nextInt(11);
        double phase=random.nextDouble()*.4;
        // Root endpoints remain on a 22–25 block ring; growth is primarily vertical.
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4+.28+phase,reach=22+random.nextDouble()*2;
            for(double t=0;t<=1;t+=.018){
                double r=6+(reach-6)*t,y=6*(1-t)+11*Math.sin(Math.PI*t)-2*t;
                double angle=a+(i==5?-.30:.18)*Math.sin(t*Math.PI),thick=2.7*(1-t)+.7;
                blob(Math.cos(angle)*r,y,Math.sin(angle)*r,thick,thick*1.05,thick,WOOD);
            }
        }
        for(int y=-3;y<height;y++)for(int x=-15;x<=15;x++)for(int z=-15;z<=15;z++){
            double dx=x-centerX(y),dz=z-centerZ(y),angle=Math.atan2(dz,dx);
            double r=radius(y)+.60*Math.sin(5*angle-y*.055)+.27*Math.sin(9*angle+y*.09);
            int rim=height-29+(int)Math.round(5*Math.sin(angle*3+phase));
            if(Math.hypot(dx,dz)<=r&&y<=rim)set(x,y,z,WOOD);
        }
        for(int i=0;i<5;i++){
            double a=phase+i*2.399,y=18+i*12;
            blob(centerX(y)+Math.cos(a)*(radius(y)-.3),y,centerZ(y)+Math.sin(a)*(radius(y)-.3),2.4,5.5,2.4,WOOD);
        }
        for(int i=0;i<3;i++){
            double a=phase+new double[]{-.35,1.65,3.75}[i];
            double start=height-43,end=height-new double[]{0,9,17}[i],lean=new double[]{15,12,11}[i];
            for(double t=0;t<=1;t+=.012){
                double r=7+lean*t*t,angle=a+.16*Math.sin(Math.PI*t),thick=4.5*(1-t)+.6;
                blob(centerX(start)+Math.cos(angle)*r,start+(end-start)*t,centerZ(start)+Math.sin(angle)*r,thick,thick*1.25,thick,WOOD);
            }
            branch(a-.46,start+13,15+i,end-6,2.2,.5);
        }
        // Connected moss drapes, rather than separate balls at the end of six spokes.
        // Broad overlapping skirts descend toward one side; the three broken tips stay bare.
        double crownAngle=phase-.35;
        double[][] drapes={{12,2,44,10,2.8,15},{-1,12,33,7,2.7,10},{9,-9,22,8,2.5,8}};
        for(int i=0;i<drapes.length;i++){
            var d=drapes[i];double cx=d[0]*Math.cos(crownAngle)-d[1]*Math.sin(crownAngle);
            double cz=d[0]*Math.sin(crownAngle)+d[1]*Math.cos(crownAngle),cy=height-d[2];
            double a=Math.atan2(cz,cx),reach=Math.hypot(cx,cz);
            branch(a,cy-9,Math.max(1,reach-6),cy,2.8,.85);
            crown(cx,cy,cz,d[3],d[4],d[5],crownAngle,seed+i);
        }
        for(int i=0;i<4;i++)branch(phase+Math.PI+i*.57,height-61+i*7,16-i,height-47+i*4,2.8,.65);
        // Keep walk-through openings under the eight bridges even where the additive
        // terrain skirt would otherwise close them. Endpoints / support probes stay unchanged.
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4+.28+phase+(i==5?-.30:.18),cx=Math.cos(a)*16,cz=Math.sin(a)*16;
            for(int x=(int)Math.floor(cx-5);x<=cx+5;x++)for(int z=(int)Math.floor(cz-5);z<=cz+5;z++){
                double dx=x-cx,dz=z-cz,u=dx*Math.cos(a)+dz*Math.sin(a),v=-dx*Math.sin(a)+dz*Math.cos(a);
                // Round the arch down near its feet. A rectangular six-high cut
                // severs the descending bridge before it reaches the unchanged toe.
                int opening=(int)Math.floor(6-Math.max(0,Math.abs(u)-2.2)*2.8);
                if(Math.abs(u)<=3.5&&Math.abs(v)<=3)for(int y=1;y<=opening;y++)set(x,y,z,AIR);
            }
        }
        // Future course envelope is wider; A retains the old course unchanged for comparison.
        for(int y=1;y<=height+1;y++)for(int x=-10;x<=10;x++)for(int z=-10;z<=10;z++)
            if(Math.hypot(x-centerX(y),z-centerZ(y))<6.8)set(x,y,z,AIR);
        for(int z=-25;z<=0;z++)for(int x=-3;x<=3;x++)for(int y=1;y<=(z< -12?12:10-Math.abs(x));y++)set(x,y,z,AIR);
        for(int x=-5;x<=5;x++)for(int z=-19;z<=3;z++)if(Math.abs(x)<=3||z>=-9)set(x,0,z,WOOD);
        for(int i=0;i<29;i++){
            double a=phase+i*2.399,cy=6+i*(height-39)/28.0;
            double r=radius(cy)+.15,cx=centerX(cy)+Math.cos(a)*r,cz=centerZ(cy)+Math.sin(a)*r;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                int x=(int)Math.round(cx)+dx,z=(int)Math.round(cz)+dz,y=(int)cy;
                double u=dx*Math.cos(a)+dz*Math.sin(a),v=-dx*Math.sin(a)+dz*Math.cos(a);
                if(u>=-1&&sq(u/2.2)+sq(v/2.7)<1&&cell(x,y,z)==EMPTY)set(x,y,z,FUNGUS);
            }
        }
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)for(int y=2;y<height;y++){
            if(cell(x,y,z)!=WOOD||!exposed(x,y,z))continue;
            boolean patch=hash(Math.floorDiv(x,2),y/3,Math.floorDiv(z,2),seed)%13==0;
            boolean streak=Math.floorMod(x*3+z*5+y/12,23)==0&&y>height-38;
            boolean arch=y<=10&&z<=-8&&z>=-14&&Math.abs(x)>=4&&Math.abs(x)<=6;
            if(patch||streak||arch)set(x,y,z,SAP);
        }
        // Long uneven curtains hang from the moss skirts only, not all around the trunk.
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            if(Math.hypot(x,z)<12)continue;
            int bottom=-1;for(int y=height-65;y<height;y++)if(cell(x,y,z)==LEAF){bottom=y;break;}
            boolean leafy=bottom>=0;
            if(leafy){
                boolean edge=cell(x-4,bottom+2,z)!=LEAF||cell(x+4,bottom+2,z)!=LEAF
                        ||cell(x,bottom+2,z-4)!=LEAF||cell(x,bottom+2,z+4)!=LEAF;
                if(hash(x,0,z,seed)%(edge?7:24)!=0)continue;
            }
            if(bottom<0&&hash(x,2,z,seed)%28==0)
                for(int y=height-34;y<height;y++)if(timber(cell(x,y,z))&&cell(x,y-1,z)==EMPTY){bottom=y;break;}
            if(bottom<0)continue;
            int len=leafy?12+(int)(hash(x,1,z,seed)%14):5+(int)(hash(x,1,z,seed)%7);
            for(int y=bottom-1;y>=bottom-len&&cell(x,y,z)==EMPTY;y--)set(x,y,z,VINE);
        }
    }
    static boolean mossy(int x,int y,int z){return y<25&&hash(x/2,y/3,z/2,17)%5!=0||z<0&&hash(x/3,y/4,z/3,21)%3!=0;}
    static double centerX(double y){return 1.65*Math.sin(Math.max(0,y)*.048);}
    static double centerZ(double y){return 1.2*Math.sin(Math.max(0,y)*.061);}
    static double radius(double y){return 10.2-Math.max(0,y)*.024;}
    byte cell(int x,int y,int z){return inside(x,y,z)?cells[index(x,y,z)]:EMPTY;}
    private void set(int x,int y,int z,byte c){if(inside(x,y,z))cells[index(x,y,z)]=c;}
    private boolean exposed(int x,int y,int z){return cell(x-1,y,z)==EMPTY||cell(x+1,y,z)==EMPTY||cell(x,y,z-1)==EMPTY||cell(x,y,z+1)==EMPTY||cell(x,y-1,z)==EMPTY;}
    private static boolean inside(int x,int y,int z){return Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS&&y>=MIN_Y&&y<=MAX_Y;}
    private static int index(int x,int y,int z){return ((x+RADIUS)*WIDTH+z+RADIUS)*(MAX_Y-MIN_Y+1)+y-MIN_Y;}
    private void blob(double cx,double cy,double cz,double rx,double ry,double rz,byte c){
        for(int x=(int)Math.floor(cx-rx);x<=cx+rx;x++)for(int z=(int)Math.floor(cz-rz);z<=cz+rz;z++)
            for(int y=(int)Math.floor(cy-ry);y<=cy+ry;y++)if(sq((x-cx)/rx)+sq((y-cy)/ry)+sq((z-cz)/rz)<=1)set(x,y,z,c);
    }
    private void branch(double a,double start,double reach,double end,double r0,double r1){
        for(double t=0;t<=1;t+=.012){
            double angle=a+.28*Math.sin(t*Math.PI),r=6+reach*Math.sin(t*Math.PI*.57),y=start+(end-start)*t;
            double radius=r0*(1-t)+r1*t;
            blob(centerX(start)+Math.cos(angle)*r,y,centerZ(start)+Math.sin(angle)*r,radius,radius*1.15,radius,WOOD);
        }
    }
    private void crown(double cx,double cy,double cz,double rx,double ry,double rz,double angle,long seed){
        double bound=Math.hypot(rx,rz)+2;
        for(int x=(int)Math.floor(cx-bound);x<=cx+bound;x++)for(int z=(int)Math.floor(cz-bound);z<=cz+bound;z++){
            double dx=x-cx,dz=z-cz,u=dx*Math.cos(angle)+dz*Math.sin(angle),v=-dx*Math.sin(angle)+dz*Math.cos(angle);
            double outline=100;
            for(int i=0;i<5;i++)outline=Math.min(outline,sq((u-DRAPE_U[i]*rx)/(rx*.72))+sq((v-DRAPE_V[i]*rz)/(rz*.60)));
            double edge=1+.12*Math.sin(x*.7+seed)*Math.cos(z*.54);
            if(outline>edge)continue;
            // A bent moss sheet: a thick raised saddle at the branch, irregular humps
            // above it, and a 5–8-block hanging hem. Not flattened ellipsoid slices.
            double radius=Math.hypot(u/rx,v/rz);
            double middle=cy-6.5*Math.pow(radius,1.7)+1.3*Math.sin(u*.35)*Math.cos(v*.27)+.8*Math.sin(v*.48+seed%17);
            double half=ry-.65*Math.min(1,radius);
            for(int y=(int)Math.ceil(middle-half);y<=middle+half;y++){
                if(outline>.88&&hash(x,y,z,seed)%13==0||timber(cell(x,y,z)))continue;
                set(x,y,z,LEAF);
            }
        }
    }
    private static double sq(double v){return v*v;}
    private static long hash(int x,int y,int z,long s){long n=s^x*341873128712L^y*132897987541L^z*42317861L;n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;return(n^(n>>>27))&Long.MAX_VALUE;}
}
