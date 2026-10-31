package cn.wwl.orimod.gametest;

import cn.wwl.orimod.block.*;
import cn.wwl.orimod.entity.WillWispEntity;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.progression.*;
import cn.wwl.orimod.spirit.*;
import cn.wwl.orimod.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

@GameTestHolder("orimod_p5")
public final class P5StoryGameTests {
    static boolean done(ServerPlayer p,String id) {
        var advancement=p.server.getAdvancements().get(ResourceLocation.parse("orimod:"+id));
        return advancement!=null&&p.getAdvancements().getOrStartProgress(advancement).isDone();
    }
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests=new ArrayList<TestFunction>();
        add(tests,"core_inventory_event",(h,p)->{
            h.assertFalse(done(p,"story/root"),"Login invented a core milestone");
            p.getInventory().setItem(0,new ItemStack(OriItems.SPIRIT_CORE.get()));p.inventoryMenu.broadcastChanges();
            h.assertTrue(done(p,"story/root"),"Vanilla inventory event failed");
            h.assertFalse(done(p,"story/spirit"),"Acquiring core also awarded form");
        });
        add(tests,"core_use_and_first_form",(h,p)->{
            p.setData(SpiritData.AWAKENED,false);p.setData(SpiritData.FORM,false);
            p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(OriItems.SPIRIT_CORE.get()));
            OriItems.SPIRIT_CORE.get().use(h.getLevel(),p,InteractionHand.MAIN_HAND);
            h.assertTrue(done(p,"story/root")&&done(p,"story/spirit"),"Core use did not award milestones");
            h.assertFalse(done(p,"story/skill"),"Awakening invented an ability");
        });
        add(tests,"rejected_then_successful_unlock",(h,p)->{
            SpiritProgression.request(p,"ability","dash");h.assertFalse(done(p,"story/skill"),"Rejected purchase awarded skill");
            SpiritProgression.grantLight(p,20);SpiritProgression.request(p,"ability","dash");
            h.assertTrue(Ability.DASH.unlocked(p)&&done(p,"story/skill"),"Real unlock missing milestone");
        });
        add(tests,"tree_interaction",(h,p)->{
            var at=p.blockPosition().east();h.getLevel().setBlock(at,OriBlocks.SPIRIT_TREE_HEART.get().defaultBlockState(),2);
            h.assertFalse(SpiritTreeHeartBlock.claim(p,at),"Human claim accepted");h.assertFalse(done(p,"story/explore"),"Refused tree claimed discovery");
            p.setData(SpiritData.FORM,true);h.assertTrue(SpiritTreeHeartBlock.claim(p,at),"Tree claim refused");
            h.assertTrue(done(p,"story/explore")&&done(p,"story/skill"),"Tree blessing failed milestones");
        });
        add(tests,"shrine_interaction",(h,p)->{
            var at=p.blockPosition().east();h.getLevel().setBlock(at,OriBlocks.FOREST_SHRINE.get().defaultBlockState(),2);
            SpiritChallenge.offer(p,at,RuinKind.FOREST);h.assertTrue(done(p,"story/explore"),"Shrine event missing");
        });
        for(var kind:WillWisp.values())add(tests,"pickup_"+kind.id(),(h,p)->{
            WillWisps.give(p,7^kind.bit);
            var at=p.blockPosition();WillWisps.offer(p,kind,h.getLevel(),at);
            var claim=RestorationData.get(p.server).claim(p.getUUID(),kind);
            var entity=(WillWispEntity)h.getLevel().getEntity(claim.entity());h.assertTrue(entity!=null,"Pickup was not materialized");
            h.assertFalse(done(p,"story/all_wisps"),"No pickup but milestone already awarded");
            p.setPos(entity.position());h.assertTrue(WillWisps.pickup(p,entity),"Pickup refused");
            h.assertTrue(done(p,"story/"+kind.id()+"_wisp")&&done(p,"story/all_wisps"),"Pickup event missing milestones");
        });
        add(tests,"legacy_login_backfills_only_saved_progress",(h,p)->{
            p.setData(SpiritData.ABILITIES,Ability.DASH.bit);p.setData(SpiritData.PROGRESS,p.getData(SpiritData.PROGRESS).withRewards(Map.of("discover/old",1,"purify/old",1)));
            StoryProgress.login(new PlayerEvent.PlayerLoggedInEvent(p));
            h.assertTrue(done(p,"story/root")&&done(p,"story/skill")&&done(p,"story/explore")&&done(p,"story/blight"),"Saved milestones lost");
            h.assertFalse(done(p,"story/ginso")||done(p,"story/all_wisps")||done(p,"restoration"),"Login awarded unproven milestones");
        });
        add(tests,"ginso_start_then_success",(h,p)->{
            var heart=h.absolutePos(new BlockPos(4,5,4));var piece=new GinsoPiece(heart.below(5),115117,0,0);
            h.getLevel().setBlock(heart,OriBlocks.GINSO_WATER_HEART.get().defaultBlockState(),2);
            p.setData(SpiritData.FORM,true);p.refreshDimensions();p.setData(SpiritData.ABILITIES,GinsoClimbEscape.REQUIRED.stream().mapToInt(a->a.bit).reduce(0,(a,b)->a|b));
            p.setPos(Vec3.atBottomCenterOf(piece.point(piece.escapeSafe())));p.setOnGround(true);
            try {
                h.assertTrue(GinsoEscape.start(p,piece),"Escape refused");h.assertTrue(done(p,"story/ginso"),"Start not awarded");
                h.assertFalse(done(p,"story/ginso_escape"),"Start also awarded success");
                for(int i=0;i<61;i++)GinsoEscape.tick(p);
                var exit=piece.escape().exit();p.setPos(Vec3.atBottomCenterOf(piece.point(new BlockPos((int)((exit.x0()+exit.x1())*.5),(int)exit.y0(),(int)((exit.z0()+exit.z1())*.5)))));
                p.setOnGround(true);GinsoEscape.tick(p);h.assertTrue(done(p,"story/ginso_escape"),"Actual success missing milestone");
            }finally{GinsoEscape.disconnect(p);p.setData(SpiritData.GINSO_ESCAPE_RECOVERY,GinsoEscapeRecovery.EMPTY);}
        });
        add(tests,"purification_completion_receipt",(h,p)->{
            var center=p.blockPosition().east(2);var soil=center.below();var level=h.getLevel();
            level.setBlock(soil,OriBlocks.BLIGHTED_SOIL.get().defaultBlockState(),2);
            level.setBlock(center,OriBlocks.GLOOM_CORE.get().defaultBlockState(),2);
            var data=BlightData.get(level);data.register(center,new long[]{soil.asLong()});
            for(int i=0;i<4;i++)BlightInteractions.hit(p,center,BlightRules.Source.SMASH);
            h.assertFalse(done(p,"story/blight"),"Core break skipped restoration wave");
            for(int i=0;i<600;i++)data.advance(level);
            h.assertTrue(data.completed(center)&&done(p,"story/blight"),"Purification receipt did not award progress");
        });
        add(tests,"restoration_keeps_legacy_advancement_id",(h,p)->{
            var tree=p.blockPosition().east();h.getLevel().setBlock(tree,OriBlocks.GREAT_TREE_HEART.get().defaultBlockState(),2);
            p.setData(SpiritData.FORM,true);WillWisps.give(p,7);
            try {
                h.assertTrue(Restoration.start(p,tree),"Ritual rejected");h.assertFalse(done(p,"restoration"),"Start awarded ending");
                h.assertTrue(Restoration.finish(p)&&done(p,"restoration"),"Ending event did not award retained ID");
            } finally {Restoration.cancel(p);}
        });
        return tests;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,ServerPlayer> body) {
        tests.add(new TestFunction("p5_story",name,"orimod_p5:platform_room",net.minecraft.world.level.block.Rotation.NONE,200,0,true,h->{
            try(var c=Fixtures.connectedPlayer(h)){body.accept(h,c.player());}h.succeed();
        }));
    }
}
