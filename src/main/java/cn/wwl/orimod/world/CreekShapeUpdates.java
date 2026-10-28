package cn.wwl.orimod.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Only the placed block's own no-op update; reverse edge updates remain vanilla. */
public final class CreekShapeUpdates {
    private static final ClassValue<Boolean> UNCHANGED=new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type){
            for(var c=type;c!=null;c=c.getSuperclass()){
                try{
                    c.getDeclaredMethod("updateShape",BlockState.class,Direction.class,BlockState.class,LevelAccessor.class,BlockPos.class,BlockPos.class);
                    return c==BlockBehaviour.class;
                }catch(NoSuchMethodException ignored){}
                catch(SecurityException denied){return false;}
            }
            return false;
        }
    };
    static boolean unchanged(Block block){return UNCHANGED.get(block.getClass());}
    static boolean unchanged(Class<? extends Block> type){return UNCHANGED.get(type);}
    public static boolean maySkip(BlockState state,StructurePlaceSettings settings){
        return settings instanceof CreekSliceSettings&&unchanged(state.getBlock());
    }
    private CreekShapeUpdates(){}
}
