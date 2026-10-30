package cn.wwl.orimod.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;

/** Immutable saved progress. Missing fields from older saves retain explicit defaults. */
public record SpiritProgress(int version, int light, int ownedShards, int equippedShards,
                             int claimedWeapons, int capacityLevel, Map<String, Integer> rewards, int wisps) {
    public static final int VERSION = 1, LIGHT_LIMIT = 1_000_000;
    public static final SpiritProgress EMPTY = new SpiritProgress(VERSION, 0, 0, 0, 0, 0, Map.of());
    public static final Codec<SpiritProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("version", VERSION).forGetter(SpiritProgress::version),
            Codec.INT.optionalFieldOf("light", 0).forGetter(SpiritProgress::light),
            Codec.INT.optionalFieldOf("owned_shards", 0).forGetter(SpiritProgress::ownedShards),
            Codec.INT.optionalFieldOf("equipped_shards", 0).forGetter(SpiritProgress::equippedShards),
            Codec.INT.optionalFieldOf("claimed_weapons", 0).forGetter(SpiritProgress::claimedWeapons),
            Codec.INT.optionalFieldOf("capacity_level", 0).forGetter(SpiritProgress::capacityLevel),
            Codec.INT.optionalFieldOf("wisps", 0).forGetter(SpiritProgress::wisps),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("rewards", Map.of()).forGetter(SpiritProgress::rewards)
    ).apply(instance, (v,l,o,e,w,c, wisps,r)->new SpiritProgress(v,l,o,e,w,c,r,wisps)));

    /** Existing construction sites and pre-R108 saves start without any collected wisps. */
    public SpiritProgress(int version,int light,int owned,int equipped,int weapons,int capacity,Map<String,Integer> rewards){this(version,light,owned,equipped,weapons,capacity,rewards,0);}
    public SpiritProgress {
        version = Math.max(VERSION, version);
        light = Math.clamp(light, 0, LIGHT_LIMIT);
        ownedShards &= 2047; // R99: eleven shards; the original six bits retain their meanings.
        equippedShards &= ownedShards;
        claimedWeapons &= WeaponGift.MASK;
        capacityLevel = Math.clamp(capacityLevel, 0, 3);
        rewards = Map.copyOf(rewards);
        wisps &= 7;
    }
    public SpiritProgress withLight(int value) { return new SpiritProgress(version, value, ownedShards, equippedShards, claimedWeapons, capacityLevel, rewards, wisps); }
    public SpiritProgress withOwned(int value) { return new SpiritProgress(version, light, value, equippedShards, claimedWeapons, capacityLevel, rewards, wisps); }
    public SpiritProgress withEquipped(int value) { return new SpiritProgress(version, light, ownedShards, value, claimedWeapons, capacityLevel, rewards, wisps); }
    public SpiritProgress withClaims(int value) { return new SpiritProgress(version, light, ownedShards, equippedShards, value, capacityLevel, rewards, wisps); }
    public SpiritProgress withCapacity(int value) { return new SpiritProgress(version, light, ownedShards, equippedShards, claimedWeapons, value, rewards, wisps); }
    public SpiritProgress withRewards(Map<String, Integer> value) { return new SpiritProgress(version, light, ownedShards, equippedShards, claimedWeapons, capacityLevel, value, wisps); }
    public SpiritProgress withWisps(int value){return new SpiritProgress(version,light,ownedShards,equippedShards,claimedWeapons,capacityLevel,rewards,value);}
}
