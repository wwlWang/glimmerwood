package cn.wwl.orimod.block;

import cn.wwl.orimod.world.RuinShrineBlock;
import cn.wwl.orimod.world.SpiritWellBlock;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;

/** Only these eight item-placed buildings own parts. Templates and the escape heart remain single cores. */
public enum SanctuaryKind implements StringRepresentable {
    WELL("glades_well"), FOREST("forest_spring_shrine"), CREEK("creek_ruins_shrine"), CAVE("cave_altar_shrine"),
    MAP("map_stone"), HEART("great_tree_heart"), GLOOM("gloom_altar"), STORM("storm_altar");
    private final String id;
    SanctuaryKind(String id){this.id=id;}
    @Override public String getSerializedName(){return id;}
    public boolean basin(){return ordinal()<=CAVE.ordinal();}
    public boolean shrine(){return this==FOREST||this==CREEK||this==CAVE;}
    public int radius(){return this==MAP?0:1;}
    public double floorLift(){return this==HEART?.062622093:0;}
    public Block block(){return switch(this){
        case WELL->OriBlocks.GLADES_WELL.get();case FOREST->OriBlocks.FOREST_SHRINE.get();
        case CREEK->OriBlocks.CREEK_SHRINE.get();case CAVE->OriBlocks.CAVE_SHRINE.get();
        case MAP->OriBlocks.MAP_STONE.get();case HEART->OriBlocks.GREAT_TREE_HEART.get();
        case GLOOM->OriBlocks.GLOOM_ALTAR.get();case STORM->OriBlocks.STORM_ALTAR.get();};}
    /** Safe even during Block's constructor: no access to deferred registry values/subclass fields. */
    public static boolean supports(Block block){return block instanceof SpiritWellBlock||block instanceof MapStoneBlock
            ||block instanceof GreatTreeHeartBlock||block instanceof GloomAltarBlock||block instanceof StormAltarBlock;}
    public static SanctuaryKind of(Block block){
        if(block instanceof RuinShrineBlock shrine)return switch(shrine.kind){case FOREST->FOREST;case CREEK->CREEK;case CAVE->CAVE;};
        if(block instanceof SpiritWellBlock)return WELL;
        if(block instanceof MapStoneBlock)return MAP;if(block instanceof GreatTreeHeartBlock)return HEART;
        if(block instanceof GloomAltarBlock)return GLOOM;if(block instanceof StormAltarBlock)return STORM;
        throw new IllegalArgumentException("Not an assembled sanctuary: "+block);
    }
}
