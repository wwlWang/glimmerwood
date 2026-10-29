package cn.wwl.orimod.world;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntBinaryOperator;

/** Registry-free height-field rules for the new creek's variable-height banks. */
class RuinTerrainRules {
    static final int RADIUS = 8, REACH = 12, NONE = Integer.MIN_VALUE / 4;
    record Ground(int x, int z, int y) {}
    record Column(int x, int z, int natural, int top, int distance, int retainingBottom,
                  boolean entrance, boolean rear) {
        boolean retaining() { return retainingBottom != NONE; }
    }

    private final int sizeX, sizeZ, width, depth, padding = RADIUS + REACH;
    private final int[] ground, distance, plainFloor, bankFloor, authoredCeiling;
    private final boolean[] exterior;
    private final int entranceX, entranceHalfWidth, rearFromZ;

    RuinTerrainRules(int sizeX, int sizeZ, List<Ground> columns, int entranceX, int entranceHalfWidth, int rearFromZ) {
        if (sizeX < 1 || sizeZ < 1 || columns.isEmpty()) throw new IllegalArgumentException("Empty creek footprint");
        this.sizeX = sizeX; this.sizeZ = sizeZ; this.entranceX = entranceX;
        this.entranceHalfWidth = entranceHalfWidth; this.rearFromZ = rearFromZ;
        width = sizeX + 2 * padding; depth = sizeZ + 2 * padding;
        ground = new int[width * depth]; Arrays.fill(ground, NONE);
        distance = new int[ground.length]; Arrays.fill(distance, 1_000_000);
        for (var c : columns) {
            if (c.x < 0 || c.x >= sizeX || c.z < 0 || c.z >= sizeZ || ground[index(c.x,c.z)] != NONE)
                throw new IllegalArgumentException("Invalid/duplicate creek ground column " + c);
            ground[index(c.x,c.z)] = c.y; distance[index(c.x,c.z)] = 0;
        }
        // An unlisted pocket enclosed by authored ground is not a new outer bank.
        // In particular a stream bed must never acquire a soil skirt of its own.
        exterior = exterior();
        for (int z = 0; z < depth; z++) for (int x = 0; x < width; x++) {
            int i=z*width+x;
            if (x>0) distance[i]=Math.min(distance[i],distance[i-1]+1);
            if (z>0) distance[i]=Math.min(distance[i],distance[i-width]+1);
        }
        for (int z=depth-1; z>=0; z--) for (int x=width-1; x>=0; x--) {
            int i=z*width+x;
            if (x+1<width) distance[i]=Math.min(distance[i],distance[i+1]+1);
            if (z+1<depth) distance[i]=Math.min(distance[i],distance[i+width]+1);
        }
        // A fixed authored potential, independent of sampled/previously written soil.
        // Adding noise to natural heights on each slice would grow the bank again.
        plainFloor=ground.clone();relax(plainFloor,width,depth);
        // The template cannot move. Natural hills may be cut within the rim,
        // but must never lift its first column above an authored edge + 1.
        // This dual envelope is immutable and propagates that bound outwards.
        authoredCeiling=ground.clone();
        for(int i=0;i<authoredCeiling.length;i++)authoredCeiling[i]=ground[i]==NONE?NONE:-ground[i];
        relax(authoredCeiling,width,depth);
        for(int i=0;i<authoredCeiling.length;i++)authoredCeiling[i]=-authoredCeiling[i];
        bankFloor=plainFloor.clone();
        int rearTop=columns.stream().filter(c->c.z>=rearFromZ).mapToInt(Ground::y).max().orElse(0)-2;
        for(int z=-RADIUS;z<sizeZ+RADIUS;z++)for(int x=-RADIUS;x<sizeX+RADIUS;x++){
            int i=index(x,z);
            if(z<rearFromZ||!rim(x,z))continue;
            int desired=rearTop;
            for(int band=0;band<7;band++)if(z>rearEdge(x,band,sizeZ))desired--;
            bankFloor[i]=Math.max(plainFloor[i],desired);
        }
        relax(bankFloor,width,depth);
    }

    static int rearEdge(int x,int band,int sizeZ){
        int edge=Integer.MIN_VALUE/4;
        for(int i=0;i<=band;i++)edge=Math.max(edge+1,sizeZ-7+2*i+
                (int)Math.round(2.65*Math.sin(x*.21+i*.65)+.35*Math.cos(x*.37-i*.45)));
        return edge;
    }

    private static void relax(int[] heights,int w,int h){
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int i=z*w+x;
            if(x>0)heights[i]=Math.max(heights[i],heights[i-1]-1);
            if(z>0)heights[i]=Math.max(heights[i],heights[i-w]-1);
        }
        for(int z=h-1;z>=0;z--)for(int x=w-1;x>=0;x--){
            int i=z*w+x;
            if(x+1<w)heights[i]=Math.max(heights[i],heights[i+1]-1);
            if(z+1<h)heights[i]=Math.max(heights[i],heights[i+w]-1);
        }
    }

    /** Samples each local source once; four linear passes replace per-column diamond scans. */
    List<Column> plan(int minX, int minZ, int maxX, int maxZ,
                      IntBinaryOperator natural, IntBinaryOperator savedApproachFloor) {
        minX=Math.max(-RADIUS,minX); minZ=Math.max(-RADIUS,minZ);
        maxX=Math.min(sizeX-1+RADIUS,maxX); maxZ=Math.min(sizeZ-1+RADIUS,maxZ);
        if (minX>maxX || minZ>maxZ) return List.of();
        boolean any=false;
        for(int z=minZ;z<=maxZ&&!any;z++)for(int x=minX;x<=maxX;x++)if(rim(x,z)){any=true;break;}
        if(!any)return List.of();
        int x0=minX-REACH,z0=minZ-REACH,w=maxX-minX+1+2*REACH,h=maxZ-minZ+1+2*REACH;
        var original=new int[w*h];var heights=new int[w*h];var ceilings=new int[w*h];
        var fixedCeilings=new int[w*h];
        for(int z=0;z<h;z++)for(int x=0;x<w;x++){
            int lx=x0+x,lz=z0+z,i=z*w+x,authored=ground(lx,lz);
            // Only unchanged terrain immediately beyond the rim constrains its
            // seam. Far-away hills and enclosed template pockets do not belong
            // to this additive skirt, and need no expensive noise query.
            int actual=authored!=NONE?authored:needsNatural(lx,lz)?natural.applyAsInt(lx,lz):NONE;
            original[i]=actual;
            int approach=savedApproachFloor.applyAsInt(lx,lz);
            int anchor=approach!=NONE?approach:actual;
            fixedCeilings[i]=approach!=NONE?-approach:inside(lx,lz)?-authoredCeiling[index(lx,lz)]:NONE;
            heights[i]=Math.max(anchor,inside(lx,lz)?plainFloor[index(lx,lz)]:NONE);
            // Only use noise where the unchanged bank and outside ground have
            // spare grading distance. Do not manufacture a new cliff/guard.
            ceilings[i]=rim(lx,lz)||anchor==NONE?1_000_000:anchor;
        }
        // Natural heights supply the additive envelope; the fixed template also
        // supplies an upper envelope so a hill can be cut instead of raising
        // the first rim column two or more blocks above the template floor.
        relax(heights,w,h);
        // The saved entrance is another immutable piece, not a suggestion to
        // raise it to surrounding hills. Both sides of its shoulder meet it.
        relax(fixedCeilings,w,h);
        for(int i=0;i<ceilings.length;i++)ceilings[i]=-ceilings[i];
        relax(ceilings,w,h); // dual min-plus envelope from immutable boundary heights
        for(int i=0;i<ceilings.length;i++)ceilings[i]=-ceilings[i];
        var out=new ArrayList<Column>();
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++){
            if(!rim(x,z))continue;
            int i=(z-z0)*w+x-x0,top=Math.min(-fixedCeilings[i],Math.max(heights[i],Math.min(bankFloor[index(x,z)],ceilings[i]))),bottom=NONE;
            if(!entrance(x,z)){
                for(int d=0;d<4;d++){
                    int dx=d==0?-1:d==1?1:0,dz=d==2?-1:d==3?1:0;
                    if(ground(x+dx,z+dz)!=NONE||rim(x+dx,z+dz))continue;
                    int outside=original[i+dx+dz*w];
                    if(top-outside>1)bottom=bottom==NONE?outside+1:Math.min(bottom,outside+1);
                }
            }
            out.add(new Column(x,z,original[i],top,distance[index(x,z)],bottom,entrance(x,z),z>=rearFromZ));
        }
        return List.copyOf(out);
    }

    int ground(int x,int z){return inside(x,z)?ground[index(x,z)]:NONE;}
    boolean needsNatural(int x,int z){return inside(x,z)&&exterior[index(x,z)]&&distance[index(x,z)]>0&&distance[index(x,z)]<=RADIUS+1;}
    // Only this immutable outer ring may be read back during writing. It is
    // disjoint from both the grading footprint and the separate entry approach.
    boolean readOnlySeam(int x,int z){return needsNatural(x,z)&&distance[index(x,z)]==RADIUS+1&&!entrance(x,z);}
    boolean rim(int x,int z){return inside(x,z)&&exterior[index(x,z)]&&distance[index(x,z)]>0&&distance[index(x,z)]<=RADIUS;}
    boolean entrance(int x,int z){return z<0&&Math.abs(x-entranceX)<=entranceHalfWidth;}
    boolean outerBoundary(int x,int z){
        if(!rim(x,z))return false;
        return outside(x-1,z)||outside(x+1,z)||outside(x,z-1)||outside(x,z+1);
    }
    private boolean outside(int x,int z){return ground(x,z)==NONE&&!rim(x,z);}
    private boolean inside(int x,int z){return x>=-padding&&x<sizeX+padding&&z>=-padding&&z<sizeZ+padding;}
    private int index(int x,int z){return (z+padding)*width+x+padding;}
    private boolean[] exterior(){
        var out=new boolean[ground.length];var queue=new int[ground.length];int head=0,tail=0;
        out[0]=true;queue[tail++]=0;
        while(head<tail){
            int i=queue[head++],x=i%width,z=i/width;
            if(x>0&&ground[i-1]==NONE&&!out[i-1]){out[i-1]=true;queue[tail++]=i-1;}
            if(x+1<width&&ground[i+1]==NONE&&!out[i+1]){out[i+1]=true;queue[tail++]=i+1;}
            if(z>0&&ground[i-width]==NONE&&!out[i-width]){out[i-width]=true;queue[tail++]=i-width;}
            if(z+1<depth&&ground[i+width]==NONE&&!out[i+width]){out[i+width]=true;queue[tail++]=i+width;}
        }
        return out;
    }
}
