package cn.wwl.orimod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Pre-R115 chunks have the block IDs but no BE tags. Run before chunk packets enumerate BEs. */
public final class SanctuaryMigration {
    public static void load(ChunkEvent.Load event) {
        if (event.getChunk() instanceof LevelChunk chunk) upgrade(chunk);
    }
    public static int upgrade(LevelChunk chunk) {
        int count = 0;
        var pos = new BlockPos.MutableBlockPos();
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            if (!section.maybeHas(s -> s.getBlock() instanceof SanctuaryBlock)) continue;
            int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int y=0;y<16;y++) for (int z=0;z<16;z++) for (int x=0;x<16;x++) {
                if (!(section.getBlockState(x,y,z).getBlock() instanceof SanctuaryBlock)) continue;
                pos.set(chunk.getPos().getMinBlockX()+x,y0+y,chunk.getPos().getMinBlockZ()+z);
                if (chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK) == null
                        && chunk.getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE) != null) count++;
            }
        }
        if (count > 0 && !chunk.getLevel().isClientSide) chunk.setUnsaved(true);
        return count;
    }
    private SanctuaryMigration() {}
}
