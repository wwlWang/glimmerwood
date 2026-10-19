package cn.wwl.orimod.gametest;

import cn.wwl.orimod.progression.SoulLink;
import cn.wwl.orimod.spirit.SpiritData;
import cn.wwl.orimod.spirit.SpiritForm;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

/** R34: Soul Link spends energy, stores the link through the data attachment and checks safety. */
@GameTestHolder("orimod_gametest")
public final class SoulLinkGameTests {
    @GameTestGenerator
    public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "creates_link_and_spends_energy", h -> {
            var p = Fixtures.player(h); SpiritForm.toggle(p); p.setOnGround(true); p.setData(SpiritData.ENERGY, 60f);
            h.assertTrue(SoulLink.create(p), "Soul Link refused for a ready spirit");
            var link = p.getData(SpiritData.SOUL_LINK);
            h.assertTrue(link.isPresent() && link.get().pos().equals(p.blockPosition()), "Link not stored at the player");
            Fixtures.near(h, p.getData(SpiritData.ENERGY), 60 - SoulLink.COST, "Energy after Soul Link");
            h.assertFalse(SoulLink.create(p), "Second link ignored the cooldown");
        });
        add(tests, "refuses_without_energy_or_spirit", h -> {
            var p = Fixtures.player(h); p.setOnGround(true); p.setData(SpiritData.ENERGY, 100f);
            h.assertFalse(SoulLink.create(p), "Human form created a Soul Link");
            SpiritForm.toggle(p); p.setData(SpiritData.ENERGY, 10f);
            h.assertFalse(SoulLink.create(p), "Soul Link without enough energy");
            h.assertTrue(p.getData(SpiritData.SOUL_LINK).isEmpty(), "Refused link was stored");
            Fixtures.near(h, p.getData(SpiritData.ENERGY), 10, "Refused link spent energy");
        });
        add(tests, "blocked_link_is_unsafe", h -> {
            BlockPos at = h.absolutePos(new BlockPos(5, 2, 5));
            h.assertTrue(SoulLink.usable(h.getLevel(), at), "Open floor should be a safe link");
            h.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);
            h.assertFalse(SoulLink.usable(h.getLevel(), at), "Head block filled but link still safe");
        });
        add(tests, "spirit_pulls_nearby_light_only", h -> {
            var p = Fixtures.player(h);
            var near = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), p.getX() + 2, p.getY(), p.getZ(),
                    new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.SPIRIT_LIGHT.get()));
            var far = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), p.getX(), p.getY(), p.getZ() + 3.5,
                    new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.SPIRIT_LIGHT.get()));
            near.setNoPickUpDelay(); far.setNoPickUpDelay(); near.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); far.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            h.getLevel().addFreshEntity(near); h.getLevel().addFreshEntity(far);
            try {
                h.assertValueEqual(cn.wwl.orimod.progression.SpiritProgression.pullLight(p), 0, "Human form pulled light");
                SpiritForm.toggle(p);
                h.assertValueEqual(cn.wwl.orimod.progression.SpiritProgression.pullLight(p), 1, "Spirit pulled count");
                h.assertTrue(near.getDeltaMovement().x < -.3, "Near orb not pulled toward the player");
                h.assertTrue(far.getDeltaMovement().lengthSqr() < 1e-6, "Orb beyond 2.5 blocks pulled without Gathering");
            } finally { near.discard(); far.discard(); }
        });
        return tests;
    }

    private static void add(Collection<TestFunction> tests, String name, Consumer<GameTestHelper> run) {
        tests.add(new TestFunction("soul_link", "soul_link." + name, "orimod_gametest:room", 30, 0, true,
                h -> { run.accept(h); h.succeed(); }));
    }
}
