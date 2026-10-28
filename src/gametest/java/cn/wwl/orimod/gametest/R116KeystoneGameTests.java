package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.network.*;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder("orimod_gametest")
public final class R116KeystoneGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        for(String danger:List.of("fire","lava","explosion","cactus"))add(tests,"survives_"+danger,h->{
            var item=drop(h);var damage=h.getLevel().damageSources();
            var source=switch(danger){case "fire"->damage.inFire();case "lava"->damage.lava();case "explosion"->damage.explosion(null,null);default->damage.cactus();};
            h.assertFalse(item.hurt(source,100),"Keystone accepted "+danger+" damage");h.assertTrue(item.isAlive(),"Key destroyed");
        });
        add(tests,"never_expires_after_reload",h->{
            var item=drop(h);var tag=item.saveWithoutId(new CompoundTag());tag.putShort("Age",(short)5999);
            var copy=drop(h);copy.load(tag);copy.tick();copy.tick();
            h.assertTrue(copy.isAlive(),"Key expired at vanilla item age");h.assertValueEqual(copy.getItem().getCount(),2,"Key count lost");
        });
        add(tests,"sneak_empty_hand_refunds_partial_door_once",h->{
            var p=Fixtures.player(h);var pos=h.absolutePos(new BlockPos(5,2,6));var level=h.getLevel();
            level.setBlock(pos,OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(),3);
            level.setBlock(pos.above(),OriBlocks.KEYSTONE_DOOR.get().defaultBlockState(),3);
            h.assertValueEqual(KeystoneDoorBlock.insert(p,pos,new ItemStack(OriItems.KEYSTONE.get())),1,"Door fixture did not consume one key");
            p.setShiftKeyDown(true);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false);
            level.getBlockState(pos).useWithoutItem(level,p,hit);level.getBlockState(pos).useWithoutItem(level,p,hit);
            h.assertValueEqual(p.getInventory().countItem(OriItems.KEYSTONE.get()),1,"Partial key not refunded exactly once");
            h.assertValueEqual(level.getBlockState(pos.above()).getValue(KeystoneDoorBlock.FILLED),0,"Legacy group retained credit after refund");
        });
        for(int version=0;version<=1;version++)for(var rotation:net.minecraft.world.level.block.Rotation.values()){
            final int v=version;
            add(tests,"binding_v"+v+"_"+rotation.name().toLowerCase(),h->{
                try(var a=new R116RuinFixture(h,v,rotation,0);var b=new R116RuinFixture(h,v,rotation,128)){
                    var p=Fixtures.player(h);p.getInventory().clearContent();
                    h.assertTrue(KeystonePedestalBlock.take(p,a.pedestals.getFirst()),"Own pedestal not usable");
                    var key=p.getInventory().getItem(0);var original=key.copy();
                    h.assertValueEqual(KeystoneDoorBlock.insert(p,b.doors.getFirst(),key),-1,"Foreign ruin accepted key");
                    h.assertTrue(ItemStack.matches(key,original),"Rejected key consumed/changed");
                    h.assertValueEqual(KeystoneDoorBlock.insert(p,a.doors.getFirst(),key),1,"Own key rejected");
                    p.getInventory().clearContent();p.setShiftKeyDown(true);
                    var door=a.doors.getFirst();var hit=new BlockHitResult(Vec3.atCenterOf(door),Direction.NORTH,door,false);
                    h.getLevel().getBlockState(door).useWithoutItem(h.getLevel(),p,hit);
                    var returned=p.getInventory().getItem(0);
                    h.assertTrue(ItemStack.matches(returned,original),"Refund lost binding");
                    h.assertValueEqual(KeystoneDoorBlock.insert(p,b.doors.getFirst(),returned),-1,"Refund became universal key");
                    h.assertValueEqual(KeystoneDoorBlock.insert(p,a.doors.getFirst(),returned),1,"Refund not reusable at home");
                    h.assertValueEqual(KeystoneDoorBlock.insert(p,a.doors.getFirst(),new ItemStack(OriItems.KEYSTONE.get())),0,"Old unbound key incompatible");
                    for(var cell:a.doors)h.assertFalse(h.getLevel().getBlockState(cell).is(OriBlocks.KEYSTONE_DOOR.get()),"Door did not open fully");
                }
            });
        }
        add(tests,"pedestal_full_day_then_open_stops_regeneration",h->{
            try(var a=new R116RuinFixture(h,1,net.minecraft.world.level.block.Rotation.NONE,0)){
                var p=Fixtures.player(h);var pos=a.pedestals.getFirst();var level=h.getLevel();long before=level.getGameTime();
                try{
                    h.assertTrue(KeystonePedestalBlock.take(p,pos),"Take failed");
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before+23999);tickPedestal(h,pos);
                    h.assertFalse(level.getBlockState(pos).getValue(KeystonePedestalBlock.FULL),"Respawn before 24000 ticks");
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before+24000);tickPedestal(h,pos);
                    h.assertTrue(level.getBlockState(pos).getValue(KeystonePedestalBlock.FULL),"No regeneration after one day");
                    h.assertTrue(KeystonePedestalBlock.take(p,pos),"Regenerated key unavailable");
                    for(int i=0;i<2;i++)KeystoneDoorBlock.insert(p,a.doors.getFirst(),new ItemStack(OriItems.KEYSTONE.get()));
                    ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before+48000);tickPedestal(h,pos);
                    h.assertFalse(level.getBlockState(pos).getValue(KeystonePedestalBlock.FULL),"Opened ruin regenerated key");
                }finally{((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before);}
            }
        });
        add(tests,"saved_deadline_and_deposit_keep_binding",h->{
            var level=h.getLevel();long before=level.getGameTime();
            var old=cn.wwl.orimod.world.KeystoneTimers.get(level);
            try(var a=new R116RuinFixture(h,0,net.minecraft.world.level.block.Rotation.CLOCKWISE_90,0)){
                var p=Fixtures.player(h);var pos=a.pedestals.getFirst();KeystonePedestalBlock.take(p,pos);
                var key=p.getInventory().getItem(0).copy();var original=key.copy();
                KeystoneDoorBlock.insert(p,a.doors.getFirst(),key);
                var be=(SanctuaryBlockEntity)level.getBlockEntity(a.doors.getFirst());
                var beTag=be.saveWithoutMetadata(level.registryAccess());be.loadWithComponents(beTag,level.registryAccess());
                p.getInventory().clearContent();p.setShiftKeyDown(true);var door=a.doors.getFirst();
                level.getBlockState(door).useWithoutItem(level,p,new BlockHitResult(Vec3.atCenterOf(door),Direction.NORTH,door,false));
                h.assertTrue(ItemStack.matches(original,p.getInventory().getItem(0)),"Reloaded deposit lost its exact binding");
                var saved=old.save(new CompoundTag(),level.registryAccess());
                level.getDataStorage().set(cn.wwl.orimod.world.KeystoneTimers.ID,cn.wwl.orimod.world.KeystoneTimers.load(saved,level.registryAccess()));
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before+23999);tickPedestal(h,pos);
                h.assertFalse(level.getBlockState(pos).getValue(KeystonePedestalBlock.FULL),"Reload shortened day cooldown");
                ((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before+24000);tickPedestal(h,pos);
                h.assertTrue(level.getBlockState(pos).getValue(KeystonePedestalBlock.FULL),"Reload reset day cooldown");
            }finally{((net.minecraft.world.level.storage.ServerLevelData)level.getLevelData()).setGameTime(before);level.getDataStorage().set(cn.wwl.orimod.world.KeystoneTimers.ID,old);}
        });
        add(tests,"refund_requires_empty_main_hand",h->{
            try(var a=new R116RuinFixture(h,1,net.minecraft.world.level.block.Rotation.CLOCKWISE_180,0)){
                var p=Fixtures.player(h);KeystonePedestalBlock.take(p,a.pedestals.getFirst());
                var original=p.getInventory().getItem(0).copy();KeystoneDoorBlock.insert(p,a.doors.getFirst(),p.getInventory().getItem(0));
                for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
                // Empty off-hand request cannot refund while the main hand is occupied.
                p.setShiftKeyDown(true);var door=a.doors.getFirst();var hit=new BlockHitResult(Vec3.atCenterOf(door),Direction.NORTH,door,false);
                h.getLevel().getBlockState(door).useWithoutItem(h.getLevel(),p,hit);
                h.assertValueEqual(h.getLevel().getBlockState(door).getValue(KeystoneDoorBlock.FILLED),1,"Occupied hand refunded key");
                p.getInventory().setItem(0,ItemStack.EMPTY);
                h.getLevel().getBlockState(door).useWithoutItem(h.getLevel(),p,hit);
                h.assertTrue(ItemStack.matches(original,p.getInventory().getItem(0)),"Available slot failed bound refund");
            }
        });
        return tests;
    }
    private static void tickPedestal(GameTestHelper h,BlockPos pos){var state=h.getLevel().getBlockState(pos);state.tick(h.getLevel(),pos,net.minecraft.util.RandomSource.create(1));}
    private static ItemEntity drop(GameTestHelper h){
        var pos=h.absolutePos(new BlockPos(4,2,4));h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),3);
        var item=new ItemEntity(h.getLevel(),pos.getX()+.5,pos.getY()+.05,pos.getZ()+.5,new ItemStack(OriItems.KEYSTONE.get(),2));
        item.setDeltaMovement(Vec3.ZERO);return item;
    }
    private static void add(List<TestFunction> tests,String name,Consumer<GameTestHelper> run){tests.add(new TestFunction("r116_key","r116_key."+name,"orimod_gametest:room",150,0,true,h->{run.accept(h);h.succeed();}));}
}
