package cn.wwl.orimod.block;

import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.progression.SpiritTree;
import cn.wwl.orimod.progression.SpiritProgression;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import java.util.HashMap;

/** R100: an unbreakable root heart; each player receives one Ori-style blessing per tree site. */
public final class SpiritTreeHeartBlock extends Block {
    public static final BooleanProperty BLOOMED = BooleanProperty.create("bloomed");
    public SpiritTreeHeartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BLOOMED, false));
    }
    @Override protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this) && level instanceof net.minecraft.server.level.ServerLevel server)
            cn.wwl.orimod.world.GloomTideDefense.invalidate(server, pos);
    }
    @Override protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState next, boolean moving) {
        super.onRemove(state, level, pos, next, moving);
        if (!next.is(this) && level instanceof net.minecraft.server.level.ServerLevel server)
            cn.wwl.orimod.world.GloomTideDefense.invalidate(server, pos);
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(BLOOMED); }
    public static String siteKey(Level level, BlockPos pos) {
        return "tree/" + level.dimension().location() + "/" + pos.getX() + "/" + pos.getY() + "/" + pos.getZ();
    }
    public static boolean claim(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        var state = level.getBlockState(pos);
        if (!state.is(OriBlocks.SPIRIT_TREE_HEART)) return false;
        if (!player.isAlive() || !SpiritData.isAwakened(player) || !SpiritData.isSpirit(player)) {
            player.displayClientMessage(Component.translatable("message.orimod.tree.dormant"), true);
            return false;
        }
        var progress = player.getData(SpiritData.PROGRESS);
        String key = siteKey(level, pos);
        if (progress.rewards().containsKey(key)) {
            player.displayClientMessage(Component.translatable("message.orimod.tree.taken"), true);
            return false;
        }
        var rewards = new HashMap<>(progress.rewards());
        rewards.put(key, 1);
        player.setData(SpiritData.PROGRESS, progress.withRewards(rewards));
        int abilities = player.getData(SpiritData.ABILITIES);
        var gift = SpiritTree.gift(abilities);
        if (gift != null) {
            player.setData(SpiritData.ABILITIES, abilities | gift.bit);
            player.displayClientMessage(Component.translatable("message.orimod.tree.skill", gift.title()), true);
        } else {
            SpiritProgression.grantLight(player, SpiritTree.LIGHT_GIFT);
            player.displayClientMessage(Component.translatable("message.orimod.tree.light", SpiritTree.LIGHT_GIFT), true);
        }
        if (!state.getValue(BLOOMED)) level.setBlock(pos, state.setValue(BLOOMED, true), Block.UPDATE_ALL);
        SpiritFx.emit(level, SpiritFxKind.TREE_BLESSING, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, player);
        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, .7f, 1.4f);
        return true;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) claim(server, pos);
        return InteractionResult.CONSUME;
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        boolean bloomed = state.getValue(BLOOMED);
        int count = bloomed ? 3 : 1;
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2, radius = bloomed ? .5 : .15;
            level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(), bloomed ? 0xF2FBFF : 0xA4DEFF,
                            bloomed ? .22f : .13f, 30), pos.getX() + .5 + Math.cos(angle) * radius,
                    pos.getY() + .7, pos.getZ() + .5 + Math.sin(angle) * radius,
                    bloomed ? Math.cos(angle) * .012 : 0, .025, bloomed ? Math.sin(angle) * .012 : 0);
        }
    }
}
