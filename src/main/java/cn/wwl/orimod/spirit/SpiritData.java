package cn.wwl.orimod.spirit;

import cn.wwl.orimod.OriMod;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Permanent awakening and current form have separate death-copy rules. */
public final class SpiritData {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OriMod.MOD_ID);
    public static final Supplier<AttachmentType<Boolean>> AWAKENED = TYPES.register("awakened", () ->
            AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().sync(ByteBufCodecs.BOOL).build());
    public static final Supplier<AttachmentType<Boolean>> FORM = TYPES.register("spirit_form", () ->
            AttachmentType.builder(() -> false).serialize(Codec.BOOL).sync(ByteBufCodecs.BOOL).build());
    public static final Supplier<AttachmentType<Integer>> ABILITIES = TYPES.register("abilities", () ->
            AttachmentType.builder(() -> 0).serialize(Codec.INT).copyOnDeath().sync(ByteBufCodecs.VAR_INT).build());
    public static final Supplier<AttachmentType<cn.wwl.orimod.progression.SpiritProgress>> PROGRESS = TYPES.register("progress", () ->
            AttachmentType.builder(() -> cn.wwl.orimod.progression.SpiritProgress.EMPTY)
                    .serialize(cn.wwl.orimod.progression.SpiritProgress.CODEC).copyOnDeath()
                    .sync(ByteBufCodecs.fromCodec(cn.wwl.orimod.progression.SpiritProgress.CODEC)).build());
    public static final Supplier<AttachmentType<Float>> ENERGY = TYPES.register("energy", () ->
            AttachmentType.builder(() -> 100f).serialize(Codec.FLOAT).sync(ByteBufCodecs.FLOAT).build());
    public static final Supplier<AttachmentType<cn.wwl.orimod.progression.EnergyState>> ENERGY_STATE = TYPES.register("energy_state", () ->
            AttachmentType.builder(cn.wwl.orimod.progression.EnergyState::new).build());
    public static final Supplier<AttachmentType<cn.wwl.orimod.movement.MovementState>> MOVEMENT = TYPES.register("movement", () ->
            AttachmentType.builder(cn.wwl.orimod.movement.MovementState::new).build());
    public static final Supplier<AttachmentType<cn.wwl.orimod.combat.CombatState>> COMBAT = TYPES.register("combat", () ->
            AttachmentType.builder(cn.wwl.orimod.combat.CombatState::new).build());
    public static final Supplier<AttachmentType<cn.wwl.orimod.combat.CombatView>> COMBAT_VIEW = TYPES.register("combat_view", () ->
            AttachmentType.builder(() -> cn.wwl.orimod.combat.CombatView.IDLE)
                    .sync(ByteBufCodecs.fromCodec(cn.wwl.orimod.combat.CombatView.CODEC)).build());

    private SpiritData() {}
    public static void register(IEventBus bus) { TYPES.register(bus); }
    public static boolean isSpirit(Player player) { return player.hasData(FORM) && player.getData(FORM); }
    public static boolean isAwakened(Player player) { return player.hasData(AWAKENED) && player.getData(AWAKENED); }
    public static boolean hasDash(Player player) { return isAwakened(player) && (player.getData(ABILITIES) & 1) != 0; }
    public static boolean hasBash(Player player) { return isAwakened(player) && (player.getData(ABILITIES) & 2) != 0; }
    public static boolean hasGlide(Player player) { return isAwakened(player) && (player.getData(ABILITIES) & 4) != 0; }
    public static boolean hasGrapple(Player player) { return isAwakened(player) && (player.getData(ABILITIES) & 8) != 0; }
    public static boolean hasStomp(Player player) { return isAwakened(player) && (player.getData(ABILITIES) & 16) != 0; }
}
