package cn.wwl.orimod.gametest;

import cn.wwl.orimod.spirit.SpiritData;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
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
}
