package cn.wwl.orimod.world;

import cn.wwl.orimod.mixin.StructurePaletteInvoker;
import cn.wwl.orimod.mixin.StructureTemplatePaletteAccessor;
import com.google.common.collect.MapMaker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

/** Creek v1: immutable template-local column index; settings and selected palette are invocation-local. */
public final class CreekSliceSettings extends StructurePlaceSettings {
    // Weak identity keys let a discarded template/world/resource reload release its index.
    // Values retain block infos, not their owning Palette. Reads never acquire the cold-build lock.
    private static final ConcurrentMap<StructureTemplate.Palette,LocalColumns> INDEXES=new MapMaker().weakKeys().makeMap();
    private record Selection(StructureTemplate.Palette source,BlockPos origin,Mirror mirror,Rotation rotation,
                             BlockPos pivot,int minX,int minZ,int maxX,int maxZ,StructureTemplate.Palette palette) {}
    private Selection selection;

    /** Called at manager initialisation/reload; piece constructors keep an idempotent safety check. */
    public static void prepare(StructureTemplate template){
        for(var palette:((StructureTemplatePaletteAccessor)template).orimod$palettes())index(palette);
    }
    public static void prewarm(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager manager,String phase){
        long start=Boolean.getBoolean("orimod.profileRuins")?System.nanoTime():0;
        RuinKind.CREEK.layout(1);CreekRuinTerrain.prepare();
        var template=manager.get(RuinKind.CREEK.template(1));
        template.ifPresent(CreekSliceSettings::prepare);
        CreekPlacementTrace.prepareDiagnostics();
        long end=start==0?0:System.nanoTime();
        if(start!=0){
            template.ifPresent(t->{for(var palette:((StructureTemplatePaletteAccessor)t).orimod$palettes()){
                var ready=INDEXES.get(palette);
                cn.wwl.orimod.OriMod.LOGGER.info("CREEK_COLUMN_INDEX infos={} columns={} ms={}",palette.blocks().size(),ready.columns.length,ready.buildNanos/1e6);
            }});
            cn.wwl.orimod.OriMod.LOGGER.info("CREEK_TEMPLATE_PREWARM phase={} ms={}",phase,(end-start)/1e6);
        }
    }
    /** Readiness observation does not itself load or build the index. */
    public static boolean prepared(StructureTemplate template){
        var palettes=((StructureTemplatePaletteAccessor)template).orimod$palettes();
        return !palettes.isEmpty()&&palettes.stream().allMatch(INDEXES::containsKey);
    }
    private static LocalColumns index(StructureTemplate.Palette palette){
        var ready=INDEXES.get(palette);if(ready!=null)return ready;
        synchronized(palette){
            ready=INDEXES.get(palette);if(ready!=null)return ready;
            ready=new LocalColumns(palette.blocks());INDEXES.put(palette,ready);
            return ready;
        }
    }
    @Override public StructureTemplate.Palette getRandomPalette(List<StructureTemplate.Palette> palettes,BlockPos origin){
        var selected=super.getRandomPalette(palettes,origin);var clip=getBoundingBox();
        // A processor can move blocks or inspect the complete input in finalizeProcessing.
        // Keep its original whole-palette semantics and positional random sequence.
        if(origin==null||clip==null||getProcessors().stream().anyMatch(p->!(p instanceof CreekRimDecorationProcessor))||!isIgnoreEntities()
                ||(clip.minX()>>4)!=(clip.maxX()>>4)||(clip.minZ()>>4)!=(clip.maxZ()>>4))return selected;
        var s=selection;
        if(s==null||s.source!=selected||!s.origin.equals(origin)||s.mirror!=getMirror()||s.rotation!=getRotation()
                ||!s.pivot.equals(getRotationPivot())||s.minX!=clip.minX()||s.minZ!=clip.minZ()||s.maxX!=clip.maxX()||s.maxZ!=clip.maxZ()){
            var result=StructurePaletteInvoker.orimod$create(index(selected).slice(origin,this,clip));
            selection=new Selection(selected,origin.immutable(),getMirror(),getRotation(),getRotationPivot().immutable(),clip.minX(),clip.minZ(),clip.maxX(),clip.maxZ(),result);
        }
        return selection.palette;
    }

    /** Source indices, never rewritten states/NBT. Final arrays are private and never exposed or mutated. */
    static final class LocalColumns {
        private static final ThreadLocal<BitSet> SELECTED=ThreadLocal.withInitial(BitSet::new);
        private final List<StructureTemplate.StructureBlockInfo> infos;
        private final int minX,minZ,width,depth;
        private final int[][] columns;
        private final long buildNanos;
        LocalColumns(List<StructureTemplate.StructureBlockInfo> source){
            long start=Boolean.getBoolean("orimod.profileRuins")?System.nanoTime():0;
            infos=List.copyOf(source);
            for(var info:infos)if(info.state()!=null)CreekShapeUpdates.unchanged(info.state().getBlock());
            int x0=Integer.MAX_VALUE,z0=Integer.MAX_VALUE,x1=Integer.MIN_VALUE,z1=Integer.MIN_VALUE;
            for(var info:infos){x0=Math.min(x0,info.pos().getX());z0=Math.min(z0,info.pos().getZ());x1=Math.max(x1,info.pos().getX());z1=Math.max(z1,info.pos().getZ());}
            if(infos.isEmpty()){minX=minZ=width=depth=0;columns=new int[0][];buildNanos=0;return;}
            minX=x0;minZ=z0;width=x1-x0+1;depth=z1-z0+1;
            var counts=new int[width*depth];for(var info:infos)counts[column(info.pos())]++;
            columns=new int[counts.length][];for(int i=0;i<counts.length;i++)columns[i]=new int[counts[i]];
            Arrays.fill(counts,0);
            for(int i=0;i<infos.size();i++){int c=column(infos.get(i).pos());columns[c][counts[c]++]=i;}
            buildNanos=start==0?0:System.nanoTime()-start;
        }
        private int column(BlockPos p){return (p.getZ()-minZ)*width+p.getX()-minX;}
        List<StructureTemplate.StructureBlockInfo> slice(BlockPos origin,StructurePlaceSettings settings,BoundingBox clip){
            if(infos.isEmpty())return List.of();
            int lowX=Integer.MAX_VALUE,lowZ=Integer.MAX_VALUE,highX=Integer.MIN_VALUE,highZ=Integer.MIN_VALUE;
            int px=settings.getRotationPivot().getX(),pz=settings.getRotationPivot().getZ();
            for(int wx:new int[]{clip.minX(),clip.maxX()})for(int wz:new int[]{clip.minZ(),clip.maxZ()}){
                int x=wx-origin.getX(),z=wz-origin.getZ(),a,b;
                // Undo rotation around pivot, then undo mirror (mirror is its own inverse).
                switch(settings.getRotation()){
                    case CLOCKWISE_90->{a=z-pz+px;b=px+pz-x;}
                    case CLOCKWISE_180->{a=2*px-x;b=2*pz-z;}
                    case COUNTERCLOCKWISE_90->{a=px+pz-z;b=x-px+pz;}
                    default->{a=x;b=z;}
                }
                if(settings.getMirror()==Mirror.FRONT_BACK)a=-a;
                else if(settings.getMirror()==Mirror.LEFT_RIGHT)b=-b;
                lowX=Math.min(lowX,a);highX=Math.max(highX,a);lowZ=Math.min(lowZ,b);highZ=Math.max(highZ,b);
            }
            lowX=Math.max(lowX,minX);highX=Math.min(highX,minX+width-1);lowZ=Math.max(lowZ,minZ);highZ=Math.min(highZ,minZ+depth-1);
            if(lowX>highX||lowZ>highZ)return List.of();
            // Per-worker scratch, not a mutable palette/piece cache. Enumerating
            // set bits merges source indices in original order without sorting
            // or allocating a fresh int[] on every chunk.
            var selected=SELECTED.get();selected.clear();int count=0;
            for(int z=lowZ;z<=highZ;z++)for(int x=lowX;x<=highX;x++){
                var column=columns[(z-minZ)*width+x-minX];count+=column.length;
                for(int i:column)selected.set(i);
            }
            var out=new ArrayList<StructureTemplate.StructureBlockInfo>(count);
            for(int i=selected.nextSetBit(0);i>=0;i=selected.nextSetBit(i+1))out.add(infos.get(i));
            return Collections.unmodifiableList(out); // no second reference-array copy
        }
    }
    // Inherited copy() intentionally makes ordinary settings for external callers.
}
