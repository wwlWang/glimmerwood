package cn.wwl.orimod.movement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TargetPermissionTest {
    @Test void eachKindRequiresItsOwnSkillAndSpiritForm() {
        for (boolean spirit : new boolean[]{false,true}) for (boolean bash : new boolean[]{false,true})
            for (boolean grapple : new boolean[]{false,true}) {
                assertEquals(spirit && bash, SpiritTargets.permitted(spirit,bash,grapple,false));
                assertEquals(spirit && grapple, SpiritTargets.permitted(spirit,bash,grapple,true));
            }
    }
}
