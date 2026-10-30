package cn.wwl.orimod.block;
import cn.wwl.orimod.fx.*;
import cn.wwl.orimod.progression.CompanionHints;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class GloomCoreBlock extends Block implements EntityBlock {
    public static final IntegerProperty DAMAGE=IntegerProperty.create("damage",0,4);
    public GloomCoreBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(DAMAGE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(DAMAGE);}
    @Override public BlockEntity newBlockEntity(BlockPos p,BlockState s){return new GloomCoreBlockEntity(p,s);}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext c){return box(3,0,3,13,16,13);}
    @Override public boolean canEntityDestroy(BlockState s,BlockGetter l,BlockPos p,Entity e){return false;}
    @Override protected void attack(BlockState s,Level l,BlockPos p,Player player){if(l instanceof ServerLevel server && player instanceof ServerPlayer sp)reject(server,p,sp);}
    @Override protected void onProjectileHit(Level l,BlockState s,BlockHitResult hit,Projectile projectile){
        // SpiritSpear's impact adapter has already applied its allowed damage.
        if(!(projectile instanceof cn.wwl.orimod.combat.SpiritSpear) && l instanceof ServerLevel server && projectile.getOwner() instanceof ServerPlayer p)reject(server,hit.getBlockPos(),p);
    }
    public static void reject(ServerLevel l,BlockPos p,ServerPlayer player){
        CompanionHints.core(player,p);
        if(l.getBlockEntity(p) instanceof GloomCoreBlockEntity core){
            long now=l.getGameTime();if(core.lastRefused!=Long.MIN_VALUE && now>=core.lastRefused && now-core.lastRefused<10)return;core.lastRefused=now;
        }
        l.playSound(null,p,SoundEvents.AMETHYST_BLOCK_HIT,SoundSource.BLOCKS,.5f,1.7f);
        l.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,p.getX()+.5,p.getY()+.7,p.getZ()+.5,5,.3,.4,.3,.02);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState next,boolean moving){
        if(!s.is(next.getBlock()) && l instanceof ServerLevel server)cn.wwl.orimod.world.BlightData.get(server).removed(server,p);
        super.onRemove(s,l,p,next,moving);
    }
    @Override public void animateTick(BlockState s,Level l,BlockPos p,RandomSource random){
        double pulse=.5+.5*Math.sin((l.getGameTime()+p.asLong()%60)*Math.PI/30);
        if(random.nextDouble()<.25+.6*pulse)l.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_MOTE.get(),0x9143BD,(float)(.15+.2*pulse),30),p.getX()+.5+(random.nextDouble()-.5)*.6,p.getY()+1,p.getZ()+.5+(random.nextDouble()-.5)*.6,0,-.025,0);
    }
}
