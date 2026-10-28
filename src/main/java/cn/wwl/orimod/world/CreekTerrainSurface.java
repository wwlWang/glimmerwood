package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Rim ground includes any supporting outcrop, not a finite list of rock types. */
public final class CreekTerrainSurface {
    public static final TagKey<Block> IGNORE=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("orimod","terrain_scan_ignore"));
    /** Mutation permission is separate from the deliberately broad ground scan.
     * Unknown supporting blocks still count as ground, but are never bulldozed. */
    public static final TagKey<Block> NATURAL=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("orimod","terrain_grading_natural"));
    public static boolean natural(BlockState state){return state.is(NATURAL);}
    public static final TagKey<Block> PROTECTED=TagKey.create(Registries.BLOCK,ResourceLocation.fromNamespaceAndPath("orimod","terrain_scan_protected"));
    public enum Category { NATURAL, IGNORE_SOFT, IGNORE_PROTECTED, BUILDING }
    public static boolean ignored(Category category){return category==Category.IGNORE_SOFT||category==Category.IGNORE_PROTECTED;}
    /** The scanner and fill permission share this one classification. */
    public static Category category(BlockState state,BlockGetter level,BlockPos pos){
        if(state.is(PROTECTED))return Category.IGNORE_PROTECTED;
        if(state.is(IGNORE))return Category.IGNORE_SOFT;
        if(natural(state))return Category.NATURAL;
        if(state.isAir()||state.canBeReplaced()||state.getCollisionShape(level,pos).isEmpty())return Category.IGNORE_SOFT;
        return Category.BUILDING;
    }
    public static boolean building(BlockState state,BlockGetter level,BlockPos pos,boolean ownGuard){
        return !ownGuard&&category(state,level,pos)==Category.BUILDING;
    }
    public static boolean replaceable(BlockState state,BlockGetter level,BlockPos pos){
        return mutable(state,level,pos)&&category(state,level,pos)==Category.IGNORE_SOFT;
    }
    /** Independent of natural/building classification, including replaceable BEs. */
    public static boolean mutable(BlockState state,BlockGetter level,BlockPos pos){
        return mutable(state.getDestroySpeed(level,pos),state.hasBlockEntity());
    }
    static boolean mutable(float destroySpeed,boolean blockEntity){return destroySpeed>=0&&!blockEntity;}
    public static boolean ground(BlockState state,BlockGetter level,BlockPos pos){
        if(ignored(category(state,level,pos))||!state.getFluidState().isEmpty())return false;
        return state.isFaceSturdy(level,pos,Direction.UP);
    }
    /** Actual collision top in world coordinates. NaN means no standing surface.
     * Classification/ownership is deliberately separate: protected vegetation is
     * still omitted by the terrain scanner, without changing its physical shape. */
    public static double standingTop(BlockState state,BlockGetter level,BlockPos pos){
        var shape=state.getCollisionShape(level,pos);
        return shape.isEmpty()?Double.NaN:pos.getY()+shape.max(Direction.Axis.Y);
    }
    /** Conservatively round the drop UP to half-block units. A half/full step is
     * permitted; >2 units (>1 block) needs protection. Never round a slab up to
     * a full block before comparing it with the departing surface. */
    public static int dropHalfUnits(double from,double to){
        return (int)Math.ceil(Math.max(0,from-to)*2-1e-7);
    }
    public static boolean standingCandidate(BlockState state,BlockGetter level,BlockPos pos){
        return !ignored(category(state,level,pos))&&Double.isFinite(standingTop(state,level,pos));
    }
    private CreekTerrainSurface(){}
}
