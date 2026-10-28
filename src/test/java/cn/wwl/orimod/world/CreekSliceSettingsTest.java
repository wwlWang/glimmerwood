package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CreekSliceSettingsTest {
    @ParameterizedTest @EnumSource(Rotation.class)
    void partitionsTransformedCoordinatesPreservingInputOrderAndNbtIdentity(Rotation rotation){
        var infos=new ArrayList<StructureTemplate.StructureBlockInfo>();
        for(int y=0;y<3;y++)for(int z=-2;z<35;z++)for(int x=-3;x<29;x++){
            var tag=new CompoundTag();tag.putInt("index",infos.size());
            infos.add(new StructureTemplate.StructureBlockInfo(new BlockPos(x,y,z),null,tag));
        }
        var origin=new BlockPos(-21,70,31);
        var index=new CreekSliceSettings.LocalColumns(infos);
        for(var mirror:Mirror.values()){
            var settings=new StructurePlaceSettings().setRotation(rotation).setMirror(mirror).setRotationPivot(new BlockPos(7,0,11));
            int total=0;
            for(int cx=-6;cx<=6;cx++)for(int cz=-6;cz<=6;cz++){
                var clip=new net.minecraft.world.level.levelgen.structure.BoundingBox(cx*16,-64,cz*16,cx*16+15,319,cz*16+15);
                var actual=index.slice(origin,settings,clip);
                var expected=infos.stream().filter(info->clip.isInside(StructureTemplate.calculateRelativePosition(settings,info.pos()).offset(origin))).toList();
                assertEquals(expected.size(),actual.size());
                for(int i=0;i<actual.size();i++)assertSame(expected.get(i),actual.get(i),"Source order and NBT identity");
                total+=actual.size();
            }
            assertEquals(infos.size(),total);
        }
    }
}
