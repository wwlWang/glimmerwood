package cn.wwl.orimod.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;

/** The animal remains the vanilla entity. No inventory/name/age snapshot is rolled back. */
public record CorruptionState(String dimension,long core,int exposure,boolean infected,double distance) {
    public static final CorruptionState CLEAN=new CorruptionState("",0,0,false,0);
    public static final Codec<CorruptionState> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(1,1).optionalFieldOf("version").forGetter(s->java.util.Optional.of(1)),
            Codec.STRING.fieldOf("dimension").forGetter(CorruptionState::dimension),Codec.LONG.fieldOf("core").forGetter(CorruptionState::core),
            Codec.intRange(0,24000).fieldOf("exposure").forGetter(CorruptionState::exposure),Codec.BOOL.fieldOf("infected").forGetter(CorruptionState::infected),
            Codec.doubleRange(0,64).fieldOf("distance").forGetter(CorruptionState::distance)).apply(i,(version,dimension,core,exposure,infected,distance)->new CorruptionState(dimension,core,exposure,infected,distance)));
    public BlockPos center(){return BlockPos.of(core);}
    public CorruptionState exposed(int ticks,boolean infection){return new CorruptionState(dimension,core,ticks,infection,distance);}
}
