package cn.wwl.orimod.client.fx;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.config.OriClientConfig;
import cn.wwl.orimod.fx.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.*;

public final class BloomSparkClient {
    private static final BloomSparkQueue QUEUE=new BloomSparkQueue();
    private static Level world;
    private static int emitted,last;
    public static void changed(Level level,BlockPos pos,BlockState old,BlockState state){
        if(!level.isClientSide || old==null || !old.is(OriBlocks.SPIRIT_LEAVES) || !state.is(OriBlocks.SPIRIT_LEAVES)
                || old.getValue(SpiritLeavesBlock.BLOOMED) || !state.getValue(SpiritLeavesBlock.BLOOMED))return;
        var mc=Minecraft.getInstance();
        if(mc.level!=level || mc.player==null || mc.player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>48*48)return;
        if(world!=level){QUEUE.clear();world=level;emitted=0;}
        QUEUE.offer(pos.asLong(),level.getGameTime());
    }
    public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();last=0;
        if(mc.level!=world){QUEUE.clear();world=mc.level;emitted=0;}
        if(mc.level==null || mc.isPaused())return;
        var visuals=OriClientConfig.visuals();int budget=FxBudget.bloomSparks(visuals.quality(),visuals.advancedParticles());
        if(budget==0){QUEUE.clear();return;}
        for(long key:QUEUE.drain(mc.level.getGameTime(),budget)){
            var pos=BlockPos.of(key);
            if(!mc.level.hasChunk(pos.getX()>>4,pos.getZ()>>4))continue;
            var state=mc.level.getBlockState(pos);
            if(!state.is(OriBlocks.SPIRIT_LEAVES)||!state.getValue(SpiritLeavesBlock.BLOOMED))continue;
            var at=net.minecraft.world.phys.Vec3.atCenterOf(pos);
            // Exposed face if available; one small sparkle even for an interior conversion.
            for(var face:Direction.values())if(mc.level.getBlockState(pos.relative(face)).isAir()){
                at=at.add(face.getStepX()*.55,face.getStepY()*.55,face.getStepZ()*.55);break;
            }
            mc.level.addParticle(new SpiritParticleOptions(OriParticles.SPIRIT_SPARK.get(),0xFFF0BC,.19f,12),
                    at.x,at.y,at.z,0,.025,0);last++;emitted++;
        }
    }
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e){QUEUE.clear();world=null;last=emitted=0;}
    public static int pending(){return QUEUE.size();}
    public static int last(){return last;}
    public static int emitted(){return emitted;}
    private BloomSparkClient(){}
}
