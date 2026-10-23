package cn.wwl.orimod.world;
import cn.wwl.orimod.entity.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Separate from ruin contracts. Merchant slots are consumed once, even on repeated chunk postProcess. */
public final class GladesOutpostPiece extends TemplateStructurePiece implements net.neoforged.neoforge.common.world.PieceBeardifierModifier {
    public static final BlockPos SIZE=new BlockPos(31,18,31), ENTRY=new BlockPos(15,3,0), WELL=new BlockPos(15,3,19);
    public static final java.util.List<BlockPos> MERCHANTS=java.util.List.of(new BlockPos(7,3,13),new BlockPos(23,3,13));
    private static final ResourceLocation TEMPLATE=ResourceLocation.parse("orimod:glades_outpost");
    private int spawned;
    /** Only loaded starts/references, shared by entry lines and devtools. */
    public static GladesOutpostPiece at(net.minecraft.server.level.ServerLevel level,BlockPos pos) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null)return null;
        for(var start:chunk.getAllStarts().values()){var piece=containing(start,pos);if(piece!=null)return piece;}
        for(var entry:chunk.getAllReferences().entrySet())if(entry.getKey() instanceof GladesOutpostStructure)for(long reference:entry.getValue()){
            var at=new ChunkPos(reference);var origin=level.getChunkSource().getChunkNow(at.x,at.z);
            if(origin!=null){var piece=containing(origin.getStartForStructure(entry.getKey()),pos);if(piece!=null)return piece;}
        }
        return null;
    }
    private static GladesOutpostPiece containing(StructureStart start,BlockPos pos){
        if(start==null || !start.isValid())return null;
        for(var piece:start.getPieces())if(piece instanceof GladesOutpostPiece outpost && piece.getBoundingBox().isInside(pos))return outpost;
        return null;
    }
    public GladesOutpostPiece(StructureTemplateManager m,RuinPlacement at){super(OriWorldgen.OUTPOST_PIECE.get(),0,m,TEMPLATE,TEMPLATE.toString(),settings(at.rotation()),at.origin());}
    public GladesOutpostPiece(StructureTemplateManager m,CompoundTag tag){super(OriWorldgen.OUTPOST_PIECE.get(),tag,m,s->settings(RuinPlacement.readRotation(tag.getString("Rot"))));spawned=tag.getInt("Merchants")&3;}
    /** Terrace edges blend into the slope instead of a five-block cut or fill wall; base = the courtyard floor. */
    @Override public BoundingBox getBeardifierBox(){return boundingBox;}
    @Override public TerrainAdjustment getTerrainAdjustment(){return TerrainAdjustment.BEARD_THIN;}
    @Override public int getGroundLevelDelta(){return ENTRY.getY();}
    private static StructurePlaceSettings settings(Rotation r){return new StructurePlaceSettings().setIgnoreEntities(true).setRotation(r);}
    @Override protected void handleDataMarker(String name,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox bounds) {}
    public RuinPlacement placement(){return new RuinPlacement(templatePosition,getRotation());}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag t){super.addAdditionalSaveData(c,t);t.putString("Rot",getRotation().name());t.putInt("Merchants",spawned);}
    @Override public synchronized void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator gen,RandomSource random,BoundingBox bounds,ChunkPos chunk,BlockPos pivot){
        super.postProcess(level,manager,gen,random,bounds,chunk,pivot);
        for(int x=Math.max(boundingBox.minX(),bounds.minX());x<=Math.min(boundingBox.maxX(),bounds.maxX());x++)for(int z=Math.max(boundingBox.minZ(),bounds.minZ());z<=Math.min(boundingBox.maxZ(),bounds.maxZ());z++)
            RuinApproachPiece.clearCanopy(level,bounds,x,z,boundingBox.maxY()+1,boundingBox.maxY()+48);
        // Extend the template's two dirt layers onto sloping ground, bounded like ruin approaches.
        // Only the current generation chunk is touched; floor/canopy coordinates remain unchanged.
        for(int x=Math.max(boundingBox.minX(),bounds.minX());x<=Math.min(boundingBox.maxX(),bounds.maxX());x++)for(int z=Math.max(boundingBox.minZ(),bounds.minZ());z<=Math.min(boundingBox.maxZ(),bounds.maxZ());z++){
            for(int depth=1;depth<=RuinSiteRules.MAX_FILL;depth++){
                var below=new BlockPos(x,templatePosition.getY()-depth,z);if(below.getY()<level.getMinBuildHeight())break;
                if(level.getBlockState(below).isCollisionShapeFullBlock(level,below))break;
                level.setBlock(below,net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(),2);
            }
        }
        for(int i=0;i<2;i++){
            var pos=placement().block(MERCHANTS.get(i));if((spawned&(1<<i))!=0 || !bounds.isInside(pos))continue;spawned|=1<<i;
            var npc=OriMobs.GLADES_MERCHANT.get().create(level.getLevel());if(npc==null)continue;
            npc.bind(i,placement().block(WELL),pos,getRotation().rotate(Direction.NORTH).toYRot());npc.setPersistenceRequired();
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(npc,level,level.getCurrentDifficultyAt(pos),net.minecraft.world.entity.MobSpawnType.STRUCTURE,null);level.addFreshEntity(npc);
        }
    }
}
