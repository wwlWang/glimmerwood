package cn.wwl.orimod.network;

import cn.wwl.orimod.progression.*;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public record SkillBindPayload(int slot,WheelSkill skill) implements CustomPacketPayload {
    public static final String VERSION="1";
    public static final Type<SkillBindPayload> TYPE=new Type<>(ResourceLocation.parse("orimod:skill_bind"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SkillBindPayload> CODEC=StreamCodec.of((b,p)->{
        b.writeByte(1);b.writeByte(p.slot);b.writeUtf(p.skill.id(),32);
    },b->{
        if(b.readUnsignedByte()!=1)throw new DecoderException("Incompatible skill binding protocol: expected 1");
        int slot=b.readUnsignedByte();var skill=WheelSkill.byId(b.readUtf(32));
        if(slot>=3||skill==null)throw new DecoderException("Invalid skill binding");return new SkillBindPayload(slot,skill);
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void register(RegisterPayloadHandlersEvent e){e.registrar(VERSION).playToServer(TYPE,CODEC,(p,c)->{
        if(c.player() instanceof ServerPlayer player)SpiritSkills.bind(player,p.slot,p.skill);
    });}
}
