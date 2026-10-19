package cn.wwl.orimod.block;

import cn.wwl.orimod.progression.SpiritCells;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * R44: a Life or Energy Cell resting on a pedestal, placed by ruin generation at hard-to-reach spots. Each
 * player can take its cell once (recorded per site in their progress, so the pedestal stays for others).
 */
public final class CellPedestalBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);
    public final SpiritCells.Kind kind;

    public CellPedestalBlock(SpiritCells.Kind kind, Properties properties) { super(properties); this.kind = kind; }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    public static String siteKey(Level level, BlockPos pos) {
        return "cellsite/" + level.dimension().location() + "/" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static boolean claimed(Player player, Level level, BlockPos pos) {
        return player.getData(SpiritData.PROGRESS).rewards().containsKey(siteKey(level, pos));
    }

    /** Give this pedestal's cell to the player once. @return whether a cell was given. */
    public boolean claim(ServerPlayer player, BlockPos pos) {
        var level = player.level();
        if (!SpiritData.isAwakened(player)) { player.displayClientMessage(Component.translatable("message.orimod.cell_dormant"), true); return false; }
        if (claimed(player, level, pos)) { player.displayClientMessage(Component.translatable("message.orimod.cell_site.taken"), true); return false; }
        var p = player.getData(SpiritData.PROGRESS);
        var records = new java.util.HashMap<>(p.rewards());
        records.put(siteKey(level, pos), 1);
        player.setData(SpiritData.PROGRESS, p.withRewards(records));
        var stack = new ItemStack(kind == SpiritCells.Kind.LIFE ? cn.wwl.orimod.item.OriItems.LIFE_CELL.get() : cn.wwl.orimod.item.OriItems.ENERGY_CELL.get());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1, 1.4f);
        player.displayClientMessage(Component.translatable("message.orimod.cell_site.found." + kind.name().toLowerCase(java.util.Locale.ROOT)), true);
        return true;
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) claim(server, pos);
        return InteractionResult.CONSUME;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int color = kind == SpiritCells.Kind.LIFE ? 0x9CFFB0 : 0x8FD8FF;
        try {
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), color, .5f, 12),
                    pos.getX() + .5, pos.getY() + .95, pos.getZ() + .5, 0, .003, 0);
            if (random.nextInt(3) == 0)
                level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_SPARK.get(), color, .2f, 24),
                        pos.getX() + .3 + random.nextDouble() * .4, pos.getY() + .8, pos.getZ() + .3 + random.nextDouble() * .4, 0, .03, 0);
        } catch (Throwable ignored) { }
    }
}
