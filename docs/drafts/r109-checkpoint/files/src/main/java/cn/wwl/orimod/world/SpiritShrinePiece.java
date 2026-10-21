package cn.wwl.orimod.world;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;

/** Independent arena template. Its saved contract is not recomputed when later templates change. */
public final class SpiritShrinePiece extends TemplateStructurePiece {
    public static final BlockPos ENTRY=ShrineContract.ENTRY;
    private static final ResourceLocation TEMPLATE=ResourceLocation.parse("orimod:spirit_shrine");
    private final ShrineContract contract;
    public SpiritShrinePiece(StructureTemplateManager manager,RuinPlacement at){super(OriWorldgen.SHRINE_PIECE.get(),0,manager,TEMPLATE,TEMPLATE.toString(),settings(at.rotation()),at.origin());contract=ShrineContract.at(at);}
    public SpiritShrinePiece(StructureTemplateManager manager,CompoundTag tag){super(OriWorldgen.SHRINE_PIECE.get(),tag,manager,s->settings(RuinPlacement.readRotation(tag.getString("Rot"))));contract=tag.contains("Contract")?ShrineContract.load(tag.getCompound("Contract")):null;}
    private static StructurePlaceSettings settings(Rotation rotation){return new StructurePlaceSettings().setIgnoreEntities(true).setRotation(rotation);}
    public ShrineContract contract(){return contract;}
    public RuinPlacement placement(){return new RuinPlacement(templatePosition,getRotation());}
    @Override protected void handleDataMarker(String name,BlockPos pos,ServerLevelAccessor level,RandomSource random,BoundingBox bounds){}
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext c,CompoundTag t){super.addAdditionalSaveData(c,t);t.putString("Rot",getRotation().name());if(contract!=null)t.put("Contract",contract.save());}
    @Override public void postProcess(WorldGenLevel level,StructureManager manager,ChunkGenerator gen,RandomSource random,BoundingBox bounds,ChunkPos chunk,BlockPos pivot){
        super.postProcess(level,manager,gen,random,bounds,chunk,pivot);
        for(int x=Math.max(boundingBox.minX(),bounds.minX());x<=Math.min(boundingBox.maxX(),bounds.maxX());x++)for(int z=Math.max(boundingBox.minZ(),bounds.minZ());z<=Math.min(boundingBox.maxZ(),bounds.maxZ());z++){
            RuinApproachPiece.clearCanopy(level,bounds,x,z,boundingBox.maxY()+1,boundingBox.maxY()+48);
            for(int depth=1;depth<=RuinSiteRules.MAX_FILL;depth++){
                var below=new BlockPos(x,templatePosition.getY()-depth,z);if(below.getY()<level.getMinBuildHeight() || level.getBlockState(below).isCollisionShapeFullBlock(level,below))break;
                level.setBlock(below,Blocks.STONE.defaultBlockState(),Block.UPDATE_CLIENTS);
            }
        }
        if(contract!=null && bounds.isInside(contract.altar()) && level.getBlockState(contract.altar()).is(cn.wwl.orimod.block.OriBlocks.SPIRIT_SHRINE_ALTAR))ShrineTrials.generated(level,contract);
    }
}
