package cn.wwl.orimod.world;

import java.util.Random;

/** Frozen v2 sculpture from 42a50ec. Do not retune: old chunk slices must remain byte-identical. */
final class LegacyGinsoShape {
    public static final int VERSION=2, RADIUS=28, MIN_Y=-8, MAX_Y=64, WIDTH=57;
    public static final byte EMPTY=0, WOOD=1, LEAF=2, VINE=3, AIR=4, SAP=5, FUNGUS=6;
    private final byte[] cells=new byte[WIDTH*WIDTH*(MAX_Y-MIN_Y+1)];
    public final int height;
    public LegacyGinsoShape(long seed){
        var random=new Random(seed);height=53+random.nextInt(5);
        double phase=random.nextDouble()*.4;
        // Arched stilt roots, not the Spirit Tree's broad solid buttress mound.
        for(int i=0;i<8;i++){
            double a=i*Math.PI/4+.28+phase,len=17+random.nextDouble()*7;
            for(double t=0;t<=1;t+=.025){
                double r=4+len*t,y=4*(1-t)+5*Math.sin(Math.PI*t)-2*t;
                double twist=a+.15*Math.sin(t*Math.PI),thick=2.4*(1-t)+.65;
                blob(Math.cos(twist)*r,y,Math.sin(twist)*r,thick,thick*1.3,thick,WOOD);
            }
        }
        // Uneven torsion ribs and occasional burls; a tall open tower, not a broad luminous crown.
        for(int y=-3;y<height;y++)for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            double dx=x-centerX(y),dz=z-centerZ(y),angle=Math.atan2(dz,dx);
            double r=radius(y)+.65*Math.sin(5*angle-y*.10)+.3*Math.sin(9*angle+y*.16);
            int rim=height-20+(int)Math.round(3*Math.sin(angle*3+phase));
            if(Math.hypot(dx,dz)<=r && y<=rim)set(x,y,z,WOOD);
        }
        for(int i=0;i<3;i++){
            double a=phase+i*2.1;
            blob(centerX(14+i*9)+Math.cos(a)*7,14+i*9,centerZ(14+i*9)+Math.sin(a)*7,2.6,4.0,2.6,WOOD);
        }
        // The bole breaks into three unequal, leaning forks, with sky between them.
        // Their bases overlap the living shell; no full-height circular rim survives.
        for(int i=0;i<3;i++){
            double a=phase+new double[]{-.35,1.65,3.75}[i];
            double start=height-27,end=height-new double[]{0,6,11}[i],lean=new double[]{11,9,8}[i];
            for(double t=0;t<=1;t+=.018){
                double r=6+lean*t*t,angle=a+.16*Math.sin(Math.PI*t),thick=3.4*(1-t)+.55;
                blob(centerX(start)+Math.cos(angle)*r,start+(end-start)*t,centerZ(start)+Math.sin(angle)*r,thick,thick*1.1,thick,WOOD);
            }
            // A split snag leaves each main fork below its broken tip.
            branch(a-.42, start+8, 12+i, end-4, 1.6, .45);
        }
        // Six mossy fans hang off the eastern forks at uneven heights, not a cap over the shaft.
        for(int i=0;i<6;i++){
            double a=phase+new double[]{.15,1.2,.15,1.35,-.25,.8}[i];
            double reach=new double[]{17,14,16,12,14,12}[i];
            double cy=height-new double[]{28,21,16,12,7,5}[i],rx=new double[]{8,7.4,6.5,6.1,5.2,4.6}[i];
            branch(a,cy-9,reach-4,cy-1,1.9,.65);
            crown(Math.cos(a)*reach,cy,Math.sin(a)*reach,rx,3.1,rx*.70,a,seed+i);
        }
        // Bare broken arms opposite the foliage, preserving the half-dead silhouette.
        for(int i=0;i<3;i++)branch(phase+Math.PI+i*.67,24+i*5,12-i,height-16+i*4,2.1,.65);
        // Full-height shaft reserved for the forthcoming ascent/escape; no inaccessible reward yet.
        for(int y=1;y<=height+1;y++)for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)
            if(Math.hypot(x-centerX(y),z-centerZ(y))<5.15)set(x,y,z,AIR);
        // Ground-level root mouth, opened to the sky beyond the arch.
        for(int z=-22;z<=0;z++)for(int x=-2;x<=2;x++)for(int y=1;y<=(z< -10?10:7-Math.abs(x));y++)set(x,y,z,AIR);
        for(int x=-4;x<=4;x++)for(int z=-15;z<=2;z++)if(Math.abs(x)<=2||z>=-7)set(x,0,z,WOOD);
        // Shelves attach to the exposed bole in staggered groups. Low light spills onto wet bark.
        for(int i=0;i<14;i++){
            double a=phase+i*2.399,cy=6+i*(height-30)/13.0;
            double r=radius(cy)+.15,cx=centerX(cy)+Math.cos(a)*r,cz=centerZ(cy)+Math.sin(a)*r;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                int x=(int)Math.round(cx)+dx,z=(int)Math.round(cz)+dz,y=(int)cy;
                double u=dx*Math.cos(a)+dz*Math.sin(a),v=-dx*Math.sin(a)+dz*Math.cos(a);
                if(u>=-1&&sq(u/2.2)+sq(v/2.7)<1&&cell(x,y,z)==EMPTY)set(x,y,z,FUNGUS);
            }
        }
        // Patchy bioluminescence follows cracks and knot hollows, including the upper forks.
        for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++)for(int y=2;y<height;y++){
            if(cell(x,y,z)!=WOOD||!exposed(x,y,z))continue;
            boolean patch=hash(Math.floorDiv(x,2),y/3,Math.floorDiv(z,2),seed)%13==0;
            boolean streak=Math.floorMod(x*3+z*5+y/8,23)==0 && y>height-24;
            boolean arch=y<=7&&z<=-5&&z>=-11&&Math.abs(x)>=3&&Math.abs(x)<=5;
            if(patch||streak||arch)set(x,y,z,SAP);
        }
        // Hanging wet foliage keeps its support; each curtain begins under a real leaf block.
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            if(Math.hypot(x,z)<9 || hash(x,0,z,seed)%7!=0)continue;
            int bottom=-1;for(int y=20;y<height;y++)if(cell(x,y,z)==LEAF){bottom=y;break;}
            // Thin glowing drips also descend from the forks' wooden undersides.
            if(bottom<0 && hash(x,2,z,seed)%3==0)
                for(int y=height-22;y<height;y++)if(timber(cell(x,y,z))&&cell(x,y-1,z)==EMPTY){bottom=y;break;}
            if(bottom<0)continue;
            int len=4+(int)(hash(x,1,z,seed)%8);
            for(int y=bottom-1;y>=bottom-len && cell(x,y,z)==EMPTY;y--)set(x,y,z,VINE);
        }
    }
    public static boolean timber(byte c){return c==WOOD||c==SAP;}
    /** North faces always get moss in the model; the lower bole gets it on all sides in patches. */
    public static boolean mossy(int x,int y,int z){return y<14 && hash(x/2,y/3,z/2,17)%5!=0 || z<0&&hash(x/3,y/4,z/3,21)%3!=0;}
    private boolean exposed(int x,int y,int z){return cell(x-1,y,z)==EMPTY||cell(x+1,y,z)==EMPTY||cell(x,y,z-1)==EMPTY||cell(x,y,z+1)==EMPTY||cell(x,y-1,z)==EMPTY;}
    public static double centerX(double y){return 1.2*Math.sin(Math.max(0,y)*.085);}
    public static double centerZ(double y){return .8*Math.sin(Math.max(0,y)*.11);}
    public static double radius(double y){return 8.3-Math.max(0,y)*.025;}
    public byte cell(int x,int y,int z){return inside(x,y,z)?cells[index(x,y,z)]:EMPTY;}
    private void set(int x,int y,int z,byte c){if(inside(x,y,z))cells[index(x,y,z)]=c;}
    private static boolean inside(int x,int y,int z){return Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS&&y>=MIN_Y&&y<=MAX_Y;}
    private static int index(int x,int y,int z){return ((x+RADIUS)*WIDTH+z+RADIUS)*(MAX_Y-MIN_Y+1)+y-MIN_Y;}
    private void blob(double cx,double cy,double cz,double rx,double ry,double rz,byte c){
        for(int x=(int)Math.floor(cx-rx);x<=cx+rx;x++)for(int z=(int)Math.floor(cz-rz);z<=cz+rz;z++)
            for(int y=(int)Math.floor(cy-ry);y<=cy+ry;y++)if(sq((x-cx)/rx)+sq((y-cy)/ry)+sq((z-cz)/rz)<=1)set(x,y,z,c);
    }
    private void branch(double a,double start,double reach,double end,double r0,double r1){
        for(double t=0;t<=1;t+=.018){
            double angle=a+.28*Math.sin(t*Math.PI),r=5+reach*Math.sin(t*Math.PI*.57),y=start+(end-start)*t;
            double radius=r0*(1-t)+r1*t;
            blob(centerX(start)+Math.cos(angle)*r,y,centerZ(start)+Math.sin(angle)*r,radius,radius,radius,WOOD);
        }
    }
    private void crown(double cx,double cy,double cz,double rx,double ry,double rz,double angle,long seed){
        // Overlapping flattened lobes with a lowered outer rim and a sparse ragged underside.
        for(int x=(int)(cx-rx-3);x<=cx+rx+3;x++)for(int z=(int)(cz-rx-3);z<=cz+rx+3;z++)
            for(int y=(int)(cy-ry-4);y<=cy+ry+1;y++){
                double dx=x-cx,dz=z-cz,u=dx*Math.cos(angle)+dz*Math.sin(angle),v=-dx*Math.sin(angle)+dz*Math.cos(angle),d=100;
                for(int i=-1;i<=1;i++)d=Math.min(d,sq((u-i*rx*.30)/(rx*.73))+sq((y-cy+Math.max(0,u)*.2+i*.4)/ry)+sq((v+i*rz*.28)/(rz*.85)));
                double edge=1+.15*Math.sin(x*.7+seed)*Math.cos(z*.54)+.12*Math.sin(y*1.8);
                if(d>edge||d<.22||hash(x,y,z,seed)%23==0||cell(x,y,z)==WOOD)continue;
                set(x,y,z,LEAF);
            }
    }
    private static double sq(double v){return v*v;}
    private static long hash(int x,int y,int z,long s){long n=s^x*341873128712L^y*132897987541L^z*42317861L;n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;return(n^(n>>>27))&Long.MAX_VALUE;}
}
