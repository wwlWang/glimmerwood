package cn.wwl.orimod.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.*;

/** Immutable, three slots. Migration never binds unlearned skills or overwrites a player's saved arrangement. */
public record SkillBindings(List<String> slots, boolean initialized, List<String> known) {
    public static final SkillBindings EMPTY=new SkillBindings(List.of("","",""),false);
    public static final Codec<SkillBindings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.STRING.listOf().optionalFieldOf("slots",List.of("","","")).forGetter(SkillBindings::slots),
            Codec.BOOL.optionalFieldOf("initialized",false).forGetter(SkillBindings::initialized),
            Codec.STRING.listOf().optionalFieldOf("known",List.of()).forGetter(SkillBindings::known)).apply(i,SkillBindings::new));
    public SkillBindings(List<String> slots,boolean initialized){this(slots,initialized,List.of());}
    public SkillBindings {
        var valid=new ArrayList<String>();
        for(int i=0;i<3;i++){String id=i<slots.size()?slots.get(i):"";valid.add(WheelSkill.byId(id)!=null&&!valid.contains(id)?id:"");}
        slots=List.copyOf(valid);
        known=known.stream().filter(id->WheelSkill.byId(id)!=null).distinct().toList();
    }
    public WheelSkill skill(int slot) { return slot>=0&&slot<3?WheelSkill.byId(slots.get(slot)):null; }
    public SkillBindings migrate(boolean awakened,int learned) {
        if(initialized||!awakened)return this;
        var result=new ArrayList<>(List.of("","",""));
        var defaults=List.of(WheelSkill.LIGHT_BURST,WheelSkill.CHARGE_FLAME,WheelSkill.FLASH);
        for(int i=0;i<3;i++)if(defaults.get(i).learned(awakened,learned))result.set(i,defaults.get(i).id());
        int free=result.indexOf("");if(free>=0)result.set(free,WheelSkill.REGENERATE.id());
        return new SkillBindings(result,true,learnedIds(awakened,learned));
    }
    public SkillBindings bind(int slot,WheelSkill skill,boolean awakened,int learned) {
        if(slot<0||slot>=3||skill==null||!skill.learned(awakened,learned))return this;
        var result=new ArrayList<>(slots);
        for(int i=0;i<3;i++)if(result.get(i).equals(skill.id()))result.set(i,"");
        result.set(slot,skill.id());return new SkillBindings(result,true,known);
    }
    private static List<String> learnedIds(boolean awakened,int bits) {
        return Arrays.stream(WheelSkill.values()).filter(s->s.learned(awakened,bits)).map(WheelSkill::id).toList();
    }
    /** Only learning edges fill slots. Existing learned skills deliberately moved out never reappear on login. */
    public SkillBindings discover(boolean awakened,int bits) {
        if(!initialized)return this;
        var current=learnedIds(awakened,bits);var result=new ArrayList<>(slots);
        for(String id:current)if(!known.contains(id)&&!result.contains(id)){
            int free=result.indexOf("");if(free>=0)result.set(free,id);
        }
        return new SkillBindings(result,initialized,current);
    }
    public SkillBindings withoutUnlearned(boolean awakened,int bits) {
        return new SkillBindings(slots.stream().map(id->{var s=WheelSkill.byId(id);return s!=null&&s.learned(awakened,bits)?id:"";}).toList(),initialized,known);
    }
}
