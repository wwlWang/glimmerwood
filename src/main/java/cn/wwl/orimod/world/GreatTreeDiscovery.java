package cn.wwl.orimod.world;

import cn.wwl.orimod.block.OriBlocks;
import cn.wwl.orimod.network.RuinGuidePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** First sight and map guidance share the persisted unique site, never a world-sized block scan. */
public final class GreatTreeDiscovery {
    public static final int RANGE=120;
    public static BlockPos visible(ServerPlayer p){
        var level=p.serverLevel();if(level.dimension()!=Level.OVERWORLD)return null;
        var site=GreatTreeSite.get(level).site();if(site==null)return null;var base=site.origin();
        if(Math.hypot(p.getX()-base.getX(),p.getZ()-base.getZ())>RANGE || p.getY()<base.getY()-4)return null;
        var eye=p.getEyePosition();
        for(int height:new int[]{28,45,60}){
            var target=Vec3.atCenterOf(base.above(height));boolean loaded=true;
            for(int x=((int)Math.floor(Math.min(eye.x,target.x)))>>4;x<=((int)Math.floor(Math.max(eye.x,target.x)))>>4;x++)
                for(int z=((int)Math.floor(Math.min(eye.z,target.z)))>>4;z<=((int)Math.floor(Math.max(eye.z,target.z)))>>4;z++)
                    if(level.getChunkSource().getChunkNow(x,z)==null)loaded=false;
            if(!loaded)continue;
            var hit=level.clip(new ClipContext(eye,target,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.BLOCK)continue;
            var at=hit.getBlockPos();var state=level.getBlockState(at);
            if(site.covers(at.getX(),at.getZ(),0) && at.getY()>=base.getY() &&
                    (state.is(OriBlocks.GREAT_SPIRIT_WOOD)||state.is(OriBlocks.SPIRIT_LEAVES)))return base;
        }return null;
    }
    /** Sneak-use a map stone: guidance can point into new chunks without generating them. */
    public static boolean guide(ServerPlayer p){
        var site=GreatTreeSite.get(p.server.overworld()).site();
        if(site==null){p.displayClientMessage(Component.translatable("commands.orimod.great_tree.none"),true);return false;}
        PacketDistributor.sendToPlayer(p,new RuinGuidePayload(Level.OVERWORLD.location().toString(),site.origin().north(18),"great_spirit_tree"));
        p.displayClientMessage(Component.translatable("message.orimod.great_tree.guide"),true);return true;
    }
    private GreatTreeDiscovery(){}
}
