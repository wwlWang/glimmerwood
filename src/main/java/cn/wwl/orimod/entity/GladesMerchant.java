package cn.wwl.orimod.entity;

import cn.wwl.orimod.progression.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Stationary, non-hostile camp residents. Vanilla goal/look control drives head turning; no walking goals. */
public final class GladesMerchant extends PathfinderMob {
    private static final EntityDataAccessor<Integer> JOB=SynchedEntityData.defineId(GladesMerchant.class,EntityDataSerializers.INT);
    private BlockPos well,anchor;private float facing;
    private long stockDay=Long.MIN_VALUE;
    private final Map<UUID,List<String>> stocks=new HashMap<>();
    public GladesMerchant(EntityType<? extends GladesMerchant> type,Level level){super(type,level);setPersistenceRequired();setInvulnerable(true);}
    public static AttributeSupplier.Builder attributes(){return createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,0).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new LookAtPlayerGoal(this,Player.class,8,1));}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){super.defineSynchedData(b);b.define(JOB,0);}
    public int job(){return entityData.get(JOB);}
    public BlockPos well(){return well;}
    public void bind(int job,BlockPos well,BlockPos anchor,float yaw){entityData.set(JOB,job==1?1:0);this.well=well.immutable();this.anchor=anchor.immutable();facing=yaw;moveTo(anchor.getX()+.5,anchor.getY(),anchor.getZ()+.5,yaw,0);}
    @Override public Component getName(){return Component.translatable(job()==0?"merchant.orimod.mosswhisper":"merchant.orimod.lulu");}
    public List<String> stock(ServerPlayer player){
        if(job()==1)return List.of("tree","blight");
        long day=MerchantRules.day(level().getDayTime());if(day!=stockDay){stocks.clear();stockDay=day;}
        return stocks.computeIfAbsent(player.getUUID(),id->MerchantRules.stock(getUUID().getMostSignificantBits()^getUUID().getLeastSignificantBits()^id.getMostSignificantBits()^id.getLeastSignificantBits()^day*0x9E3779B97F4A7C15L,player.getData(cn.wwl.orimod.spirit.SpiritData.PROGRESS).ownedShards()));
    }
    @Override public void tick(){super.tick();if(!level().isClientSide && anchor!=null){setDeltaMovement(Vec3.ZERO);setPos(Vec3.atBottomCenterOf(anchor));setYBodyRot(facing);}}
    @Override public boolean hurt(DamageSource source,float amount){
        // Survival combat stays blocked; /kill and creative punches still remove the merchant.
        if(source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))return super.hurt(source,amount);
        if(source.getEntity() instanceof Player attacker && attacker.getAbilities().instabuild)return super.hurt(source,amount);
        return false;
    }
    @Override public boolean isPushable(){return false;}
    @Override public void push(Entity e){}
    @Override public void push(double x,double y,double z){}
    @Override public void knockback(double strength,double x,double z){}
    @Override public boolean canBeLeashed(){return false;}
    @Override protected boolean canAddPassenger(Entity e){return false;}
    @Override public boolean startRiding(Entity e,boolean force){return false;}
    @Override public void travel(Vec3 motion){setDeltaMovement(Vec3.ZERO);}
    @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){if(p instanceof ServerPlayer server)MerchantTrading.open(server,this);return InteractionResult.sidedSuccess(level().isClientSide);}
    @Override public void addAdditionalSaveData(CompoundTag tag){
        super.addAdditionalSaveData(tag);tag.putInt("Job",job());tag.putFloat("Facing",facing);if(well!=null)tag.putLong("Well",well.asLong());if(anchor!=null)tag.putLong("Anchor",anchor.asLong());tag.putLong("StockDay",stockDay);
        var list=new ListTag();stocks.forEach((id,stock)->{var v=new CompoundTag();v.putUUID("Player",id);var items=new ListTag();stock.forEach(s->items.add(StringTag.valueOf(s)));v.put("Items",items);list.add(v);});tag.put("Stock",list);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag){
        super.readAdditionalSaveData(tag);entityData.set(JOB,tag.getInt("Job")==1?1:0);facing=tag.getFloat("Facing");well=tag.contains("Well")?BlockPos.of(tag.getLong("Well")):null;anchor=tag.contains("Anchor")?BlockPos.of(tag.getLong("Anchor")):null;stockDay=tag.getLong("StockDay");stocks.clear();
        for(var t:tag.getList("Stock",Tag.TAG_COMPOUND)){var v=(CompoundTag)t;if(!v.hasUUID("Player"))continue;var items=new ArrayList<String>();for(var item:v.getList("Items",Tag.TAG_STRING)){String id=item.getAsString();if(Shard.byId(id)!=null && !items.contains(id) && items.size()<3)items.add(id);}stocks.put(v.getUUID("Player"),List.copyOf(items));}
    }
}
