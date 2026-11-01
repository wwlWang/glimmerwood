package cn.wwl.orimod.block;
import cn.wwl.orimod.world.BlightRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;

/** Small block adapters; combat damage, targeting and energy rules stay with each ability. */
public final class BlightInteractions {
    public static void hit(ServerPlayer player,BlockPos pos,BlightRules.Source source){
        var level=player.serverLevel();if(level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null)return;
        if(level.getBlockState(pos).is(OriBlocks.GLOOM_THORNS)){
            if(BlightRules.clearsThorns(source))level.destroyBlock(pos,false,player);
        }
        else if(level.getBlockEntity(pos) instanceof GloomCoreBlockEntity core)core.hit(player,source);
    }
    public static void area(ServerPlayer player,Vec3 at,double radius,BlightRules.Source source){
        for(var pos:CrackedRuin.scan(player.serverLevel(),new AABB(at,at).inflate(radius),s->s.is(OriBlocks.GLOOM_CORE)||(BlightRules.clearsThorns(source)&&s.is(OriBlocks.GLOOM_THORNS))))
            if(pos.distToCenterSqr(at)<=radius*radius)hit(player,pos,source);
    }
    public static void smash(ServerPlayer player){
        var at=cn.wwl.orimod.item.SpiritHammerItem.impactPoint(player.position(),player.getYRot());
        for(var pos:CrackedRuin.scan(player.serverLevel(),new AABB(at,at).inflate(cn.wwl.orimod.item.SpiritHammerItem.RADIUS,1.5,cn.wwl.orimod.item.SpiritHammerItem.RADIUS).expandTowards(0,1,0),s->s.is(OriBlocks.GLOOM_CORE)||s.is(OriBlocks.GLOOM_THORNS)))hit(player,pos,BlightRules.Source.SMASH);
    }
    /** Use outline rays so collision-free brambles can be struck, while solid walls still occlude them. */
    public static void melee(ServerPlayer player,double reach){melee(player,reach,new java.util.HashSet<>());}
    public static void melee(ServerPlayer player,double reach,java.util.Set<Long> hitBlocks){
        var from=player.getEyePosition();var hit=player.level().clip(new net.minecraft.world.level.ClipContext(from,from.add(player.getLookAngle().scale(reach)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        if(hit.getType()==HitResult.Type.BLOCK && hitBlocks.add(hit.getBlockPos().asLong()))hit(player,hit.getBlockPos(),BlightRules.Source.BLADE);
    }
    public static void projectile(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event){
        var entity=event.getEntity();
        if(!(entity instanceof net.minecraft.world.entity.projectile.Projectile projectile) || !(projectile.getOwner() instanceof ServerPlayer player))return;
        if(!(projectile instanceof cn.wwl.orimod.combat.SpiritSpear) && !(projectile instanceof net.minecraft.world.entity.projectile.Arrow) && !(projectile instanceof net.minecraft.world.entity.projectile.SpectralArrow))return;
        var from=entity.position();var hit=entity.level().clip(new net.minecraft.world.level.ClipContext(from,from.add(entity.getDeltaMovement()),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,entity));
        if(hit.getType()==HitResult.Type.BLOCK && entity.level().getBlockState(hit.getBlockPos()).is(OriBlocks.GLOOM_THORNS))hit(player,hit.getBlockPos(),projectile instanceof cn.wwl.orimod.combat.SpiritSpear ? BlightRules.Source.SPEAR : BlightRules.Source.ARROW);
    }
    private BlightInteractions(){}
}
