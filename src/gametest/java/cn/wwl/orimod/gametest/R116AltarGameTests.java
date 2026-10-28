package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.world.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class R116AltarGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases(){
        var tests=new ArrayList<TestFunction>();
        for(int version=0;version<=1;version++)for(var rotation:Rotation.values()){
            int v=version;add(tests,"gate_v"+v+"_"+rotation.name().toLowerCase(),h->{
                try(var a=new R116RuinFixture(h,v,rotation,0);var b=new R116RuinFixture(h,v,rotation,128)){
                    var p=Fixtures.player(h);
                    var premature=GloomAltarBlock.summon(p,a.altar);if(premature!=null)premature.discard();
                    h.assertTrue(premature==null,"Never-activated altar bypassed sealed door");
                    for(int i=0;i<2;i++)KeystoneDoorBlock.insert(p,b.doors.getFirst(),new ItemStack(OriItems.KEYSTONE.get()));
                    premature=GloomAltarBlock.summon(p,a.altar);if(premature!=null)premature.discard();
                    h.assertTrue(premature==null,"Another ruin opened this altar");
                    for(var pedestal:a.pedestals)h.assertTrue(KeystonePedestalBlock.take(p,pedestal),"Key route missing pedestal");
                    var key=p.getInventory().getItem(0);
                    for(int i=0;i<2;i++)KeystoneDoorBlock.insert(p,a.doors.getFirst(),key);
                    var boss=GloomAltarBlock.summon(p,a.altar);
                    try{h.assertTrue(boss!=null,"Own open door did not release altar");h.assertValueEqual(boss.home(),a.altar,"Altar summon home changed");}finally{if(boss!=null)boss.discard();}
                    // activated survives a lost boss and a block-entity reload: no re-lock of historical progress.
                    var be=(SanctuaryBlockEntity)h.getLevel().getBlockEntity(a.altar);var tag=be.saveWithoutMetadata(h.getLevel().registryAccess());
                    be.loadWithComponents(tag,h.getLevel().registryAccess());
                    for(var cell:a.doors)h.getLevel().setBlock(cell,OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(),2);
                    boss=GloomAltarBlock.summon(p,a.altar);try{h.assertTrue(boss!=null,"Historical activation re-locked");}finally{if(boss!=null)boss.discard();}
                }
            });
        }
        add(tests,"reward_first_per_altar_then_ten",h->{
            var p=Fixtures.player(h);var origin=h.absoluteVec(new Vec3(6,3,6));
            try{
                EscapeSequence.start(p,origin);EscapeSequence.victory(p);int once=p.getData(SpiritData.PROGRESS).light();
                EscapeSequence.start(p,origin);EscapeSequence.victory(p);
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light()-once,10,"Repeat altar still gives 100 light");
                var tag=p.saveWithoutId(new net.minecraft.nbt.CompoundTag());var copy=Fixtures.player(h);copy.load(tag);
                EscapeSequence.start(copy,origin);EscapeSequence.victory(copy);
                h.assertValueEqual(copy.getData(SpiritData.PROGRESS).light()-p.getData(SpiritData.PROGRESS).light(),10,"Reload lost reward receipt");
                int before=copy.getData(SpiritData.PROGRESS).light();EscapeSequence.start(copy,origin.add(80,0,0));EscapeSequence.victory(copy);
                h.assertValueEqual(copy.getData(SpiritData.PROGRESS).light()-before,100,"Another altar lost first reward");
                var other=Fixtures.player(h);EscapeSequence.start(other,origin);EscapeSequence.victory(other);
                h.assertValueEqual(other.getData(SpiritData.PROGRESS).light(),100,"Another player lost first reward");
            }finally{EscapeSequence.stopAll();}
        });
        add(tests,"duplicate_victory_has_no_reward",h->{
            var p=Fixtures.player(h);try{EscapeSequence.start(p,h.absoluteVec(new Vec3(6,3,6)));EscapeSequence.victory(p);
                int before=p.getData(SpiritData.PROGRESS).light();EscapeSequence.victory(p);
                h.assertValueEqual(p.getData(SpiritData.PROGRESS).light(),before,"Duplicate callback repeated reward");
            }finally{EscapeSequence.stop(p);}
        });
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run){tests.add(new TestFunction("r116_altar","r116_altar."+name,"orimod_gametest:room",400,0,true,h->{run.accept(h);h.succeed();}));}
}
