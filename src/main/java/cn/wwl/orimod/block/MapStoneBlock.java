package cn.wwl.orimod.block;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * R62 map stone (after BF's map stones): standing at each ruin's entrance. Activating it surveys the ruin
 * (cells this player has not taken, keystones still resting, whether a gloom altar sleeps here), grants a little
 * Spirit Light the first time and points the ruin guide at the next ruin.
 */
public final class MapStoneBlock extends SanctuaryBlock {
    public static final int SURVEY_RADIUS = 28, FIRST_LIGHT = 10;

    public MapStoneBlock(Properties properties) { super(properties); }

    public record Survey(int cells, int keystones, int altars) {}

    public static Survey survey(ServerPlayer player, BlockPos center) {
        var level = player.serverLevel(); int cells = 0, keys = 0, altars = 0;
        var piece=cn.wwl.orimod.world.RuinLayouts.find(level,center,p->p.layoutVersion()>0 && p.mapStone()!=null && p.placement().block(p.mapStone()).equals(center));
        if(piece!=null){
            // Versioned large ruins exceed the legacy radius. Query their saved contract, never load chunks.
            for(var cell:piece.layout().cells()) {var pos=piece.placement().block(cell.position());
                if(level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof CellPedestalBlock && !CellPedestalBlock.claimed(player,level,pos))cells++;}
            for(var local:piece.layout().keystones()) {var pos=piece.placement().block(local);
                if(level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof KeystonePedestalBlock && level.getBlockState(pos).getValue(KeystonePedestalBlock.FULL))keys++;}
            if(piece.layout().bossAltar()!=null){var pos=piece.placement().block(piece.layout().bossAltar());
                if(level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof GloomAltarBlock)altars++;}
            if(piece.layout().stormAltar()!=null){var pos=piece.placement().block(piece.layout().stormAltar());
                if(level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() instanceof StormAltarBlock)altars++;}
            return new Survey(cells,keys,altars);
        }
        for (var pos : BlockPos.betweenClosed(center.offset(-SURVEY_RADIUS, -12, -SURVEY_RADIUS), center.offset(SURVEY_RADIUS, 16, SURVEY_RADIUS))) {
            if (!level.isLoaded(pos)) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof CellPedestalBlock && !CellPedestalBlock.claimed(player, level, pos)) cells++;
            else if (state.getBlock() instanceof KeystonePedestalBlock && state.getValue(KeystonePedestalBlock.FULL)) keys++;
            else if (state.getBlock() instanceof GloomAltarBlock) altars++;
        }
        return new Survey(cells, keys, altars);
    }

    public static String key(Level level, BlockPos pos) {
        return "mapstone/" + level.dimension().location() + "/" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** @return the survey, or null if the player is not awakened. */
    public static Survey activate(ServerPlayer player, BlockPos pos) {
        if (!SpiritData.isAwakened(player)) { player.displayClientMessage(Component.translatable("message.orimod.map_stone.dormant"), true); return null; }
        var survey = survey(player, pos);
        SanctuaryBlockEntity.activate(player.level(), pos);
        if (cn.wwl.orimod.progression.SpiritProgression.reward(player, key(player.level(), pos), FIRST_LIGHT, 0)) {
            player.level().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1, .8f);
            var fragment = new net.minecraft.world.item.ItemStack(cn.wwl.orimod.item.OriItems.MAP_FRAGMENT.get());   // R68
            if (!player.getInventory().add(fragment)) player.drop(fragment, false);
        }
        player.level().playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1, 1.2f);
        cn.wwl.orimod.fx.SpiritFx.emit((net.minecraft.server.level.ServerLevel) player.level(), cn.wwl.orimod.fx.SpiritFxKind.CHECKPOINT_REACHED, pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5, null);
        player.displayClientMessage(Component.translatable("message.orimod.map_stone.survey", survey.cells(), survey.keystones(),
                Component.translatable(survey.altars() > 0 ? "message.orimod.map_stone.altar" : "message.orimod.map_stone.no_altar")), false);
        if(player.isShiftKeyDown())cn.wwl.orimod.world.GreatTreeDiscovery.guide(player);
        else {cn.wwl.orimod.world.RuinLocator.request(player);player.sendSystemMessage(Component.translatable("message.orimod.great_tree.map_tip"));}
        return survey;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) activate(server, pos);
        return InteractionResult.CONSUME;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(2) != 0) return;
        try {
            double a = random.nextDouble() * Math.PI * 2;
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_SPARK.get(), 0x9FE8FF, .25f, 30),
                    pos.getX() + .5 + Math.cos(a) * .6, pos.getY() + 1.2, pos.getZ() + .5 + Math.sin(a) * .6, 0, .02, 0);
        } catch (Throwable ignored) { }
    }
}
