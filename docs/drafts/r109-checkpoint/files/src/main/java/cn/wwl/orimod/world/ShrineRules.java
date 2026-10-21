package cn.wwl.orimod.world;

import cn.wwl.orimod.progression.Shard;
import java.util.*;

/** Side-effect-free trial rules. Death retains credit; leaving permanently forfeits it. */
public final class ShrineRules {
    public static final int MAX_PLAYERS=8, TIMEOUT=6000, WARNING=20, INTERMISSION=60;
    public enum MobKind { SPITTER, BEETLE, HAWK }
    public enum Participant { ACTIVE, DEAD, LEFT }
    public enum Phase { IDLE, SEALING, WARNING, FIGHT, INTERMISSION, CLEANUP }
    public record Reward(int shard, int light) {}
    public static List<MobKind> wave(int number,int players){
        if(number<1 || number>3 || players<1 || players>MAX_PLAYERS)throw new IllegalArgumentException("Invalid shrine wave/party");
        var mobs=new ArrayList<MobKind>(switch(number){
            case 1->List.of(MobKind.SPITTER,MobKind.SPITTER,MobKind.SPITTER);
            case 2->List.of(MobKind.BEETLE,MobKind.BEETLE,MobKind.SPITTER,MobKind.SPITTER);
            default->List.of(MobKind.HAWK,MobKind.HAWK,MobKind.BEETLE,MobKind.SPITTER,MobKind.SPITTER);
        });
        for(int i=0;i<Math.min(3,players-1);i++)mobs.add(MobKind.SPITTER);
        return List.copyOf(mobs);
    }
    public static boolean allOut(Collection<Participant> people){return people.stream().noneMatch(p->p==Participant.ACTIVE);}
    public static boolean rewardEligible(Participant state){return state!=Participant.LEFT;}
    public static boolean timedOut(long elapsed){return elapsed>=TIMEOUT;}
    public static Reward reward(int owned,boolean seen,int roll){
        if(seen)return new Reward(0,20);
        var missing=Arrays.stream(Shard.values()).filter(s->(owned&s.bit)==0).toList();
        return missing.isEmpty()?new Reward(0,200):new Reward(missing.get(Math.floorMod(roll,missing.size())).bit,50);
    }
    private ShrineRules(){}
}
