package cn.wwl.orimod.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RuinShrineBlock extends Block {
    private static final MapCodec<RuinShrineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(block -> block.kind.id), propertiesCodec()
    ).apply(instance, (kind, properties) -> new RuinShrineBlock(RuinKind.byId(kind), properties)));
    public final RuinKind kind;
    public RuinShrineBlock(RuinKind kind, Properties properties) { super(properties); this.kind = kind; }
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) SpiritChallenge.offer(serverPlayer, pos, kind);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
