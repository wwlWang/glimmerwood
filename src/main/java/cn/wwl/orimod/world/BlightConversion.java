package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import java.util.*;

/** Small loaded-only transactions. Neither water nor trunks are conversion targets. */
public final class BlightConversion {
    public static boolean flower(BlockState s){return !s.hasBlockEntity()&&(s.is(BlockTags.FLOWERS)&&s.getBlock() instanceof BushBlock
            ||s.is(Blocks.SHORT_GRASS)||s.is(Blocks.TALL_GRASS)||s.is(Blocks.FERN)||s.is(Blocks.LARGE_FERN));}
    public static boolean removableCover(BlockState s){return flower(s)||s.is(Blocks.SNOW);}
    public static boolean leaf(BlockState s){return s.getBlock() instanceof LeavesBlock&&!s.hasBlockEntity()&&!s.getValue(LeavesBlock.PERSISTENT)
            &&BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace().equals("minecraft")&&s.getFluidState().isEmpty();}
    public static boolean exposed(BlockState top){return !top.hasBlockEntity()&&top.getFluidState().isEmpty()&&(top.isAir()||flower(top)||top.is(Blocks.SNOW)||top.is(BlockTags.LOGS)
            ||top.canBeReplaced()&&!top.blocksMotion());}
    public static List<BlightRegion.Change> ground(ServerLevel level,BlockPos floor,BlightEcologyRules.Budget b,long salt){
        if(b.remainingReads()<4||b.remainingWrites()<1)return List.of();
        b.read();var soil=level.getBlockState(floor);if(!BlightProtection.soil(soil))return List.of();
        b.read();var top=level.getBlockState(floor.above());if(!exposed(top))return List.of();
        var batch=new ArrayList<BlightRegion.Change>();batch.add(new BlightRegion.Change(floor,soil,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState()));
        if(top.is(Blocks.SNOW))batch.add(new BlightRegion.Change(floor.above(),top,Blocks.AIR.defaultBlockState()));
        else if(flower(top)){
            if(top.hasProperty(DoublePlantBlock.HALF)){
                if(top.getValue(DoublePlantBlock.HALF)!=DoubleBlockHalf.LOWER)return List.of();
                b.read();var upper=level.getBlockState(floor.above(2));
                if(!upper.is(top.getBlock())||!upper.hasProperty(DoublePlantBlock.HALF)||upper.getValue(DoublePlantBlock.HALF)!=DoubleBlockHalf.UPPER)return List.of();
                batch.add(new BlightRegion.Change(floor.above(),top,Blocks.AIR.defaultBlockState()));batch.add(new BlightRegion.Change(floor.above(2),upper,Blocks.AIR.defaultBlockState()));
            }else batch.add(new BlightRegion.Change(floor.above(),top,Math.floorMod(salt,7)==0?OriBlocks.GLOOM_THORNS.get().defaultBlockState():Blocks.AIR.defaultBlockState()));
        }else if(top.isAir()&&Math.floorMod(salt,31)==0)batch.add(new BlightRegion.Change(floor.above(),top,OriBlocks.GLOOM_THORNS.get().defaultBlockState()));
        return batch;
    }
    /** Revisit initial and grown columns; repeated growth reuses the original undo record. */
    public static List<BlightRegion.Change> plants(ServerLevel level,BlockPos floor,BlightRegion journal,BlightEcologyRules.Budget b){
        if(b.remainingReads()<3)return List.of();
        b.read();if(!level.getBlockState(floor).is(OriBlocks.BLIGHTED_SOIL))return List.of();
        b.read();var lower=level.getBlockState(floor.above());if(!removableCover(lower))return List.of();
        var positions=new ArrayList<BlockPos>();positions.add(floor.above());
        if(lower.hasProperty(DoublePlantBlock.HALF)){
            if(lower.getValue(DoublePlantBlock.HALF)!=DoubleBlockHalf.LOWER)return List.of();
            b.read();var upper=level.getBlockState(floor.above(2));
            if(!upper.is(lower.getBlock())||upper.getValue(DoublePlantBlock.HALF)!=DoubleBlockHalf.UPPER)return List.of();
            positions.add(floor.above(2));
        }
        var result=new ArrayList<BlightRegion.Change>();
        for(var pos:positions){
            var old=journal.changeAt(pos);var expected=old==null?Blocks.AIR.defaultBlockState():old.expected();
            // Re-growth / fresh snowfall may reuse cover records, never a journalled tree/soil.
            if(!expected.isAir()&&!expected.is(OriBlocks.GLOOM_THORNS))return List.of();
            if(!b.read())return List.of();result.add(new BlightRegion.Change(pos,level.getBlockState(pos),expected));
        }
        return result;
    }
    /** CAS the entire transaction before reserving its journal entry or doing any write. */
    public static boolean commit(ServerLevel level,BlightData data,BlockPos center,BlockPos floor,List<BlightRegion.Change> batch,boolean decoration,BlightEcologyRules.Budget b){
        if(batch.isEmpty()||batch.size()>b.remainingReads()||batch.size()>b.remainingWrites())return false;
        for(var c:batch){b.read();if(!level.getBlockState(c.pos()).equals(c.before()))return false;}
        if(!(decoration?data.recordDecoration(center,floor,batch):data.recordGrowth(center,floor,batch)))return false;
        // All affected plant halves are in this transaction. Shape propagation between writes
        // could break an unsupported flower first and spawn vanilla drops outside our journal.
        for(var c:batch){b.write();level.setBlock(c.pos(),c.expected(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);}
        return true;
    }
    private BlightConversion(){}
}
