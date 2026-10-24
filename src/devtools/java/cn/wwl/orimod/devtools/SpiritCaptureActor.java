package cn.wwl.orimod.devtools;

import cn.wwl.orimod.movement.MoveMode;
import cn.wwl.orimod.progression.Regenerate;
import cn.wwl.orimod.spirit.SpiritData;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.UUID;

/** Development-only networked actor. The observing client renders a real RemotePlayer.
 * Position/glide are fixtures; regeneration runs its normal server skill and FX path. */
final class SpiritCaptureActor {
    private static ServerPlayer actor;
    private static EmbeddedChannel channel;
    private static Vec3 position;
    private static String mode = "idle";
    static Map<String,Object> configure(ServerPlayer observer, JsonObject action) {
        String next = action.has("mode") ? action.get("mode").getAsString() : "idle";
        if (next.equals("remove")) { close(); return Map.of("removed",true); }
        if (!java.util.Set.of("idle","glide","regenerate","human").contains(next))
            throw new IllegalArgumentException("Unknown capture actor mode");
        if (actor == null) {
            var profile = new GameProfile(UUID.randomUUID(), "OriCapture");
            actor = new ServerPlayer(observer.getServer(),observer.serverLevel(),profile,ClientInformation.createDefault());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            try { observer.getServer().getPlayerList().placeNewPlayer(connection,actor,CommonListenerCookie.createInitial(profile,false)); }
            catch (RuntimeException error) { close(); throw error; }
            actor.setGameMode(GameType.SURVIVAL);
            actor.setData(SpiritData.AWAKENED,true);
            actor.setData(SpiritData.COMPANION_FLAME,false);
            actor.setNoGravity(true);
            position = observer.position().add(0,0,3);
        }
        Regenerate.release(actor);
        mode = next;
        actor.setData(SpiritData.FORM,!mode.equals("human"));
        actor.setData(SpiritData.ABILITIES,4);
        actor.setItemSlot(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));
        if (action.has("x")) position = new Vec3(action.get("x").getAsDouble(),action.get("y").getAsDouble(),action.get("z").getAsDouble());
        float yaw = action.has("yaw") ? action.get("yaw").getAsFloat() : 180;
        actor.setYRot(yaw); actor.setYHeadRot(yaw); actor.yBodyRot = yaw;
        actor.teleportTo(position.x,position.y,position.z);
        if (mode.equals("regenerate")) { actor.setHealth(6); actor.setData(SpiritData.ENERGY,100f); }
        return Map.of("id",actor.getId(),"uuid",actor.getUUID().toString(),"mode",mode,"position",java.util.List.of(position.x,position.y,position.z));
    }
    static void tick(MinecraftServer server) {
        if (actor == null || actor.getServer()!=server) return;
        actor.setPos(position); actor.setDeltaMovement(Vec3.ZERO);
        actor.setOnGround(!mode.equals("glide"));
        actor.getData(SpiritData.MOVEMENT).mode = mode.equals("glide") ? MoveMode.GLIDE : MoveMode.IDLE;
        actor.setData(SpiritData.FEATHER_GLIDING,mode.equals("glide"));
        if (mode.equals("regenerate")) Regenerate.keepAlive(actor);
        Object packet;
        while ((packet=channel.readOutbound())!=null) io.netty.util.ReferenceCountUtil.release(packet);
    }
    static void close() {
        if (actor == null) return;
        try { Regenerate.release(actor); actor.getServer().getPlayerList().remove(actor); }
        finally { channel.finishAndReleaseAll(); actor=null; channel=null; }
    }
}
