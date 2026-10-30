package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import java.util.*;
import java.util.function.Predicate;

/** Bounded half-block vertical cells shared by submersion and rendering; never loads a chunk. */
public final class GinsoFloodVolume {
    public static final int MAX_CELLS = 65536;
    public record Cell(int x, int halfY, int z) {
        public double y() { return halfY * .5; }
        Cell neighbour(Direction d) { return new Cell(x + d.getStepX(), halfY + d.getStepY(), z + d.getStepZ()); }
    }
    public record Face(Direction direction, double x0, double y0, double z0, double x1, double y1, double z1) {}
    private final Set<Cell> cells;
    private GinsoFloodVolume(Set<Cell> cells) { this.cells = Set.copyOf(cells); }
    public Set<Cell> cells() { return cells; }
    public static Cell cell(double x, double y, double z) { return new Cell((int)Math.floor(x), (int)Math.floor(y * 2), (int)Math.floor(z)); }
    public boolean contains(double x, double y, double z) { return cells.contains(cell(x,y,z)); }
    public static GinsoFloodVolume of(List<GinsoEscapeLayout.Box> boxes) {
        var cells = new HashSet<Cell>();
        for (var b : boxes) {
            if (b.x1()-b.x0()>128 || b.z1()-b.z0()>128 || b.y1()-b.y0()>176) throw new IllegalArgumentException("Flood bounds");
            for (int x = (int)Math.floor(b.x0()); x < Math.ceil(b.x1()); x++)
                for (int z = (int)Math.floor(b.z0()); z < Math.ceil(b.z1()); z++)
                    for (int y = (int)Math.floor(b.y0()*2); y < Math.ceil(b.y1()*2); y++) {
                        cells.add(new Cell(x,y,z));
                        if (cells.size() > MAX_CELLS) throw new IllegalArgumentException("Flood cell budget exceeded");
                    }
        }
        return new GinsoFloodVolume(cells);
    }
    public GinsoFloodVolume clip(Predicate<Cell> passable) {
        var result = new HashSet<Cell>(); for (var c : cells) if (passable.test(c)) result.add(c);
        return new GinsoFloodVolume(result);
    }
    /** Frozen older root walks get bounded channels, not downward-extruded sky rectangles. */
    public static GinsoFloodVolume forPiece(Level level, GinsoPiece piece) {
        List<GinsoEscapeLayout.Box> candidates;
        if (piece.escapeVersion() == 2) candidates = piece.escape().flood();
        else {
            candidates = new ArrayList<>(); candidates.add(new GinsoEscapeLayout.Box(-4,90,-4,5,100,5));
            for (var b : piece.escape().route()) candidates.add(new GinsoEscapeLayout.Box(b.x0(),Math.max(90,b.y0()-1),b.z0(),b.x1(),b.y1(),b.z1()));
        }
        var open=new HashMap<Cell,Boolean>();
        Predicate<Cell> pass=c->open.computeIfAbsent(c,k->passable(level,piece.origin(),piece.entrance(),k));
        return of(candidates).enclosed(pass,c->loaded(level,piece.origin(),piece.entrance(),c)&&!pass.test(c));
    }
    private static final List<Direction> HORIZONTAL=List.of(Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST);
    /** Flood a horizontal component only if every boundary is actual collision, never open sky.
     * The same clipped volume is used for drowning and sent to the renderer. Each half-layer is
     * independent: an open crown lip cannot acquire a floating sheet above its enclosing wood. */
    public GinsoFloodVolume enclosed(Predicate<Cell> passable,Predicate<Cell> barrier) {
        var pending=new HashSet<Cell>();for(var c:cells)if(passable.test(c))pending.add(c);
        var result=new HashSet<Cell>();var queue=new ArrayDeque<Cell>();var component=new ArrayList<Cell>();
        while(!pending.isEmpty()){
            var first=pending.iterator().next();pending.remove(first);queue.add(first);component.clear();boolean closed=true;
            while(!queue.isEmpty()){
                var c=queue.removeFirst();component.add(c);
                for(var d:HORIZONTAL){var next=c.neighbour(d);
                    if(cells.contains(next)&&passable.test(next)){if(pending.remove(next))queue.add(next);}
                    else if(!barrier.test(next))closed=false;
                }
            }
            if(closed)result.addAll(component);
        }
        return new GinsoFloodVolume(result);
    }
    private static boolean loaded(Level level,BlockPos origin,int turn,Cell c){
        return level.hasChunkAt(origin.offset(GinsoInterior.rotate(new BlockPos(c.x(),Math.floorDiv(c.halfY(),2),c.z()),turn)));
    }
    public static boolean passable(Level level, BlockPos origin, int turn, Cell c) {
        var local = new BlockPos(c.x(), Math.floorDiv(c.halfY(),2), c.z());
        var pos = origin.offset(GinsoInterior.rotate(local,turn));
        if (!level.hasChunkAt(pos)) return false;
        double low = Math.floorMod(c.halfY(),2)*.5;
        var volume = new AABB(0,low,0,1,low+.5,1);
        for (var box : level.getBlockState(pos).getCollisionShape(level,pos).toAabbs())
            if (box.intersects(volume)) return false;
        // Fences and root lips extend into the next block, too.
        var below = pos.below();
        for (var box : level.getBlockState(below).getCollisionShape(level,below).toAabbs())
            if (box.move(0,-1,0).intersects(volume)) return false;
        return true;
    }
    /** Compact one-time packet: merge consecutive half-cells in each vertical column. */
    public List<GinsoEscapeLayout.Box> boxes() {
        var columns = new TreeMap<Long, TreeSet<Integer>>();
        for (var c : cells) columns.computeIfAbsent(((long)c.x()<<32)|(c.z()&0xffffffffL), k -> new TreeSet<>()).add(c.halfY());
        var result = new ArrayList<GinsoEscapeLayout.Box>();
        columns.forEach((key, heights) -> {
            int x=(int)(key>>32), z=(int)(long)key, start=heights.first(), previous=start;
            for (int y : heights.tailSet(start,false)) {
                if (y != previous+1) { result.add(new GinsoEscapeLayout.Box(x,start*.5,z,x+1,(previous+1)*.5,z+1)); start=y; }
                previous=y;
            }
            result.add(new GinsoEscapeLayout.Box(x,start*.5,z,x+1,(previous+1)*.5,z+1));
        });
        return List.copyOf(result);
    }
    /** Only exposed faces; neighbouring cells never double-blend internal faces. */
    public List<Face> faces(double water, Predicate<Cell> passable) {
        var filled = new HashSet<Cell>();
        for (var c : cells) if (c.y() < water && passable.test(c)) filled.add(c);
        var faces = new ArrayList<Face>();
        for (var c : filled) {
            double x=c.x(), y=c.y(), z=c.z(), top=Math.min(y+.5,water);
            for (var d : Direction.values()) if (!filled.contains(c.neighbour(d))) {
                faces.add(switch(d) {
                    case DOWN -> new Face(d,x,y,z,x+1,y,z+1);
                    case UP -> new Face(d,x,top,z,x+1,top,z+1);
                    case WEST -> new Face(d,x,y,z,x,top,z+1);
                    case EAST -> new Face(d,x+1,y,z,x+1,top,z+1);
                    case NORTH -> new Face(d,x,y,z,x+1,top,z);
                    case SOUTH -> new Face(d,x,y,z+1,x+1,top,z+1);
                });
            }
        }
        return List.copyOf(faces);
    }
}
