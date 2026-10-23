package cn.wwl.orimod.world;

import java.util.Random;

/** Version 2 landmark: original block-scale detail, not a nearest-neighbour enlargement of version 1. */
public final class GreatTreeShape {
    public static final int VERSION=2, RADIUS=84, MIN_Y=-16, MAX_Y=224, WIDTH=2*RADIUS+1;
    public static final byte EMPTY=0, WOOD=1, LEAF=2, VINE=3, AIR=4, VEIN=5, CORE=15; // 6–14 belong to the lower interior overlay.
    private static final byte CROWN_VOLUME=16,HOLLOW=17;
    private final java.util.List<double[]> crowns=new java.util.ArrayList<>();
    private final LegacyGreatTreeShape legacy;
    private final byte[] cells;
    public final int height,version;
    public GreatTreeShape(long seed){this(seed,VERSION);}
    public GreatTreeShape(long seed,int version){
        this.version=version;
        if(version==1){legacy=new LegacyGreatTreeShape(seed);cells=null;height=legacy.height;return;}
        if(version!=VERSION)throw new IllegalArgumentException("Unsupported great tree shape version "+version);
        legacy=null;cells=new byte[WIDTH*WIDTH*(MAX_Y-MIN_Y+1)];
        var random=new Random(seed);height=212+random.nextInt(7);
        // Nine buttresses, broad where they join the bole, individually tapering into the ground.
        for(int i=0;i<9;i++){
            double a=i*Math.PI*2/9+.12,reach=34+random.nextDouble()*6;
            for(double t=0;t<=1;t+=.02){
                double r=8+(reach-8)*t,thick=5.5*(1-t)+.8;
                ellipsoid(Math.cos(a)*r,18*Math.pow(1-t,2)-2,Math.sin(a)*r,thick,16*(1-t)+1.2,thick,WOOD);
            }
        }
        for(int y=-5;y<=202;y++){
            double r=trunkRadius(y);ellipsoid(bendX(y),y,bendZ(y),r,1.2,r*.94,WOOD);
        }
        // Six heavy primary arms carry overlapping masses, not isolated balls on nine thin sticks.
        // Four wide lower groups and two taller groups occupy the upper 45% of the bole.
        double rotation=random.nextDouble()*.10;
        double tierShift=(height-216)*.5;
        double[] levels={131+tierShift,132+tierShift,129+tierShift,131+tierShift};
        for(int i=0;i<4;i++)armAndCrowns(rotation+i*Math.PI/2,52,102+(i%2)*3,levels[i],26,12,seed+i,false);
        for(int i=0;i<2;i++)armAndCrowns(rotation+.72+i*Math.PI,31,129+i*3,169+tierShift+i*2,28,13.5,seed+10+i,true);
        skinCrown(seed);
        for(double a=0;a<=Math.PI;a+=.035)ellipsoid(8*Math.cos(a),13*Math.sin(a),-16+3*Math.sin(a),2.1,2.1,2.1,WOOD);
        // Short, capped curtains: at most eight strands per contributing cluster, each 6–16 blocks.
        int[] strands=new int[crowns.size()];
        for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
            if(x*x+z*z<22*22||hash(x,0,z,seed)%97!=0)continue;
            int bottom=-1;for(int y=85;y<=MAX_Y;y++)if(cell(x,y,z)==LEAF){bottom=y;break;}
            if(bottom<0)continue;
            int nearest=0;double best=Double.MAX_VALUE;
            for(int i=0;i<crowns.size();i++){var a=crowns.get(i);double d=sq(x-a[0])+sq(z-a[2])+sq((bottom-a[1])*.7);if(d<best){best=d;nearest=i;}}
            if(strands[nearest]>=8)continue;strands[nearest]++;
            int length=6+(int)(hash(x,1,z,seed)%11);
            for(int y=bottom-1;y>=bottom-length&&cell(x,y,z)==EMPTY;y--)set(x,y,z,VINE);
        }
        // Reserve the hollow core for the later ascent/lookout pass. The existing lower hall overlays this.
        for(int y=0;y<=185;y++)for(int x=-13;x<=13;x++)for(int z=-12;z<=12;z++){
            double dx=x-bendX(y),dz=z-bendZ(y),r=y<45?7:5.3;
            if(dx*dx+dz*dz<=r*r)set(x,y,z,AIR);
        }
        for(int z=-22;z<=0;z++)for(int x=-3;x<=3;x++)for(int y=0;y<=9-Math.abs(x)/2;y++)set(x,y,z,AIR);
        // Narrow luminous seams connect the same bark material from root to branch forks.
        for(int i=0;i<9;i++){
            double a=i*Math.PI*2/9+.12;
            for(double r=8;r<40;r+=.4){int x=(int)Math.round(Math.cos(a)*r),z=(int)Math.round(Math.sin(a)*r);
                for(int y=34;y>=-5;y--)if(cell(x,y,z)==WOOD){set(x,y,z,VEIN);break;}}
            for(int y=0;y<=176;y++){
                double angle=a+.075*Math.sin(y*.075+i);
                for(double r=trunkRadius(y)+1;r>5;r-=.4){
                    int x=(int)Math.round(bendX(y)+Math.cos(angle)*r),z=(int)Math.round(bendZ(y)+Math.sin(angle)*r);
                    if(cell(x,y,z)==WOOD){set(x,y,z,VEIN);break;}
                }
            }
        }
    }
    public static int radius(int version){return version==1?40:RADIUS;}
    public static int minY(int version){return version==1?-12:MIN_Y;}
    public static int maxY(int version){return version==1?82:MAX_Y;}
    public static double trunkRadius(double y){return 15.8-Math.min(150,Math.max(0,y))*.028+.65*Math.exp(-sq((y-55)/22));}
    public static double bendX(double y){return 4.2*Math.sin(Math.max(0,y)*.011);}
    public static double bendZ(double y){return 2.4*Math.sin(Math.max(0,y)*.018);}
    public byte cell(int x,int y,int z){return legacy!=null?legacy.cell(x,y,z):inside(x,y,z)?cells[index(x,y,z)]:EMPTY;}
    private static boolean inside(int x,int y,int z){return Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS&&y>=MIN_Y&&y<=MAX_Y;}
    private static int index(int x,int y,int z){return ((x+RADIUS)*WIDTH+z+RADIUS)*(MAX_Y-MIN_Y+1)+y-MIN_Y;}
    private void set(int x,int y,int z,byte c){if(inside(x,y,z))cells[index(x,y,z)]=c;}
    private void ellipsoid(double cx,double cy,double cz,double rx,double ry,double rz,byte c){
        for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++)for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++){
            double horizontal=sq((x-cx)/rx)+sq((z-cz)/rz);if(horizontal>1)continue;
            for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++)if(horizontal+sq((y-cy)/ry)<=1)set(x,y,z,c);
        }
    }
    private void armAndCrowns(double angle,double reach,double startY,double cy,double rx,double ry,long seed,boolean upper){
        double x=bendX(startY),z=bendZ(startY),cx=Math.cos(angle)*reach,cz=Math.sin(angle)*reach;
        double fx=Math.cos(angle)*reach*.68,fz=Math.sin(angle)*reach*.68,fy=cy-ry+(upper?1:-7);
        branch(x,startY,z,fx,fy,fz,6,3.8);
        // The low arms remain nearly horizontal; each forks twice before entering the broad outer masses.
        for(int side:new int[]{-1,1}){
            double a=angle+side*.40,bx=Math.cos(a)*reach,bz=Math.sin(a)*reach,by=cy-4;
            branch(fx,fy,fz,bx,by,bz,3.9,2.6);
            for(int twig:new int[]{-1,1})branch(bx,by,bz,bx+Math.cos(a)*4-Math.sin(a)*twig*5,cy,
                    bz+Math.sin(a)*4+Math.cos(a)*twig*5,2.7,2);
            crown(bx,cy,bz,rx,ry,rx*.94,a,seed+side+2,!upper);
        }
        // Lower shoulder joins the outer pair. Middle groups carry a deliberately small, separate top tier.
        double inner=upper?13:35,innerY=upper?height-11:cy+1;
        double ix=Math.cos(angle)*inner,iz=Math.sin(angle)*inner;
        if(upper)branch(fx,fy,fz,ix,innerY-4,iz,3.7,2.2);
        crown(ix,innerY,iz,upper?23:26,upper?10:10.5,upper?22:24,angle,seed+31,!upper);
    }
    private void branch(double x,double y,double z,double ex,double ey,double ez,double startR,double endR){
        double angle=Math.atan2(ez-z,ex-x);
        for(int pass=0;pass<2;pass++)for(double t=0;t<=1;t+=.008){
            double u=1-t,h=3*u*u*t*.50+3*u*t*t*.90+t*t*t,v=3*u*u*t*.72+3*u*t*t*.97+t*t*t;
            double px=x+(ex-x)*h,pz=z+(ez-z)*h,py=y+(ey-y)*v,r=startR+(endR-startR)*t;
            if(pass==0)ellipsoid(px,py,pz,r,r,r,WOOD);
            else for(int side:new int[]{-1,1})for(double rr=r+.5;rr>0;rr-=.4){
                int xx=(int)Math.round(px-Math.sin(angle)*rr*side),zz=(int)Math.round(pz+Math.cos(angle)*rr*side),yy=(int)Math.round(py);
                if(cell(xx,yy,zz)==WOOD){set(xx,yy,zz,VEIN);break;}
            }
        }
    }
    private void crown(double cx,double cy,double cz,double rx,double ry,double rz,double angle,long seed,boolean flat){
        crowns.add(new double[]{cx,cy,cz});
        double[][] blobs={{0,0,0,.72,1,.70},{.42,-1.4,.06,.62,.87,.59},{-.12,1.2,.40,.58,.84,.62},{-.20,-1,-.43,.62,.89,.57},{-.45,.5,.08,.51,.75,.55}};
        int bound=(int)Math.ceil(rx*1.2);double c=Math.cos(angle),s=Math.sin(angle),phase=(seed&255)*.11;
        double[] horizontal=new double[blobs.length],centreY=new double[blobs.length],tangentSquare=new double[blobs.length],down=new double[blobs.length];
        for(int x=(int)cx-bound;x<=cx+bound;x++)for(int z=(int)cz-bound;z<=cz+bound;z++){
            double dx=x-cx,dz=z-cz,radial=dx*c+dz*s,tangent=-dx*s+dz*c,droop=3.2*Math.max(0,radial/rx);
            double ripple=1+.10*Math.sin(radial*.3+tangent*.35+phase),min=100;
            for(int i=0;i<blobs.length;i++){
                var b=blobs[i];horizontal[i]=sq((radial-b[0]*rx)/(rx*b[3]));
                // Keep the addition order unchanged: this preserves boundary voxels bit for bit.
                tangentSquare[i]=sq((tangent-b[2]*rz)/(rz*b[5]));centreY[i]=cy+b[1]-droop;
                down[i]=ry*b[4]*ripple;min=Math.min(min,horizontal[i]+tangentSquare[i]);
            }
            if(min>1.17)continue;
            double edgeXZ=1+.10*Math.sin(x*.27+phase)*Math.cos(z*.23-phase);
            double floor=cy-ry*.70-.8*Math.sin(radial*.13)*Math.cos(tangent*.15)-droop;
            for(int y=(int)(cy-ry-9);y<=cy+ry+4;y++){
                // Partial table-like undersides, with a wavy lip and drooping outward edges, not a uniform cut plane.
                if(flat&&Math.abs(radial)<rx*.66&&Math.abs(tangent)<rz*.66&&y<floor)continue;
                double d=100;
                for(int i=0;i<blobs.length;i++){
                    double dy=y-centreY[i],denom=dy<0?down[i]:ry*blobs[i][4];
                    d=Math.min(d,horizontal[i]+sq(dy/denom)+tangentSquare[i]);
                }
                double edge=edgeXZ+.06*Math.sin(y*.5+x*.17);
                if(d>edge||cell(x,y,z)==WOOD||cell(x,y,z)==VEIN)continue;
                set(x,y,z,CROWN_VOLUME);
            }
        }
    }

    private static boolean occupied(byte c){return c==CROWN_VOLUME||c==HOLLOW||c==LEAF||c==CORE||c==WOOD||c==VEIN;}
    private void skinCrown(long seed){
        // Skin the union: outer cutout leaves, one opaque backing layer, then empty interior.
        // All sides (including the underside) get backing; overlapping lobes have no hidden inner shells.
        int row=MAX_Y-MIN_Y+1,plane=WIDTH*row;
        for(int x=-RADIUS+2;x<=RADIUS-2;x++)for(int z=-RADIUS+2;z<=RADIUS-2;z++)
            for(int y=85,i=index(x,85,z);y<=MAX_Y-2;y++,i++){
                if(cells[i]!=CROWN_VOLUME)continue;boolean interior=true;int depth=0;
                for(int d=1;d<=2&&interior;d++){
                    interior=occupied(cells[i+d*plane])&&occupied(cells[i-d*plane])
                            &&occupied(cells[i+d])&&occupied(cells[i-d])&&occupied(cells[i+d*row])&&occupied(cells[i-d*row]);
                    if(interior)depth=d;
                }
                cells[i]=depth==2?HOLLOW:depth==1?CORE:LEAF;
            }
        for(int i=0;i<cells.length;i++){
            byte c=cells[i];if(c==HOLLOW)cells[i]=EMPTY;
            else if(c==LEAF||c==CROWN_VOLUME){
                int x=i/plane-RADIUS,z=(i/row)%WIDTH-RADIUS,y=i%row+MIN_Y;
                cells[i]=hash(x,y,z,seed)%113==0?EMPTY:LEAF;
            }
        }
    }
    private static double sq(double d){return d*d;}
    private static long hash(int x,int y,int z,long seed){long n=seed^(x*341873128712L)^(y*132897987541L)^(z*42317861L);n=(n^(n>>>30))*0xbf58476d1ce4e5b9L;return(n^(n>>>27))&Long.MAX_VALUE;}
}
