package cn.wwl.orimod.world;

import java.util.Random;

/** Pure, versioned voxel sculpture. Terrain/interior gameplay are deliberately outside the shape algorithm. */
public final class GreatTreeShape {
    public static final int VERSION = 1, RADIUS = 40, MIN_Y = -12, MAX_Y = 82, WIDTH = RADIUS * 2 + 1;
    public static final byte EMPTY = 0, WOOD = 1, LEAF = 2, VINE = 3, AIR = 4, VEIN = 5;
    private final byte[] cells = new byte[WIDTH * WIDTH * (MAX_Y - MIN_Y + 1)];
    public final int height;

    public GreatTreeShape(long seed) {
        var random = new Random(seed);
        height = 70 + random.nextInt(7);
        // Broad buttresses taper into individual roots; an open south-facing cleft remains the entrance.
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + .12;
            double length = 23 + random.nextDouble() * 8;
            for (double t = 0; t <= 1; t += .018) {
                double r = 3 + length * t;
                ellipsoid(Math.cos(a) * r, 7 * Math.pow(1 - t, 2) - 1,
                        Math.sin(a) * r, 3.1 * (1 - t) + .6, 7.5 * (1 - t) + 1.1,
                        3.1 * (1 - t) + .6, WOOD);
            }
        }
        // A continuous tapered, bent bole, with a wider heart-level burl (not a straight cylinder).
        for (int y = -4; y <= 54; y++) {
            double r = trunkRadius(y);
            ellipsoid(bendX(y), y, bendZ(y), r, 1.6, r * .92, WOOD);
        }
        // Three tiers of irregular, flattened clusters. The low arms carry the broadest
        // crowns; their separated silhouettes leave real sky windows, not pinholes in a dome.
        double rotation = random.nextDouble() * .18;
        double[] widths = {12.2, 10.8, 11.6, 10.2}, levels = {42, 45, 40, 44};
        for (int i = 0; i < 4; i++) {
            double angle = rotation + i * Math.PI / 2;
            double reach = 22 + i * .4 + random.nextDouble() * .5;
            armAndCrown(angle, reach, 19 + i % 2 * 3, levels[i], widths[i], widths[i] * .43, seed+i);
        }
        for (int i = 0; i < 3; i++) {
            double angle = rotation + .72 + i * Math.PI * 2 / 3;
            double width = new double[]{9.5, 8.2, 10}[i];
            armAndCrown(angle, 15.5, 33 + i % 2 * 2, new double[]{55,58,53}[i], width, width * .44, seed+10+i);
        }
        armAndCrown(rotation + .2, 7.5, 47, height-7, 8.5, 4.1, seed+20);
        armAndCrown(rotation + Math.PI+.2, 8, 43, height-16, 9.0, 4.2, seed+21);
        // Root gate: two curved buttresses meet above a five-block, ground-level opening.
        for(double a=0;a<=Math.PI;a+=.025)
            ellipsoid(5*Math.cos(a),8*Math.sin(a),-8+2*Math.sin(a),1.5,1.5,1.5,WOOD);
        // Cool hanging light strands, in separated curtains so the bough silhouette stays readable.
        for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
            if (x * x + z * z < 15 * 15 || hash(x, 0, z, seed) % 19 != 0) continue;
            int bottom = -1;
            for (int y = 26; y <= height; y++) if (cell(x, y, z) == LEAF) { bottom = y; break; }
            if (bottom < 0) continue;
            int length = 3 + (int)(hash(x, 1, z, seed) % 9);
            for (int y = bottom - 1; y >= bottom - length && cell(x, y, z) == EMPTY; y--) set(x, y, z, VINE);
        }
        // Reserve a continuous hollow bole and the front arch, without building the interior in this shape pass.
        for (int y = 0; y <= 39; y++) for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            double dx = x - bendX(y), dz = z - bendZ(y), r = y < 4 ? 3.1 : 3.55;
            if (dx * dx + dz * dz <= r * r) set(x, y, z, AIR);
        }
        for (int z = -11; z <= 0; z++) for (int x = -2; x <= 2; x++)
            for (int y = 0; y <= 7 - Math.abs(x) / 2; y++) set(x, y, z, AIR);
        // One-block wandering seams use a masked emissive overlay, leaving the bark itself world-lit.
        for(int i=0;i<9;i++) {
            double a=i*Math.PI*2/9+.12;
            for(double r=4;r<30;r+=.35) {
                int x=(int)Math.round(Math.cos(a)*r),z=(int)Math.round(Math.sin(a)*r);
                for(int y=16;y>=-4;y--)if(cell(x,y,z)==WOOD){set(x,y,z,VEIN);break;}
            }
            if(i%2==1)continue;
            for(int y=0;y<=53;y++) {
                double angle=a+.11*Math.sin(y*.17+i);
                for(double r=trunkRadius(y)+1;r>3.5;r-=.3) {
                    int x=(int)Math.round(bendX(y)+Math.cos(angle)*r),z=(int)Math.round(bendZ(y)+Math.sin(angle)*r);
                    if(cell(x,y,z)==WOOD){set(x,y,z,VEIN);break;}
                }
            }
        }

    }

    public static double trunkRadius(double y) {
        return 8 - Math.max(0, y) * .08 + .45 * Math.exp(-Math.pow((y - 26) / 7, 2));
    }
    public static double bendX(double y) { return 2.5 * Math.sin(Math.max(0, y) * .042); }
    public static double bendZ(double y) { return 1.2 * Math.sin(Math.max(0, y) * .065); }
    public byte cell(int x, int y, int z) {
        return inside(x, y, z) ? cells[index(x, y, z)] : EMPTY;
    }
    private void set(int x, int y, int z, byte value) { if (inside(x, y, z)) cells[index(x, y, z)] = value; }
    private static boolean inside(int x, int y, int z) { return Math.abs(x) <= RADIUS && Math.abs(z) <= RADIUS && y >= MIN_Y && y <= MAX_Y; }
    private static int index(int x, int y, int z) { return ((x + RADIUS) * WIDTH + z + RADIUS) * (MAX_Y - MIN_Y + 1) + y - MIN_Y; }
    private void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, byte value) {
        for (int x = (int)Math.floor(cx - rx); x <= Math.ceil(cx + rx); x++)
            for (int z = (int)Math.floor(cz - rz); z <= Math.ceil(cz + rz); z++)
                for (int y = (int)Math.floor(cy - ry); y <= Math.ceil(cy + ry); y++)
                    if (sq((x-cx)/rx) + sq((y-cy)/ry) + sq((z-cz)/rz) <= 1) set(x,y,z,value);
    }
    private void armAndCrown(double angle,double reach,double startY,double cy,double rx,double ry,long seed) {
        double x=bendX(startY),z=bendZ(startY),cx=Math.cos(angle)*reach,cz=Math.sin(angle)*reach;
        // Rise beside the bole, then bow outward. Fork below the ragged crown underside,
        // before both terminal arms enter separate sub-blobs; neither pierces the crown top.
        double fx=x+(cx-x)*.64,fz=z+(cz-z)*.64,fy=cy-ry-3;
        curvedBranch(x,startY,z,fx,fy,fz,3.4,1.75);
        for(int side:new int[]{-1,1})
            curvedBranch(fx,fy,fz,cx-Math.sin(angle)*side*rx*.36,
                    cy-1.7,cz+Math.cos(angle)*side*rx*.36,1.8,1.05);
        crown(cx,cy,cz,rx,ry,rx*.83,angle,seed);
    }
    private void curvedBranch(double x,double y,double z,double ex,double ey,double ez,double startR,double endR) {
        double direction=Math.atan2(ez-z,ex-x);
        for(double t=0;t<=1;t+=.014){
            // Cubic Bezier: near-vertical first tangent, near-horizontal final tangent.
            double u=1-t,h=3*u*t*t*.70+t*t*t,v=3*u*u*t*.72+3*u*t*t*.97+t*t*t;
            double px=x+(ex-x)*h,pz=z+(ez-z)*h,py=y+(ey-y)*v,r=startR+(endR-startR)*t;
            ellipsoid(px,py,pz,r,r,r,WOOD);
        }
        for(double t=0;t<=1;t+=.012){
            double u=1-t,h=3*u*t*t*.70+t*t*t,v=3*u*u*t*.72+3*u*t*t*.97+t*t*t;
            double px=x+(ex-x)*h,pz=z+(ez-z)*h,py=y+(ey-y)*v,r=startR+(endR-startR)*t;
            for(int side:new int[]{-1,1}){
                for(double rr=r+.5;rr>0;rr-=.3){
                    int xx=(int)Math.round(px-Math.sin(direction)*rr*side),zz=(int)Math.round(pz+Math.cos(direction)*rr*side),yy=(int)Math.round(py);
                    if(cell(xx,yy,zz)==WOOD){set(xx,yy,zz,VEIN);break;}
                }
            }
        }
    }
    private void crown(double cx,double cy,double cz,double rx,double ry,double rz,double angle,long seed) {
        // Five overlapping sub-blobs form one asymmetrical cluster. All measurements are
        // in its radial/tangential frame. The outer end hangs lower than the inner shoulder.
        double[][] blobs={{0,0,0,.72,1,.70},{.42,-.8,.06,.62,.87,.59},
                {-.12,.7,.40,.58,.84,.62},{-.20,-.6,-.43,.62,.89,.57},{-.45,.3,.08,.51,.75,.55}};
        int bound=(int)Math.ceil(rx*1.2);double c=Math.cos(angle),s=Math.sin(angle),phase=(seed&255)*.11;
        for(int x=(int)cx-bound;x<=cx+bound;x++)for(int z=(int)cz-bound;z<=cz+bound;z++)
            for(int y=(int)(cy-ry-5);y<=cy+ry+3;y++){
                double dx=x-cx,dz=z-cz,radial=dx*c+dz*s,tangent=-dx*s+dz*c;
                double droop=1.6*Math.max(0,radial/rx),d=100;
                for(var b:blobs){
                    double dy=y-(cy+b[1]-droop);
                    double down=dy<0?1+.11*Math.sin(radial*.6+tangent*.7+phase):1;
                    d=Math.min(d,sq((radial-b[0]*rx)/(rx*b[3]))+sq(dy/(ry*b[4]*down))+sq((tangent-b[2]*rz)/(rz*b[5])));
                }
                double edge=1+.12*Math.sin(x*.63+phase)*Math.cos(z*.51-phase)+.07*Math.sin(y*.91+x*.32);
                if(d>edge||d<.28||hash(x,y,z,seed)%61==0||cell(x,y,z)==WOOD||cell(x,y,z)==VEIN)continue;
                set(x,y,z,LEAF);
            }
    }
    private static double sq(double d) { return d*d; }
    private static long hash(int x,int y,int z,long seed) {
        long n = seed ^ (x*341873128712L) ^ (y*132897987541L) ^ (z*42317861L);
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        return (n ^ (n >>> 27)) & Long.MAX_VALUE;
    }
}
