package cn.wwl.orimod.block;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Only presentation is stored here. Rewards, travel and boss eligibility retain their original owners. */
public final class SanctuaryBlockEntity extends BlockEntity {
    public enum Phase { DORMANT, ACTIVE, DEFEATED }
    private boolean activated, defeated;
    private Phase phase = Phase.DORMANT;
    public SanctuaryBlockEntity(BlockPos pos, BlockState state) { super(OriBlockEntities.SANCTUARY.get(), pos, state); }
    public Phase phase() { return phase; }
    public boolean activated() { return activated; }

    public static void activate(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SanctuaryBlockEntity b) {
            b.activated = true; b.phase = Phase.ACTIVE; b.sync();
        }
    }
    public static void victory(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SanctuaryBlockEntity b) {
            b.defeated = true; b.phase = Phase.DEFEATED; b.sync();
        }
    }
    void tickAltar() {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + worldPosition.asLong()) % 20 != 0) return;
        boolean awake = getBlockState().getBlock() instanceof GloomAltarBlock
                ? GloomAltarBlock.awake(server, worldPosition) != null : StormAltarBlock.awake(server, worldPosition) != null;
        Phase next = awake ? Phase.ACTIVE : defeated ? Phase.DEFEATED : Phase.DORMANT;
        if (next != phase) { phase = next; sync(); }
    }
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("activated", activated); tag.putBoolean("defeated", defeated); tag.putString("phase", phase.name());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        activated = tag.getBoolean("activated"); defeated = tag.getBoolean("defeated");
        try { phase = Phase.valueOf(tag.getString("phase")); } catch (IllegalArgumentException e) { phase = Phase.DORMANT; }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
