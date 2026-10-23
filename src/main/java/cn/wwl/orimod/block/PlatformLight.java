package cn.wwl.orimod.block;

import cn.wwl.orimod.OriMod;
import cn.wwl.orimod.combat.Flash;
import cn.wwl.orimod.world.GloomTideDefense;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.*;
import net.minecraft.world.ticks.LevelChunkTicks;
import net.neoforged.neoforge.event.level.ChunkEvent;
import java.util.*;
import static cn.wwl.orimod.block.PlatformRules.*;
import static cn.wwl.orimod.block.SpiritPlatformBlock.*;

/** Source-driven bounded pushes plus scheduled warning/expiry/retry. No periodic world traversal. */
public final class PlatformLight {
    public static int push(ServerLevel level, Vec3 center, Source source) {
        var targets=targets(level,center,source); int count=0;
        for (var pos:targets) {
            if (++count>LIMIT) { OriMod.LOGGER.debug("Spirit platform push {} at {} capped at {} blocks",source,center,LIMIT); break; }
            var data=PlatformTimers.get(level); var old=data.get(pos.asLong()); long now=level.getGameTime();
            if (source==Source.FLASH || source==Source.BURST) {
                long pulse=renew(old==null ? Long.MIN_VALUE : old.pulseUntil(),now);
                data.put(pos.asLong(),new PlatformTimers.Lease(pulse,Math.max(pulse,old==null ? Long.MIN_VALUE : old.until())));
            }
            tick(level,pos);
        }
        return Math.min(count,LIMIT);
    }
    private static List<BlockPos> targets(ServerLevel level, Vec3 center, Source source) {
        return CrackedRuin.scan(level,new AABB(center,center).inflate(source.radius),s->s.is(OriBlocks.SPIRIT_PLATFORM)).stream()
                .filter(p->within(source,p.distToCenterSqr(center))).sorted(Comparator.<BlockPos>comparingDouble(p->p.distToCenterSqr(center)).thenComparingLong(BlockPos::asLong)).toList();
    }
    /** Called for extinction/removal, with the changing source explicitly excluded. */
    public static void lost(ServerLevel level, BlockPos source, Source kind) {
        int count=0;
        for (var pos:targets(level,Vec3.atCenterOf(source),kind)) {
            if (++count>LIMIT) { OriMod.LOGGER.debug("Spirit platform loss at {} capped at {} blocks",source,LIMIT); break; }
            var data=PlatformTimers.get(level);var lease=data.get(pos.asLong());
            if(lease==null) {
                if(!level.getBlockState(pos).getValue(LIT))continue;
                lease=new PlatformTimers.Lease(Long.MIN_VALUE,Long.MIN_VALUE);
            }
            long until=Math.max(renew(lease.pulseUntil(),level.getGameTime()),persistentUntil(level,pos,source));
            data.put(pos.asLong(),new PlatformTimers.Lease(lease.pulseUntil(),until));
            update(level,pos,until); // Do not reintroduce the source excluded by this notification.
        }
        // Rechecking can repopulate the section just invalidated by the source callback.
        // Leave it invalidated so subsequent R101 readers re-evaluate the settled world state.
        GloomTideDefense.invalidate(level,source);
    }
    public static void initialize(ServerLevel level, BlockPos pos) {
        if (!loaded(level,pos)) return;
        var state=level.getBlockState(pos);if(!state.is(OriBlocks.SPIRIT_PLATFORM))return;
        var data=PlatformTimers.get(level);var old=data.get(pos.asLong());
        if (old==null && state.getValue(LIT) || Flash.protectsSpawn(level,Vec3.atCenterOf(pos),Source.FLASH.radius)) {
            long until=level.getGameTime()+HOLD;
            data.put(pos.asLong(),new PlatformTimers.Lease(until,Math.max(until,old==null ? Long.MIN_VALUE : old.until())));
        }
        tick(level,pos);
    }
    public static void tick(ServerLevel level, BlockPos pos) {
        if (!loaded(level,pos))return;
        if (!level.getBlockState(pos).is(OriBlocks.SPIRIT_PLATFORM)) { PlatformTimers.get(level).remove(pos.asLong());return; }
        var data=PlatformTimers.get(level);var old=data.get(pos.asLong());
        long persistent=persistentUntil(level,pos,null), until=Math.max(persistent,old==null ? Long.MIN_VALUE : old.until());
        if(until>level.getGameTime())data.put(pos.asLong(),new PlatformTimers.Lease(old==null ? Long.MIN_VALUE : old.pulseUntil(),until));
        update(level,pos,until);
    }
    private static long persistentUntil(ServerLevel level,BlockPos pos,BlockPos excluded) {
        long now=level.getGameTime(),until=Long.MIN_VALUE;var center=Vec3.atCenterOf(pos);
        for(var p:GloomTideDefense.lightSources(level,center,Source.TREE.radius)) {
            if(p.equals(excluded) || !loaded(level,p))continue;
            var state=level.getBlockState(p);double distance=p.distSqr(pos);
            if((state.is(OriBlocks.SPIRIT_TREE_HEART)||state.is(OriBlocks.GREAT_TREE_HEART)) && within(Source.TREE,distance))until=Math.max(until,now+HOLD);
            if(state.is(OriBlocks.BASH_ANCHOR) && state.getValue(SpiritLanternBlock.LIT) && within(Source.LANTERN,distance)) {
                var ticks=(LevelChunkTicks<Block>)level.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4).getBlockTicks();
                long expiry=ticks.getAll().filter(t->t.type()==state.getBlock() && t.pos().equals(p))
                        .mapToLong(net.minecraft.world.ticks.ScheduledTick::triggerTick).max().orElse(now);
                until=Math.max(until,Math.max(now,expiry)+HOLD);
            }
        }
        return until;
    }
    private static void update(ServerLevel level,BlockPos pos,long until) {
        var state=level.getBlockState(pos);long now=level.getGameTime();
        if(until<=now) {
            PlatformTimers.get(level).remove(pos.asLong());
            var next=state.setValue(LIT,false).setValue(FADING,false).setValue(BLINK,false);
            if(next!=state)level.setBlock(pos,next,Block.UPDATE_ALL);
            return;
        }
        boolean pending=!state.getValue(LIT) && occupied(level,pos);
        boolean lit=state.getValue(LIT) || solidify(pending,now,until);
        var next=state.setValue(LIT,lit).setValue(FADING,lit && fading(now,until)).setValue(BLINK,lit && blink(now,until));
        // Warning changes only the model: suppress neighbor shape propagation as well as neighborChanged.
        if(next!=state)level.setBlock(pos,next,state.getValue(LIT)==lit ? Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE : Block.UPDATE_ALL);
        schedule(level,pos,PlatformRules.next(pending,now,until));
    }
    public static boolean occupied(ServerLevel level,BlockPos pos) {
        // Strict interior intersection: standing on the top face is safe; include items/projectiles too.
        var box=new AABB(pos).deflate(1e-6);
        return !level.getEntities((net.minecraft.world.entity.Entity)null,box,e->!e.isRemoved() && !e.isSpectator() && !e.noPhysics && e.getBoundingBox().intersects(box)).isEmpty();
    }
    private static boolean loaded(ServerLevel level,BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null;
    }
    private static void schedule(ServerLevel level,BlockPos pos,int delay) {
        var chunk=level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);if(chunk==null)return;
        var block=OriBlocks.SPIRIT_PLATFORM.get();var ticks=(LevelChunkTicks<Block>)chunk.getBlockTicks();
        long at=level.getGameTime()+delay;
        long existing=ticks.getAll().filter(t->t.type()==block && t.pos().equals(pos)).mapToLong(net.minecraft.world.ticks.ScheduledTick::triggerTick).min().orElse(Long.MAX_VALUE);
        if(existing<=at)return;
        ticks.removeIf(t->t.type()==block && t.pos().equals(pos));level.scheduleTick(pos,block,delay);chunk.setUnsaved(true);
    }
    /** No neighboring query in the load callback. Only matching palettes enumerate blocks; work is deferred. */
    public static void load(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk))return;
        initializeChunk(level,chunk);
    }
    public static int initializeChunk(ServerLevel level,LevelChunk chunk) {
        int found=0;var cp=chunk.getPos();
        // Only stored coordinates in this chunk; never load a neighboring chunk to prune the ledger.
        var data=PlatformTimers.get(level);
        for(long key:data.positions(cp.toLong()))
            if(!chunk.getBlockState(BlockPos.of(key)).is(OriBlocks.SPIRIT_PLATFORM))data.remove(key);
        for(int i=0;i<chunk.getSections().length;i++) {
            var section=chunk.getSection(i);if(!section.maybeHas(s->s.is(OriBlocks.SPIRIT_PLATFORM)))continue;
            int base=chunk.getSectionYFromSectionIndex(i)<<4;
            for(int y=0;y<16;y++)for(int x=0;x<16;x++)for(int z=0;z<16;z++)
                if(section.getBlockState(x,y,z).is(OriBlocks.SPIRIT_PLATFORM)) {
                    level.scheduleTick(new BlockPos((cp.x<<4)+x,base+y,(cp.z<<4)+z),OriBlocks.SPIRIT_PLATFORM.get(),1); found++;
                }
        }
        return found;
    }
    private PlatformLight() {}
}
