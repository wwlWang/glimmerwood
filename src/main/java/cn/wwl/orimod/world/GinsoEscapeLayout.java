package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import java.util.*;
import static cn.wwl.orimod.world.GinsoInterior.*;
import static cn.wwl.orimod.world.TallGinsoInterior.*;

/** Seed-specific open root helix and fork walk. Gate 1 contains no challenge interaction. */
public final class GinsoEscapeLayout {
    public static final int VERSION=1;
    public static final byte ROOT_LIP=20;
    public static final BlockPos HEART=new BlockPos(0,94,0), SAFE=new BlockPos(0,94,1);
    public static final Box START_FORECOURT=new Box(0,94,-1,4,96,3);
    public record Point(int x,double feet,int z,double distance,int corners) {}
    public record Box(double x0,double y0,double z0,double x1,double y1,double z1) {
        public boolean contains(double x,double y,double z){return x>=x0&&x<x1&&y>=y0&&y<y1&&z>=z0&&z<z1;}
    }
    public record Audit(int clearanceRemoved,int trimmed,int maximumTrim,int bracedBlocks,int maximumSevered) {}
    public record Layout(Map<BlockPos,Byte> blocks,List<Point> path,List<Box> route,List<Box> flood,
                         Box exit,int height,int cleftIndex,int branchIndex,long seed,int turn,List<Box> clearance,Audit audit) {
        public Layout {blocks=Collections.unmodifiableMap(new HashMap<>(blocks));path=List.copyOf(path);route=List.copyOf(route);flood=List.copyOf(flood);clearance=List.copyOf(clearance);}
        public byte cell(int x,int y,int z){return blocks.getOrDefault(new BlockPos(x,y,z),(byte)0);}
        public boolean inRoute(double x,double y,double z){return route.stream().anyMatch(b->b.contains(x,y,z));}
        public boolean inFlood(double x,double y,double z){return flood.stream().anyMatch(b->b.contains(x,y,z));}
    }
    /** Keep the highest walk below the native hooked tips; the shortest trees
     * still retain eight blocks of ascent for a meaningful constant-speed chase. */
    public static int exitHeight(int treeHeight){return Math.max(102,treeHeight-11);}
    private double rise(){return exitHeight(shape.height)-94;}
    private record Surface(int x,int z,double feet) {}
    private record Key(long seed,int turn) {}
    private record Tune(int sign,int laps,double bias,double bend,Candidate candidate) {}
    private record Candidate(List<int[]> cells,int cleft,double length,double sweep,double bias) {}
    private record GuideKey(int sign,double sweep) {}
    private static final Layout EMPTY=new Layout(Map.of(),List.of(),List.of(),List.of(),new Box(0,0,0,0,0,0),0,0,0,0,0,List.of(),new Audit(0,0,0,0,0));
    private static final Map<Key,Layout> CACHE=new LinkedHashMap<>();
    private final Map<BlockPos,Byte> blocks=new HashMap<>();
    private final GinsoShape shape;private final long seed;private final int turn;
    private final List<Surface> floors=new ArrayList<>();
    private final Map<Long,List<Integer>> floorColumns=new HashMap<>();
    private static long column(int x,int z){return ((long)x<<32)^(z&0xffffffffL);}
    private List<Integer> at(int x,int z){return floorColumns.getOrDefault(column(x,z),List.of());}
    private void indexFloors(){floorColumns.clear();for(int i=0;i<floors.size();i++){var s=floors.get(i);floorColumns.computeIfAbsent(column(s.x(),s.z()),k->new ArrayList<>()).add(i);}}

    private final List<Point> path=new ArrayList<>();
    private final Map<GuideKey,List<double[]>> guides=new HashMap<>();
    private int saddleSide=1;
    private String rejected="";
    private Audit audit=new Audit(0,0,0,0,0);
    private GinsoEscapeLayout(GinsoShape shape,long seed,int turn){this.shape=shape;this.seed=seed;this.turn=turn;}
    public static int checked(int shape,int course,int escape){
        if(escape!=0&&(escape!=VERSION||shape!=3||course!=2))throw new IllegalArgumentException("Ginso escape version mismatch: "+shape+"/"+course+"/"+escape);
        return escape;
    }
    public static Layout empty(){return EMPTY;}
    public static synchronized Layout forTree(long seed,int turn,GinsoShape shape){
        var key=new Key(seed,Math.floorMod(turn,4));var existing=CACHE.get(key);if(existing!=null)return existing;
        if(shape.version!=3)throw new IllegalArgumentException("Escape needs shape 3");
        var built=new GinsoEscapeLayout(shape,seed,key.turn()).build();
        if(CACHE.size()>=32)CACHE.remove(CACHE.keySet().iterator().next());CACHE.put(key,built);return built;
    }
    private byte nativeCell(int x,int y,int z){return switch(turn){case 1->shape.cell(-z,y,x);case 2->shape.cell(-x,y,-z);case 3->shape.cell(z,y,-x);default->shape.cell(x,y,z);};}
    private static double[] rotate(double x,double z,int t){for(int i=0;i<Math.floorMod(t,4);i++){double a=x;x=-z;z=a;}return new double[]{x,z};}
    private double[] centre(double y){return rotate(TallGinsoShape.centerX(y),TallGinsoShape.centerZ(y),-turn);}
    private void put(int x,int y,int z,byte b){blocks.put(new BlockPos(x,y,z),b);}
    /** Find the actual surviving high fork, rather than duplicating its seed/phase recipe. */
    private double[] tip(){
        double sx=0,sz=0,n=0;int y=shape.height-5;
        for(int x=-30;x<=30;x++)for(int z=-30;z<=30;z++)if(GinsoShape.timber(nativeCell(x,y,z))&&Math.hypot(x,z)>10){sx+=x;sz+=z;n++;}
        if(n==0)throw new IllegalStateException("No surviving Ginso fork "+seed);
        // Land alongside the fork, not through its terminal wood. The original
        // hook and pointed tip remain above and beside the small root saddle.
        double x=sx/n,z=sz/n,r=Math.hypot(x,z);
        return new double[]{Math.rint(x-x/r*5-z/r*2*saddleSide),Math.rint(z-z/r*5+x/r*2*saddleSide)};
    }
    /** Above the broken rim there are gaps: bridge them, never fabricate a new cylindrical wall. */
    private double innerRadius(double a,double y){
        // Stay inside the three native fork arms. Open the route through a
        // measured bark cleft only where wood actually crosses this inner band.
        var c=centre(y);
        for(double r=5.3;r<=8;r+=.9){int x=(int)Math.round(c[0]+Math.cos(a)*r),z=(int)Math.round(c[1]+Math.sin(a)*r);
            if(GinsoShape.timber(nativeCell(x,(int)y,z)))return Math.clamp(r-1.0,5.8,6.3);
        }
        return 6.0+.25*Math.sin(a*2+y*.04);
    }
    private static void append(List<int[]> out,double x,double z){
        int xx=(int)Math.round(x),zz=(int)Math.round(z);
        if(out.isEmpty()){out.add(new int[]{xx,zz});return;}
        var last=out.getLast();int px=last[0],pz=last[1];
        while(px!=xx||pz!=zz){px+=Integer.signum(xx-px);pz+=Integer.signum(zz-pz);out.add(new int[]{px,pz});}
    }
    private List<double[]> guide(double start,double sweep,int sign){
        // Bias variants share their angles/heights and native-wall probes. Do
        // those probes once, rather than re-reading the same bark six times.
        return guides.computeIfAbsent(new GuideKey(sign,sweep),key->{
            var samples=new ArrayList<double[]>();
            for(double q=0;q<=sweep+.001;q+=.09){
                double t=Math.min(1,q/sweep),a=start+sign*q,y=94+(rise()-2.5)*t;
                var c=centre(y);
                samples.add(new double[]{y,Math.cos(a),Math.sin(a),c[0],c[1],innerRadius(a,y)});
            }
            return samples;
        });
    }
    private Candidate candidate(double[] tip,int sign,int laps,double bias,double bend){
        var out=new ArrayList<int[]>();append(out,2,0);append(out,3,1);
        var c=centre(94);double start=Math.atan2(-c[1],6-c[0]);
        double end=Math.atan2(tip[1]-c[1],tip[0]-c[0])-sign*bend;
        double sweep=Math.floorMod((long)Math.round(sign*(end-start)*1e9),(long)Math.round(2*Math.PI*1e9))/1e9+laps*2*Math.PI;
        if(sweep<2)return null;
        double pitch=(rise()-2.5)*2*Math.PI/sweep;
        if(pitch<3.6||pitch>24)return null;
        // Smooth the measured wall radius so bark noise does not become zigzag treads.
        var guide=guide(start,sweep,sign);double radius=guide.getFirst()[5]+bias;
        for(var sample:guide){
            double y=sample[0],cos=sample[1],sin=sample[2],cx=sample[3],cz=sample[4];
            radius+=.18*(sample[5]+bias-radius);
            if(y<99)radius=Math.max(radius,8.3+.8*(1-sin)+Math.max(0,cz)*Math.max(0,-sin));
            if(y<101&&sin<0){
                double westEast=(6.5-Math.signum(cos)*cx)/Math.max(.001,Math.abs(cos));
                double north=(-11.5-cz)/sin;
                if(cz+sin*radius< -3)radius=Math.max(radius,Math.min(westEast,north));
            }
            append(out,cx+cos*radius,cz+sin*radius);
        }
        int cleft=out.size()-1;var at=out.getLast();double a=start+sign*sweep;
        double len=Math.hypot(tip[0]-at[0],tip[1]-at[1]),helixLength=0;
        for(int i=1;i<out.size();i++)helixLength+=Math.hypot(out.get(i)[0]-out.get(i-1)[0],out.get(i)[1]-out.get(i-1)[1]);
        // Every possible hook is at least its chord long. Reject an overlong
        // helix before allocating / scoring four copies of the branch route.
        if(helixLength+len>9.5*rise())return null;
        var hooks=new ArrayList<Candidate>();
        // A hooked branch supplies the missing horizontal run where another full
        // helix lap would be too long. Reuse the measured helix for all three hooks.
        for(double hook:new double[]{4,8,12,16,20,24,28,32}){
            var walk=new ArrayList<int[]>(out);
            double c1x=at[0]-Math.sin(a)*sign*hook,c1z=at[1]+Math.cos(a)*sign*hook;
            double c2x=tip[0]-(tip[0]-at[0])/len*3,c2z=tip[1]-(tip[1]-at[1])/len*3;
            for(double t=.02;t<=1.001;t+=.02){double u=1-t;
                append(walk,u*u*u*at[0]+3*u*u*t*c1x+3*u*t*t*c2x+t*t*t*tip[0],u*u*u*at[1]+3*u*u*t*c1z+3*u*t*t*c2z+t*t*t*tip[1]);}
            double distance=0;for(int i=1;i<walk.size();i++)distance+=Math.hypot(walk.get(i)[0]-walk.get(i-1)[0],walk.get(i)[1]-walk.get(i-1)[1]);
            var cnd=new Candidate(walk,cleft,distance,sweep,bias);
            if(Double.isFinite(cheapScore(cnd)))hooks.add(cnd);
        }
        // The full score is either this cheap value or infinity (intersection).
        // Stable sorting preserves the old tie-break. Stop at the first valid
        // hook instead of checking all eight quadratically for every candidate.
        hooks.sort(Comparator.comparingDouble(this::cheapScore));
        for(var hook:hooks)if(Double.isFinite(score(hook)))return hook;
        return null;
    }
    private final Map<Candidate,Double> scores=new IdentityHashMap<>();
    private double score(Candidate c){return scores.computeIfAbsent(c,this::computeScore);}
    private double cheapScore(Candidate c){
        // At .25 blocks/s versus a 2.5 blocks/s walker, ten horizontal blocks
        // consume the water's one-block rise. Keep a modest lead, not a dry final lap.
        double rise=rise(),target=9.5*rise-10;
        if(c.length()>target+10||c.length()<62)return Double.POSITIVE_INFINITY;
        double score=Math.abs(c.length()-target)+Math.abs(c.bias())*.2;
        if(c.length()>target+3||c.length()<10*rise-43)score+=1000;
        return score;
    }
    private double computeScore(Candidate c){
        double cost=cheapScore(c);if(!Double.isFinite(cost))return cost;
        double rise=rise();
        double[] ys=new double[c.cells().size()];double travelled=0;
        for(int i=0;i<ys.length;i++){
            var p=c.cells().get(i);if(i>0){var q=c.cells().get(i-1);travelled+=Math.hypot(p[0]-q[0],p[1]-q[1]);}
            ys[i]=94+Math.floor(2*rise*Math.clamp((travelled-3)/(c.length()-3),0,1)+1e-8)/2;
            for(int j=0;j<i-8;j++){var q=c.cells().get(j);
                if(Math.max(Math.abs(p[0]-q[0]),Math.abs(p[1]-q[1]))<=3&&ys[i]-ys[j]<4)return Double.POSITIVE_INFINITY;
            }
        }
        return cost;
    }
    private Layout build(){
        var tip=tip();double rise=rise();var choices=new ArrayList<Candidate>();
        var coarse=new ArrayList<Tune>();
        for(int sign:new int[]{1,-1})for(int laps=0;laps<7;laps++)for(double bias=-.6;bias<=3.41;bias+=.8)for(double bend:new double[]{.2,.8,1.4,2,2.6,3.2,3.8,4.4,5,5.6}){
            var c=candidate(tip,sign,laps,bias,bend);if(c==null)continue;
            coarse.add(new Tune(sign,laps,bias,bend,c));double score=score(c);if(Double.isFinite(score))choices.add(c);
        }
        coarse.sort(Comparator.comparingDouble(t->Math.abs(t.candidate().length()-(9.5*rise-10))));
        for(var t:coarse.subList(0,Math.min(5,coarse.size())))for(double db:new double[]{-.2,0,.2})for(double da=-.3;da<=.301;da+=.1){
            var c=candidate(tip,t.sign(),t.laps(),t.bias()+db,t.bend()+da);if(c==null)continue;double score=score(c);if(Double.isFinite(score))choices.add(c);
        }
        choices.sort(Comparator.comparingDouble(this::score));int attempts=0;
        var tried=new HashSet<String>();var rejects=new TreeMap<String,Integer>();
        for(var choice:choices){
            StringBuilder signature=new StringBuilder();for(var v:choice.cells())signature.append(v[0]).append(",").append(v[1]).append(";");String key=signature.toString();
            if(!tried.add(key))continue;
            var builder=new GinsoEscapeLayout(shape,seed,turn);var layout=builder.finish(choice);attempts++;
            if(builder.valid(layout)){
                builder.preserveNativeWood();
                return new Layout(builder.blocks,layout.path(),layout.route(),layout.flood(),layout.exit(),layout.height(),layout.cleftIndex(),layout.branchIndex(),seed,turn,layout.clearance(),builder.audit);
            }
            rejects.merge(builder.rejected,1,Integer::sum);
        }
        if(saddleSide==1){saddleSide=-1;return build();}
        throw new IllegalStateException("No safe Ginso helix "+seed+"/"+turn+" candidates="+attempts+" "+rejects);
    }
    private Layout finish(Candidate best){
        double rise=rise();
        double d=0;
        for(int i=0;i<best.cells().size();i++){
            var p=best.cells().get(i);if(i>0){var a=best.cells().get(i-1);d+=Math.hypot(p[0]-a[0],p[1]-a[1]);}
            double progress=Math.clamp((d-3)/(best.length()-3),0,1),feet=94+Math.floor(2*rise*progress+1e-8)/2;
            path.add(new Point(p[0],feet,p[1],d,Math.min(6,(int)(progress*6))));
        }
        var columns=new HashMap<BlockPos,TreeSet<Double>>();
        for(var p:path)if(p.distance()>1)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(dx*dx+dz*dz<=2)columns.computeIfAbsent(new BlockPos(p.x()+dx,0,p.z()+dz),k->new TreeSet<>()).add(p.feet());
        var end=path.getLast();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<=5)
            columns.computeIfAbsent(new BlockPos(end.x()+dx,0,end.z()+dz),k->new TreeSet<>()).add(end.feet());
        columns.forEach((p,ys)->{double last=-999,top=-999;for(double y:ys){if(y-last>2.5&&top>-999)floors.add(new Surface(p.getX(),p.getZ(),top));top=y;last=y;}floors.add(new Surface(p.getX(),p.getZ(),top));});
        // Relax in a stable order: Minecraft BlockPos and the offline coordinate
        // stub have different hashes, which must not select different corner treads.
        floors.sort(Comparator.comparingDouble(Surface::feet).thenComparingInt(Surface::x).thenComparingInt(Surface::z));indexFloors();
        // Full-width half steps, including diagonal turns; never join separate helical laps.
        for(boolean changed=true;changed;){changed=false;
            for(int i=0;i<floors.size();i++){var s=floors.get(i);double top=s.feet();
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0)
                    for(int j:at(s.x()+dx,s.z()+dz)){var q=floors.get(j);if(Math.abs(q.feet()-s.feet())<2.5)top=Math.max(top,q.feet()-.5);}
                if(top>s.feet()){floors.set(i,new Surface(s.x(),s.z(),top));changed=true;}
            }
            // A diagonal sweep touches both inside corners. Raise its endpoints
            // if a nearby tread rounds that corner higher; do not ask the player's
            // body to cut through a half slab before Minecraft can step up.
            for(int i=1;i<path.size();i++){
                var a=path.get(i-1);var b=path.get(i);
                if(a.x()==b.x()||a.z()==b.z())continue;
                int ai=floorIndex(a.x(),a.z(),a.feet()),bi=floorIndex(b.x(),b.z(),b.feet());
                if(ai<0||bi<0)continue;
                double top=Math.max(floors.get(ai).feet(),floors.get(bi).feet());
                int ac=floorIndex(a.x(),b.z(),top),bc=floorIndex(b.x(),a.z(),top);
                double corner=Math.max(ac<0?top:floors.get(ac).feet(),bc<0?top:floors.get(bc).feet());
                if(corner>top){
                    for(int j:new int[]{ai,bi}){var s=floors.get(j);floors.set(j,new Surface(s.x(),s.z(),corner));}
                    changed=true;
                }
            }
        }
        floors.sort(Comparator.comparingDouble(Surface::feet).thenComparingInt(Surface::x).thenComparingInt(Surface::z));indexFloors();
        // Grow a curved, tapered root spine underneath only unsupported treads.
        for(var s:floors){int y=(int)Math.ceil(s.feet())-1;
            for(int depth=1;depth<=2;depth++)if(y-depth>=93&&Math.floorMod(s.x()*7+s.z()*11,5)>=depth-1)put(s.x(),y-depth,s.z(),WOOD);
        }
        for(var s:floors){int lower=(int)Math.ceil(s.feet()),top=(int)Math.ceil(s.feet()+3);
            for(int y=lower;y<top;y++)put(s.x(),y,s.z(),AIR);
        }
        for(var s:floors){int y=(int)Math.ceil(s.feet())-1;put(s.x(),y,s.z(),s.feet()==Math.rint(s.feet())?CUSHION:STEP);}
        for(var s:floors)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0)continue;int x=s.x()+dx,z=s.z()+dz;
            if(x>=0&&x<=3&&Math.abs(z)<=1&&s.feet()<95)continue;
            boolean inside=at(x,z).stream().anyMatch(j->Math.abs(floors.get(j).feet()-s.feet())<=1.5);
            if(inside)continue;
            int y=(int)Math.ceil(s.feet());
            // Keep real bark as the guard where it exists; open edges get the same
            // 1.5-block fence collision as the accepted course, with embedded sap below.
            if(!GinsoShape.timber(nativeCell(x,y,z))||!GinsoShape.timber(nativeCell(x,y+1,z)))put(x,y,z,ROOT_LIP);
            put(x,y-1,z,Math.floorMod(x*7+z*11+y,6)==0?GinsoShape.SAP:WOOD);
        }
        // Restore clearance/floors after guards at overlaps, including the crack through a fork.
        for(var s:floors)for(int y=(int)Math.ceil(s.feet());y<Math.ceil(s.feet()+3);y++)put(s.x(),y,s.z(),AIR);
        for(var s:floors){int y=(int)Math.ceil(s.feet())-1;put(s.x(),y,s.z(),s.feet()==Math.rint(s.feet())?CUSHION:STEP);}
        for(int x=3;x<=4;x++)for(int z=-1;z<=1;z++)for(int y=94;y<=97;y++)put(x,y,z,AIR);
        // The already reviewed manual return gates; no per-player door state or auto-close.
        for(int z=-4;z<=4;z++){put(-1,93,z,WOOD);for(int y=94;y<=97;y++)put(-1,y,z,WOOD);}
        for(int z=0;z<=1;z++){put(-2,93,z,STEP);put(-1,94,z,DOOR);put(-1,95,z,DOOR_TOP);put(-1,96,z,AIR);}
        // Replace only exposed top-course window/light cells; published EscapeVersion=0 is untouched.
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++)for(int y=94;y<=98;y++){
            byte old=GinsoCourses.layout(2).cell(x,y,z);
            if(old==WINDOW&&!blocks.containsKey(new BlockPos(x,y,z)))put(x,y,z,WOOD);
            if(old==LIGHT&&!blocks.containsKey(new BlockPos(x,y,z)))put(x,y,z,GinsoShape.SAP);
        }
        // Preserve the reserved room/return corridor. Upper root undersides may not
        // hang into the three-block clearance above the water-heart approach.
        for(int x=0;x<=2;x++)for(int z=-2;z<=2;z++)for(int y=94;y<=96;y++)put(x,y,z,AIR);
        blocks.remove(HEART);
        for(var well:GinsoCourses.layout(2).wells())blocks.remove(well);
        for(int z=-3;z>=-7;z--)for(int y=94;y<=96;y++)blocks.remove(new BlockPos(0,y,z));
        // The existing return well remains enclosed; the escape root may not
        // shave its side wall open and expose the previous 91-level landing.
        for(int x=-4;x<=4;x++)for(int z=-9;z<=-5;z++)for(int y=94;y<=98;y++)
            if(GinsoCourses.layout(2).cell(x,y,z)==WOOD)blocks.remove(new BlockPos(x,y,z));
        var route=new ArrayList<Box>();double walked=0,previous=94;
        for(int i=0;i<path.size();i++){
            var p=path.get(i);double actual=at(p.x(),p.z()).stream().map(floors::get).filter(s->Math.abs(s.feet()-p.feet())<=1.5).mapToDouble(Surface::feet).max().orElse(p.feet());
            if(i>0){var a=path.get(i-1);walked+=Math.sqrt(Math.pow(p.x()-a.x(),2)+Math.pow(p.z()-a.z(),2)+Math.pow(actual-previous,2));}
            path.set(i,new Point(p.x(),actual,p.z(),walked,p.corners()));previous=actual;
            route.add(new Box(p.x()-1,Math.max(94,actual-.5),p.z()-1,p.x()+2,actual+2.5,p.z()+2));
        }
        route.add(new Box(-1,94,-1,4,97,3));
        end=path.getLast();var exit=new Box(end.x()-1,end.feet(),end.z()-1,end.x()+2,end.feet()+2.5,end.z()+2);route.add(exit);
        var flood=new ArrayList<Box>();flood.add(new Box(-4,90,-4,5,100,5));
        for(var b:route)flood.add(new Box(b.x0(),90,b.z0(),b.x1(),end.feet(),b.z1()));
        int cleft=0;
        for(int i=1;i<best.cleft();i++){
            var p=path.get(i);int y=(int)Math.ceil(p.feet());
            if(GinsoShape.timber(nativeCell(p.x(),y,p.z()))||GinsoShape.timber(nativeCell(p.x(),y+1,p.z())))cleft=i;
        }
        return new Layout(blocks,path,route,flood,exit,shape.height,cleft,best.cleft(),seed,turn,floors.stream().map(s->new Box(s.x(),Math.ceil(s.feet()),s.z(),s.x()+1,Math.ceil(s.feet()+3),s.z()+1)).toList(),audit);
    }
    private byte combined(int x,int y,int z){byte c=cellAt(x,y,z);if(c==0)c=GinsoCourses.layout(2).cell(x,y,z);return c==0?nativeCell(x,y,z):c;}
    private byte cellAt(int x,int y,int z){return blocks.getOrDefault(new BlockPos(x,y,z),(byte)0);}
    private static double collision(byte c){return switch(c){case 0,AIR,GinsoShape.VINE,FLOWER,LAMP->0;case STEP->.5;case RAIL,ROOT_LIP->1.5;case WIND->.1875;default->1;};}
    private boolean clear(double x,double y,double z,double h){
        for(int xx=(int)Math.floor(x-.3+1e-6);xx<=(int)Math.floor(x+.3-1e-6);xx++)for(int zz=(int)Math.floor(z-.3+1e-6);zz<=(int)Math.floor(z+.3-1e-6);zz++)
            for(int yy=(int)Math.floor(y)-1;yy<y+h-1e-6;yy++){double c=collision(combined(xx,yy,zz));if(c>0&&yy+c>y+1e-6)return false;}return true;
    }
    /** Fine checks run only after the cheap centreline score, before native braces. */
    private boolean valid(Layout e){
        if(e.path().getLast().distance()<65){rejected="length";return false;}
        var plan=GinsoEscapeRules.plan(e);if(GinsoEscapeRules.stationaryFailureSeconds(plan,1.62)>25){rejected="idle";return false;}
        var samples=GinsoEscapeRules.samples(e);int n=samples.size();
        for(int i=0;i<n;i++){var s=samples.get(i);double margin=s.waterAtHumanEyes()-s.referenceArrival();
            if(i<=n/3&&margin>20||i>=n*2/3&&margin>25){rejected="margin";return false;}}
        for(int i=0;i<e.path().size();i++){
            var p=e.path().get(i);if(!clear(p.x()+.5,p.feet(),p.z()+.5,3)){rejected="headroom";return false;}
            if(i==0)continue;var a=e.path().get(i-1);if(Math.abs(p.feet()-a.feet())>.5){rejected="step";return false;}
            for(int j=0;j<=4;j++){double t=j/4.0;if(!clear(a.x()+.5+(p.x()-a.x())*t,Math.max(p.feet(),a.feet()),a.z()+.5+(p.z()-a.z())*t,1.8)){rejected="sweep";return false;}}
        }
        return true;
    }
    private static final int SIDE=71,DEPTH=32,GRID=SIDE*SIDE*DEPTH;
    private static int index(int x,int y,int z){return ((x+35)*SIDE+z+35)*DEPTH+y-93;}
    private static int gx(int i){return i/DEPTH/SIDE-35;}
    private static int gz(int i){return i/DEPTH%SIDE-35;}
    private static int gy(int i){return i%DEPTH+93;}
    private static int neighbour(int i,int d){
        return switch(d){case 0->gx(i)<35?i+SIDE*DEPTH:-1;case 1->gx(i)>-35?i-SIDE*DEPTH:-1;
            case 2->gz(i)<35?i+DEPTH:-1;case 3->gz(i)>-35?i-DEPTH:-1;
            case 4->gy(i)<124?i+1:-1;default->gy(i)>93?i-1:-1;};
    }
    private static boolean structural(byte c){return c==WOOD||c==GinsoShape.SAP||c==CUSHION||c==STEP||c==ROOT_LIP||c==RAIL||c==DOOR||c==DOOR_TOP||c==WELL;}
    private void preserveNativeWood(){
        // No detached native component is deleted, of any size. Never turn
        // a clearance cut into recursive branch pruning. A short root grows
        // around the clearance instead, using only otherwise unoccupied cells.
        blocks.entrySet().removeIf(e->e.getValue()==AIR&&GinsoShape.timber(nativeCell(e.getKey().getX(),e.getKey().getY(),e.getKey().getZ()))
            &&at(e.getKey().getX(),e.getKey().getZ()).stream().map(floors::get).noneMatch(f->e.getKey().getY()>=Math.ceil(f.feet())&&e.getKey().getY()<Math.ceil(f.feet()+3)));
        boolean[] solid=new boolean[GRID],attached=new boolean[GRID],cut=new boolean[GRID],forbidden=new boolean[GRID],seen=new boolean[GRID];
        int[] queue=new int[GRID];int head=0,tail=0,cleared=0,braced=0,largest=0;
        for(int x=-35;x<=35;x++)for(int z=-35;z<=35;z++)for(int y=93;y<=124;y++)solid[index(x,y,z)]=GinsoShape.timber(nativeCell(x,y,z));
        for(var e:GinsoCourses.layout(2).blocks().entrySet()){
            var p=e.getKey();if(p.getY()>=93&&p.getY()<=124)solid[index(p.getX(),p.getY(),p.getZ())]=structural(e.getValue());
        }
        for(var e:blocks.entrySet()){
            var p=e.getKey();int i=index(p.getX(),p.getY(),p.getZ());solid[i]=structural(e.getValue());forbidden[i]=!solid[i];
            if(e.getValue()==AIR&&GinsoShape.timber(nativeCell(p.getX(),p.getY(),p.getZ()))){cut[i]=true;cleared++;}
        }
        for(int i=0;i<GRID;i++)if(gy(i)==93&&solid[i]){queue[tail++]=i;attached[i]=true;}
        while(head<tail){int i=queue[head++];for(int d=0;d<6;d++){int j=neighbour(i,d);if(j>=0&&solid[j]&&!attached[j]){attached[j]=true;queue[tail++]=j;}}}
        for(int first=0;first<GRID;first++)if(solid[first]&&!attached[first]&&!seen[first]){
            var component=new ArrayList<Integer>();head=0;tail=0;queue[tail++]=first;seen[first]=true;boolean severed=false;
            while(head<tail){int i=queue[head++];component.add(i);for(int d=0;d<6;d++){int j=neighbour(i,d);if(j<0)continue;
                severed|=cut[j];if(solid[j]&&!attached[j]&&!seen[j]){seen[j]=true;queue[tail++]=j;}}}
            if(!severed)continue;largest=Math.max(largest,component.size());
            int[] previous=new int[GRID];Arrays.fill(previous,-1);head=0;tail=0;
            for(int i:component){previous[i]=i;queue[tail++]=i;}int reached=-1;
            while(head<tail&&reached<0){int i=queue[head++];for(int d=0;d<6;d++){int j=neighbour(i,d);if(j<0||previous[j]>=0||forbidden[j])continue;
                previous[j]=i;if(attached[j]){reached=j;break;}queue[tail++]=j;}}
            if(reached<0)throw new IllegalStateException("No safe fork brace "+seed+"/"+turn+" size="+component.size());
            for(int i=reached;previous[i]!=i;i=previous[i]){
                if(!solid[i]){put(gx(i),gy(i),gz(i),WOOD);braced++;solid[i]=true;}attached[i]=true;
            }
            for(int i:component)attached[i]=true;
        }
        audit=new Audit(cleared,0,0,braced,largest);
    }
    private int floorIndex(int x,int z,double near){
        int found=-1;double distance=2;
        for(int i:at(x,z)){var s=floors.get(i);double d=Math.abs(s.feet()-near);
            if(s.x()==x&&s.z()==z&&d<distance){found=i;distance=d;}}
        return found;
    }
}
