package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import java.util.*;

/** Saved course contracts. The released v1 field is never rebuilt from the current layout. */
public final class GinsoCourses {
    public static final int VERSION=2;
    public record Hop(String section,int floor,int top,int side,BlockPos start,BlockPos goal,
                      BlockPos interaction,byte kind) {}
    public record Layout(Map<BlockPos,Byte> blocks,List<GinsoInterior.Section> sections,List<BlockPos> wells,
                         BlockPos heart,List<Hop> hops,List<BlockPos> retreat) {
        public Layout {
            // BlockPos hashes cluster across this dense, tall lattice. Map.copyOf's
            // open-addressed MapN makes empty outer-chunk queries linear scans.
            // Keep an immutable view of a private chained hash map instead.
            blocks=Collections.unmodifiableMap(new HashMap<>(blocks));
            sections=List.copyOf(sections);wells=List.copyOf(wells);hops=List.copyOf(hops);retreat=List.copyOf(retreat);
        }
        public byte cell(int x,int y,int z){return blocks.getOrDefault(new BlockPos(x,y,z),(byte)0);}
        public GinsoInterior.Section section(double y){for(var s:sections)if(y<s.top()+1)return s;return null;}
    }
    private static final Layout EMPTY=new Layout(Map.of(),List.of(),List.of(),BlockPos.ZERO,List.of(),List.of());
    private static final Layout LEGACY=new Layout(GinsoInterior.layout().blocks(),GinsoInterior.SECTIONS,GinsoInterior.WELLS,GinsoInterior.HEART,List.of(),List.of());
    public static int forShape(int shape){return GinsoShape.checkedVersion(shape)<=2?1:2;}
    public static int checked(int shape,int course){
        // CourseVersion was absent before 993a88f: those already-built exteriors stay empty.
        if(course!=0&&course!=forShape(shape))throw new IllegalArgumentException("Ginso shape/course mismatch: "+shape+"/"+course);
        return course;
    }
    public static Layout layout(int version){return switch(version){case 0->EMPTY;case 1->LEGACY;case 2->TallGinsoInterior.layout();default->throw new IllegalArgumentException("Unknown Ginso course "+version);};}
    private GinsoCourses(){}
}
