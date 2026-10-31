package cn.wwl.orimod.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

/** Functional deadlines survive reconnects and player clones. Leases and active casts live in EnergyState. */
public final class SpiritCooldowns {
    public static final Codec<SpiritCooldowns> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("soulLinkReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.soulLinkReadyAt),
            Codec.LONG.optionalFieldOf("flameReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.flameReadyAt),
            Codec.LONG.optionalFieldOf("burstReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.burstReadyAt),
            Codec.LONG.optionalFieldOf("waterDashReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.waterDashReadyAt),
            Codec.LONG.optionalFieldOf("chargeFlameReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.chargeFlameReadyAt),
            Codec.LONG.optionalFieldOf("spearReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.spearReadyAt),
            Codec.LONG.optionalFieldOf("wellReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.wellReadyAt),
            Codec.LONG.optionalFieldOf("wellTravelReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.wellTravelReadyAt),
            Codec.LONG.optionalFieldOf("healReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.healReadyAt),
            Codec.LONG.optionalFieldOf("flashReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.flashReadyAt),
            Codec.LONG.optionalFieldOf("dashReadyAt", 0L).forGetter((SpiritCooldowns s) -> s.dashReadyAt)
    ).apply(i, SpiritCooldowns::new));
    public long soulLinkReadyAt, flameReadyAt, burstReadyAt, waterDashReadyAt, chargeFlameReadyAt, spearReadyAt, wellReadyAt, wellTravelReadyAt, healReadyAt, flashReadyAt, dashReadyAt;
    public SpiritCooldowns() {}
    private SpiritCooldowns(long soulLinkReadyAt, long flameReadyAt, long burstReadyAt, long waterDashReadyAt, long chargeFlameReadyAt, long spearReadyAt, long wellReadyAt, long wellTravelReadyAt, long healReadyAt, long flashReadyAt, long dashReadyAt) {
        this.soulLinkReadyAt = soulLinkReadyAt;
        this.flameReadyAt = flameReadyAt;
        this.burstReadyAt = burstReadyAt;
        this.waterDashReadyAt = waterDashReadyAt;
        this.chargeFlameReadyAt = chargeFlameReadyAt;
        this.spearReadyAt = spearReadyAt;
        this.wellReadyAt = wellReadyAt;
        this.wellTravelReadyAt = wellTravelReadyAt;
        this.healReadyAt = healReadyAt;
        this.flashReadyAt = flashReadyAt;
        this.dashReadyAt = dashReadyAt;
    }
    /** Overworld game time advances with the server, independent of dimension day/time settings. */
    public static long now(ServerPlayer player) { return player.server.overworld().getGameTime(); }
}
