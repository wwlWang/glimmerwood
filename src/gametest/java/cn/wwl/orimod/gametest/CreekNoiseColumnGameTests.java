package cn.wwl.orimod.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.levelgen.*;
import net.neoforged.neoforge.gametest.GameTestHolder;

@GameTestHolder("orimod_creek_rebuild")
public final class CreekNoiseColumnGameTests {
    @GameTestGenerator public static java.util.Collection<TestFunction> cases(){
        return java.util.List.of(new TestFunction("creek_noise","creek_noise.native_cell_cache_matches_original_height_water_and_full_column",
                "orimod_creek_rebuild:room",net.minecraft.world.level.block.Rotation.NONE,200,0,true,h->{
                    try{native_cell_cache_matches_original_height_water_and_full_column(h);}catch(Exception e){throw new AssertionError(e);}
                }));
    }
    public static void native_cell_cache_matches_original_height_water_and_full_column(GameTestHelper h)throws Exception{
        var level=h.getLevel();var settings=level.registryAccess().registryOrThrow(Registries.NOISE_SETTINGS).getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
        var generator=new NoiseBasedChunkGenerator(level.getChunkSource().getGenerator().getBiomeSource(),settings);
        var random=RandomState.create(settings.value(),level.registryAccess().lookupOrThrow(Registries.NOISE),116118L);
        var type=Class.forName("cn.wwl.orimod.world.CreekSurfaceSample$Cells");
        var ctor=type.getDeclaredConstructor(net.minecraft.world.level.chunk.ChunkGenerator.class,net.minecraft.world.level.LevelHeightAccessor.class,RandomState.class);ctor.setAccessible(true);
        var cache=ctor.newInstance(generator,level,random);
        var method=type.getDeclaredMethod("at",int.class,int.class);method.setAccessible(true);
        var rng=new java.util.Random(116118);int wet=0,dry=0;
        var points=new java.util.ArrayList<int[]>();
        for(int cell=0;cell<32;cell++){
            int x0=cell==0?8:(rng.nextInt(8192)-4096)*4,z0=cell==0?-816:(rng.nextInt(8192)-4096)*4;
            for(int dx=0;dx<4;dx++)for(int dz=0;dz<4;dz++)points.add(new int[]{x0+dx,z0+dz});
        }
        // Deliberately interleave cells and query each native cell out of column order.
        java.util.Collections.shuffle(points,new java.util.Random(115));
        for(var point:points){
            int x=point[0],z=point[1];
            var result=method.invoke(cache,x,z);
            int floor=generator.getBaseHeight(x,z,Heightmap.Types.OCEAN_FLOOR_WG,level,random),surface=generator.getBaseHeight(x,z,Heightmap.Types.WORLD_SURFACE_WG,level,random);
            var column=generator.getBaseColumn(x,z,level,random);
            boolean isDry=column.getBlock(floor).getFluidState().isEmpty()&&column.getBlock(floor-1).getFluidState().isEmpty()&&!column.getBlock(floor-1).isAir();
            if(isDry)dry++;else wet++;
            for(var name:new String[]{"floor","surface","dry"}){
                var accessor=result.getClass().getDeclaredMethod(name);accessor.setAccessible(true);
                Object expected=name.equals("floor")?floor:name.equals("surface")?surface:isDry;
                h.assertValueEqual(accessor.invoke(result),expected,"Native cell cache differs at "+x+","+z+" field="+name);
            }
        }
        var reverseCache=ctor.newInstance(generator,level,random);java.util.Collections.reverse(points);
        for(var point:points)h.assertValueEqual(method.invoke(reverseCache,point[0],point[1]),method.invoke(cache,point[0],point[1]),"Noise cache query order differs");
        h.assertTrue(wet>0&&dry>0,"Coverage must include both submerged and dry terrain");
        System.out.println("CREEK_NATIVE_CELL checked=512 dry="+dry+" wet="+wet+" differences=0");h.succeed();
    }
}
