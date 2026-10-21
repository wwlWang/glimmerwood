package cn.wwl.orimod.block;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** A hazard for both forms. One contact cooldown per living entity, shared across adjacent bushes. */
public final class GloomThornsBlock extends Block {
    private static final String NEXT="orimod_thorns_next";
    public GloomThornsBlock(Properties p){super(p);}
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity){
        entity.makeStuckInBlock(state,new Vec3(.65,1,.65));
        if(level instanceof ServerLevel && entity instanceof LivingEntity living){
            long now=level.getGameTime();var data=entity.getPersistentData();
            if(!data.contains(NEXT) || now>=data.getLong(NEXT) || data.getLong(NEXT)>now+15){
                data.putLong(NEXT,now+15);living.hurt(level.damageSources().sweetBerryBush(),2);
            }
        }
    }
    @Override protected void attack(BlockState state,Level level,BlockPos pos,Player player){
        if(!level.isClientSide && player.getMainHandItem().is(Items.SHEARS)){
            popResource(level,pos,new ItemStack(this));level.removeBlock(pos,false);
            player.getMainHandItem().hurtAndBreak(1,player,EquipmentSlot.MAINHAND);
        }else if(player instanceof net.minecraft.server.level.ServerPlayer server && player.getMainHandItem().is(cn.wwl.orimod.item.OriItems.SPIRIT_BLADE)){
            BlightInteractions.hit(server,pos,cn.wwl.orimod.world.BlightRules.Source.BLADE);
        }else super.attack(state,level,pos,player);
    }
}
