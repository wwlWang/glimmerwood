package cn.wwl.orimod.gametest;

import cn.wwl.orimod.entity.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

/** Exercise the actual entity-data wire format, then read it through the same getters used by the client visual. */
@GameTestHolder("orimod_gametest")
public final class GloomVisualSyncGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        add(tests, "hawk_countdown", h -> {
            var server = OriMobs.GLOOM_HAWK.get().create(h.getLevel());
            var client = OriMobs.GLOOM_HAWK.get().create(h.getLevel());
            try {
                server.telegraph(Fixtures.player(h));
                for (int tick = 0; tick <= GloomHawk.TELEGRAPH_TICKS; tick++) {
                    copy(server, client); h.assertValueEqual(client.state(), server.state(), "Hawk state");
                    h.assertValueEqual(client.timer(), server.timer(), "Hawk countdown not synchronized");
                    if (tick < GloomHawk.TELEGRAPH_TICKS) { h.assertValueEqual(client.timer(), GloomHawk.TELEGRAPH_TICKS - tick, "Changed warning duration"); server.step(); }
                }
                h.assertValueEqual(client.state(), GloomHawk.DIVE, "Hawk did not dive at original deadline");
            } finally { server.discard(); client.discard(); }
        });
        add(tests, "matriarch_countdown_and_enrage", h -> {
            var server = OriMobs.GLOOM_MATRIARCH.get().create(h.getLevel());
            var client = OriMobs.GLOOM_MATRIARCH.get().create(h.getLevel());
            try {
                server.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(8, 8, 8))); server.setNoAi(true);
                for (int attack : new int[]{GloomMatriarch.DIVE_TELEGRAPH, GloomMatriarch.FEATHERS, GloomMatriarch.GUST}) {
                    server.startAttack(attack, Fixtures.player(h)); int total = GloomMatriarch.telegraph(attack);
                    for (int tick = 0; tick < total; tick++) {
                        copy(server, client); h.assertValueEqual(client.attack(), server.attack(), "Matriarch action");
                        h.assertValueEqual(client.timer(), total - tick, "Matriarch countdown"); server.step();
                    }
                    copy(server, client); h.assertValueEqual(client.attack(), server.attack(), "Release action");
                    h.assertTrue(client.attack() != attack, "Changed matriarch warning duration");
                }
                server.tickCount = 500; server.enrage();
                for (int tick = 0; tick <= 40; tick++) {
                    copy(server, client); h.assertValueEqual(client.enrageRemaining(), 40 - tick, "Matriarch enrage countdown");
                    h.assertValueEqual(client.phase(), 2, "Matriarch phase");
                    h.assertValueEqual(client.enrageRemaining() > 0, server.enraging(), "Client/server enrage differs");
                    if (tick < 40) { server.tickCount++; server.tick(); } // ServerLevel increments age before Entity.tick.
                }
            } finally { cleanup(h, server); client.discard(); }
        });
        add(tests, "heart_countdown_and_enrage", h -> {
            var server = OriMobs.GLOOM_HEART.get().create(h.getLevel());
            var client = OriMobs.GLOOM_HEART.get().create(h.getLevel());
            try {
                server.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(8, 8, 8))); server.setNoAi(true);
                server.startAttack(GloomHeart.RING, Fixtures.player(h));
                for (int tick = 0; tick < GloomHeart.RING_TELEGRAPH; tick++) {
                    copy(server, client); h.assertValueEqual(client.timer(), GloomHeart.RING_TELEGRAPH - tick, "Heart countdown"); server.tickAttack();
                }
                copy(server, client); h.assertValueEqual(client.attack(), GloomHeart.IDLE, "Heart release deadline");
                server.tickCount = 500; server.enrage();
                for (int tick = 0; tick <= 40; tick++) {
                    copy(server, client); h.assertValueEqual(client.enrageRemaining(), 40 - tick, "Heart enrage countdown");
                    h.assertValueEqual(client.phase(), 2, "Heart phase");
                    h.assertValueEqual(client.enrageRemaining() > 0, server.enraging(), "Client/server enrage differs");
                    if (tick < 40) { server.tickCount++; server.tick(); } // ServerLevel increments age before Entity.tick.
                }
            } finally { cleanup(h, server); client.discard(); }
        });
        return tests;
    }
    private static void copy(Entity server, Entity client) {
        var dirty = server.getEntityData().packDirty();
        if (dirty == null) return;
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ClientboundSetEntityDataPacket.STREAM_CODEC.encode(buffer, new ClientboundSetEntityDataPacket(server.getId(), dirty));
            client.getEntityData().assignValues(ClientboundSetEntityDataPacket.STREAM_CODEC.decode(buffer).packedItems());
        } finally { buffer.release(); }
    }
    private static void cleanup(GameTestHelper h, Entity boss) {
        for (var e : h.getLevel().getEntities(boss, boss.getBoundingBox().inflate(30), e -> e instanceof GloomShot || e instanceof GloomHawk || e instanceof GloomSpitter)) e.discard();
        boss.discard();
    }
    private static void add(List<TestFunction> tests, String name, Consumer<GameTestHelper> action) {
        tests.add(new TestFunction("gloom_visual", "gloom_visual." + name, "orimod_gametest:platform_room", 100, 0, true, h -> { action.accept(h); h.succeed(); }));
    }
}
