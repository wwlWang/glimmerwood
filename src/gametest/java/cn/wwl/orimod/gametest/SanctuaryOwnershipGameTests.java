package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.item.SanctuaryItem;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.*;
import java.lang.reflect.InvocationTargetException;
import static cn.wwl.orimod.gametest.SanctuaryPlacementGameTests.*;

@GameTestHolder("orimod_gametest")
public final class SanctuaryOwnershipGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(var kind:SanctuaryKind.values()){
            add(tests,"ownership_pick_permissions_save_rotate_"+kind.getSerializedName(),h->{
                var p=origin(h);var player=player(h);
                for(var facing:Direction.Plane.HORIZONTAL){
                    prepare(h,p);player.setYRot(facing.getOpposite().toYRot());
                    h.assertTrue(place(player,p,new ItemStack(kind.block())).consumesAction(),"Placement refused");
                    var owner=new SanctuaryAssembly.Owner(p,kind,facing);
                    for(var cell:footprint(kind.block(),p)){
                        var s=h.getLevel().getBlockState(cell);
                        h.assertValueEqual(SanctuaryAssembly.owner(cell,s),owner,"Part owner/facing mismatch");
                        h.assertTrue(s.getCloneItemStack(new BlockHitResult(Vec3.atCenterOf(cell),Direction.UP,cell,false),h.getLevel(),cell,player).is(kind.block().asItem()),"Pick-block did not return core");
                        h.assertFalse(s.getDestroyProgress(player,h.getLevel(),cell)>0,"Unobtainable building member can be mined");
                        h.assertValueEqual(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),NbtUtils.writeBlockState(s)),s,"Palette round trip changed ownership");
                        if(!cell.equals(p)){
                            h.assertValueEqual(s.getRenderShape(),RenderShape.INVISIBLE,"Part rendered another model");
                            h.assertValueEqual(s.getLightBlock(h.getLevel(),cell),0,"Part blocks skylight");
                            h.assertFalse(s.canOcclude(),"Part occludes adjacent faces");
                            for(var mirror:List.of(Mirror.LEFT_RIGHT,Mirror.FRONT_BACK)){
                                var offset=cell.subtract(p);var mirrored=p.offset(mirror==Mirror.FRONT_BACK?-offset.getX():offset.getX(),offset.getY(),mirror==Mirror.LEFT_RIGHT?-offset.getZ():offset.getZ());
                                h.assertValueEqual(SanctuaryAssembly.owner(mirrored,s.mirror(mirror)),new SanctuaryAssembly.Owner(p,kind,mirror.mirror(facing)),"Mirror lost core");
                            }
                        }
                    }
                }
            });
            add(tests,"replace_each_member_keeps_adjacent_group_and_legacy_"+kind.getSerializedName(),h->{
                var p=origin(h);var player=player(h);var other=p.east(kind.radius()*2+1);var legacy=p.west(kind.radius()+1);
                prepare(h,p);prepare(h,other);
                h.assertTrue(place(player,other,new ItemStack(kind.block())).consumesAction(),"Second group refused");
                var expected=new HashMap<BlockPos,BlockState>();for(var cell:footprint(kind.block(),other))expected.put(cell,h.getLevel().getBlockState(cell));
                h.getLevel().setBlock(legacy,kind.block().defaultBlockState(),3);
                for(var target:footprint(kind.block(),p)){
                    h.assertTrue(place(player,p,new ItemStack(kind.block())).consumesAction(),"First group refused");
                    h.getLevel().setBlock(target,Blocks.GOLD_BLOCK.defaultBlockState(),3);
                    for(var cell:footprint(kind.block(),p))h.assertTrue(h.getLevel().getBlockState(cell).is(cell.equals(target)?Blocks.GOLD_BLOCK:Blocks.AIR),"Replacement erased itself or left orphan");
                    expected.forEach((cell,s)->h.assertValueEqual(h.getLevel().getBlockState(cell),s,"Adjacent group changed"));
                    h.assertTrue(h.getLevel().getBlockState(legacy).is(kind.block())&&!SanctuaryAssembly.assembled(h.getLevel().getBlockState(legacy)),"Legacy changed");
                    h.getLevel().setBlock(target,Blocks.AIR.defaultBlockState(),3);
                }
            });
            add(tests,"piston_and_single_drop_path_"+kind.getSerializedName(),h->{
                var p=origin(h);prepare(h,p);var player=player(h);h.assertTrue(place(player,p,new ItemStack(kind.block())).consumesAction(),"Placement refused");
                for(var target:footprint(kind.block(),p)){
                    h.assertFalse(new PistonStructureResolver(h.getLevel(),target.north(),Direction.SOUTH,true).resolve(),"Piston can push member");
                    h.assertFalse(new PistonStructureResolver(h.getLevel(),target.north(2),Direction.SOUTH,false).resolve(),"Sticky piston can pull member");
                }
                int[] events={0};Consumer<BlockDropsEvent> listener=e->{if(e.getLevel()==h.getLevel()&&footprint(kind.block(),p).contains(e.getPos()))events[0]++;};
                NeoForge.EVENT_BUS.addListener(listener);
                try{h.getLevel().destroyBlock(p.above(),true);}finally{NeoForge.EVENT_BUS.unregister(listener);}
                h.assertValueEqual(events[0],1,"Group executed more than one initiating drop path");
                h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(p).inflate(2)).stream().noneMatch(e->e.getItem().is(kind.block().asItem())),"Added a building item drop to default no-loot behavior");
                for(var cell:footprint(kind.block(),p))h.assertTrue(h.getLevel().isEmptyBlock(cell),"Drop cleanup left member");
            });
        }
        for(boolean capture:List.of(false,true))for(String fault:List.of("before","after","exception","invalidates_core"))add(tests,"rollback_"+fault+"_"+capture,h->rollback(h,capture,fault));
        add(tests,"legacy_nbt_defaults_and_bloomed_state_do_not_dismantle",h->{
            for(var block:buildings()){
                var tag=new CompoundTag();tag.putString("Name",BuiltInRegistries.BLOCK.getKey(block).toString());
                h.assertFalse(SanctuaryAssembly.assembled(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),tag)),"Missing mode did not decode Legacy");
            }
            var p=origin(h);prepare(h,p);h.assertTrue(place(player(h),p,new ItemStack(OriBlocks.GREAT_TREE_HEART.get())).consumesAction(),"Heart refused");
            var before=h.getLevel().getBlockEntity(p);
            h.getLevel().setBlock(p,h.getLevel().getBlockState(p).setValue(SpiritTreeHeartBlock.BLOOMED,true),3);
            h.assertTrue(h.getLevel().getBlockEntity(p)==before,"Bloom replaced BE");
            for(var cell:footprint(OriBlocks.GREAT_TREE_HEART.get(),p))h.assertFalse(h.getLevel().isEmptyBlock(cell),"Bloom dismantled heart");
            h.assertFalse(OriBlocks.GINSO_WATER_HEART.get().defaultBlockState().hasProperty(SanctuaryBlock.ASSEMBLED),"Escape heart gained assembly mode");
        });
        add(tests,"item_components_cannot_forge_legacy_or_facing",h->{
            var p=origin(h);prepare(h,p);var player=player(h);player.setYRot(Direction.WEST.toYRot());
            var stack=new ItemStack(OriBlocks.MAP_STONE.get(),2);var props=new BlockItemStateProperties(Map.of("assembled","false","facing","south"));stack.set(DataComponents.BLOCK_STATE,props);
            h.assertTrue(place(player,p,stack).consumesAction(),"Tagged item refused");h.assertValueEqual(stack.get(DataComponents.BLOCK_STATE),props,"Remaining item changed");
            h.assertValueEqual(SanctuaryAssembly.owner(p,h.getLevel().getBlockState(p)),new SanctuaryAssembly.Owner(p,SanctuaryKind.MAP,Direction.EAST),"Component forged ownership");
        });
        add(tests,"ceiling_permission_and_entity_preflight_are_atomic",h->{
            var p=origin(h);prepare(h,p);var player=player(h);var stack=new ItemStack(OriBlocks.GLADES_WELL.get(),2);
            player.getAbilities().mayBuild=false;h.assertFalse(place(player,p,stack).consumesAction(),"Restricted player built parts");player.getAbilities().mayBuild=true;
            var cow=EntityType.COW.create(h.getLevel());cow.setNoAi(true);cow.setPos(Vec3.atBottomCenterOf(p.east()));h.getLevel().addFreshEntity(cow);
            try{h.assertFalse(place(player,p,stack).consumesAction(),"Entity inside basin ignored");}finally{cow.discard();}
            var high=p.atY(h.getLevel().getMaxBuildHeight()-2);var support=high.below();var old=h.getLevel().getBlockState(support);h.getLevel().setBlock(support,Blocks.STONE.defaultBlockState(),3);
            try{h.assertFalse(place(player,high,stack).consumesAction(),"Building exceeded ceiling");}finally{h.getLevel().setBlock(support,old,3);}
            h.assertValueEqual(stack.getCount(),2,"Failed preflight consumed item");for(var cell:footprint(OriBlocks.GLADES_WELL.get(),p))h.assertTrue(h.getLevel().isEmptyBlock(cell),"Failed preflight wrote parts");
        });
        add(tests,"player_can_stand_on_new_basin_rim",h->{
            var p=origin(h);prepare(h,p);var player=player(h);
            h.assertTrue(place(player,p,new ItemStack(OriBlocks.GLADES_WELL.get())).consumesAction(),"Well refused");
            player.setPos(p.getX()+1.35,p.getY()+3,p.getZ()+.5);
            player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,-3,0));
            h.assertTrue(player.onGround(),"Player fell through basin rim");
            h.assertTrue(Math.abs(player.getY()-p.getY()-1.055)<.001,"Player did not land on visible rim height");
        });
        add(tests,"legacy_well_keeps_adjacent_destination",h->{
            var p=origin(h);prepare(h,p);h.getLevel().setBlock(p,OriBlocks.GLADES_WELL.get().defaultBlockState(),3);
            h.assertValueEqual(cn.wwl.orimod.world.SpiritWell.safeDestination(h.getLevel(),p,player(h)),p.north(),"Legacy well travel changed");
        });
        return tests;
    }
    private static void rollback(GameTestHelper h,boolean capture,String fault){
        var p=origin(h);prepare(h,p);var level=h.getLevel();var player=player(h);var stack=new ItemStack(OriBlocks.GLADES_WELL.get(),2);
        var before=new HashMap<BlockPos,BlockState>();for(var cell:footprint(OriBlocks.GLADES_WELL.get(),p)){
            var s=cell.getX()<p.getX()?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState();level.setBlock(cell,s,18);before.put(cell,s);
        }
        var state=OriBlocks.GLADES_WELL.get().defaultBlockState().setValue(SanctuaryBlock.ASSEMBLED,true);
        var context=new BlockPlaceContext(player,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atBottomCenterOf(p),Direction.UP,p.below(),false));
        boolean previous=level.captureBlockSnapshots;int captured=level.capturedBlockSnapshots.size();int[] writes={0};var injected=new IllegalStateException("injected sanctuary write failure");
        level.captureBlockSnapshots=capture;
        try{
            boolean success=false,threw=false;
            try{success=invoke(context,state,(cell,s)->{
                int n=++writes[0];if(n==6&&fault.equals("before"))return false;boolean changed=level.setBlock(cell,s,3);
                if(n==6&&fault.equals("after"))return false;if(n==6&&fault.equals("exception"))throw injected;
                if(n==27&&fault.equals("invalidates_core"))level.setBlock(p,Blocks.AIR.defaultBlockState(),3);return changed;
            });}catch(IllegalStateException e){if(e!=injected)throw e;threw=true;}
            h.assertFalse(success,"Partial transaction accepted");h.assertValueEqual(writes[0],fault.equals("invalidates_core")?27:6,"Fixture never reached fault");h.assertValueEqual(threw,fault.equals("exception"),"Exception not propagated");
            before.forEach((cell,s)->{h.assertValueEqual(level.getBlockState(cell),s,"Rollback lost original cell");h.assertTrue(level.getBlockEntity(cell)==null,"Rollback retained BE");});
            h.assertValueEqual(level.capturedBlockSnapshots.size(),captured,"Leaked snapshots");h.assertValueEqual(level.captureBlockSnapshots,capture,"Capture mode changed");h.assertFalse(level.restoringBlockSnapshots,"Restore mode leaked");
        }finally{level.captureBlockSnapshots=previous;}
    }
    private static boolean invoke(BlockPlaceContext c,BlockState s,BiPredicate<BlockPos,BlockState> writer){try{
        var m=SanctuaryItem.class.getDeclaredMethod("placeParts",BlockPlaceContext.class,BlockState.class,BiPredicate.class);m.setAccessible(true);return (boolean)m.invoke(null,c,s,writer);
    }catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new AssertionError(e.getCause());}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
}
