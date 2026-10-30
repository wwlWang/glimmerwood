package cn.wwl.orimod.entity;

import cn.wwl.orimod.config.OriServerConfig;
import cn.wwl.orimod.item.OriItems;
import cn.wwl.orimod.world.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.horse.*;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;
import java.util.function.Supplier;

public final class CorruptedAnimals {
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,"orimod");
    public static final Supplier<AttachmentType<CorruptionState>> STATE=TYPES.register("animal_corruption",()->AttachmentType.builder(()->CorruptionState.CLEAN)
            .serialize(CorruptionState.CODEC).build());
    public static final Supplier<AttachmentType<Boolean>> VIEW=TYPES.register("animal_corruption_view",()->AttachmentType.builder(()->false).sync(ByteBufCodecs.BOOL).build());
    private static final class Entry {
        final Animal animal;long seen;CorruptAnimalGoal goal;
        Entry(Animal a,long now){animal=a;seen=now;}
    }
    private static final Map<UUID,Entry> LOADED=new HashMap<>();
    private static final ArrayDeque<UUID> QUEUE=new ArrayDeque<>();
    private static int lastCandidates,lastChanges,lastNavigation;
    public static void register(IEventBus bus){TYPES.register(bus);}
    public static boolean supported(Entity e){return e.getType()==EntityType.COW||e.getType()==EntityType.PIG||e.getType()==EntityType.SHEEP||e.getType()==EntityType.CHICKEN||e.getType()==EntityType.RABBIT
            ||e.getType()==EntityType.HORSE||e.getType()==EntityType.DONKEY||e.getType()==EntityType.MULE;}
    public static boolean infected(Entity e){return supported(e)&&(e.level().isClientSide?e.getData(VIEW):e.getData(STATE).infected());}
    public static void join(EntityJoinLevelEvent event){if(event.getLevel() instanceof ServerLevel l&&supported(event.getEntity())){
        var a=(Animal)event.getEntity();var old=LOADED.put(a.getUUID(),new Entry(a,l.getServer().getTickCount()));
        if(old!=null)detach(old);else QUEUE.add(a.getUUID());
        // Resolve completed/orphan owners before the entity's first AI tick; no owner chunk access.
        if(infected(a))a.setData(VIEW,!restoreDue(a));
    }}
    public static void leave(EntityLeaveLevelEvent event){if(event.getLevel() instanceof ServerLevel&&supported(event.getEntity())){
        var e=LOADED.get(event.getEntity().getUUID());if(e!=null&&e.animal==event.getEntity()){LOADED.remove(e.animal.getUUID());QUEUE.remove(e.animal.getUUID());detach(e);}
        // Unloading/dimension transfer retain the reservation; permanent removal cannot leak a slot.
        var animal=(Animal)event.getEntity();var reason=animal.getRemovalReason();
        if(reason!=null&&reason.shouldDestroy()&&infected(animal)){
            var owner=ownerLevel(animal);if(owner!=null)BlightData.get(owner).releaseAnimal(animal.getData(STATE).center(),animal.getUUID());
        }
    }}
    private static ServerLevel ownerLevel(Animal a){
        var id=ResourceLocation.tryParse(a.getData(STATE).dimension());return id==null?null:a.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
    }
    public static boolean ownerActive(Animal a){if(!infected(a))return false;var l=ownerLevel(a);return l!=null&&BlightData.get(l).activeCore(a.getData(STATE).center());}
    private static boolean restoreDue(Animal a){var l=ownerLevel(a);var s=a.getData(STATE);return l==null||BlightData.get(l).animalRestoreDue(s.center(),s.distance());}
    public static void restore(Animal a){
        var l=ownerLevel(a);if(l!=null)BlightData.get(l).releaseAnimal(a.getData(STATE).center(),a.getUUID());
        var e=LOADED.get(a.getUUID());if(e!=null)detach(e);state(a,CorruptionState.CLEAN);
    }
    public static void state(Animal a,CorruptionState state){a.setData(STATE,state);if(a.getData(VIEW)!=state.infected())a.setData(VIEW,state.infected());}
    private static void detach(Entry e){if(e.goal!=null){e.goal.stop();e.animal.goalSelector.removeGoal(e.goal);e.goal=null;}}
    public static void tick(ServerTickEvent.Post event){step(event.getServer());}
    public static void step(net.minecraft.server.MinecraftServer server){step(server,server.getTickCount());}
    public static void step(net.minecraft.server.MinecraftServer server,long now){
        lastCandidates=lastChanges=lastNavigation=0;int count=Math.min(CorruptionRules.CANDIDATES,QUEUE.size());
        for(int i=0;i<count;i++){
            var id=QUEUE.remove();var e=LOADED.get(id);if(e==null)continue;QUEUE.add(id);var a=e.animal;
            if(a.getServer()!=server||a.isRemoved()||!a.isAlive())continue;lastCandidates++;
            int dt=(int)Math.clamp(now-e.seen,0,20);e.seen=now;var s=a.getData(STATE);
            if(s.infected()){
                if(restoreDue(a)){if(lastChanges<CorruptionRules.CHANGES){restore(a);lastChanges++;}continue;}
                if(!ownerActive(a)||!OriServerConfig.blightEnabled()){detach(e);continue;}
                if(e.goal==null){e.goal=new CorruptAnimalGoal(a);a.goalSelector.addGoal(1,e.goal);}
                if(lastNavigation<CorruptionRules.NAVIGATIONS){if(e.goal.decide(now))lastNavigation++;}
                continue;
            }
            if(!OriServerConfig.blightEnabled()||!OriServerConfig.blightAnimals()||a.isNoAi()||a.isInvulnerable()||a.isVehicle()){
                if(s.exposure()!=0)state(a,CorruptionState.CLEAN);continue;
            }
            var l=(ServerLevel)a.level();var data=BlightData.get(l);var owner=data.activeAt(a.position());
            if(owner.isEmpty()){if(s.exposure()!=0)state(a,CorruptionState.CLEAN);continue;}
            String dim=l.dimension().location().toString();boolean same=dim.equals(s.dimension())&&owner.get().asLong()==s.core();
            int ticks=CorruptionRules.exposure(s.exposure(),dt,same,true);double distance=Math.min(64,Math.hypot(a.getX()-owner.get().getX(),a.getZ()-owner.get().getZ()));
            boolean infect=ticks>=OriServerConfig.blightExposure()&&lastChanges<CorruptionRules.CHANGES&&data.claimAnimal(owner.get(),id,OriServerConfig.blightAnimalCap());
            state(a,new CorruptionState(dim,owner.get().asLong(),ticks,infect,distance));
            if(infect){lastChanges++;a.resetLove();}
        }
    }
    /** Production loot only. Equipment/chest contents are emitted by vanilla's separate equipment path. */
    public static boolean replaceLoot(Animal animal){return infected(animal)&&ownerActive(animal);}
    public static void drops(LivingDropsEvent event){if(event.getEntity() instanceof Animal a&&infected(a)){
        var l=ownerLevel(a);if(l!=null)BlightData.get(l).releaseAnimal(a.getData(STATE).center(),a.getUUID());
    }}
    public static void interact(PlayerInteractEvent.EntityInteract event){
        if(event.getTarget() instanceof Animal a&&infected(a)){
            var item=event.getItemStack();boolean product=item.is(Items.BUCKET)||item.is(Items.SHEARS);
            if(a.isFood(item)||product){event.setCanceled(true);event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);}
        }
    }
    public static Map<String,Integer> metrics(){return Map.of("candidates",lastCandidates,"changes",lastChanges,"navigation",lastNavigation,"loaded",LOADED.size());}
    public static void clear(){for(var e:LOADED.values())detach(e);LOADED.clear();QUEUE.clear();}
    private CorruptedAnimals(){}
}
