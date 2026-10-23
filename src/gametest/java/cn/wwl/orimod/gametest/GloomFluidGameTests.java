package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class GloomFluidGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        add(tests,"source_flows_but_never_renews",0,s->{
            var p=s.base;s.floor(10);s.level.setBlock(p,OriBlocks.GLOOM_WATER.get().defaultBlockState(),3);
            s.later(100,()->{
                s.h.assertTrue(s.level.getFluidState(p).isSource(),"Old default block lost source state");
                s.h.assertTrue(s.level.getBlockState(p.east()).is(OriBlocks.GLOOM_WATER),"Liquid did not spread");
                s.h.assertFalse(s.level.getFluidState(p.east()).isSource(),"Flow made an infinite source");
                s.h.assertTrue(s.level.getFluidState(p.east(4)).isEmpty(),"Spread exceeded three cells on flat ground");
                s.h.assertValueEqual(s.level.getFluidState(p.east()).getAmount(),6,"Flow decrease is not two");
                s.done();
            });
        });
        add(tests,"no_mining_outline_drop_or_bucket",1,s->{
            var p=s.base;var block=OriBlocks.GLOOM_WATER.get();var state=block.defaultBlockState();s.level.setBlock(p,state,3);
            s.h.assertTrue(state.getShape(s.level,p).isEmpty() && state.getCollisionShape(s.level,p).isEmpty(),"Fluid selectable/collidable");
            s.h.assertTrue(state.getDestroySpeed(s.level,p)<0,"Fluid can be mined");
            s.h.assertTrue(Block.getDrops(state,s.level,p,null).isEmpty(),"Liquid drops items");
            s.h.assertTrue(block.pickupBlock(null,s.level,p,state).isEmpty(),"Liquid bucket obtainable");
            s.h.assertTrue(s.level.getBlockState(p).is(block),"Bucket deleted the source");
            s.h.assertTrue(state.getFluidState().is(net.minecraft.tags.FluidTags.WATER),"Water swimming tag missing");s.done();
        });
        add(tests,"source_and_flow_cleanse",2,s->{
            var p=s.base;s.floor(8);s.level.setBlock(p,OriBlocks.GLOOM_WATER.get().defaultBlockState(),3);
            s.later(80,()->{
                var before=s.cells();s.h.assertTrue(before.size()>1,"Fixture did not flow");
                s.h.assertValueEqual(GloomWaterBlock.cleanse(s.level,p,8),before.size(),"Source/flow cleanse missed cells");
                for(var at:before)s.h.assertTrue(s.level.getBlockState(at).is(Blocks.WATER),"Cleansed cell not water");
                s.later(30,()->{s.h.assertTrue(s.cells().isEmpty(),"Corruption returned without source");s.done();});
            });
        });
        add(tests,"contact_hurts_swimming_spirit",3,s->{
            try(var c=Fixtures.connectedPlayer(s.h)) {
                var p=c.player();for(int i=0;i<61;i++)p.tick();p.setData(cn.wwl.orimod.spirit.SpiritData.FORM,true);p.setHealth(20);p.invulnerableTime=0;
                s.floor(8);s.level.setBlock(s.base,OriBlocks.GLOOM_WATER.get().defaultBlockState(),3);p.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(s.base));p.baseTick();
                s.h.assertTrue(OriFluids.inWater(p) && p.canSwimInFluidType(OriFluids.GLOOM_TYPE.get()),"Custom fluid is not swimmable");
                p.move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(.001,0,0));s.h.assertTrue(p.getHealth()<20,"Spirit ignored corruption contact");
            }s.done();
        });
        for(var kind:RuinKind.values())add(tests,"pool_contained_"+kind.id,4+kind.ordinal(),s->{
            var placement=RuinPlacement.atMinimum(kind,s.base,Rotation.NONE);
            var piece=new RuinPiece(s.level.getStructureManager(),kind,placement);
            piece.postProcess(s.level,s.level.structureManager(),s.level.getChunkSource().getGenerator(),RandomSource.create(114),piece.getBoundingBox(),new ChunkPos(s.base),BlockPos.ZERO);
            var initial=s.cells();if(kind==RuinKind.CREEK)s.h.assertFalse(initial.isEmpty(),"Creek pool fixture absent");
            s.later(200,()->{var added=s.cells();added.removeAll(initial);s.h.assertTrue(added.isEmpty(),"Pool leaked outside original set: "+added.stream().map(p->p.subtract(s.base)).limit(12).toList());s.done();});
        });
        add(tests,"pool_contained_blight",7,s->{
            s.floor(16);
            var feature=new BlightedGroundsFeature();
            s.h.assertTrue(feature.place(new FeaturePlaceContext<>(Optional.empty(),s.level,s.level.getChunkSource().getGenerator(),RandomSource.create(114),s.base,NoneFeatureConfiguration.INSTANCE)),"Blight fixture rejected");
            var initial=s.cells();s.h.assertFalse(initial.isEmpty(),"Blight pools absent");
            s.later(200,()->{var added=s.cells();added.removeAll(initial);s.h.assertTrue(added.isEmpty(),"Blight pools leaked: "+added.stream().map(p->p.subtract(s.base)).limit(12).toList());s.done();});
        });
        add(tests,"pool_contained_blight_on_five_block_slope",8,s->{
            // Five one-block terraces, west lower than east. Solid beds extend below every terrace.
            for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++) {
                int top=Math.clamp(Math.floorDiv(x+10,4),0,5);
                for(int y=-3;y<top;y++)s.level.setBlock(s.base.offset(x,y,z),Blocks.DIRT.defaultBlockState(),18);
            }
            var plan=BlightedGroundsShape.plan(RandomSource.create(114).nextLong());
            var exposed=new HashSet<BlockPos>();int min=Integer.MAX_VALUE,max=Integer.MIN_VALUE;
            for(var offset:plan.soil()) {
                var at=s.base.offset(offset);
                int y=s.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at.getX(),at.getZ());
                min=Math.min(min,y);max=Math.max(max,y);
                if(plan.pools().contains(offset))for(var side:net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    var next=at.relative(side);
                    if(s.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,next.getX(),next.getZ())<y)
                        exposed.add(new BlockPos(at.getX(),y-1,at.getZ()));
                }
            }
            s.h.assertValueEqual(max-min,5,"Sloped soil disc relief");
            s.h.assertFalse(exposed.isEmpty(),"Fixture has no downhill-exposed pool candidates");
            s.h.assertTrue(new BlightedGroundsFeature().place(new FeaturePlaceContext<>(Optional.empty(),s.level,s.level.getChunkSource().getGenerator(),RandomSource.create(114),s.base,NoneFeatureConfiguration.INSTANCE)),"Sloped blight rejected");
            var initial=s.cells();s.h.assertFalse(initial.isEmpty(),"Slope removed every pool; flow check would be vacuous");
            for(var at:exposed)s.h.assertTrue(s.level.getBlockState(at).is(OriBlocks.BLIGHTED_SOIL),"Exposed pool was not kept as soil: "+at.subtract(s.base));
            s.later(200,()->{
                var current=s.cells();s.h.assertTrue(current.containsAll(initial),"Sloped pool sources disappeared");
                current.removeAll(initial);s.h.assertTrue(current.isEmpty(),"Sloped blight pools leaked: "+current.stream().map(p->p.subtract(s.base)).limit(12).toList());
                com.mojang.logging.LogUtils.getLogger().info("R114 sloped blight: relief=5, candidates={}, exposed={}, kept={}, new_after_200=0",plan.pools().size(),exposed.size(),initial.size());
                s.done();
            });
        });
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,int index,Consumer<Site> run) {
        tests.add(new TestFunction("gloom_fluid","gloom_fluid."+name,"orimod_gametest:platform_room",280,0,true,h->{
            var s=new Site(h,index);s.checked(()->run.accept(s));
            h.runAfterDelay(270,()->{s.close();h.fail("Fluid test did not finish");});
        }));
    }
    private static final class Site {
        static final TicketType<UUID> TICKET=TicketType.create("orimod_fluid_test",UUID::compareTo);
        final GameTestHelper h; final ServerLevel level; final BlockPos base; final UUID ticket=UUID.randomUUID();
        final List<ChunkPos> chunks=new ArrayList<>();final Map<BlockPos,BlockState> old=new HashMap<>();boolean closed;
        Site(GameTestHelper h,int index) {
            this.h=h;level=h.getLevel();base=h.absolutePos(new BlockPos(256+index*192,100,0));
            for(int x=(base.getX()-18)>>4;x<=(base.getX()+42)>>4;x++)for(int z=(base.getZ()-18)>>4;z<=(base.getZ()+42)>>4;z++){
                var c=new ChunkPos(x,z);chunks.add(c);level.getChunkSource().addRegionTicket(TICKET,c,2,ticket);level.getChunk(x,z);
            }
            for(var pos:BlockPos.betweenClosed(base.offset(-18,-4,-18),base.offset(42,34,42))){old.put(pos.immutable(),level.getBlockState(pos));level.setBlock(pos,Blocks.AIR.defaultBlockState(),18);}
        }
        void floor(int r){for(var p:BlockPos.betweenClosed(base.offset(-r,-3,-r),base.offset(r,-1,r)))level.setBlock(p,Blocks.DIRT.defaultBlockState(),18);}
        Set<BlockPos> cells(){var found=new HashSet<BlockPos>();for(var p:old.keySet())if(level.getBlockState(p).is(OriBlocks.GLOOM_WATER))found.add(p);return found;}
        void checked(Runnable r){try{r.run();}catch(RuntimeException|Error e){close();throw e;}}
        void later(int ticks,Runnable r){h.runAfterDelay(ticks,()->checked(r));}
        void done(){close();h.succeed();}
        void close(){if(closed)return;closed=true;try{old.forEach((p,s)->level.setBlock(p,s,18));}finally{for(var c:chunks)level.getChunkSource().removeRegionTicket(TICKET,c,2,ticket);}}
    }
}
