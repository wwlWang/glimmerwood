package cn.wwl.orimod.block;

import cn.wwl.orimod.entity.GloomMatriarch;
import cn.wwl.orimod.entity.OriMobs;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/** R70: the storm altar at the far end of the forest spring ruin; an awakened player calls the Gloom Matriarch down. */
public final class StormAltarBlock extends SanctuaryBlock {
    public StormAltarBlock(Properties properties) { super(properties); }

    public static GloomMatriarch awake(ServerLevel level, BlockPos pos) {
        var found = level.getEntitiesOfClass(GloomMatriarch.class, new AABB(pos).inflate(GloomMatriarch.ARENA), GloomMatriarch::isAlive);
        return found.isEmpty() ? null : found.getFirst();
    }

    public static GloomMatriarch summon(ServerPlayer player, BlockPos pos) {
        var level = (ServerLevel) player.level();
        if (!SpiritData.isAwakened(player)) { player.displayClientMessage(Component.translatable("message.orimod.altar_dormant"), true); return null; }
        if (awake(level, pos) != null) { player.displayClientMessage(Component.translatable("message.orimod.matriarch.awake"), true); return null; }
        var boss = OriMobs.GLOOM_MATRIARCH.get().create(level);
        if (boss == null) return null;
        boss.setHome(pos);
        boss.moveTo(pos.getX() + .5, pos.getY() + GloomMatriarch.hover(1) + 4, pos.getZ() + .5, 0, 0);
        level.addFreshEntity(boss);
        SanctuaryBlockEntity.activate(level, pos);
        level.playSound(null, pos, SoundEvents.PHANTOM_DEATH, SoundSource.HOSTILE, 1.5f, .4f);
        cn.wwl.orimod.fx.SpiritFx.emit(level, cn.wwl.orimod.fx.SpiritFxKind.FORM_SWITCH, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, null);
        player.displayClientMessage(Component.translatable("message.orimod.matriarch.woke"), true);
        return boss;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) summon(server, pos);
        return InteractionResult.CONSUME;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        try {
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0x5A7BD8, .4f, 24),
                    pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, .05, 0);
        } catch (Throwable ignored) { }
    }
}
