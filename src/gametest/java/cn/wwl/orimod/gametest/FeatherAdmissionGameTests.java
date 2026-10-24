package cn.wwl.orimod.gametest;

import cn.wwl.orimod.movement.*;
import cn.wwl.orimod.spirit.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;

@GameTestHolder("orimod_gametest")
public final class FeatherAdmissionGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (boolean feather : new boolean[]{false, true}) for (boolean learned : new boolean[]{false, true}) {
            tests.add(new TestFunction("feather_admission", "feather_admission.equipped_" + feather + "_learned_" + learned,
                    "orimod_gametest:room", 40, 0, true, h -> {
                try (var c = Fixtures.connectedPlayer(h)) {
                    var p = c.player(); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, learned ? 4 : 0);
                    if (feather) p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                    p.setPos(h.absoluteVec(new Vec3(5.5, 5, 5.5))); p.setOnGround(false); p.setDeltaMovement(0, -.1, 0);
                    boolean expected = feather || learned;
                    h.assertTrue(FeatherGlide.permitted(p) == expected, "Feather OR Glide permission mismatch");
                    var s = p.getData(SpiritData.MOVEMENT); long now = p.level().getGameTime();
                    s.jumpHeld = true; s.jumpEdgeAt = now; s.jumpPerformedAt = now - 1; s.observedDy = -.1;
                    AirMovement.glide(p, s, now, false, false);
                    h.assertTrue((s.mode == MoveMode.GLIDE) == expected, "Server activation disagrees with permission");
                    if (expected) {
                        p.setDeltaMovement(0, -1, 0);
                        Fixtures.near(h, FeatherGlide.velocity(p, Vec3.ZERO).y, feather ? -.07 : -.12, "Equipment speed tier");
                    }
                    SpiritMovement.cancel(p, s);
                }
                h.succeed();
            }));
        }
        tests.add(new TestFunction("feather_admission", "feather_admission.tutorial_once_and_hints_use_equipment", "orimod_gametest:room", 40, 0, true, h -> {
            try (var c = Fixtures.connectedPlayer(h)) {
                var p = c.player(); SpiritForm.toggle(p); p.setData(SpiritData.ABILITIES, 0);
                h.assertFalse(FeatherGlide.offerTutorial(p), "Tutorial without feather");
                p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                h.assertTrue(FeatherGlide.offerTutorial(p), "First acquisition not explained");
                h.assertFalse(FeatherGlide.offerTutorial(p), "Repeated acquisition repeated tutorial");
                var hints = cn.wwl.orimod.progression.CompanionHints.hintAbilities(p);
                h.assertValueEqual(cn.wwl.orimod.progression.CompanionHints.wireId(cn.wwl.orimod.progression.CompanionHints.Hint.GINSO_WIND,hints), "ginso_wind", "Equipped feather still told to buy Glide");
                h.assertValueEqual(p.getData(SpiritData.ABILITIES), 0, "Equipment granted saved ability bit");
                p.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                h.assertFalse(FeatherGlide.permitted(p), "Unequipped feather retained permission");
            }
            h.succeed();
        }));
        tests.add(new TestFunction("feather_admission", "feather_admission.held_glide_survives_batched_position_packets", "orimod_gametest:room",40,0,true,h -> {
            try(var c=Fixtures.connectedPlayer(h)) {
                var p=c.player(); SpiritForm.toggle(p);p.setData(SpiritData.ABILITIES,0);
                p.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));
                p.setPos(h.absoluteVec(new Vec3(5.5,5,5.5)));p.setOnGround(false);
                var s=p.getData(SpiritData.MOVEMENT);long now=p.level().getGameTime();
                s.jumpHeld=true;s.jumpEdgeAt=now;s.jumpPerformedAt=now-1;s.observedDy=-.1;
                AirMovement.glide(p,s,now,false,false);
                for(double dy:new double[]{-.028,0,-.07,0,-.023}) {
                    s.observedDy=dy;AirMovement.glide(p,s,++now,false,false);
                    h.assertValueEqual(s.mode,MoveMode.GLIDE,"Valid held glide ended between position packets: "+dy);
                }
                h.assertValueEqual(s.startedGlides,1,"Held glide repeatedly restarted");
                s.observedDy=.08;AirMovement.glide(p,s,++now,false,false);
                h.assertValueEqual(s.mode,MoveMode.IDLE,"An upward external impulse kept ordinary glide");
                s.observedDy=0;AirMovement.glide(p,s,++now,false,false);
                h.assertValueEqual(s.mode,MoveMode.IDLE,"Initial glide no longer requires falling");
                s.observedDy=-.1;AirMovement.glide(p,s,++now,false,false);
                s.jumpHeld=false;AirMovement.glide(p,s,++now,false,false);
                h.assertValueEqual(s.mode,MoveMode.IDLE,"Released jump kept glide");
            }
            h.succeed();
        }));
        return tests;
    }
}
