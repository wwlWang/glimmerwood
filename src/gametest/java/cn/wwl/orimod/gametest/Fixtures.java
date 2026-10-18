package cn.wwl.orimod.gametest;

import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

final class Fixtures {
    private Fixtures() {}

    /** Fresh, unregistered player per case: no shared inventory, sockets or save files. */
    static ServerPlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ori-test"));
        player.setGameMode(GameType.SURVIVAL);
        // The structure block is one block below the template floor.
        player.setPos(helper.absoluteVec(new Vec3(5.5, 2, 5.5)));
        player.setYRot(0);
        player.setXRot(0);
        player.setData(SpiritData.AWAKENED, true);
        return player;
    }

    static void near(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) < 0.001, label + ": expected " + expected + ", got " + actual);
    }

    /** Same embedded network transport as GameTestHelper, with a real survival player. */
    static ConnectedPlayer connectedPlayer(GameTestHelper helper) {
        var profile = new GameProfile(UUID.randomUUID(), "ori" + UUID.randomUUID().toString().substring(0, 8));
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        try {
            helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(5.5, 2, 5.5)));
            player.setData(SpiritData.AWAKENED, true);
            return new ConnectedPlayer(player, channel);
        } catch (RuntimeException exception) {
            if (player.getServer().getPlayerList().getPlayers().contains(player)) player.getServer().getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            throw exception;
        }
    }

    record ConnectedPlayer(ServerPlayer player, EmbeddedChannel channel) implements AutoCloseable {
        @Override public void close() {
            try { player.getServer().getPlayerList().remove(player); }
            finally { channel.finishAndReleaseAll(); }
        }
    }
}
