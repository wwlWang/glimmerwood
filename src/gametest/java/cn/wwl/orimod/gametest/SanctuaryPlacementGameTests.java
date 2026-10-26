package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

/** Item-use acceptance contracts, deliberately independent of the multiblock implementation. */
@GameTestHolder("orimod_gametest")
public final class SanctuaryPlacementGameTests {
    static List<Block> buildings(){return List.of(OriBlocks.GLADES_WELL.get(),OriBlocks.FOREST_SHRINE.get(),OriBlocks.CREEK_SHRINE.get(),
            OriBlocks.CAVE_SHRINE.get(),OriBlocks.MAP_STONE.get(),OriBlocks.GREAT_TREE_HEART.get(),OriBlocks.GLOOM_ALTAR.get(),OriBlocks.STORM_ALTAR.get());}
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var block:buildings()){
            String id=BuiltInRegistries.BLOCK.getKey(block).getPath();
            add(tests,"item_footprint_"+id,h->{
                var p=origin(h);prepare(h,p);var stack=new ItemStack(block,2);
                h.assertTrue(place(player(h),p,stack).consumesAction(),"Item placement refused");
                h.assertValueEqual(stack.getCount(),1,"One building must cost one item");
                for(var cell:footprint(block,p)){
                    h.assertFalse(h.getLevel().isEmptyBlock(cell),"Missing occupied part at "+cell.subtract(p));
                    h.assertTrue((h.getLevel().getBlockEntity(cell)!=null)==cell.equals(p),"Only the core may own a BE");
                }
                if(block==OriBlocks.MAP_STONE.get())h.assertTrue(h.getLevel().isEmptyBlock(p.east()),"Map stone reserves horizontal overhang");
            });
            add(tests,"obstructed_upper_cell_"+id,h->{
                var p=origin(h);prepare(h,p);var target=p.above(2);h.getLevel().setBlock(target,Blocks.STONE.defaultBlockState(),3);
                var stack=new ItemStack(block,2);
                h.assertFalse(place(player(h),p,stack).consumesAction(),"Upper obstruction did not reject whole placement");
                h.assertValueEqual(stack.getCount(),2,"Obstructed placement consumed item");
                for(var cell:footprint(block,p))h.assertTrue(h.getLevel().getBlockState(cell).is(cell.equals(target)?Blocks.STONE:Blocks.AIR),"Preflight modified world");
            });
        }
        add(tests,"upper_part_removal_cleans_core_keeps_legacy",h->{
            var p=origin(h);prepare(h,p);var block=OriBlocks.MAP_STONE.get();
            h.getLevel().setBlock(p.east(),block.defaultBlockState(),3);
            h.assertTrue(place(player(h),p,new ItemStack(block)).consumesAction(),"Placement refused");
            h.getLevel().destroyBlock(p.above(2),false);
            for(var cell:footprint(block,p))h.assertTrue(h.getLevel().isEmptyBlock(cell),"Removing upper part left core/group");
            h.assertTrue(h.getLevel().getBlockState(p.east()).is(block),"Cleanup erased legacy neighbor");
        });
        add(tests,"map_upper_part_forwards_activation_once",h->{
            try(var c=Fixtures.connectedPlayer(h)){
                var p=origin(h);prepare(h,p);var player=c.player();player.setPos(Vec3.atBottomCenterOf(p.north(3)));
                h.assertTrue(place(player,p,new ItemStack(OriBlocks.MAP_STONE.get())).consumesAction(),"Placement refused");
                var top=p.above(2);h.getLevel().getBlockState(top).useWithoutItem(h.getLevel(),player,new BlockHitResult(Vec3.atCenterOf(top),Direction.NORTH,top,false));
                h.assertTrue(((SanctuaryBlockEntity)h.getLevel().getBlockEntity(p)).activated(),"Upper part did not activate its core");
            }
        });
        add(tests,"cancelled_event_restores_entire_footprint",h->{
            var p=origin(h);prepare(h,p);var player=player(h);var stack=new ItemStack(OriBlocks.GLADES_WELL.get(),2);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
            int[] captured={0};Consumer<BlockEvent.EntityMultiPlaceEvent> listener=e->{if(e.getEntity()==player){captured[0]=e.getReplacedBlockSnapshots().size();e.setCanceled(true);}};
            NeoForge.EVENT_BUS.addListener(listener);
            try{
                var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false)));
                h.assertValueEqual(captured[0],27,"Protection event did not receive 27 snapshots");
                h.assertFalse(result.consumesAction(),"Cancelled event consumed action");h.assertValueEqual(stack.getCount(),2,"Cancelled event consumed item");
                for(var cell:footprint(OriBlocks.GLADES_WELL.get(),p))h.assertTrue(h.getLevel().isEmptyBlock(cell)&&h.getLevel().getBlockEntity(cell)==null,"Cancellation left block/BE");
            }finally{NeoForge.EVENT_BUS.unregister(listener);}
        });
        add(tests,"basin_rim_is_solid_but_upper_air_is_walkable",h->{
            var p=origin(h);prepare(h,p);h.assertTrue(place(player(h),p,new ItemStack(OriBlocks.GLADES_WELL.get())).consumesAction(),"Placement refused");
            h.assertFalse(h.getLevel().noCollision(new AABB(p.getX()+1.2,p.getY()+.4,p.getZ()+.35,p.getX()+1.4,p.getY()+.7,p.getZ()+.65)),"Player can pass through stone basin rim");
            h.assertTrue(h.getLevel().noCollision(new AABB(p.getX()+.35,p.getY()+1.1,p.getZ()-.8,p.getX()+.65,p.getY()+2.7,p.getZ()-.4)),"Approach air has invisible collision");
        });
        add(tests,"legacy_and_water_heart_stay_single",h->{
            var p=origin(h);prepare(h,p);
            for(var block:buildings()){
                h.getLevel().setBlock(p,block.defaultBlockState(),3);
                h.assertTrue(h.getLevel().isEmptyBlock(p.above()),"Template core auto-expanded");
                h.getLevel().setBlock(p,Blocks.AIR.defaultBlockState(),3);
            }
            h.assertTrue(place(player(h),p,new ItemStack(OriBlocks.GINSO_WATER_HEART.get())).consumesAction(),"Water heart item refused");
            h.assertTrue(h.getLevel().isEmptyBlock(p.above())&&h.getLevel().getBlockState(p).getCollisionShape(h.getLevel(),p).isEmpty(),"Escape trigger changed");
        });
        add(tests,"new_well_has_safe_destination_outside_basin",h->{
            var p=origin(h);prepare(h,p);var player=player(h);
            h.assertTrue(place(player,p,new ItemStack(OriBlocks.GLADES_WELL.get())).consumesAction(),"Placement refused");
            var stand=cn.wwl.orimod.world.SpiritWell.safeDestination(h.getLevel(),p,player);
            h.assertTrue(stand!=null,"Old adjacent-only candidates reject the new basin; an outside landing is required");
            h.assertTrue(Math.max(Math.abs(stand.getX()-p.getX()),Math.abs(stand.getZ()-p.getZ()))>=2,"Destination is inside the basin footprint");
            var at=Vec3.atBottomCenterOf(stand);h.assertTrue(h.getLevel().noCollision(player,player.getBoundingBox().move(at.subtract(player.position()))),"Destination clips stone");
        });
        return tests;
    }
    static BlockPos origin(GameTestHelper h){return h.absolutePos(new BlockPos(6,3,6));}
    static List<BlockPos> footprint(Block block,BlockPos p){var out=new ArrayList<BlockPos>();int r=block==OriBlocks.MAP_STONE.get()?0:1;
        for(int y=0;y<3;y++)for(int x=-r;x<=r;x++)for(int z=-r;z<=r;z++)out.add(p.offset(x,y,z));return out;}
    static void prepare(GameTestHelper h,BlockPos p){for(var cell:BlockPos.betweenClosed(p.offset(-3,-1,-3),p.offset(3,3,3)))
        h.getLevel().setBlock(cell,cell.getY()<p.getY()?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);}
    static ServerPlayer player(GameTestHelper h){var p=Fixtures.player(h);p.setPos(h.absoluteVec(new Vec3(2,3,2)));p.setYRot(180);return p;}
    static InteractionResult place(ServerPlayer player,BlockPos p,ItemStack stack){return ((BlockItem)stack.getItem()).place(new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,
            new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false)));}
    static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> body){tests.add(new TestFunction("sanctuary_placement","sanctuary_placement."+name,"orimod_gametest:room",80,0,true,h->{body.accept(h);h.succeed();}));}
}
