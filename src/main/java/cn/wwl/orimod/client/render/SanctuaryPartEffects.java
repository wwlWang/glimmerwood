package cn.wwl.orimod.client.render;

import cn.wwl.orimod.block.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.extensions.common.*;

/** Invisible part geometry still digs/breaks with the original core's particle atlas. */
final class SanctuaryPartEffects implements IClientBlockExtensions {
    static void register(RegisterClientExtensionsEvent event){event.registerBlock(new SanctuaryPartEffects(),OriBlocks.SANCTUARY_PART.get());}
    @Override public boolean addDestroyEffects(BlockState state,Level level,BlockPos pos,ParticleEngine particles){
        particles.destroy(pos,state.getValue(SanctuaryPartBlock.KIND).block().defaultBlockState());return true;
    }
    @Override public boolean addHitEffects(BlockState state,Level level,HitResult target,ParticleEngine particles){
        if(level instanceof ClientLevel client && target instanceof BlockHitResult hit){
            var point=hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(.04));
            var core=state.getValue(SanctuaryPartBlock.KIND).block().defaultBlockState();
            particles.add(new TerrainParticle(client,point.x,point.y,point.z,0,0,0,core,hit.getBlockPos()).setPower(.2f).scale(.6f));
        }
        return true;
    }
}
