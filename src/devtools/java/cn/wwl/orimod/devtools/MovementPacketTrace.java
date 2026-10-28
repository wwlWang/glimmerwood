package cn.wwl.orimod.devtools;

import cn.wwl.orimod.devtools.mixin.CombatPendingAccessor;
import cn.wwl.orimod.network.MovementResultPayload;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import java.util.*;

/** Opt-in client-thread observation only; absent from the release source set. */
public final class MovementPacketTrace {
    private static boolean enabled;
    private static final int LIMIT = 20_000;
    private static final List<Map<String,Object>> EVENTS = new ArrayList<>();
    private static Map<String,Object> current;
    private static int overflow;
    private MovementPacketTrace() {}

    public static void configure(boolean on, boolean clear) {
        if(clear) { EVENTS.clear(); overflow=0; }
        enabled=on;
    }
    public static Map<String,Object> report() {
        return Map.of("enabled",enabled,"overflow",overflow,"events",List.copyOf(EVENTS));
    }
    public static Map<String,Object> snapshot(Player p) {
        var m=p.getData(SpiritData.MOVEMENT);
        var out=new LinkedHashMap<String,Object>();
        out.put("entityId",p.getId());out.put("playerIdentity",System.identityHashCode(p));
        out.put("movementIdentity",System.identityHashCode(m));out.put("dimension",p.level().dimension().location().toString());
        out.put("gameTime",p.level().getGameTime());out.put("playerTicks",p.tickCount);out.put("actionId",m.actionId);
        out.put("pending",m.predictedJumps.size());out.put("sequences",List.copyOf(m.predictedJumps.keySet()));
        out.put("predictedTotal",m.predictedJumpCount);out.put("active",m.active);out.put("mode",m.mode.id());
        out.put("form",SpiritData.isSpirit(p));out.put("jumpHeld",m.jumpHeld);out.put("groundLockUntil",m.groundLockUntil);
        out.put("wallReadyAt",m.wallReadyAt);out.put("wallDirection",m.wallDirection);out.put("lease",m.activeTicks);
        out.put("combat",p.getData(SpiritData.COMBAT_VIEW).mode());
        if(p.level().isClientSide) {
            out.put("awaitingAttackUntil",CombatPendingAccessor.orimod$awaitingAttackUntil());
            out.put("awaitingAttack",cn.wwl.orimod.client.SpiritCombatClient.awaitingAttack());
        }
        return out;
    }
    public static void begin(MovementResultPayload packet,Player p) {
        if(!enabled)return;
        current=new LinkedHashMap<>();current.put("event","receipt");current.put("nanos",System.nanoTime());
        current.put("payload",Map.of("entityId",packet.entityId(),"dimension",packet.dimension(),"actionId",packet.actionId(),
                "jumpSequence",packet.jumpSequence(),"mode",packet.mode().id()));
        current.put("before",snapshot(p));
    }
    public static void end(Player p,String reason) {
        if(current==null)return;
        current.put("return",reason);current.put("after",snapshot(p));append(current);current=null;
    }
    public static void clonePlayer(ClientPlayerNetworkEvent.Clone event) {
        if(enabled)append(Map.of("event","clone","nanos",System.nanoTime(),"old",snapshot(event.getOldPlayer()),"new",snapshot(event.getNewPlayer())));
    }
    private static void append(Map<String,Object> event) {
        if(EVENTS.size()<LIMIT)EVENTS.add(event);else overflow++;
    }
}
