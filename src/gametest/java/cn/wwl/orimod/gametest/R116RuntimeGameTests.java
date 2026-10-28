package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.world.PracticeTarget;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.*;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class R116RuntimeGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        add(tests,"practice_player_owner_and_usable",h->{
            try(var connection=Fixtures.connectedPlayer(h)){
                var p=connection.player();var base=h.absolutePos(new BlockPos(5,2,6));h.getLevel().setBlock(base,Blocks.STONE.defaultBlockState(),3);
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.PRACTICE_TARGET_SPAWN_EGG.get()));
                OriItems.PRACTICE_TARGET_SPAWN_EGG.get().useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false)));
                var targets=h.getLevel().getEntitiesOfClass(PracticeTarget.class,new AABB(base).inflate(3));
                try{
                    h.assertValueEqual(targets.size(),1,"Egg failed to spawn target");var target=targets.getFirst();
                    h.assertTrue(p.getUUID().equals(target.owner),"Egg target has no player owner: "+target.owner);
                    for(int tick=0;tick<60;tick++)target.tick();h.assertTrue(target.isAlive(),"Practice target discarded outside challenge");
                    p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_BLADE.get()));
                    float hp=target.getHealth();h.assertTrue(target.hurt(h.getLevel().damageSources().playerAttack(p),3),"Owner cannot practice");
                    h.assertTrue(target.getHealth()<hp,"Practice hit did not damage target");
                    h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),0,"Free practice farmed challenge reward");
                }finally{targets.forEach(Entity::discard);}
            }
        });
        add(tests,"practice_no_player_no_consumption",h->{
            var pos=h.absolutePos(new BlockPos(6,2,5));var stack=new ItemStack(OriItems.PRACTICE_TARGET_SPAWN_EGG.get());
            var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
            var use=new UseOnContext(h.getLevel(),null,InteractionHand.MAIN_HAND,stack,hit);
            try{stack.getItem().useOn(use);h.assertValueEqual(stack.getCount(),1,"Playerless use consumed egg");
                h.assertTrue(h.getLevel().getEntitiesOfClass(PracticeTarget.class,new AABB(pos).inflate(3)).isEmpty(),"Playerless egg spawned target");
            }finally{h.getLevel().getEntitiesOfClass(PracticeTarget.class,new AABB(pos).inflate(3)).forEach(Entity::discard);}
        });
        add(tests,"practice_dispenser_no_consumption",h->{
            var pos=h.absolutePos(new BlockPos(6,2,5));var level=h.getLevel();level.setBlock(pos,Blocks.DISPENSER.defaultBlockState(),3);
            var stack=new ItemStack(OriItems.PRACTICE_TARGET_SPAWN_EGG.get());
            var source=new BlockSource(level,pos,level.getBlockState(pos),(DispenserBlockEntity)level.getBlockEntity(pos));
            try{var result=DispenserBlock.DISPENSER_REGISTRY.get(stack.getItem()).dispense(source,stack);
                h.assertValueEqual(result.getCount(),1,"Dispenser consumed practice egg");h.assertTrue(level.getEntitiesOfClass(PracticeTarget.class,new AABB(pos).inflate(3)).isEmpty(),"Dispenser spawned ownerless target");
            }finally{level.getEntitiesOfClass(PracticeTarget.class,new AABB(pos).inflate(3)).forEach(Entity::discard);}
        });
        add(tests,"stacked_updraft_jump_and_glide",h->{
            var level=h.getLevel();var base=h.absolutePos(new BlockPos(5,20,5));
            for(int y=0;y<22;y++)for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            for(int y=0;y<3;y++)level.setBlock(base.above(y),OriBlocks.UPDRAFT.get().defaultBlockState(),3);
            h.assertFalse(level.getBlockState(base).getCollisionShape(level,base).isEmpty(),"Single bottom vent changed");
            for(int y=1;y<3;y++)h.assertTrue(level.getBlockState(base.above(y)).getCollisionShape(level,base.above(y)).isEmpty(),"Stacked vent blocks launch");
            var p=Fixtures.player(h);p.setData(SpiritData.FORM,true);p.refreshDimensions();p.setData(SpiritData.ABILITIES,4);
            p.setPos(Vec3.atBottomCenterOf(base).add(0,3.0/16,0));double start=p.getY();
            p.move(MoverType.SELF,new Vec3(0,.42,0));h.assertTrue(p.getY()>start+.4,"Ground jump struck upper vent");
            double vy=-.08;
            for(int i=0;i<25;i++){
                h.assertTrue(UpdraftBlock.lifting(level,p),"Stacked vents interrupted lift at "+p.position());
                vy=cn.wwl.orimod.movement.MovementRules.updraftVelocity(vy);double before=p.getY();p.move(MoverType.SELF,new Vec3(0,vy,0));
                if(vy>0)h.assertTrue(p.getY()>before,"Glide collided with stacked vent at tick "+i);
            }
            h.assertTrue(p.getY()>base.getY()+4,"Glide did not pass all three vents");
        });
        add(tests,"updraft_subclass_and_mixed_collision",h->{
            var level=h.getLevel();var at=h.absolutePos(new BlockPos(5,20,5));
            var vents=net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream().filter(b->b instanceof UpdraftBlock).toList();
            h.assertTrue(vents.contains(OriBlocks.UPDRAFT.get())&&vents.contains(OriBlocks.GREAT_TREE_LANDING.get()),"Missing registered vent/cushion coverage");
            for(var block:vents){
                var state=block.defaultBlockState();level.setBlock(at,state,3);
                // A different subtype underneath must not erase this block's collision either.
                var supports=new ArrayList<Block>(vents);supports.remove(block);supports.add(Blocks.STONE);
                for(var below:supports){
                    level.setBlock(at.below(),below.defaultBlockState(),3);
                    var collision=state.getCollisionShape(level,at);var outline=state.getShape(level,at);
                    h.assertFalse(net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(collision,outline,net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME),
                            "Vent subclass lost its shape: "+block+" above "+below);
                }
            }
            level.setBlock(at.below(),Blocks.STONE.defaultBlockState(),3);
            for(var block:vents){
                var state=block.defaultBlockState();level.setBlock(at,state,3);
                double expected=block==OriBlocks.GREAT_TREE_LANDING.get()?.25:3.0/16;
                if(block==OriBlocks.GREAT_TREE_LANDING.get()||block==OriBlocks.UPDRAFT.get())
                    Fixtures.near(h,state.getCollisionShape(level,at).max(Direction.Axis.Y),expected,"Original single vent/cushion height");
            }
        });
        for(boolean matriarch:List.of(false,true))add(tests,(matriarch?"matriarch":"heart")+"_egg_fixed_home",h->{
            var p=Fixtures.player(h);var base=h.absolutePos(new BlockPos(5,25,5));var level=h.getLevel();level.setBlock(base,Blocks.STONE.defaultBlockState(),3);
            var egg=matriarch?OriItems.GLOOM_MATRIARCH_SPAWN_EGG.get():OriItems.GLOOM_HEART_SPAWN_EGG.get();p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(egg));
            egg.useOn(new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false)));
            var mobs=level.getEntitiesOfClass(Mob.class,new AABB(base).inflate(4),m->matriarch?m instanceof GloomMatriarch:m instanceof GloomHeart);
            try{
                h.assertValueEqual(mobs.size(),1,"Boss egg failed without altar");var mob=mobs.getFirst();var home=mob.blockPosition();double initialY=mob.getY();
                h.assertTrue(Math.abs(initialY-(base.getY()+1))<.1,"Egg added a non-vanilla vertical offset");
                for(int i=0;i<160;i++)mob.tick();
                var actual=matriarch?((GloomMatriarch)mob).home():((GloomHeart)mob).home();
                h.assertValueEqual(actual,home,"Hover origin drifted with current position");
                h.assertTrue(mob.getY()-initialY<12,"Hover climbed without bound");
                var tag=mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());mob.load(tag);
                h.assertValueEqual(matriarch?((GloomMatriarch)mob).home():((GloomHeart)mob).home(),home,"Reload changed hover center");
            }finally{mobs.forEach(Entity::discard);}
        });
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run){tests.add(new TestFunction("r116_runtime","r116_runtime."+name,"orimod_gametest:room",400,0,true,h->{run.accept(h);h.succeed();}));}
}
