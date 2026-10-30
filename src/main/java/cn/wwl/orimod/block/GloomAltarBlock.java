package cn.wwl.orimod.block;

import cn.wwl.orimod.entity.GloomHeart;
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

/** R50: the gloom altar in the cave altar ruin. An awakened player wakes the Gloom Heart here (one at a time). */
public final class GloomAltarBlock extends SanctuaryBlock {
    public GloomAltarBlock(Properties properties) { super(properties); }

    public static GloomHeart awake(ServerLevel level, BlockPos pos) {
        var hearts = level.getEntitiesOfClass(GloomHeart.class, new AABB(pos).inflate(GloomHeart.ARENA), GloomHeart::isAlive);
        return hearts.isEmpty() ? null : hearts.getFirst();
    }

    /** @return the woken boss, or null if one is already awake or the player is not awakened. */
    public static GloomHeart summon(ServerPlayer player, BlockPos pos) {
        var level = (ServerLevel) player.level();
        if (!SpiritData.isAwakened(player)) { player.displayClientMessage(Component.translatable("message.orimod.altar_dormant"), true); return null; }
        if (SanctuaryBlockEntity.refuseCooldown(player, pos)) return null;
        if (awake(level, pos) != null) { player.displayClientMessage(Component.translatable("message.orimod.altar_awake"), true); return null; }
        var be=level.getBlockEntity(pos);
        boolean progressed=be instanceof SanctuaryBlockEntity sanctuary && (sanctuary.activated()||sanctuary.phase()==SanctuaryBlockEntity.Phase.DEFEATED);
        if(!progressed){
            var site=cn.wwl.orimod.world.RuinKeystones.atCaveAltar(level,pos);
            var door=site==null?null:site.state(level);
            if(door!=null&&door!=cn.wwl.orimod.world.RuinKeystones.DoorState.OPEN
                    ||site==null&&cn.wwl.orimod.world.RuinKeystones.unresolvedCaveReference(level,pos)){
                boolean unloaded=door==null||door==cn.wwl.orimod.world.RuinKeystones.DoorState.UNLOADED;
                player.displayClientMessage(Component.translatable(unloaded?"message.orimod.altar_gate_unloaded":"message.orimod.altar_gate_sealed"),true);
                return null;
            }
        }
        var heart = OriMobs.GLOOM_HEART.get().create(level);
        if (heart == null) return null;
        heart.setHome(pos);
        heart.moveTo(pos.getX() + .5, pos.getY() + GloomHeart.HOVER, pos.getZ() + .5, 0, 0);
        level.addFreshEntity(heart);
        SanctuaryBlockEntity.activate(level, pos);
        level.playSound(null, pos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, .7f, 1.6f);
        cn.wwl.orimod.fx.SpiritFx.emit(level, cn.wwl.orimod.fx.SpiritFxKind.STOMP_IMPACT, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, null);
        player.displayClientMessage(Component.translatable("message.orimod.altar_woke"), true);
        return heart;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) summon(server, pos);
        return InteractionResult.CONSUME;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (SanctuaryBlockEntity.coolingDown(level, pos)) return;
        try {
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0x8A3AB8, .4f, 20),
                    pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, .02, 0);
        } catch (Throwable ignored) { }
    }
}
