package cn.wwl.orimod.block;

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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Shared ruin keys; empty pedestals reform after one game day until their own door opens. */
public final class KeystonePedestalBlock extends ProtectedBlock {
    public static final BooleanProperty FULL = BooleanProperty.create("full");
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public KeystonePedestalBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FULL, true)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FULL); }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    /** @return whether a keystone was taken. */
    public static boolean take(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel(); var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof KeystonePedestalBlock)) return false;
        var site=cn.wwl.orimod.world.RuinKeystones.atPedestal(level,pos);
        if(site==null){player.displayClientMessage(Component.translatable("message.orimod.keystone.unavailable"),true);return false;}
        var timers=cn.wwl.orimod.world.KeystoneTimers.get(level);
        if(site.state(level)==cn.wwl.orimod.world.RuinKeystones.DoorState.OPEN){
            timers.open(site.id());player.displayClientMessage(Component.translatable("message.orimod.keystone.finished"),true);return false;
        }
        if(!state.getValue(FULL)){
            if(timers.entry(pos)==null)timers.taken(pos,site,level.getGameTime());
            level.scheduleTick(pos,state.getBlock(),1);
            player.displayClientMessage(Component.translatable("message.orimod.keystone.reform"),true);return false;
        }
        timers.taken(pos,site,level.getGameTime());
        level.scheduleTick(pos,state.getBlock(),cn.wwl.orimod.world.KeystoneTimers.COOLDOWN);
        level.setBlock(pos, state.setValue(FULL, false), 3);
        var stack = new ItemStack(cn.wwl.orimod.item.OriItems.KEYSTONE.get());
        stack.set(cn.wwl.orimod.item.OriComponents.KEYSTONE_RUIN.get(),site.id());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1, 1.4f);
        player.displayClientMessage(Component.translatable("message.orimod.keystone.found_bound"), true);
        return true;
    }

    @Override protected void tick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,RandomSource random){
        if(state.getValue(FULL))return;
        var timers=cn.wwl.orimod.world.KeystoneTimers.get(level);var entry=timers.entry(pos);
        if(entry==null)return;
        var door=entry.site().state(level);
        if(door==cn.wwl.orimod.world.RuinKeystones.DoorState.OPEN){timers.open(entry.site().id());return;}
        long remaining=entry.readyAt()-level.getGameTime();
        if(remaining>0){level.scheduleTick(pos,this,(int)Math.min(Integer.MAX_VALUE,remaining));return;}
        if(door==cn.wwl.orimod.world.RuinKeystones.DoorState.UNLOADED){level.scheduleTick(pos,this,200);return;}
        level.setBlock(pos,state.setValue(FULL,true),UPDATE_ALL);timers.remove(pos);
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return state.getValue(FULL) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        return player instanceof ServerPlayer server && take(server, pos) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FULL)) return;
        try {
            level.addParticle(new cn.wwl.orimod.fx.SpiritParticleOptions(cn.wwl.orimod.fx.OriParticles.SPIRIT_MOTE.get(), 0xFFD27A, .45f, 12),
                    pos.getX() + .5, pos.getY() + .95, pos.getZ() + .5, 0, .003, 0);
        } catch (Throwable ignored) { }
    }
}
