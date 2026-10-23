package cn.wwl.orimod.gametest;

import cn.wwl.orimod.entity.*;
import cn.wwl.orimod.item.*;
import cn.wwl.orimod.spirit.SpiritData;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.*;
import java.util.function.BiConsumer;

/** R114: real players, including mode changes after an attack has already been telegraphed. */
@GameTestHolder("orimod_gametest")
public final class GameplaySafetyGameTests {
    @GameTestGenerator public static Collection<TestFunction> cases() {
        var tests = new ArrayList<TestFunction>();
        for (String kind : List.of("spitter", "beetle", "hawk", "heart", "matriarch")) {
            for (var mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) add(tests, kind + "_rejects_" + mode.getName(), (h,p) -> {
                var mob = mob(h, kind);
                try {
                    p.setGameMode(mode); mob.setTarget(p);
                    h.assertTrue(mob.getTarget() == null, kind + " retained protected target");
                    h.assertFalse(mob.canAttack(p), kind + " canAttack protected player");
                } finally { mob.discard(); }
            });
        }
        for (boolean invulnerable : List.of(true, false)) add(tests, "creative_contact_invulnerable_" + invulnerable, (h,p) -> {
            // /ori god can turn creative's vanilla ability OFF. Creative targeting must still be safe.
            var hawk = (GloomHawk) mob(h, "hawk");
            var shot = new GloomShot(OriMobs.GLOOM_SHOT.get(), h.getLevel()); shot.setOwner(hawk);
            try {
                hawk.telegraph(p); p.setGameMode(GameType.CREATIVE); p.getAbilities().invulnerable = invulnerable;
                h.assertFalse(hawk.strike(p), "Dive struck creative player after mode switch");
                h.assertFalse(shot.strike(p), "Orb struck creative player");
                h.assertValueEqual(p.getHealth(), 20f, "Creative health changed");
            } finally { hawk.discard(); shot.discard(); }
        });
        add(tests, "creative_gust_has_no_push", (h,p) -> {
            var boss = (GloomMatriarch) mob(h, "matriarch");
            try {
                p.setGameMode(GameType.CREATIVE); p.setDeltaMovement(Vec3.ZERO);
                boss.gust(); h.assertValueEqual(p.getDeltaMovement(), Vec3.ZERO, "Creative player pushed by gust");
            } finally { boss.discard(); }
        });
        add(tests, "hammer_waits_for_ground", (h,p) -> {
            p.setData(SpiritData.FORM, true); p.setData(SpiritData.ABILITIES, 65535);
            p.setPos(p.position().add(0,6,0)); p.setOnGround(false);
            var target = h.spawn(EntityType.HUSK, new Vec3(5.5,8,7.7)); target.setNoAi(true); target.setNoGravity(true);
            var hammer = new ItemStack(OriItems.SPIRIT_HAMMER.get()); p.setItemInHand(InteractionHand.MAIN_HAND,hammer);
            try {
                hammer.getItem().releaseUsing(hammer, h.getLevel(), p, 72000 - SpiritHammerItem.WIND_UP_TICKS);
                h.assertValueEqual(target.getHealth(), target.getMaxHealth(), "Hammer impact resolved in midair");
            } finally { target.discard(); }
        });
        add(tests, "retargeted_charge_and_dive_cancel", (h,p) -> {
            var spitter=(GloomSpitter)mob(h,"spitter"); var hawk=(GloomHawk)mob(h,"hawk");
            var heart=(GloomHeart)mob(h,"heart"); var mother=(GloomMatriarch)mob(h,"matriarch");
            try {
                spitter.performRangedAttack(p,1); hawk.telegraph(p); heart.startAttack(GloomHeart.VOLLEY,p); mother.startAttack(GloomMatriarch.DIVE_TELEGRAPH,p);
                p.setGameMode(GameType.CREATIVE);
                h.assertTrue(spitter.tickCharge()==null,"Protected player got charged shot");
                h.assertValueEqual(spitter.charge(),0,"Spitter kept charging protected player");
                hawk.step();h.assertValueEqual(hawk.state(),GloomHawk.CIRCLE,"Hawk retained dive");
                heart.tickAttack();h.assertValueEqual(heart.attack(),GloomHeart.IDLE,"Heart retained volley");
                mother.step();h.assertValueEqual(mother.attack(),GloomMatriarch.IDLE,"Matriarch retained dive");
            }finally{spitter.discard();hawk.discard();heart.discard();mother.discard();}
        });
        add(tests, "survival_contacts_still_hurt", (h,p)->{
            var hawk=(GloomHawk)mob(h,"hawk");var shot=new GloomShot(OriMobs.GLOOM_SHOT.get(),h.getLevel());shot.setOwner(hawk);
            try { h.assertTrue(hawk.strike(p),"Survival dive was disabled");p.invulnerableTime=0;
                h.assertTrue(shot.strike(p),"Survival projectile was disabled");
            }finally{hawk.discard();shot.discard();}
        });
        for(String weapon:List.of("blade","bow","hammer","spear")) for(boolean swap:List.of(false,true))
            add(tests,weapon+"_ends_with_movement_swap_"+swap,(h,p)->{
                p.setData(SpiritData.FORM,true);p.refreshDimensions();p.setData(SpiritData.ABILITIES,65535);
                p.setOnGround(true);p.setDeltaMovement(Vec3.ZERO);
                var item=switch(weapon){case "blade"->OriItems.SPIRIT_BLADE.get();case "bow"->OriItems.SPIRIT_BOW.get();case "hammer"->OriItems.SPIRIT_HAMMER.get();default->OriItems.SPIRIT_SPEAR.get();};
                var stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
                if(weapon.equals("blade"))cn.wwl.orimod.combat.SpiritCombat.attack(p);
                else item.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
                if(swap)p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(net.minecraft.world.item.Items.STICK));
                if(p.isUsingItem()) {item.releaseUsing(stack,h.getLevel(),p,item.getUseDuration(stack,p)-20);p.stopUsingItem();}
                var combat=p.getData(SpiritData.COMBAT);
                if(weapon.equals("blade"))combat.startedAt-=cn.wwl.orimod.combat.SpiritCombat.duration(combat.stage);
                cn.wwl.orimod.combat.SpiritCombat.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
                movement(h,p);
                for(var e:h.getLevel().getEntities(p,p.getBoundingBox().inflate(12),e->e instanceof net.minecraft.world.entity.projectile.Projectile projectile && projectile.getOwner()==p))e.discard();
            });
        add(tests,"hammer_lands_once_and_unlocks",(h,p)->{
            p.setData(SpiritData.FORM,true);p.refreshDimensions();p.setData(SpiritData.ABILITIES,65535);p.setPos(p.position().add(0,8,0));p.setOnGround(false);
            cn.wwl.orimod.combat.HammerSlam.release(p);
            var state=p.getData(SpiritData.COMBAT);state.startedAt-=40;
            cn.wwl.orimod.combat.HammerSlam.tick(p);
            h.assertValueEqual(state.mode,cn.wwl.orimod.combat.HammerSlam.MODE,"Slam ended before ground");
            p.setPos(h.absoluteVec(new Vec3(5.5,2,5.5)));p.setOnGround(true);
            var target=h.spawn(EntityType.HUSK,new Vec3(5.5,2,7.7));target.setNoAi(true);
            try {
                cn.wwl.orimod.combat.SpiritCombat.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
                h.assertTrue(target.getHealth()<target.getMaxHealth(),"Landing did not smash");
                float health=target.getHealth();target.invulnerableTime=0;
                cn.wwl.orimod.combat.SpiritCombat.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
                h.assertValueEqual(target.getHealth(),health,"Repeated landing damage");movement(h,p);
            }finally{target.discard();}
        });
        for(String cause:List.of("timeout","water","void","death","form","dimension","teleport","logout"))add(tests,"hammer_cancel_"+cause,(h,p)->{
            p.setData(SpiritData.FORM,true);p.refreshDimensions();p.setPos(p.position().add(0,8,0));p.setOnGround(false);
            cn.wwl.orimod.combat.HammerSlam.release(p);
            var state=p.getData(SpiritData.COMBAT);
            switch(cause){
                case "timeout"->state.startedAt-=cn.wwl.orimod.combat.HammerSlam.TIMEOUT;
                case "water"->{h.getLevel().setBlockAndUpdate(p.blockPosition(),net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());p.baseTick();}
                case "void"->p.setPos(p.getX(),p.level().getMinBuildHeight()-1,p.getZ());
                case "death"->p.setHealth(0);
                case "form"->p.setData(SpiritData.FORM,false);
                case "teleport"->cn.wwl.orimod.movement.SpiritMovement.teleported(p);
                case "dimension"->cn.wwl.orimod.combat.SpiritCombat.changedDimension(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent(p,net.minecraft.world.level.Level.OVERWORLD,net.minecraft.world.level.Level.NETHER));
                case "logout"->cn.wwl.orimod.combat.SpiritCombat.logout(new net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(p));
            }
            cn.wwl.orimod.combat.SpiritCombat.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
            h.assertValueEqual(state.mode,"idle","Slam retained after "+cause);
            h.assertValueEqual(p.getData(SpiritData.COMBAT_VIEW).mode(),"idle","Client retained slam after "+cause);
        });
        return tests;
    }
    private static void movement(GameTestHelper h, net.minecraft.server.level.ServerPlayer p) {
        h.assertValueEqual(p.getData(SpiritData.COMBAT).mode,"idle","Weapon retained combat mode");
        h.assertFalse(p.isUsingItem(),"Weapon retained using-item state");
        var state=p.getData(SpiritData.MOVEMENT);long now=p.level().getGameTime();
        cn.wwl.orimod.movement.SpiritMovement.accept(p,new cn.wwl.orimod.network.MovementInputPayload(1,now,1,0,0,0,0));
        cn.wwl.orimod.movement.SpiritMovement.accept(p,new cn.wwl.orimod.network.MovementInputPayload(2,now,1|2,0,0,0,0));
        h.assertValueEqual(state.acceptedJumps,1,"Jump refused after weapon");
        h.assertFalse(p.onGround(),"Jump did not leave ground");
        cn.wwl.orimod.movement.SpiritMovement.accept(p,new cn.wwl.orimod.network.MovementInputPayload(3,now,1|4,0,0,0,0));
        h.assertValueEqual(state.acceptedDashes,1,"Air dash refused after weapon");
        cn.wwl.orimod.movement.SpiritMovement.cancel(p,state);
    }
    private static Mob mob(GameTestHelper h, String kind) {
        var type = switch(kind) { case "spitter" -> OriMobs.GLOOM_SPITTER.get(); case "beetle" -> OriMobs.GLOOM_BEETLE.get();
            case "hawk" -> OriMobs.GLOOM_HAWK.get(); case "heart" -> OriMobs.GLOOM_HEART.get(); default -> OriMobs.GLOOM_MATRIARCH.get(); };
        var mob = type.create(h.getLevel()); mob.setPos(h.absoluteVec(new Vec3(7.5,2,5.5))); mob.setNoAi(true); return mob;
    }
    private static void add(List<TestFunction> tests,String name,BiConsumer<GameTestHelper,net.minecraft.server.level.ServerPlayer> test) {
        tests.add(new TestFunction("gameplay_safety", "gameplay_safety."+name,"orimod_gametest:platform_room",100,0,true,h->{
            var server=h.getLevel().getServer(); var old=server.getWorldData().getDifficulty(); server.setDifficulty(Difficulty.NORMAL,true);
            try(var c=Fixtures.connectedPlayer(h)) { var p=c.player(); for(int i=0;i<61;i++)p.tick();p.setHealth(20);p.invulnerableTime=0;
                test.accept(h,p);h.succeed();
            } finally { server.setDifficulty(old,true); }
        }));
    }
}
