package cn.wwl.orimod.progression;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class SkillBindingsTest {
    private static final int ALL=Ability.LIGHT_BURST.bit|Ability.CHARGE_FLAME.bit|Ability.FLASH.bit;
    @Test void legacyKeysKeepMuscleMemory(){assertEquals(List.of("light_burst","charge_flame","flash"),SkillBindings.EMPTY.migrate(true,ALL).slots());}
    @Test void oldAwakenedSaveGetsHealWithoutNewAbilityBit(){assertEquals(List.of("regenerate","",""),SkillBindings.EMPTY.migrate(true,0).slots());assertSame(SkillBindings.EMPTY,SkillBindings.EMPTY.migrate(false,ALL));}
    @Test void healFillsFirstGapOnly(){assertEquals(List.of("regenerate","charge_flame","flash"),SkillBindings.EMPTY.migrate(true,ALL&~Ability.LIGHT_BURST.bit).slots());}
    @Test void movingSkillClearsItsPreviousKey(){var b=SkillBindings.EMPTY.migrate(true,ALL).bind(2,WheelSkill.LIGHT_BURST,true,ALL);assertEquals(List.of("","charge_flame","light_burst"),b.slots());}
    @Test void cannotBindUnlearnedOrInvalidSlot(){var b=SkillBindings.EMPTY.migrate(true,0);assertSame(b,b.bind(1,WheelSkill.FLASH,true,0));assertSame(b,b.bind(-1,WheelSkill.REGENERATE,true,0));assertSame(b,b.bind(3,WheelSkill.REGENERATE,true,0));assertSame(b,b.bind(1,WheelSkill.REGENERATE,false,ALL));}
    @Test void migrationNeverOverwritesSavedArrangement(){var b=SkillBindings.EMPTY.migrate(true,ALL).bind(0,WheelSkill.REGENERATE,true,ALL);assertSame(b,b.migrate(true,ALL));}
    @Test void saveRoundTripAndCorruptDataAreBounded(){var b=SkillBindings.EMPTY.migrate(true,ALL).bind(2,WheelSkill.REGENERATE,true,ALL);var json=SkillBindings.CODEC.encodeStart(JsonOps.INSTANCE,b).getOrThrow();assertEquals(b,SkillBindings.CODEC.parse(JsonOps.INSTANCE,json).getOrThrow());assertEquals(List.of("flash","",""),new SkillBindings(List.of("flash","flash","hacked","regenerate"),true).slots());}
    @Test void newlyLearnedFillsOnlyFreeSlotAndDoesNotReinsertExistingSkill(){
        var initial=SkillBindings.EMPTY.migrate(true,0);
        var learned=initial.discover(true,Ability.CHARGE_FLAME.bit);
        assertEquals(List.of("regenerate","charge_flame",""),learned.slots());
        var moved=learned.bind(0,WheelSkill.CHARGE_FLAME,true,Ability.CHARGE_FLAME.bit);
        assertEquals(List.of("charge_flame","",""),moved.discover(true,Ability.CHARGE_FLAME.bit).slots());
        var full=SkillBindings.EMPTY.migrate(true,ALL&~Ability.FLASH.bit);
        assertEquals(full.slots(),full.discover(true,ALL).slots());
    }
    @Test void revokedSkillsCannotLinger(){assertEquals(List.of("","",""),SkillBindings.EMPTY.migrate(true,ALL).withoutUnlearned(true,0).slots());}
}
