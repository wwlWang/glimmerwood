package cn.wwl.orimod.movement;

import cn.wwl.orimod.network.MovementResultPayload;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class MovementProtocolTest {
    @ParameterizedTest @CsvSource({
            "IDLE,0,idle", "JUMP,1,jump", "DOUBLE_JUMP,2,double_jump", "WALL_JUMP,3,wall_jump",
            "DASH,4,dash", "WALL_SLIDE,5,wall_slide", "BASH_AIM,6,bash_aim", "BASH_LAUNCH,7,bash_launch",
            "GRAPPLE,8,grapple", "GRAPPLE_END,9,grapple_end", "GLIDE,10,glide", "STOMP,11,stomp",
            "STOMP_LAND,12,stomp_land", "RESUME,13,resume", "JUMP_REJECT,14,jump_reject", "RELOCATE,15,relocate", "LAUNCH,16,launch"
    })
    void stableIdsAndCompletePacketRoundTrip(MoveMode mode, int id, String diagnostic) {
        assertEquals(id, mode.wireId()); assertEquals(diagnostic, mode.id());
        var original = new MovementResultPayload(123, "minecraft:overworld", 12, 34, mode, 3,
                new Vec3(.4, -.2, .1), true, false, 456, new Vec3(10, 20, 30));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            MovementResultPayload.CODEC.encode(buffer, original);
            assertEquals(original, MovementResultPayload.CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }

    @ParameterizedTest @ValueSource(ints = {-1, 17, 255, Integer.MAX_VALUE})
    void unknownModeRejectsPacket(int unknown) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            buffer.writeVarInt(1); buffer.writeUtf("minecraft:overworld"); buffer.writeVarInt(2);
            buffer.writeVarInt(-1); buffer.writeVarInt(unknown);
            assertThrows(DecoderException.class, () -> MovementResultPayload.CODEC.decode(buffer));
        } finally { buffer.release(); }
    }

}
